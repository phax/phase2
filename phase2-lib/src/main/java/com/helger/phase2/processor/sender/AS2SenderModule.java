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
package com.helger.phase2.processor.sender;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.function.Consumer;

import org.bouncycastle.mail.smime.SMIMECompressedGenerator;
import org.bouncycastle.mail.smime.SMIMEException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonempty;
import com.helger.annotation.style.OverrideOnDemand;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.base.io.stream.StreamHelper;
import com.helger.base.state.ETriState;
import com.helger.base.string.StringHelper;
import com.helger.base.string.StringParser;
import com.helger.base.system.ENewLineMode;
import com.helger.base.timing.StopWatch;
import com.helger.http.CHttpHeader;
import com.helger.http.EHttpMethod;
import com.helger.http.header.HttpHeaderMap;
import com.helger.io.file.FileHelper;
import com.helger.io.file.FilenameHelper;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.mime.CMimeType;
import com.helger.phase2.cert.ECertificatePartnershipType;
import com.helger.phase2.cert.ICertificateFactory;
import com.helger.phase2.crypto.ECompressionType;
import com.helger.phase2.crypto.ECryptoAlgorithmCrypt;
import com.helger.phase2.crypto.ECryptoAlgorithmSign;
import com.helger.phase2.crypto.IMICMatchingHandler;
import com.helger.phase2.crypto.LoggingMICMatchingHandler;
import com.helger.phase2.crypto.MIC;
import com.helger.phase2.disposition.AS2DispositionException;
import com.helger.phase2.disposition.DispositionType;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.WrappedAS2Exception;
import com.helger.phase2.message.AS2Message;
import com.helger.phase2.message.AS2MessageMDN;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.message.IMessageMDN;
import com.helger.phase2.params.AS2InvalidParameterException;
import com.helger.phase2.partner.CPartnershipIDs;
import com.helger.phase2.partner.Partnership;
import com.helger.phase2.processor.AS2NoModuleException;
import com.helger.phase2.processor.CFileAttribute;
import com.helger.phase2.processor.CNetAttribute;
import com.helger.phase2.processor.storage.IProcessorStorageModule;
import com.helger.phase2.session.AS2ComponentNotFoundException;
import com.helger.phase2.util.AS2DateHelper;
import com.helger.phase2.util.AS2Helper;
import com.helger.phase2.util.AS2HttpHelper;
import com.helger.phase2.util.AS2IOHelper;
import com.helger.phase2.util.AS2ResourceHelper;
import com.helger.phase2.util.CAS2Header;
import com.helger.phase2.util.dump.IHTTPIncomingDumper;
import com.helger.phase2.util.dump.IHTTPOutgoingDumper;
import com.helger.phase2.util.http.AS2HttpClient;
import com.helger.phase2.util.http.AS2HttpHeaderSetter;
import com.helger.phase2.util.http.IAS2IncomingMDNCallback;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;

/**
 * AS2 sender module to send AS2 messages out.
 *
 * @author Philip Helger
 */
