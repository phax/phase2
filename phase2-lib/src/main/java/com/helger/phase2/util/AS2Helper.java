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
package com.helger.phase2.util;

import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.state.ETriState;
import com.helger.base.wrapper.Wrapper;
import com.helger.cache.regex.RegExHelper;
import com.helger.http.CHttp;
import com.helger.http.CHttpHeader;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.mime.CMimeType;
import com.helger.phase2.CPhase2Info;
import com.helger.phase2.cert.AS2CertificateNotFoundException;
import com.helger.phase2.cert.AS2KeyNotFoundException;
import com.helger.phase2.cert.ECertificatePartnershipType;
import com.helger.phase2.cert.ICertificateFactory;
import com.helger.phase2.crypto.BCCryptoHelper;
import com.helger.phase2.crypto.ECryptoAlgorithmSign;
import com.helger.phase2.crypto.ICryptoHelper;
import com.helger.phase2.crypto.MIC;
import com.helger.phase2.disposition.DispositionOptions;
import com.helger.phase2.disposition.DispositionType;
import com.helger.phase2.message.AS2Message;
import com.helger.phase2.message.AS2MessageMDN;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.message.IMessageMDN;
import com.helger.phase2.params.MessageParameters;
import com.helger.phase2.partner.AS2PartnershipNotFoundException;
import com.helger.phase2.partner.Partnership;
import com.helger.phase2.processor.CNetAttribute;
import com.helger.phase2.session.IAS2Session;
import com.helger.security.certificate.CertificateHelper;

import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;

