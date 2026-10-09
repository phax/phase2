/*
 * The FreeBSD Copyright
 * Copyright 1994-2008 The FreeBSD Project. All rights reserved.
 * Copyright (C) 2013-2025 Philip Helger philip[at]helger[dot]com
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE FREEBSD PROJECT ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE FREEBSD PROJECT OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official policies, either expressed or implied, of the FreeBSD Project.
 */
package com.helger.as2lib.processor.receiver.net;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.as2lib.cert.ECertificatePartnershipType;
import com.helger.as2lib.cert.ICertificateFactory;
import com.helger.as2lib.crypto.IMICMatchingHandler;
import com.helger.as2lib.crypto.LoggingMICMatchingHandler;
import com.helger.as2lib.crypto.MIC;
import com.helger.as2lib.disposition.DispositionType;
import com.helger.as2lib.exception.AS2Exception;
import com.helger.as2lib.exception.WrappedAS2Exception;
import com.helger.as2lib.message.AS2Message;
import com.helger.as2lib.message.AS2MessageMDN;
import com.helger.as2lib.message.IMessageMDN;
import com.helger.as2lib.processor.AS2NoModuleException;
import com.helger.as2lib.processor.receiver.AS2MDNReceiverModule;
import com.helger.as2lib.processor.receiver.AbstractActiveNetModule;
import com.helger.as2lib.processor.storage.IProcessorStorageModule;
import com.helger.as2lib.session.AS2ComponentNotFoundException;
import com.helger.as2lib.util.AS2Helper;
import com.helger.as2lib.util.AS2HttpHelper;
import com.helger.as2lib.util.AS2IOHelper;
import com.helger.as2lib.util.AS2ResourceHelper;
import com.helger.as2lib.util.dump.IHTTPIncomingDumper;
import com.helger.as2lib.util.http.AS2HttpClient;
import com.helger.as2lib.util.http.AS2HttpRequestDataProviderInputStream;
import com.helger.as2lib.util.http.AS2HttpResponseHandlerSocket;
import com.helger.as2lib.util.http.HTTPHelper;
import com.helger.as2lib.util.http.IAS2HttpResponseHandler;
import com.helger.as2lib.util.http.IAS2IncomingMDNCallback;
import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.annotation.OverrideOnDemand;
import com.helger.commons.collection.ArrayHelper;
import com.helger.commons.http.CHttp;
import com.helger.commons.http.CHttpHeader;
import com.helger.commons.io.file.FileHelper;
import com.helger.commons.io.file.FilenameHelper;
import com.helger.commons.io.stream.NonBlockingBufferedReader;
import com.helger.commons.io.stream.NonBlockingByteArrayOutputStream;
import com.helger.commons.io.stream.StreamHelper;
import com.helger.commons.state.ESuccess;
import com.helger.commons.state.ETriState;
import com.helger.commons.string.StringHelper;
import com.helger.commons.string.StringParser;
import com.helger.commons.timing.StopWatch;
import com.helger.mail.datasource.ByteArrayDataSource;

import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;

/**
 * The main handler for receiving AS2 async MDN messages.
 *
 * @author Philip Helger
 */