public class AS2SenderModule extends AbstractHttpSenderModule
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AS2SenderModule.class);

  private IMICMatchingHandler m_aMICMatchingHandler = new LoggingMICMatchingHandler ();
  private IAS2IncomingMDNCallback m_aIncomingMDNCallback;
  private Consumer <? super X509Certificate> m_aVerificationCertificateConsumer;

  public AS2SenderModule ()
  {}

  /**
   * @return The current MIC matching handler. Never <code>null</code>.
   * @since 4.4.0
   */
  @NonNull
  public final IMICMatchingHandler getMICMatchingHandler ()
  {
    return m_aMICMatchingHandler;
  }

  /**
   * Set the MIC matching handler to used.
   *
   * @param aMICMatchingHandler
   *        The new handler. May not be <code>null</code>.
   * @return this for chaining
   * @since 4.4.0
   */
  @NonNull
  public final AS2SenderModule setMICMatchingHandler (@NonNull final IMICMatchingHandler aMICMatchingHandler)
  {
    ValueEnforcer.notNull (aMICMatchingHandler, "MICMatchingHandler");
    m_aMICMatchingHandler = aMICMatchingHandler;
    return this;
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
   * @return The consumer for the effective certificate upon signature verification. May be
   *         <code>null</code>. The default is <code>null</code>.
   * @since 4.4.1
   */
  @Nullable
  public final Consumer <? super X509Certificate> getVerificationCertificateConsumer ()
  {
    return m_aVerificationCertificateConsumer;
  }

  /**
   * Set the consumer for the effective certificate upon signature verification.
   *
   * @param aVerificationCertificateConsumer
   *        The consumer to be used. May be <code>null</code>.
   * @return this for chaining
   * @since 4.4.1
   */
  @NonNull
  public final AS2SenderModule setVerificationCertificateConsumer (@Nullable final Consumer <? super X509Certificate> aVerificationCertificateConsumer)
  {
    m_aVerificationCertificateConsumer = aVerificationCertificateConsumer;
    return this;
  }

  public boolean canHandle (@NonNull final String sAction,
                            @NonNull final IMessage aMsg,
                            @Nullable final Map <String, Object> aOptions)
  {
    return IProcessorSenderModule.DO_SEND.equals (sAction) && aMsg instanceof AS2Message;
  }

  protected void checkRequired (@NonNull final AS2Message aMsg) throws AS2InvalidParameterException
  {
    final Partnership aPartnership = aMsg.partnership ();

    try
    {
      AS2InvalidParameterException.checkValue (aMsg, "ContentType", aMsg.getContentType ());
      AS2InvalidParameterException.checkValue (aMsg,
                                               "Attribute: " + CPartnershipIDs.PA_AS2_URL,
                                               aPartnership.getAS2URL ());
      AS2InvalidParameterException.checkValue (aMsg,
                                               "Receiver: " + CPartnershipIDs.PID_AS2,
                                               aPartnership.getReceiverAS2ID ());
      AS2InvalidParameterException.checkValue (aMsg,
                                               "Sender: " + CPartnershipIDs.PID_AS2,
                                               aPartnership.getSenderAS2ID ());
      AS2InvalidParameterException.checkValue (aMsg, "Subject", aMsg.getSubject ());
      AS2InvalidParameterException.checkValue (aMsg,
                                               "Sender: " + CPartnershipIDs.PID_EMAIL,
                                               aPartnership.getSenderEmail ());
      AS2InvalidParameterException.checkValue (aMsg, "Message Data", aMsg.getData ());
    }
    catch (final AS2InvalidParameterException ex)
    {
      ex.setSourceMsg (aMsg);
      throw ex;
    }
  }

  /**
   * Override this method to store pending MDN messages somewhere else than on disc.
   *
   * @param aMsg
   *        The message for which an async MDN is requested. Never <code>null</code>.
   * @param sMsgFilename
   *        The filename (without the path), derived from the Message ID, in which the data should
   *        be stored.
   * @return <code>null</code> if the configuration is not correct.
   * @throws AS2Exception
   *         In case of an error
   * @since 4.10.2
   */
  @Nullable
  @OverrideOnDemand
  protected OutputStream openPendingInfoStreamForWriting (@NonNull final AS2Message aMsg,
                                                          @NonNull @Nonempty final String sMsgFilename) throws AS2Exception
  {
    // The file that is written
    final String sPendingMDNInfoFolder = AS2IOHelper.getSafeFileAndFolderName (getSession ().getMessageProcessor ()
                                                                                            .getPendingMDNInfoFolder ());
    if (StringHelper.isEmpty (sPendingMDNInfoFolder))
    {
      LOGGER.error ("The pending MDN info folder is not properly configured. Cannot store async MDN data.");
      return null;
    }

    final File aPendingInfoFile = new File (sPendingMDNInfoFolder + FilenameHelper.UNIX_SEPARATOR_STR + sMsgFilename);

    LOGGER.info ("Saving original MIC and message id information into file '" +
                 aPendingInfoFile.getAbsolutePath () +
                 "'" +
                 aMsg.getLoggingText ());

    return FileHelper.getOutputStream (aPendingInfoFile);
  }

  /**
   * For storing original MIC and outgoing file into pending information file. Override this method
   * if you want to store the pending MDN information in a separate data storage like a DB etc.
   *
   * @param aMsg
   *        AS2Message. May not be <code>null</code>.
   * @param aMIC
   *        MIC value. May not be <code>null</code>.
   * @throws AS2Exception
   *         In case of an error
   */
  protected void storePendingInfo (@NonNull final AS2Message aMsg, @NonNull final MIC aMIC) throws AS2Exception
  {
    ValueEnforcer.notNull (aMsg, "Msg");
    ValueEnforcer.notNull (aMIC, "MIC");

    try
    {
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Original MIC is '" + aMIC.getAsAS2String () + "'" + aMsg.getLoggingText ());

      // The filename is created here, but the file content is placed there
      // from somewhere else
      final String sPendingMDNFolder = AS2IOHelper.getSafeFileAndFolderName (getSession ().getMessageProcessor ()
                                                                                          .getPendingMDNFolder ());
      if (StringHelper.isEmpty (sPendingMDNFolder))
      {
        LOGGER.error ("The pending MDN folder is not properly configured. Cannot store async MDN data.");
        return;
      }

      final String sMsgFilename = AS2IOHelper.getFilenameFromMessageID (aMsg.getMessageID ());
      final String sPendingFilename = sPendingMDNFolder + FilenameHelper.UNIX_SEPARATOR_STR + sMsgFilename;

      try (final OutputStream aOS = openPendingInfoStreamForWriting (aMsg, sMsgFilename))
      {
        if (aOS == null)
        {
          // Happens on misconfiguration
          return;
        }

        // input pending folder & original outgoing file name to get and
        // unique file name in order to avoid file overwriting.
        try (final Writer aWriter = StreamHelper.createWriter (aOS, StandardCharsets.ISO_8859_1))
        {
          // Write in 2 lines
          aWriter.write (aMIC.getAsAS2String () + ENewLineMode.DEFAULT.getText () + sPendingFilename);
        }

        // remember that MDN is pending
        aMsg.attrs ().putIn (CFileAttribute.MA_PENDING_FILENAME, sPendingFilename);
        aMsg.attrs ().putIn (CFileAttribute.MA_STATUS, CFileAttribute.MA_STATUS_PENDING);
      }
    }
    catch (final IOException ex)
    {
      throw WrappedAS2Exception.wrap (ex).setSourceMsg (aMsg);
    }
  }

  /**
   * From RFC 4130 section 7.3.1:
   * <ul>
   * <li>For any signed messages, the MIC to be returned is calculated on the RFC1767/RFC3023 MIME
   * header and content. Canonicalization on the MIME headers MUST be performed before the MIC is
   * calculated, since the sender requesting the signed receipt was also REQUIRED to
   * canonicalize.</li>
   * <li>For encrypted, unsigned messages, the MIC to be returned is calculated on the decrypted RFC
   * 1767/RFC3023 MIME header and content. The content after decryption MUST be canonicalized before
   * the MIC is calculated.</li>
   * <li>For unsigned, unencrypted messages, the MIC MUST be calculated over the message contents
   * without the MIME or any other RFC 2822 headers, since these are sometimes altered or reordered
   * by Mail Transport Agents (MTAs).</li>
   * </ul>
   * So headers must be included if signing or crypting is enabled.<br>
   * <br>
   * From RFC 5402 section 4.1:
   * <ul>
   * <li>MIC Calculation for Signed Message: For any signed message, the MIC to be returned is
   * calculated over the same data that was signed in the original message as per [AS1]. The signed
   * content will be a MIME bodypart that contains either compressed or uncompressed data.</li>
   * <li>MIC Calculation for Encrypted, Unsigned Message: For encrypted, unsigned messages, the MIC
   * to be returned is calculated over the uncompressed data content including all MIME header
   * fields and any applied Content-Transfer-Encoding.</li>
   * <li>MIC Calculation for Unencrypted, Unsigned Message: For unsigned, unencrypted messages, the
   * MIC is calculated over the uncompressed data content including all MIME header fields and any
   * applied Content-Transfer-Encoding</li>
   * </ul>
   * So headers must always be included if compression is enabled.
   *
   * @param aMsg
   *        Source message
   * @return MIC value. Neither <code>null</code> nor empty.
   * @throws Exception
   *         On security or AS2 issues
   */
  @NonNull
  protected MIC calculateAndStoreMIC (@NonNull final AS2Message aMsg) throws Exception
  {
    final Partnership aPartnership = aMsg.partnership ();

    // Calculate and get the original mic
    final boolean bIncludeHeadersInMIC = aPartnership.getSigningAlgorithm () != null ||
                                         aPartnership.getEncryptAlgorithm () != null ||
                                         aPartnership.getCompressionType () != null;

    // For sending, we need to use the Signing algorithm defined in the
    // partnership
    final String sSigningAlgorithm = aPartnership.getSigningAlgorithm ();
    ECryptoAlgorithmSign eSigningAlgorithm = ECryptoAlgorithmSign.getFromIDOrNull (sSigningAlgorithm);
    if (eSigningAlgorithm == null)
    {
      // If no valid algorithm is defined, fall back to the defaults
      final boolean bUseRFC3851MICAlg = aPartnership.isRFC3851MICAlgs ();
      eSigningAlgorithm = bUseRFC3851MICAlg ? ECryptoAlgorithmSign.DEFAULT_RFC_3851
                                            : ECryptoAlgorithmSign.DEFAULT_RFC_5751;

      LOGGER.warn ("The partnership signing algorithm name '" +
                   sSigningAlgorithm +
                   "' is unknown. Fallbacking back to the default '" +
                   eSigningAlgorithm.getID () +
                   "'");
    }

    final MIC aMIC = AS2Helper.getCryptoHelper ()
                              .calculateMIC (aMsg.getData (), eSigningAlgorithm, bIncludeHeadersInMIC);
    aMsg.attrs ().putIn (AS2Message.ATTRIBUTE_MIC, aMIC.getAsAS2String ());

    if (aPartnership.getAS2ReceiptDeliveryOption () != null)
    {
      // Async MDN is requested
      // if yes : PA_AS2_RECEIPT_OPTION != null
      // then keep the original mic & message id.
      // then wait for the another HTTP call by receivers
      storePendingInfo (aMsg, aMIC);
    }

    return aMIC;
  }

  @NonNull
  public static MimeBodyPart compressMimeBodyPart (@NonNull final MimeBodyPart aData,
                                                   @NonNull final ECompressionType eCompressionType,
                                                   @NonNull final EContentTransferEncoding eCTE) throws SMIMEException
  {
    ValueEnforcer.notNull (aData, "Data");
    ValueEnforcer.notNull (eCompressionType, "CompressionType");
    ValueEnforcer.notNull (eCTE, "ContentTransferEncoding");

    final SMIMECompressedGenerator aCompressedGenerator = new SMIMECompressedGenerator ();

    // Content-Transfer-Encoding to use
    aCompressedGenerator.setContentTransferEncoding (eCTE.getID ());

    // This call might modify the original mime part and add "Content-Type" and
    // "Content-Transfer-Encoding" header
    return aCompressedGenerator.generate (aData, eCompressionType.createOutputCompressor ());
  }

  private static void _logMimeBodyPart (@NonNull final MimeBodyPart aMimePart, @NonNull final String sContext)
                                                                                                               throws IOException,
                                                                                                               MessagingException
  {
    // Should always be false in production
    if (false)
    {
      LOGGER.info ("[[" + sContext + "]]");
      try (final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ())
      {
        aMimePart.writeTo (aBAOS);
        LOGGER.info (aBAOS.getAsString (StandardCharsets.ISO_8859_1));
      }
      LOGGER.info ("[[END]]");
    }
  }

  @NonNull
  public static MimeBodyPart secureMimeBodyPart (@NonNull final MimeBodyPart aSrcPart,
                                                 @NonNull final EContentTransferEncoding eCTE,
                                                 @Nullable final ECompressionType eCompressionType,
                                                 final boolean bCompressBeforeSign,
                                                 @Nullable final Consumer <MimeBodyPart> aCompressBeforeSignCallback,
                                                 @Nullable final ECryptoAlgorithmSign eSignAlgorithm,
                                                 @Nullable final X509Certificate aSenderCert,
                                                 @Nullable final PrivateKey aSenderKey,
                                                 final boolean bIncludeCertificateInSignedContent,
                                                 final boolean bUseRFC3851MICAlg,
                                                 final boolean bRemoveCmsAlgorithmProtect,
                                                 @Nullable final ECryptoAlgorithmCrypt eCryptAlgorithm,
                                                 @Nullable final X509Certificate aReceiverCert,
                                                 @NonNull final String sLoggingText) throws Exception
  {
    ValueEnforcer.notNull (aSrcPart, "SrcPart");
    ValueEnforcer.notNull (eCTE, "ContentTransferEncoding");
    if (eCompressionType != null)
    {
      if (bCompressBeforeSign)
        ValueEnforcer.notNull (aCompressBeforeSignCallback, "CompressBeforeSignCallback");
    }
    if (eSignAlgorithm != null)
    {
      ValueEnforcer.notNull (aSenderCert, "SenderCert");
      ValueEnforcer.notNull (aSenderKey, "SenderKey");
    }
    if (eCryptAlgorithm != null)
    {
      ValueEnforcer.notNull (aReceiverCert, "ReceiverCert");
    }

    MimeBodyPart aDataBP = aSrcPart;
    _logMimeBodyPart (aDataBP, "source");

    if (eCompressionType != null && bCompressBeforeSign)
    {
      // Compress before sign
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Compressing outbound message before signing...");
      aDataBP = compressMimeBodyPart (aDataBP, eCompressionType, eCTE);
      _logMimeBodyPart (aDataBP, "compressBeforeSign");

      // Invoke callback, so that source of MIC can be calculated later
      // This is usually "IAS2Message.setData (aDataBP)"
      // The MIC is always about the content that is signed
      aCompressBeforeSignCallback.accept (aDataBP);

      LOGGER.info ("Successfully compressed outgoing AS2 message" + sLoggingText);
    }

    if (eSignAlgorithm != null)
    {
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Signing outbound message...");

      aDataBP = AS2Helper.getCryptoHelper ()
                         .sign (aDataBP,
                                aSenderCert,
                                aSenderKey,
                                eSignAlgorithm,
                                bIncludeCertificateInSignedContent,
                                bUseRFC3851MICAlg,
                                bRemoveCmsAlgorithmProtect,
                                eCTE);
      _logMimeBodyPart (aDataBP, "signed");

      LOGGER.info ("Successfully signed outgoing AS2 message" + sLoggingText);
    }

    if (eCompressionType != null && !bCompressBeforeSign)
    {
      // Compress after sign
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Compressing outbound message after signing...");
      aDataBP = compressMimeBodyPart (aDataBP, eCompressionType, eCTE);
      _logMimeBodyPart (aDataBP, "compressAfterSign");

      LOGGER.info ("Successfully compressed outgoing AS2 message" + sLoggingText);
    }

    if (eCryptAlgorithm != null)
    {
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Encrypting outbound message...");
      aDataBP = AS2Helper.getCryptoHelper ().encrypt (aDataBP, aReceiverCert, eCryptAlgorithm, eCTE);
      _logMimeBodyPart (aDataBP, "encrypted");

      LOGGER.info ("Successfully encrypted outgoing AS2 message" + sLoggingText);
    }

    return aDataBP;
  }

  @NonNull
  protected MimeBodyPart secure (@NonNull final IMessage aMsg, @NonNull final EContentTransferEncoding eCTE)
                                                                                                             throws Exception
  {
    final Partnership aPartnership = aMsg.partnership ();
    final ICertificateFactory aCertFactory = getSession ().getCertificateFactory ();

    // Get compression parameters
    // If compression is enabled, by default is is compressed before signing
    ECompressionType eCompressionType = null;
    boolean bCompressBeforeSign = true;
    Consumer <MimeBodyPart> aCompressBeforeSignCallback = null;
    {
      final String sCompressionType = aPartnership.getCompressionType ();
      if (sCompressionType != null)
      {
        eCompressionType = ECompressionType.getFromIDCaseInsensitiveOrNull (sCompressionType);
        if (eCompressionType == null)
          throw new AS2Exception ("The compression type '" + sCompressionType + "' is not supported!");

        bCompressBeforeSign = aPartnership.isCompressBeforeSign ();

        if (bCompressBeforeSign)
        {
          // Replace the message data, because it is the basis for the MIC
          aCompressBeforeSignCallback = aMsg::setData;
        }
      }
    }

    // Get signing parameters
    ECryptoAlgorithmSign eSignAlgorithm = null;
    X509Certificate aSenderCert = null;
    PrivateKey aSenderKey = null;
    boolean bIncludeCertificateInSignedContent = false;
    boolean bUseRFC3851MICAlg = false;
    boolean bRemoveCmsAlgorithmProtect = false;
    {
      final String sSignAlgorithm = aPartnership.getSigningAlgorithm ();
      if (sSignAlgorithm != null)
      {
        aSenderCert = aCertFactory.getCertificate (aMsg, ECertificatePartnershipType.SENDER);
        aSenderKey = aCertFactory.getPrivateKey (aSenderCert);
        eSignAlgorithm = ECryptoAlgorithmSign.getFromIDOrNull (sSignAlgorithm);
        if (eSignAlgorithm == null)
          throw new AS2Exception ("The signing algorithm '" + sSignAlgorithm + "' is not supported!");

        // Include certificate in signed content?
        final ETriState eIncludeCertificateInSignedContent = aMsg.partnership ()
                                                                 .getIncludeCertificateInSignedContent ();
        if (eIncludeCertificateInSignedContent.isDefined ())
        {
          // Use per partnership
          bIncludeCertificateInSignedContent = eIncludeCertificateInSignedContent.getAsBooleanValue ();
        }
        else
        {
          // Use global value
          bIncludeCertificateInSignedContent = getSession ().isCryptoSignIncludeCertificateInBodyPart ();
        }

        // Use old MIC algorithms?
        bUseRFC3851MICAlg = aPartnership.isRFC3851MICAlgs ();

        // Remove CMS attributes?
        bRemoveCmsAlgorithmProtect = aPartnership.isRemoveCmsAlgorithmProtect ();
      }
    }

    // Get encryption parameters
    ECryptoAlgorithmCrypt eCryptAlgorithm = null;
    X509Certificate aReceiverCert = null;
    {
      final String sCryptAlgorithm = aPartnership.getEncryptAlgorithm ();
      if (sCryptAlgorithm != null)
      {
        aReceiverCert = aCertFactory.getCertificate (aMsg, ECertificatePartnershipType.RECEIVER);
        eCryptAlgorithm = ECryptoAlgorithmCrypt.getFromIDOrNull (sCryptAlgorithm);
        if (eCryptAlgorithm == null)
          throw new AS2Exception ("The crypting algorithm '" + sCryptAlgorithm + "' is not supported!");
      }
    }

    // Set CTE once here - required for stream creation later on!
    aMsg.headers ().setHeader (CHttpHeader.CONTENT_TRANSFER_ENCODING, eCTE.getID ());
    if (eCompressionType != null || eCryptAlgorithm != null)
    {
      // Header is needed when compression or encryption is enabled
      if (aMsg.getData ().getHeader (CHttpHeader.CONTENT_TRANSFER_ENCODING) == null)
        aMsg.getData ().setHeader (CHttpHeader.CONTENT_TRANSFER_ENCODING, eCTE.getID ());
    }
    if (eCompressionType != null && eSignAlgorithm == null && eCryptAlgorithm == null)
    {
      // Compression only - set the respective content type
      aMsg.headers ()
          .setHeader (CHttpHeader.CONTENT_TYPE, CMimeType.APPLICATION_OCTET_STREAM.getAsStringWithoutParameters ());
    }

    return secureMimeBodyPart (aMsg.getData (),
                               eCTE,
                               eCompressionType,
                               bCompressBeforeSign,
                               aCompressBeforeSignCallback,
                               eSignAlgorithm,
                               aSenderCert,
                               aSenderKey,
                               bIncludeCertificateInSignedContent,
                               bUseRFC3851MICAlg,
                               bRemoveCmsAlgorithmProtect,
                               eCryptAlgorithm,
                               aReceiverCert,
                               aMsg.getLoggingText ());
  }

  /**
   * Update the HTTP headers based on the provided message, before sending takes place.
   *
   * @param aHeaderSetter
   *        The connection abstraction. Never <code>null</code>.
   * @param aMsg
   *        The message to be send. Never <code>null</code>.
   */
  protected void updateHttpHeaders (@NonNull final AS2HttpHeaderSetter aHeaderSetter, @NonNull final IMessage aMsg)
  {
    final Partnership aPartnership = aMsg.partnership ();

    // Set all custom headers first (so that they are overridden with the
    // mandatory ones in here)
    // Use HttpHeaderMap and not String to ensure name casing is identical!
    final HttpHeaderMap aHeaderMap = aMsg.headers ().getClone ();

    aHeaderMap.setHeader (CHttpHeader.CONNECTION, CAS2Header.DEFAULT_CONNECTION);
    aHeaderMap.setHeader (CHttpHeader.USER_AGENT, CAS2Header.DEFAULT_USER_AGENT);
    aHeaderMap.setHeader (CHttpHeader.MIME_VERSION, CAS2Header.DEFAULT_MIME_VERSION);
    aHeaderMap.setHeader (CHttpHeader.AS2_VERSION, getSession ().getAS2VersionID ());

    aHeaderMap.setHeader (CHttpHeader.DATE, AS2DateHelper.getFormattedDateNow (CAS2Header.DEFAULT_DATE_FORMAT));
    aHeaderMap.setHeader (CHttpHeader.MESSAGE_ID, aMsg.getMessageID ());
    aHeaderMap.setHeader (CHttpHeader.CONTENT_TYPE, aMsg.getContentType ());
    aHeaderMap.setHeader (CHttpHeader.RECIPIENT_ADDRESS, aPartnership.getAS2URL ());
    aHeaderMap.setHeader (CHttpHeader.AS2_FROM, aPartnership.getSenderAS2ID ());
    aHeaderMap.setHeader (CHttpHeader.AS2_TO, aPartnership.getReceiverAS2ID ());
    aHeaderMap.setHeader (CHttpHeader.SUBJECT, aMsg.getSubject ());
    aHeaderMap.setHeader (CHttpHeader.FROM, aPartnership.getSenderEmail ());
    // Set when compression or encryption is enabled
    aHeaderMap.setHeader (CHttpHeader.CONTENT_TRANSFER_ENCODING,
                          aMsg.getHeader (CHttpHeader.CONTENT_TRANSFER_ENCODING));

    // Determine where to send the MDN to (legacy field)
    final String sDispTo = aPartnership.getAS2MDNTo ();
    if (sDispTo != null)
      aHeaderMap.setHeader (CHttpHeader.DISPOSITION_NOTIFICATION_TO, sDispTo);

    // MDN requirements
    final String sDispositionNotificationOptions = aPartnership.getAS2MDNOptions ();
    if (sDispositionNotificationOptions != null)
      aHeaderMap.setHeader (CHttpHeader.DISPOSITION_NOTIFICATION_OPTIONS, sDispositionNotificationOptions);

    // Async MDN 2007-03-12
    final String sReceiptDeliveryOption = aPartnership.getAS2ReceiptDeliveryOption ();
    if (sReceiptDeliveryOption != null)
      aHeaderMap.setHeader (CHttpHeader.RECEIPT_DELIVERY_OPTION, sReceiptDeliveryOption);

    // As of 2007-06-01
    final String sContententDisposition = aMsg.getContentDisposition ();
    if (sContententDisposition != null)
      aHeaderMap.setHeader (CHttpHeader.CONTENT_DISPOSITION, sContententDisposition);

    // Set once, after all were collected
    // Avoid double quoting
    aHeaderMap.forEachSingleHeader (aHeaderSetter::setHttpHeader, false);
  }

  /**
   * @param aMsg
   *        AS2Message
   * @param aHttpClient
   *        URLConnection
   * @param aOriginalMIC
   *        mic value from original msg
   * @param aIncomingDumper
   *        Incoming dumper. May be <code>null</code>.
   * @param aResHelper
   *        Resource helper
   * @throws AS2Exception
   *         in case of an error
   * @throws IOException
   *         in case of an IO error
   */
  protected void receiveSyncMDN (@NonNull final AS2Message aMsg,
                                 @NonNull final AS2HttpClient aHttpClient,
                                 @NonNull final MIC aOriginalMIC,
                                 @Nullable final IHTTPIncomingDumper aIncomingDumper,
                                 @NonNull final AS2ResourceHelper aResHelper) throws AS2Exception, IOException
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Receiving synchronous MDN for message" + aMsg.getLoggingText ());

    try
    {
      // Create a MessageMDN and copy HTTP headers
      final IMessageMDN aMDN = new AS2MessageMDN (aMsg);
      // Bug in ph-commons 9.1.3 in addAllHeaders!
      aMDN.headers ().addAllHeaders (aHttpClient.getResponseHeaderFields ());

      // Receive the MDN data
      final InputStream aConnIS = aHttpClient.getInputStream ();
      final NonBlockingByteArrayOutputStream aMDNStream = new NonBlockingByteArrayOutputStream ();
      // Retrieve the whole MDN content
      StreamHelper.copyByteStream ()
                  .from (aConnIS)
                  .closeFrom (true)
                  .to (aMDNStream)
                  .closeTo (true)
                  .limit (StringParser.parseLong (aMDN.getHeader (CHttpHeader.CONTENT_LENGTH), -1))
                  .build ();

      // Dump collected message
      if (aIncomingDumper != null)
        aIncomingDumper.dumpIncomingRequest (aMDN.headers ().getAllHeaderLines (true),
                                             aMDNStream.getBufferOrCopy (),
                                             aMDN);

      if (LOGGER.isTraceEnabled ())
      {
        // Debug print the whole MDN stream
        LOGGER.trace ("Retrieved MDN stream data:\n" + aMDNStream.getAsString (StandardCharsets.ISO_8859_1));
      }

      final MimeBodyPart aPart = new MimeBodyPart (AS2HttpHelper.getAsInternetHeaders (aMDN.headers ()),
                                                   aMDNStream.getBufferOrCopy ());
      aMDN.setData (aPart);

      // get the MDN partnership info
      aMDN.partnership ().setSenderAS2ID (aMDN.getHeader (CHttpHeader.AS2_FROM));
      aMDN.partnership ().setReceiverAS2ID (aMDN.getHeader (CHttpHeader.AS2_TO));
      // Set the appropriate key store aliases
      // For synchronous MDN, this must be swapped
      aMDN.partnership ().setSenderX509Alias (aMsg.partnership ().getReceiverX509Alias ());
      aMDN.partnership ().setReceiverX509Alias (aMsg.partnership ().getSenderX509Alias ());
      // Update the partnership
      getSession ().getPartnershipFactory ().updatePartnership (aMDN, false);

      final ICertificateFactory aCertFactory = getSession ().getCertificateFactory ();
      final X509Certificate aSenderCert = aCertFactory.getCertificate (aMDN, ECertificatePartnershipType.SENDER);

      boolean bUseCertificateInBodyPart;
      final ETriState eUseCertificateInBodyPart = aMsg.partnership ().getVerifyUseCertificateInBodyPart ();
      if (eUseCertificateInBodyPart.isDefined ())
      {
        // Use per partnership
        bUseCertificateInBodyPart = eUseCertificateInBodyPart.getAsBooleanValue ();
      }
      else
      {
        // Use global value
        bUseCertificateInBodyPart = getSession ().isCryptoVerifyUseCertificateInBodyPart ();
      }

      AS2Helper.parseMDN (aMsg, aSenderCert, bUseCertificateInBodyPart, m_aVerificationCertificateConsumer, aResHelper);

      try
      {
        getSession ().getMessageProcessor ().handle (IProcessorStorageModule.DO_STOREMDN, aMsg, null);
      }
      catch (final AS2ComponentNotFoundException | AS2NoModuleException ex)
      {
        // No message processor found
        // Or no module found in message processor
      }

      final String sDisposition = aMDN.attrs ().getAsString (AS2MessageMDN.MDNA_DISPOSITION);

      LOGGER.info ("Received synchronous AS2 MDN [" + sDisposition + "]" + aMsg.getLoggingText ());

      // Asynch MDN 2007-03-12
      // Verify if the original mic is equal to the mic in returned MDN
      final String sReturnMIC = aMDN.attrs ().getAsString (AS2MessageMDN.MDNA_MIC);
      final MIC aReturnMIC = MIC.parse (sReturnMIC);

      // Catch ReturnMIC == null in case the attribute is simply missing
      final boolean bMICMatch = aOriginalMIC != null && aReturnMIC != null && aReturnMIC.equals (aOriginalMIC);
      if (bMICMatch)
      {
        // MIC was matched - all good
        m_aMICMatchingHandler.onMICMatch (aMsg, sReturnMIC);
      }
      else
      {
        // file was sent completely but the returned mic was not matched,
        m_aMICMatchingHandler.onMICMismatch (aMsg,
                                             aOriginalMIC == null ? null : aOriginalMIC.getAsAS2String (),
                                             sReturnMIC);
      }

      if (m_aIncomingMDNCallback != null)
        m_aIncomingMDNCallback.onIncomingMDN (true,
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
      throw ex;
    }
    catch (final Exception ex)
    {
      throw WrappedAS2Exception.wrap (ex).setSourceMsg (aMsg);
    }
  }

  /**
   * Handler for errors in MDN processing.
   *
   * @param aMsg
   *        The source message that was send
   * @param ex
   *        The exception that was caught
   * @throws AS2Exception
   *         In case an overload wants to throw the exception
   */
  @OverrideOnDemand
  protected void onReceivedMDNError (@NonNull final AS2Message aMsg, @NonNull final AS2Exception ex) throws AS2Exception
  {
    new AS2Exception ("Message was sent but an error occured while receiving the MDN", ex).terminate (aMsg);
  }

  private void _sendViaHTTP (@NonNull final AS2Message aMsg,
                             @NonNull final MimeBodyPart aSecuredMimePart,
                             @Nullable final MIC aMIC,
                             @Nullable final EContentTransferEncoding eCTE,
                             @Nullable final IHTTPOutgoingDumper aOutgoingDumper,
                             @Nullable final IHTTPIncomingDumper aIncomingDumper,
                             @NonNull final AS2ResourceHelper aResHelper) throws AS2Exception,
                                                                          IOException,
                                                                          MessagingException
  {
    final Partnership aPartnership = aMsg.partnership ();

    // Create the HTTP connection
    final String sUrl = aPartnership.getAS2URL ();
    final EHttpMethod eRequestMethod = EHttpMethod.POST;
    // decide on the connection type to use according to the MimeBodyPart:
    // If it contains the data, (and no DataHandler), then use HttpUrlClient,
    // otherwise, use HttpClient
    final AS2HttpClient aConn = getHttpClient (sUrl, eRequestMethod, getSession ().getHttpProxy ());

    try
    {
      if (aOutgoingDumper != null)
        aOutgoingDumper.start (sUrl, aMsg);

      LOGGER.info ("Connecting to " + sUrl + aMsg.getLoggingText ());

      final boolean bQuoteHeaderValues = isQuoteHeaderValues ();
      updateHttpHeaders (new AS2HttpHeaderSetter (aConn, aOutgoingDumper, bQuoteHeaderValues), aMsg);

      if (aOutgoingDumper != null)
        aOutgoingDumper.finishedHeaders ();

      aMsg.attrs ().putIn (CNetAttribute.MA_DESTINATION_IP, aConn.getURL ().getHost ());
      aMsg.attrs ().putIn (CNetAttribute.MA_DESTINATION_PORT, aConn.getURL ().getPort ());

      final InputStream aMsgIS = aSecuredMimePart.getInputStream ();

      // Transfer the data
      final StopWatch aSW = StopWatch.createdStarted ();
      final long nBytes = aConn.send (aMsgIS, eCTE, aOutgoingDumper, aResHelper);
      aSW.stop ();
      LOGGER.info ("AS2 Message transferred " + AS2IOHelper.getTransferRate (nBytes, aSW) + aMsg.getLoggingText ());

      if (aOutgoingDumper != null)
        aOutgoingDumper.finishedPayload ();

      final int nHttpResponseCode = aConn.getResponseCode ();

      if (getOutgoingHttpCallback () != null)
        getOutgoingHttpCallback ().onOutgoingHttpMessage (true,
                                                          aMsg.getAS2From (),
                                                          aMsg.getAS2To (),
                                                          aMsg.getMessageID (),
                                                          aMIC == null ? null : aMIC.getClone (),
                                                          eCTE,
                                                          sUrl,
                                                          nHttpResponseCode);

      // Check the HTTP Response code
      if (AS2HttpClient.isErrorResponseCode (nHttpResponseCode))
      {
        LOGGER.error ("Error URL '" +
                      sUrl +
                      "' - HTTP " +
                      nHttpResponseCode +
                      " " +
                      aConn.getResponseMessage () +
                      " " +
                      aMsg.getLoggingText ());
        throw new AS2HttpResponseException (sUrl, nHttpResponseCode, aConn.getResponseMessage ());
      }

      // Asynch MDN 2007-03-12
      // Receive an MDN
      try
      {
        // Receive an MDN
        if (aMsg.isRequestingMDN ())
        {
          // Check if the AsyncMDN is required
          if (aPartnership.getAS2ReceiptDeliveryOption () == null)
          {
            // go ahead to receive sync MDN
            // Note: If an MDN is requested, a MIC is present
            assert aMIC != null;
            receiveSyncMDN (aMsg, aConn, aMIC, aIncomingDumper, aResHelper);

            LOGGER.info ("message sent" + aMsg.getLoggingText ());
          }
        }
      }
      catch (final AS2DispositionException ex)
      {
        // If a disposition error hasn't been handled, the message transfer
        // was not successful
        throw ex;
      }
      catch (final AS2Exception ex)
      {
        // Don't re-send or fail, just log an error if one occurs while
        // receiving the MDN
        onReceivedMDNError (aMsg, ex);
      }
    }
    finally
    {
      // Closes all resources
      aConn.disconnect ();
    }
  }

  public void handle (@NonNull final String sAction,
                      @NonNull final IMessage aBaseMsg,
                      @Nullable final Map <String, Object> aOptions) throws AS2Exception
  {
    final AS2Message aMsg = (AS2Message) aBaseMsg;

    LOGGER.info ("Submitting message" + aMsg.getLoggingText ());

    // verify all required information is present for sending
    checkRequired (aMsg);

    final int nRetries = getRetryCount (aMsg.partnership (), aOptions);

    try (final AS2ResourceHelper aResHelper = new AS2ResourceHelper ())
    {
      // Get Content-Transfer-Encoding to use
      final String sContentTransferEncoding = aMsg.partnership ()
                                                  .getContentTransferEncodingSend (EContentTransferEncoding.AS2_DEFAULT.getID ());
      final EContentTransferEncoding eCTE = EContentTransferEncoding.getFromIDCaseInsensitiveOrDefault (sContentTransferEncoding,
                                                                                                        EContentTransferEncoding.AS2_DEFAULT);

      // compress and/or sign and/or encrypt the message if needed
      final MimeBodyPart aSecuredData = secure (aMsg, eCTE);

      // Calculate MIC after compress/sign/crypt was handled, because the
      // message data might change if compression before signing is active.
      final MIC aMIC = aMsg.isRequestingMDN () ? calculateAndStoreMIC (aMsg) : null;

      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Setting message content type to '" + aSecuredData.getContentType () + "'");
      aMsg.setContentType (aSecuredData.getContentType ());

      try (final IHTTPOutgoingDumper aOutgoingDumper = getHttpOutgoingDumper (aMsg))
      {
        final IHTTPIncomingDumper aIncomingDumper = getEffectiveHttpIncomingDumper ();
        // Use no CTE, because it was set on all MIME parts
        _sendViaHTTP (aMsg, aSecuredData, aMIC, true ? null : eCTE, aOutgoingDumper, aIncomingDumper, aResHelper);
      }
    }
    catch (final AS2HttpResponseException ex)
    {
      LOGGER.error ("Http Response Error " + ex.getMessage ());
      ex.terminate (aMsg);

      if (!doResend (IProcessorSenderModule.DO_SEND, aMsg, ex, nRetries))
        throw ex;
    }
    catch (final IOException ex)
    {
      // Re-send if a network error occurs during transmission
      final AS2Exception wioe = WrappedAS2Exception.wrap (ex).setSourceMsg (aMsg).terminate ();

      if (!doResend (IProcessorSenderModule.DO_SEND, aMsg, wioe, nRetries))
        throw wioe;
    }
    catch (final Exception ex)
    {
      // Propagate error if it can't be handled by a re-send
      throw WrappedAS2Exception.wrap (ex);
    }
  }

  @Override
  public boolean equals (final Object o)
  {
    // New member, no change
    return super.equals (o);
  }

  @Override
  public int hashCode ()
  {
    // New member, no change
    return super.hashCode ();
  }
}