@Immutable
public final class AS2Helper
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AS2Helper.class);
  private static final String HEADER_RECEIVED_CONTENT_MIC = "Received-Content-MIC";
  private static final String HEADER_DISPOSITION = "Disposition";
  private static final String HEADER_ORIGINAL_MESSAGE_ID = "Original-Message-ID";
  private static final String HEADER_FINAL_RECIPIENT = "Final-Recipient";
  private static final String HEADER_ORIGINAL_RECIPIENT = "Original-Recipient";
  private static final String HEADER_REPORTING_UA = "Reporting-UA";

  private static final class SingletonHolder
  {
    static final BCCryptoHelper INSTANCE = new BCCryptoHelper ();
  }

  private AS2Helper ()
  {}

  @NonNull
  public static ICryptoHelper getCryptoHelper ()
  {
    return SingletonHolder.INSTANCE;
  }

  /**
   * Create and fill the MDN parameter
   *
   * @param aSession
   *        Session to retrieve the certificate factory for signing
   * @param aMdn
   *        The MDN object to be filled
   * @param bSignMDN
   *        <code>true</code> to sign the MDN
   * @param bIncludeCertificateInSignedContent
   *        <code>true</code> if the passed certificate should be part of the signed content,
   *        <code>false</code> if the certificate should not be put in the content. E.g. for PEPPOL
   *        this must be <code>true</code>.
   * @param eMICAlg
   *        The MIC algorithm to be used. Must be present if bSignMDN is <code>true</code>.
   * @param bUseOldRFC3851MicAlgs
   *        <code>true</code> to use the old RFC 3851 MIC algorithm names (e.g. <code>sha1</code>),
   *        <code>false</code> to use the new RFC 5751 MIC algorithm names (e.g.
   *        <code>sha-1</code>).
   * @param bRemoveCmsAlgorithmProtect
   *        if <code>true</code>, the CMS attribute "AlgorithmProtect" will be removed. This is
   *        needed in compatibility with e.g. IBM Sterling. Default value should be
   *        <code>false</code>. Since 4.10.1. See Issue #137.
   * @throws Exception
   *         In case something internally goes wrong
   */
  public static void createMDNData (@NonNull final IAS2Session aSession,
                                    @NonNull final IMessageMDN aMdn,
                                    final boolean bSignMDN,
                                    final boolean bIncludeCertificateInSignedContent,
                                    @Nullable final ECryptoAlgorithmSign eMICAlg,
                                    final boolean bUseOldRFC3851MicAlgs,
                                    final boolean bRemoveCmsAlgorithmProtect) throws Exception
  {
    ValueEnforcer.notNull (aSession, "AS2Session");
    ValueEnforcer.notNull (aMdn, "MDN");
    if (bSignMDN)
      ValueEnforcer.notNull (eMICAlg, "MICAlg");

    // Create the report and sub-body parts
    final MimeMultipart aReportParts = new MimeMultipart ();

    // Create the text part
    final MimeBodyPart aTextPart = new MimeBodyPart ();
    {
      final String sText = aMdn.getText () + CHttp.EOL;
      aTextPart.setContent (sText, CMimeType.TEXT_PLAIN.getAsString ());

      aTextPart.setHeader (CHttpHeader.CONTENT_TYPE, CMimeType.TEXT_PLAIN.getAsString ());
      aReportParts.addBodyPart (aTextPart);
    }

    // Create the report part
    final MimeBodyPart aReportPart = new MimeBodyPart ();
    {
      final InternetHeaders aReportValues = new InternetHeaders ();
      aReportValues.setHeader (HEADER_REPORTING_UA, aMdn.attrs ().getAsString (AS2MessageMDN.MDNA_REPORTING_UA));
      aReportValues.setHeader (HEADER_ORIGINAL_RECIPIENT,
                               aMdn.attrs ().getAsString (AS2MessageMDN.MDNA_ORIG_RECIPIENT));
      aReportValues.setHeader (HEADER_FINAL_RECIPIENT, aMdn.attrs ().getAsString (AS2MessageMDN.MDNA_FINAL_RECIPIENT));
      aReportValues.setHeader (HEADER_ORIGINAL_MESSAGE_ID,
                               aMdn.attrs ().getAsString (AS2MessageMDN.MDNA_ORIG_MESSAGEID));
      aReportValues.setHeader (HEADER_DISPOSITION, aMdn.attrs ().getAsString (AS2MessageMDN.MDNA_DISPOSITION));
      aReportValues.setHeader (HEADER_RECEIVED_CONTENT_MIC, aMdn.attrs ().getAsString (AS2MessageMDN.MDNA_MIC));

      final StringBuilder aReportData = new StringBuilder ();
      final Enumeration <String> aReportEn = aReportValues.getAllHeaderLines ();
      while (aReportEn.hasMoreElements ())
        aReportData.append (aReportEn.nextElement ()).append (CHttp.EOL);
      aReportData.append (CHttp.EOL);
      aReportPart.setContent (aReportData.toString (), "message/disposition-notification");

      aReportPart.setHeader (CHttpHeader.CONTENT_TYPE, "message/disposition-notification");
      aReportParts.addBodyPart (aReportPart);
    }

    // Convert report parts to MimeBodyPart
    final MimeBodyPart aReport = new MimeBodyPart ();
    aReportParts.setSubType ("report; report-type=disposition-notification");
    aReport.setContent (aReportParts);
    aReport.setHeader (CHttpHeader.CONTENT_TYPE, aReportParts.getContentType ());

    // Sign the MDN data if needed
    if (bSignMDN)
    {
      final ICertificateFactory aCertFactory = aSession.getCertificateFactory ();
      try
      {
        final X509Certificate aSenderCert = aCertFactory.getCertificate (aMdn, ECertificatePartnershipType.SENDER);

        // Check if certificate is expired per "now"
        final Date aNotAfter = aSenderCert.getNotAfter ();
        if (aNotAfter != null && aNotAfter.compareTo (new Date ()) < 0)
        {
          LOGGER.warn ("Because our signing certificate expired per " +
                       aNotAfter +
                       " we cannot sign the MDN message" +
                       aMdn.getLoggingText ());
        }
        else
        {
          final PrivateKey aSenderKey = aCertFactory.getPrivateKey (aSenderCert);
          final MimeBodyPart aSignedReport = getCryptoHelper ().sign (aReport,
                                                                      aSenderCert,
                                                                      aSenderKey,
                                                                      eMICAlg,
                                                                      bIncludeCertificateInSignedContent,
                                                                      bUseOldRFC3851MicAlgs,
                                                                      bRemoveCmsAlgorithmProtect,
                                                                      EContentTransferEncoding.BASE64);
          aMdn.setData (aSignedReport);

          LOGGER.info ("Successfully signed outgoing MDN message" + aMdn.getLoggingText ());
        }
      }
      catch (final AS2CertificateNotFoundException | AS2KeyNotFoundException ex)
      {
        ex.terminate ();
        LOGGER.warn ("Failed to sign MDN - using an unsigned MDN instead");
        aMdn.setData (aReport);
      }
    }
    else
    {
      // No signing needed
      aMdn.setData (aReport);
    }

    // Update the MDN headers with content information
    final MimeBodyPart aData = aMdn.getData ();
    aMdn.headers ().setContentType (aData.getContentType ());

    // final int size = getSize (aData);
    // aMdn.setHeader (CAS2Header.HEADER_CONTENT_LENGTH, Integer.toString
    // (size));
  }

  @Nullable
  public static MIC createMICOnReception (@NonNull final AS2Message aMsg) throws Exception
  {
    final Partnership aPartnership = aMsg.partnership ();

    final String sDispositionOptions = aMsg.getHeader (CHttpHeader.DISPOSITION_NOTIFICATION_OPTIONS);
    final DispositionOptions aDispositionOptions = DispositionOptions.createFromString (sDispositionOptions);

    ECryptoAlgorithmSign eSigningAlgorithm = aDispositionOptions.getFirstMICAlg ();
    if (eSigningAlgorithm == null)
    {
      // Try from partnership (#93)
      final String sSigningAlgorithm = aPartnership.getSigningAlgorithm ();
      eSigningAlgorithm = ECryptoAlgorithmSign.getFromIDOrNull (sSigningAlgorithm);
      if (eSigningAlgorithm == null)
      {
        LOGGER.warn ("The partnership signing algorithm name '" + sSigningAlgorithm + "' is unknown.");
      }
    }

    if (eSigningAlgorithm == null)
      return null;

    // If the source message was signed or encrypted, include the headers -
    // see message sending for details
    final boolean bIncludeHeadersInMIC = aPartnership.getSigningAlgorithm () != null ||
                                         aPartnership.getEncryptAlgorithm () != null ||
                                         aPartnership.getCompressionType () != null;

    return getCryptoHelper ().calculateMIC (aMsg.getData (), eSigningAlgorithm, bIncludeHeadersInMIC);
  }

  /**
   * Create a new MDN
   *
   * @param aSession
   *        AS2 session to be used. May not be <code>null</code>.
   * @param aMsg
   *        The source AS2 message for which the MDN is to be created. May not be <code>null</code>.
   * @param aIncomingMIC
   *        The MIC of the incoming message. May not be <code>null</code>.
   * @param aDisposition
   *        The disposition - either success or error. May not be <code>null</code>.
   * @param sText
   *        The text to be send. May not be <code>null</code>.
   * @return The created MDN object which is already attached to the passed source AS2 message.
   * @throws Exception
   *         In case of an error
   */
  @NonNull
  public static IMessageMDN createSyncMDN (@NonNull final IAS2Session aSession,
                                           @NonNull final AS2Message aMsg,
                                           @Nullable final MIC aIncomingMIC,
                                           @NonNull final DispositionType aDisposition,
                                           @NonNull final String sText) throws Exception
  {
    ValueEnforcer.notNull (aSession, "AS2Session");
    ValueEnforcer.notNull (aMsg, "AS2Message");
    ValueEnforcer.notNull (aIncomingMIC, "IncomingMIC");
    ValueEnforcer.notNull (aDisposition, "Disposition");
    ValueEnforcer.notNull (sText, "Text");

    final Partnership aPartnership = aMsg.partnership ();

    final AS2MessageMDN aMDN = new AS2MessageMDN (aMsg);
    aMDN.headers ().setHeader (CHttpHeader.AS2_VERSION, aSession.getAS2VersionID ());
    aMDN.headers ().setHeader (CHttpHeader.DATE, AS2DateHelper.getFormattedDateNow (CAS2Header.DEFAULT_DATE_FORMAT));
    aMDN.headers ().setHeader (CHttpHeader.SERVER, CPhase2Info.NAME_VERSION);
    aMDN.headers ().setHeader (CHttpHeader.MIME_VERSION, CAS2Header.DEFAULT_MIME_VERSION);
    aMDN.headers ().setHeader (CHttpHeader.AS2_FROM, aPartnership.getReceiverAS2ID ());
    aMDN.headers ().setHeader (CHttpHeader.AS2_TO, aPartnership.getSenderAS2ID ());

    // get the MDN partnership info
    aMDN.partnership ().setSenderAS2ID (aMDN.getHeader (CHttpHeader.AS2_FROM));
    aMDN.partnership ().setReceiverAS2ID (aMDN.getHeader (CHttpHeader.AS2_TO));
    // Set the appropriate key store aliases
    // When creating a sync MDN, this must be swapped
    aMDN.partnership ().setSenderX509Alias (aPartnership.getReceiverX509Alias ());
    aMDN.partnership ().setReceiverX509Alias (aPartnership.getSenderX509Alias ());
    // Update the partnership
    try
    {
      aSession.getPartnershipFactory ().updatePartnership (aMDN, true);
    }
    catch (final AS2PartnershipNotFoundException ex)
    {
      // This would block sending an MDN in case a PartnershipNotFoundException
      // was the reason for sending the MDN :)
    }

    aMDN.headers ().setHeader (CHttpHeader.FROM, aPartnership.getReceiverEmail ());
    final String sSubject = aMDN.partnership ().getMDNSubject ();
    if (sSubject != null)
    {
      aMDN.headers ().setHeader (CHttpHeader.SUBJECT, new MessageParameters (aMsg).format (sSubject));
    }
    else
    {
      aMDN.headers ().setHeader (CHttpHeader.SUBJECT, "Your Requested MDN Response");
    }

    // Content-Transfer-Encoding for outgoing MDNs
    final String sCTE = aPartnership.getContentTransferEncodingSend (EContentTransferEncoding.AS2_DEFAULT.getID ());
    aMDN.headers ().addHeader (CHttpHeader.CONTENT_TRANSFER_ENCODING, sCTE);

    aMDN.setText (new MessageParameters (aMsg).format (sText));
    aMDN.attrs ()
        .putIn (AS2MessageMDN.MDNA_REPORTING_UA,
                CPhase2Info.NAME_VERSION +
                                                 "@" +
                                                 aMsg.attrs ().getAsString (CNetAttribute.MA_DESTINATION_IP) +
                                                 ":" +
                                                 aMsg.attrs ().getAsString (CNetAttribute.MA_DESTINATION_PORT));
    aMDN.attrs ().putIn (AS2MessageMDN.MDNA_ORIG_RECIPIENT, "rfc822; " + aMsg.getHeader (CHttpHeader.AS2_TO));
    aMDN.attrs ().putIn (AS2MessageMDN.MDNA_FINAL_RECIPIENT, "rfc822; " + aPartnership.getReceiverAS2ID ());
    aMDN.attrs ().putIn (AS2MessageMDN.MDNA_ORIG_MESSAGEID, aMsg.getHeader (CHttpHeader.MESSAGE_ID));
    aMDN.attrs ().putIn (AS2MessageMDN.MDNA_DISPOSITION, aDisposition.getAsString ());

    // MIC may be null when called from the Exception handler of receiving
    final MIC aRealIncomingMIC = aIncomingMIC != null ? aIncomingMIC : createMICOnReception (aMsg);
    if (aRealIncomingMIC != null)
      aMDN.attrs ().putIn (AS2MessageMDN.MDNA_MIC, aRealIncomingMIC.getAsAS2String ());

    final String sDispositionOptions = aMsg.getHeader (CHttpHeader.DISPOSITION_NOTIFICATION_OPTIONS);
    final DispositionOptions aDispositionOptions = DispositionOptions.createFromString (sDispositionOptions);

    boolean bSignMDN = false;
    boolean bIncludeCertificateInSignedContent = false;
    if (aDispositionOptions.getProtocol () != null)
    {
      if (aDispositionOptions.isProtocolRequired () || aDispositionOptions.hasMICAlg ())
      {
        // Sign if required or if optional and a MIC algorithm is present
        bSignMDN = true;

        // Include certificate in signed content?
        final ETriState eIncludeCertificateInSignedContent = aPartnership.getIncludeCertificateInSignedContent ();
        if (eIncludeCertificateInSignedContent.isDefined ())
        {
          // Use per partnership
          bIncludeCertificateInSignedContent = eIncludeCertificateInSignedContent.getAsBooleanValue ();
        }
        else
        {
          // Use global value
          bIncludeCertificateInSignedContent = aSession.isCryptoSignIncludeCertificateInBodyPart ();
        }
      }
    }

    final boolean bUseOldRFC3851MicAlgs = aPartnership.isRFC3851MICAlgs ();
    final boolean bRemoveCmsAlgorithmProtect = aPartnership.isRemoveCmsAlgorithmProtect ();

    createMDNData (aSession,
                   aMDN,
                   bSignMDN,
                   bIncludeCertificateInSignedContent,
                   aDispositionOptions.getFirstMICAlg (),
                   bUseOldRFC3851MicAlgs,
                   bRemoveCmsAlgorithmProtect);

    aMDN.updateMessageID ();

    // store MDN into msg in case AsynchMDN is sent fails, needs to be resent by
    // send module
    aMsg.setMDN (aMDN);

    return aMDN;
  }

  public static void parseMDN (@NonNull final IMessage aMsg,
                               @Nullable final X509Certificate aReceiverCert,
                               final boolean bUseCertificateInBodyPart,
                               @Nullable final Consumer <? super X509Certificate> aEffectiveCertificateConsumer,
                               @NonNull final AS2ResourceHelper aResHelper) throws Exception
  {
    final String sLoggingText = aMsg.getLoggingText ();
    LOGGER.info ("Start parsing MDN of" + sLoggingText);

    final IMessageMDN aMDN = aMsg.getMDN ();
    MimeBodyPart aMainPart = aMDN.getData ();
    final ICryptoHelper aCryptoHelper = getCryptoHelper ();

    final boolean bDisableVerify = aMsg.partnership ().isDisableVerify ();
    final boolean bMsgIsSigned = aCryptoHelper.isSigned (aMainPart);
    final boolean bForceVerify = aMsg.partnership ().isForceVerify ();
    if (bMsgIsSigned && bDisableVerify)
    {
      LOGGER.info ("Message claims to be signed but signature validation is disabled" + sLoggingText);
    }
    else
      if (bMsgIsSigned || bForceVerify)
      {
        if (bForceVerify && !bMsgIsSigned)
          LOGGER.info ("Forced verify MDN signature" + sLoggingText);
        else
          if (LOGGER.isDebugEnabled ())
            LOGGER.debug ("Verifying MDN signature" + sLoggingText);

        final Wrapper <X509Certificate> aCertHolder = new Wrapper <> ();
        aMainPart = aCryptoHelper.verify (aMainPart,
                                          aReceiverCert,
                                          bUseCertificateInBodyPart,
                                          bForceVerify,
                                          aCertHolder::set,
                                          aResHelper);
        if (aEffectiveCertificateConsumer != null)
          aEffectiveCertificateConsumer.accept (aCertHolder.get ());

        // Remember that message was signed and verified
        aMDN.attrs ().putIn (AS2Message.ATTRIBUTE_RECEIVED_SIGNED, true);
        // Remember the PEM encoded version of the X509 certificate that was
        // used for verification
        if (aCertHolder.isSet ())
          aMsg.attrs ()
              .putIn (AS2Message.ATTRIBUTE_RECEIVED_SIGNATURE_CERTIFICATE,
                      CertificateHelper.getPEMEncodedCertificate (aCertHolder.get ()));

        LOGGER.info ("Successfully verified signature of MDN of message" + sLoggingText);
      }

    final MimeMultipart aReportParts = new MimeMultipart (aMainPart.getDataHandler ().getDataSource ());
    final ContentType aReportType = AS2HttpHelper.parseContentType (aReportParts.getContentType ());

    if (aReportType != null && aReportType.getBaseType ().equalsIgnoreCase ("multipart/report"))
    {
      final int nReportCount = aReportParts.getCount ();
      for (int j = 0; j < nReportCount; j++)
      {
        final MimeBodyPart aReportPart = (MimeBodyPart) aReportParts.getBodyPart (j);
        if (aReportPart.isMimeType (CMimeType.TEXT_PLAIN.getAsString ()))
        {
          final Object aPartContent = aReportPart.getContent ();
          if (LOGGER.isDebugEnabled ())
            LOGGER.debug (aPartContent == null ? "Report part content is null" : "Report part content is a " +
                                                                                 aPartContent.getClass ().getName ());
          // XXX is this "toString" really a correct solution?
          aMDN.setText (aPartContent.toString ());
        }
        else
          if (aReportPart.isMimeType ("message/disposition-notification"))
          {
            // https://github.com/phax/as2-lib/issues/100
            final String sCTE = aReportPart.getHeader (CHttpHeader.CONTENT_TRANSFER_ENCODING, null);
            try (final InputStream aRealIS = AS2IOHelper.getContentTransferEncodingAwareInputStream (aReportPart.getInputStream (),
                                                                                                     sCTE))
            {
              final InternetHeaders aDispositionHeaders = new InternetHeaders (aRealIS);
              aMDN.attrs ()
                  .putIn (AS2MessageMDN.MDNA_REPORTING_UA, aDispositionHeaders.getHeader (HEADER_REPORTING_UA, ", "));
              aMDN.attrs ()
                  .putIn (AS2MessageMDN.MDNA_ORIG_RECIPIENT,
                          aDispositionHeaders.getHeader (HEADER_ORIGINAL_RECIPIENT, ", "));
              aMDN.attrs ()
                  .putIn (AS2MessageMDN.MDNA_FINAL_RECIPIENT,
                          aDispositionHeaders.getHeader (HEADER_FINAL_RECIPIENT, ", "));
              aMDN.attrs ()
                  .putIn (AS2MessageMDN.MDNA_ORIG_MESSAGEID,
                          aDispositionHeaders.getHeader (HEADER_ORIGINAL_MESSAGE_ID, ", "));
              aMDN.attrs ()
                  .putIn (AS2MessageMDN.MDNA_DISPOSITION, aDispositionHeaders.getHeader (HEADER_DISPOSITION, ", "));
              aMDN.attrs ()
                  .putIn (AS2MessageMDN.MDNA_MIC, aDispositionHeaders.getHeader (HEADER_RECEIVED_CONTENT_MIC, ", "));
            }
          }
          else
            LOGGER.info ("Got unsupported MDN body part MIME type: " + aReportPart.getContentType ());
      }
    }
  }

  @NonNull
  public static String getWithoutSpaces (@NonNull final String s)
  {
    return RegExHelper.stringReplacePattern ("\\s+", s, "");
  }
}