public class AS2MDNReceiverHandler extends AbstractReceiverHandler
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AS2MDNReceiverHandler.class);

  private final AS2MDNReceiverModule m_aReceiverModule;
  private IMICMatchingHandler m_aMICMatchingHandler = new LoggingMICMatchingHandler ();
  private IAS2IncomingMDNCallback m_aIncomingMDNCallback;

  /**
   * @param aModule
   *        The receiver module for attributes, session etc. May not be <code>null</code>.
   */
  public AS2MDNReceiverHandler (@Nonnull final AS2MDNReceiverModule aModule)
  {
    m_aReceiverModule = ValueEnforcer.notNull (aModule, "Module");
  }

  /**
   * @return The receiver module passed in the constructor. Never <code>null</code>.
   */
  @Nonnull
  public final AS2MDNReceiverModule getModule ()
  {
    return m_aReceiverModule;
  }

  /**
   * @return The current MIC matching handler. Never <code>null</code>.
   * @since 4.4.0
   */
  @Nonnull
  public final IMICMatchingHandler getMICMatchingHandler ()
  {
    return m_aMICMatchingHandler;
  }

  /**
   * Set the MIC matching handler to used.
   *
   * @param aMICMatchingHandler
   *        The new handler. May not be <code>null</code>.
   * @since 4.4.0
   */
  public final void setMICMatchingHandler (@Nonnull final IMICMatchingHandler aMICMatchingHandler)
  {
    ValueEnforcer.notNull (aMICMatchingHandler, "MICMatchingHandler");
    m_aMICMatchingHandler = aMICMatchingHandler;
  }

  /**
   * @return The incoming MDN callback. May be <code>null</code>.
   * @since v4.7.1
   */
  @Nullable
  public final IAS2IncomingMDNCallback getIncomingMDNCallback ()
  {
    return m_aIncomingMDNCallback;
  }

  /**
   * Set the incoming MDN callback that is invoked for each received MDN.
   *
   * @param aIMC
   *        The callback to be invoked. May be null.
   * @since v4.7.1
   */
  public final void setIncomingMDNCallback (@Nullable final IAS2IncomingMDNCallback aIMC)
  {
    m_aIncomingMDNCallback = aIMC;
  }

  /**
   * Get the pending info file itself.
   *
   * @param aMsg
   *        Source message. Never <code>null</code>.
   * @return The file.
   * @throws AS2Exception
   *         In case of error
   * @since 4.10.2
   */
  @Nullable
  protected File getPendingInfoFile (@Nonnull final AS2Message aMsg) throws AS2Exception
  {
    // use original message id. to open the pending information file
    // from pendinginfo folder.
    final String sOrigMessageID = aMsg.getMDN ().attrs ().getAsString (AS2MessageMDN.MDNA_ORIG_MESSAGEID);

    final String sPendingInfoFolder = AS2IOHelper.getSafeFileAndFolderName (getModule ().getSession ()
                                                                                        .getMessageProcessor ()
                                                                                        .getPendingMDNInfoFolder ());
    if (StringHelper.hasNoText (sPendingInfoFolder))
    {
      LOGGER.error ("The pending MDN info folder is not properly configured. Cannot check for async MDNs.");
      return null;
    }

    return new File (sPendingInfoFolder +
                     FilenameHelper.UNIX_SEPARATOR_STR +
                     AS2IOHelper.getFilenameFromMessageID (sOrigMessageID));
  }

  /**
   * Delete the pending info file for reading.
   *
   * @param aMsg
   *        Source message. Never <code>null</code>.
   * @return <code>null</code> if open failed.
   * @throws AS2Exception
   *         In case of error
   * @since 4.10.2
   */
  @Nullable
  @OverrideOnDemand
  protected InputStream openPendingInfoStreamForReading (@Nonnull final AS2Message aMsg) throws AS2Exception
  {
    final File aPendingInfoFile = getPendingInfoFile (aMsg);
    if (aPendingInfoFile == null)
      return null;

    LOGGER.info ("Trying to read original MIC and message ID information from file '" +
                 aPendingInfoFile.getAbsolutePath () +
                 "'" +
                 aMsg.getLoggingText ());

    final InputStream ret = FileHelper.getInputStream (aPendingInfoFile);
    if (ret == null)
    {
      LOGGER.error ("The pending info file '" +
                    aPendingInfoFile.getAbsolutePath () +
                    "' with the original MIC could not be opened for reading");
    }
    return ret;
  }

  /**
   * Delete the pending info file.
   *
   * @param aMsg
   *        Source message. Never <code>null</code>.
   * @return {@link ESuccess}
   * @throws AS2Exception
   *         In case of error
   * @since 4.10.2
   */
  @OverrideOnDemand
  protected ESuccess deletePendingInfoStream (@Nonnull final AS2Message aMsg) throws AS2Exception
  {
    final File aPendingInfoFile = getPendingInfoFile (aMsg);

    LOGGER.info ("Delete pendinginfo file '" + aPendingInfoFile.getAbsolutePath () + "'" + aMsg.getLoggingText ());

    if (!aPendingInfoFile.delete ())
    {
      LOGGER.error ("Error delete pendinginfo file '" + aPendingInfoFile.getAbsolutePath () + "'");
      return ESuccess.FAILURE;
    }
    return ESuccess.SUCCESS;
  }

  /**
   * Delete the pending file itself.
   *
   * @param aMsg
   *        Source message. Never <code>null</code>.
   * @param sPendingFilename
   *        Pending filename
   * @return {@link ESuccess}
   * @throws AS2Exception
   *         In case of error
   * @since 4.10.2
   */
  @OverrideOnDemand
  protected ESuccess deletePendingFile (@Nonnull final AS2Message aMsg, @Nonnull final String sPendingFilename)
                                                                                                                throws AS2Exception
  {
    final File aPendingFile = new File (sPendingFilename);
    LOGGER.info ("Delete pending file '" + aPendingFile.getAbsolutePath () + "'" + aMsg.getLoggingText ());

    if (!aPendingFile.delete ())
    {
      LOGGER.error ("Error delete pending file '" + aPendingFile.getAbsolutePath () + "'");
      return ESuccess.FAILURE;
    }
    return ESuccess.SUCCESS;
  }

  /**
   * verify if the mic is matched.
   *
   * @param aMsg
   *        Message
   * @return <code>true</code> if the MDN was processed, <code>false</code> e.g. on MIC mismatch
   * @throws AS2Exception
   *         In case of error; e.g. MIC mismatch
   */
  public boolean checkAsyncMDN (@Nonnull final AS2Message aMsg) throws AS2Exception
  {
    try
    {
      // get the returned mic from mdn object
      final String sReturnMIC = aMsg.getMDN ().attrs ().getAsString (AS2MessageMDN.MDNA_MIC);
      final MIC aReturnMIC = MIC.parse (sReturnMIC);

      final String sOriginalMIC;
      final MIC aOriginalMIC;
      final String sPendingFilename;
      try (final InputStream aIS = openPendingInfoStreamForReading (aMsg))
      {
        if (aIS == null)
        {
          return false;
        }

        // Charset must be aligned with AS2SenderModule
        try (final NonBlockingBufferedReader aPendingInfoReader = new NonBlockingBufferedReader (StreamHelper.createReader (aIS,
                                                                                                                            StandardCharsets.ISO_8859_1)))
        {
          // Get the original MIC from the first line of pending information
          // file
          sOriginalMIC = aPendingInfoReader.readLine ();
          aOriginalMIC = MIC.parse (sOriginalMIC);

          // Get the original pending file from the second line of pending
          // information file
          sPendingFilename = aPendingInfoReader.readLine ();
        }
      }

      final String sDisposition = aMsg.getMDN ().attrs ().getAsString (AS2MessageMDN.MDNA_DISPOSITION);

      LOGGER.info ("received MDN [" + sDisposition + "]" + aMsg.getLoggingText ());

      final boolean bMICMatch = aOriginalMIC != null && aReturnMIC != null && aReturnMIC.equals (aOriginalMIC);

      if (!bMICMatch)
      {
        LOGGER.info ("MIC was not matched, so the pending file '" + sPendingFilename + "' will NOT be deleted.");

        // MIC was not matched
        m_aMICMatchingHandler.onMICMismatch (aMsg, sOriginalMIC, sReturnMIC);
        return false;
      }

      // MIC was matched
      m_aMICMatchingHandler.onMICMatch (aMsg, sReturnMIC);

      // delete the pending info & pending file if MIC is matched
      deletePendingInfoStream (aMsg);

      deletePendingFile (aMsg, sPendingFilename);
    }
    catch (final IOException | AS2ComponentNotFoundException ex)
    {
      LOGGER.error ("Error checking async MDN", ex);
      return false;
    }
    return true;
  }

  // Asynch MDN 2007-03-12
  /**
   * method for receiving and processing Async MDN sent from receiver.
   *
   * @param aMsg
   *        The MDN message
   * @param aData
   *        The MDN content
   * @param aResponseHandler
   *        The HTTP response handler for setting the correct HTTP response code
   * @param aResHelper
   *        Resource helper
   * @throws AS2Exception
   *         In case of error
   * @throws IOException
   *         In case of IO error
   */
  protected final void receiveMDN (@Nonnull final AS2Message aMsg,
                                   final byte [] aData,
                                   @Nonnull final IAS2HttpResponseHandler aResponseHandler,
                                   @Nonnull final AS2ResourceHelper aResHelper) throws AS2Exception, IOException
  {
    try
    {
      // Create a MessageMDN and copy HTTP headers
      final IMessageMDN aMDN = new AS2MessageMDN (aMsg);
      // copy headers from msg to MDN from msg
      aMDN.headers ().setAllHeaders (aMsg.headers ());

      final MimeBodyPart aPart = new MimeBodyPart (AS2HttpHelper.getAsInternetHeaders (aMDN.headers ()), aData);
      aMDN.setData (aPart);

      // get the MDN partnership info
      aMDN.partnership ().setSenderAS2ID (aMDN.getHeader (CHttpHeader.AS2_FROM));
      aMDN.partnership ().setReceiverAS2ID (aMDN.getHeader (CHttpHeader.AS2_TO));
      // Set the appropriate keystore aliases
      aMDN.partnership ().setSenderX509Alias (aMsg.partnership ().getSenderX509Alias ());
      aMDN.partnership ().setReceiverX509Alias (aMsg.partnership ().getReceiverX509Alias ());
      // Update the partnership
      getModule ().getSession ().getPartnershipFactory ().updatePartnership (aMDN, false);

      final ICertificateFactory aCertFactory = getModule ().getSession ().getCertificateFactory ();
      final X509Certificate aSenderCert = aCertFactory.getCertificate (aMDN, ECertificatePartnershipType.SENDER);

      final boolean bUseCertificateInBodyPart;
      final ETriState eUseCertificateInBodyPart = aMsg.partnership ().getVerifyUseCertificateInBodyPart ();
      if (eUseCertificateInBodyPart.isDefined ())
      {
        // Use per partnership
        bUseCertificateInBodyPart = eUseCertificateInBodyPart.getAsBooleanValue ();
      }
      else
      {
        // Use global value
        bUseCertificateInBodyPart = getModule ().getSession ().isCryptoVerifyUseCertificateInBodyPart ();
      }

      AS2Helper.parseMDN (aMsg,
                          aSenderCert,
                          bUseCertificateInBodyPart,
                          getVerificationCertificateConsumer (),
                          aResHelper);

      // in order to name & save the mdn with the original AS2-From + AS2-To +
      // Message id.,
      // the 3 msg attributes have to be reset before calling MDNFileModule
      aMsg.partnership ().setSenderAS2ID (aMDN.getHeader (CHttpHeader.AS2_TO));
      aMsg.partnership ().setReceiverAS2ID (aMDN.getHeader (CHttpHeader.AS2_FROM));
      getModule ().getSession ().getPartnershipFactory ().updatePartnership (aMsg, false);
      aMsg.setMessageID (aMDN.attrs ().getAsString (AS2MessageMDN.MDNA_ORIG_MESSAGEID));
      try
      {
        getModule ().getSession ().getMessageProcessor ().handle (IProcessorStorageModule.DO_STOREMDN, aMsg, null);
      }
      catch (final AS2ComponentNotFoundException | AS2NoModuleException ex)
      {
        // No message processor found
        // Or no module found in message processor
      }

      // check if the mic (message integrity check) is correct
      final boolean bMICMatch = checkAsyncMDN (aMsg);
      HTTPHelper.sendSimpleHTTPResponse (aResponseHandler, bMICMatch ? CHttp.HTTP_OK : CHttp.HTTP_NOT_FOUND);

      final String sDisposition = aMDN.attrs ().getAsString (AS2MessageMDN.MDNA_DISPOSITION);

      if (m_aIncomingMDNCallback != null)
        m_aIncomingMDNCallback.onIncomingMDN (false,
                                              aMDN,
                                              aMDN.getHeader (CHttpHeader.AS2_FROM),
                                              aMDN.getHeader (CHttpHeader.AS2_TO),
                                              sDisposition,
                                              aMDN.attrs ().getAsString (AS2MessageMDN.MDNA_MIC),
                                              aMDN.attrs ().getAsString (AS2MessageMDN.MDNA_ORIG_MESSAGEID),
                                              aMDN.attrs ().getAsBoolean (AS2Message.ATTRIBUTE_RECEIVED_SIGNED, false),
                                              bMICMatch);

      DispositionType.createFromString (sDisposition).validate (aMsg, aMDN.getText ());
    }
    catch (final IOException ex)
    {
      HTTPHelper.sendSimpleHTTPResponse (aResponseHandler, CHttp.HTTP_BAD_REQUEST);
      throw ex;
    }
    catch (final Exception ex)
    {
      HTTPHelper.sendSimpleHTTPResponse (aResponseHandler, CHttp.HTTP_BAD_REQUEST);
      throw WrappedAS2Exception.wrap (ex).setSourceMsg (aMsg);
    }
  }

  public void reparse (@Nonnull final AS2Message aMsg,
                       @Nonnull final AS2HttpClient aHttpClient,
                       @Nullable final IHTTPIncomingDumper aIncomingDumper) throws AS2Exception
  {
    // Create a MessageMDN and copy HTTP headers
    final IMessageMDN aMDN = new AS2MessageMDN (aMsg);
    // Bug in ph-commons 9.1.3 in addAllHeaders!
    aMDN.headers ().addAllHeaders (aHttpClient.getResponseHeaderFields ());

    // Receive the MDN data
    byte [] aMDNBytes = null;
    try (final NonBlockingByteArrayOutputStream aMDNStream = new NonBlockingByteArrayOutputStream ())
    {
      final InputStream aIS = aHttpClient.getInputStream ();

      // Retrieve the whole message content
      // The Content-Length header is not used as a copy limit, because it may not match the
      // bytes delivered by the HTTP client (e.g. Content-Encoding). Validate only.
      StreamHelper.copyByteStream ().from (aIS).closeFrom (true).to (aMDNStream).closeTo (false).build ();

      final long nContentLength = StringParser.parseLong (aMDN.getHeader (CHttpHeader.CONTENT_LENGTH), -1);
      if (nContentLength >= 0 && nContentLength != aMDNStream.size ())
        LOGGER.warn ("The Content-Length header of the MDN (" +
                     nContentLength +
                     ") does not match the number of bytes read (" +
                     aMDNStream.size () +
                     ")");

      aMDNBytes = aMDNStream.toByteArray ();
    }
    catch (final IOException ex)
    {
      LOGGER.error ("Error reparsing", ex);
    }

    if (aIncomingDumper != null)
      aIncomingDumper.dumpIncomingRequest (aMDN.headers ().getAllHeaderLines (true),
                                           aMDNBytes != null ? aMDNBytes : ArrayHelper.EMPTY_BYTE_ARRAY,
                                           aMDN);

    MimeBodyPart aPart = null;
    if (aMDNBytes != null)
      try
      {
        aPart = new MimeBodyPart (AS2HttpHelper.getAsInternetHeaders (aMDN.headers ()), aMDNBytes);
      }
      catch (final MessagingException ex)
      {
        LOGGER.error ("Error creating MimeBodyPart for received MDN", ex);
      }
    aMDN.setData (aPart);

    // get the MDN partnership info
    aMDN.partnership ().setSenderAS2ID (aMDN.getHeader (CHttpHeader.AS2_FROM));
    aMDN.partnership ().setReceiverAS2ID (aMDN.getHeader (CHttpHeader.AS2_TO));
  }

  public void handleIncomingMessage (@Nonnull @Nonempty final String sClientInfo,
                                     @Nonnull final DataSource aMsgData,
                                     @Nonnull final AS2Message aMsg,
                                     @Nonnull final IAS2HttpResponseHandler aResponseHandler)
  {
    // Read in the message request, headers, and data
    try (final AS2ResourceHelper aResHelper = new AS2ResourceHelper ())
    {
      final byte [] aData = StreamHelper.getAllBytes (aMsgData.getInputStream ());

      // Asynch MDN 2007-03-12
      // check if the requested URL is defined in attribute "as2_receipt_option"
      // in one of partnerships, if yes, then process incoming AsyncMDN
      final String sReceivedContentType = AS2HttpHelper.getCleanContentType (aMsg.getHeader (CHttpHeader.CONTENT_TYPE));

      final MimeBodyPart aReceivedPart = new MimeBodyPart (AS2HttpHelper.getAsInternetHeaders (aMsg.headers ()), aData);
      aMsg.setData (aReceivedPart);

      // MimeBodyPart receivedPart = new MimeBodyPart();
      aReceivedPart.setDataHandler (new ByteArrayDataSource (aData, sReceivedContentType, null).getAsDataHandler ());
      // Must be set AFTER the DataHandler!
      aReceivedPart.setHeader (CHttpHeader.CONTENT_TYPE, sReceivedContentType);

      aMsg.setData (aReceivedPart);

      receiveMDN (aMsg, aData, aResponseHandler, aResHelper);

      LOGGER.info ("Received async MDN " + sClientInfo + aMsg.getLoggingText ());
    }
    catch (final Exception ex)
    {
      m_aReceiverModule.handleError (aMsg, WrappedAS2Exception.wrap (ex));
    }
  }

  public void handle (@Nonnull final AbstractActiveNetModule aOwner, @Nonnull final Socket aSocket)
  {
    final String sClientInfo = getClientInfo (aSocket);
    LOGGER.info ("incoming connection for receiving AsyncMDN [" + sClientInfo + "]");

    final AS2Message aMsg = new AS2Message ();
    final boolean bQuoteHeaderValues = m_aReceiverModule.isQuoteHeaderValues ();
    final IAS2HttpResponseHandler aResponseHandler = new AS2HttpResponseHandlerSocket (aSocket, bQuoteHeaderValues);

    // Time the transmission
    final StopWatch aSW = StopWatch.createdStarted ();
    DataSource aMdnDataSource = null;
    try
    {
      // Read in the message request, headers, and data
      final IHTTPIncomingDumper aIncomingDumper = getEffectiveHttpIncomingDumper ();
      aMdnDataSource = HTTPHelper.readAndDecodeHttpRequest (new AS2HttpRequestDataProviderInputStream (aSocket.getInputStream ()),
                                                            aResponseHandler,
                                                            aMsg,
                                                            aIncomingDumper);
    }
    catch (final Exception ex)
    {
      new AS2NetException (aSocket.getInetAddress (), aSocket.getPort (), ex).terminate ();
    }

    aSW.stop ();

    if (aMdnDataSource == null)
    {
      LOGGER.error ("Not having a data source to operate on");
    }
    else
    {
      if (aMdnDataSource instanceof ByteArrayDataSource)
      {
        LOGGER.info ("received " +
                     AS2IOHelper.getTransferRate (((ByteArrayDataSource) aMdnDataSource).directGetBytes ().length,
                                                  aSW) +
                     " from " +
                     sClientInfo +
                     aMsg.getLoggingText ());

      }
      else
      {
        LOGGER.info ("received message from " +
                     sClientInfo +
                     aMsg.getLoggingText () +
                     " in " +
                     aSW.getMillis () +
                     " ms");
      }

      handleIncomingMessage (sClientInfo, aMdnDataSource, aMsg, aResponseHandler);
    }
  }
}
