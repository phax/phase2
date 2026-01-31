/*
 * The FreeBSD Copyright
 * Copyright 1994-2008 The FreeBSD Project. All rights reserved.
 * Copyright (C) 2013-2026 Philip Helger philip[at]helger[dot]com
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
package com.helger.phase2.server.test;

import java.io.File;
import java.util.Enumeration;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.system.SystemHelper;
import com.helger.io.resource.ClassPathResource;
import com.helger.phase2.cert.AbstractCertificateFactory;
import com.helger.phase2.cert.CertificateFactory;
import com.helger.phase2.client.AS2Client;
import com.helger.phase2.client.AS2ClientRequest;
import com.helger.phase2.client.AS2ClientSettings;
import com.helger.phase2.crypto.ECryptoAlgorithmCrypt;
import com.helger.phase2.crypto.ECryptoAlgorithmSign;
import com.helger.phase2.disposition.DispositionOptions;
import com.helger.phase2.message.AS2Message;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.message.IMessageMDN;
import com.helger.phase2.params.AS2InvalidParameterException;
import com.helger.phase2.partner.CPartnershipIDs;
import com.helger.phase2.partner.Partnership;
import com.helger.phase2.partner.SelfFillingPartnershipFactory;
import com.helger.phase2.processor.sender.AS2SenderModule;
import com.helger.phase2.processor.sender.IProcessorSenderModule;
import com.helger.phase2.session.AS2Session;
import com.helger.security.keystore.EKeyStoreType;
import com.helger.typeconvert.collection.StringMap;

import jakarta.mail.Header;
import jakarta.mail.internet.MimeBodyPart;

/**
 * <pre>
  * &lt;partnerships&gt;
  *  &lt;partner name="OpenAS2A" as2_id="OpenAS2A" x509_alias="OpenAS2A" email="OpenAS2 A email"/&gt;
  *  &lt;partner name="OpenAS2B" as2_id="OpenAS2B" x509_alias="OpenAS2B" email="OpenAS2 B email"/&gt;
  *  &lt;partnership name="OpenAS2A-OpenAS2B"&gt;
  *     &lt;sender name="OpenAS2A"/&gt;
  *     &lt;receiver name="OpenAS2B"/&gt;
  *     &lt;attribute name="protocol" value="as2"/&gt;
  *     &lt;attribute name="subject" value="From OpenAS2A to OpenAS2B"/&gt;
  *     &lt;attribute name="as2_url" value="http://localhost:10080"/&gt;
  *     &lt;attribute name="as2_mdn_to" value="http://localhost:10080"/&gt;
  *     &lt;attribute name="as2_mdn_options" value="signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha1" /&gt;
  *     &lt;attribute name="encrypt" value="3des"/&gt;
  *     &lt;attribute name="sign" value="md5"/&gt;
  *   &lt;/partnership&gt;
  *   &lt;partnership name="OpenAS2B-OpenAS2A"&gt;
  *     &lt;sender name="OpenAS2B"/&gt;
  *     &lt;receiver name="OpenAS2A"/&gt;
  *     &lt;attribute name="protocol" value="as2"/&gt;
  *     &lt;attribute name="subject" value="From OpenAS2B to OpenAS2A"/&gt;
  *     &lt;attribute name="as2_url" value="http://localhost:10080"/&gt;
  *     &lt;attribute name="as2_mdn_to" value="http://localhost:10080"/&gt;
  *     &lt;attribute name="as2_mdn_options" value="signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha1" /&gt;
  *     &lt;attribute name="encrypt" value="3des"/&gt;
  *     &lt;attribute name="sign" value="sha1"/&gt;
  *   &lt;/partnership&gt;
  * &lt;/partnerships&gt;
 * </pre>
 *
 * @author oleo Date: May 4, 2010 Time: 6:56:31 PM
 */
public class MainTestClient
{
  // Message msg = new AS2Message();
  // getSession().getProcessor().handle(SenderModule.DO_SEND, msg, null);

  private static final Logger LOGGER = LoggerFactory.getLogger (MainTestClient.class);

  public static void main (final String [] args)
  {
    final boolean DO_ENCRYPT = true;
    final boolean DO_SIGN = true;

    final AS2ClientSettings aSettings = new AS2ClientSettings ();
    aSettings.setKeyStore (EKeyStoreType.PKCS12, ClassPathResource.getAsFile ("config/certs.p12"), "test");
    aSettings.setSenderData ("OpenAS2A", "email@example.org", "OpenAS2A_alias");
    aSettings.setReceiverData ("OpenAS2B", "OpenAS2B_alias", "http://localhost:10080/HttpReceiver");
    aSettings.setPartnershipName ("Partnership name");
    aSettings.setEncryptAndSign (DO_ENCRYPT ? ECryptoAlgorithmCrypt.CRYPT_3DES : null,
                                 DO_SIGN ? ECryptoAlgorithmSign.DIGEST_SHA_1 : null);
    // Use the default MDN options
    // Use the default message ID format

    final AS2ClientRequest aRequest = new AS2ClientRequest ("Test message");
    aRequest.setData (new File ("src/test/resources/dummy.txt"), SystemHelper.getSystemCharset ());
    new AS2Client ().sendSynchronous (aSettings, aRequest);
  }

  /**
   * @param args
   *        Main args
   * @throws Exception
   *         in case of error
   */
  public static void main2 (final String [] args) throws Exception
  {
    // Received-content-MIC
    // original-message-id

    final String pidSenderEmail = "email";
    final String pidAs2 = "GWTESTFM2i";
    final String pidSenderAs2 = "Sender";
    final String receiverKey = "rg_trusted";// "gwtestfm2i_trusted"; //
    final String senderKey = "rg";
    final String paAs2Url = "http://172.16.148.1:8080/as2/HttpReceiver";

    final AS2SenderModule aTestSender = new AS2SenderModule ();

    final Partnership aPartnership = new Partnership ("partnership name");
    aPartnership.setSenderAS2ID (pidSenderAs2);
    aPartnership.setSenderX509Alias (senderKey);
    aPartnership.setSenderEmail (pidSenderEmail);

    aPartnership.setReceiverAS2ID (pidAs2);
    aPartnership.setReceiverX509Alias (receiverKey);

    aPartnership.setAttribute (CPartnershipIDs.PA_AS2_URL, paAs2Url);
    if (false)
      aPartnership.setAttribute (CPartnershipIDs.PA_AS2_MDN_TO, "http://localhost:10080");
    aPartnership.setAttribute (CPartnershipIDs.PA_AS2_MDN_OPTIONS,
                               new DispositionOptions ().setProtocolImportance (DispositionOptions.IMPORTANCE_OPTIONAL)
                                                        .setProtocol (DispositionOptions.PROTOCOL_PKCS7_SIGNATURE)
                                                        .setMICAlgImportance (DispositionOptions.IMPORTANCE_OPTIONAL)
                                                        .setMICAlg (ECryptoAlgorithmSign.DIGEST_SHA_1)
                                                        .getAsString ());

    aPartnership.setAttribute (CPartnershipIDs.PA_ENCRYPT, ECryptoAlgorithmCrypt.CRYPT_3DES.getID ());
    aPartnership.setAttribute (CPartnershipIDs.PA_SIGN, ECryptoAlgorithmSign.DIGEST_SHA_1.getID ());
    aPartnership.setAttribute (CPartnershipIDs.PA_PROTOCOL, AS2Message.PROTOCOL_AS2);

    aPartnership.setAttribute (CPartnershipIDs.PA_AS2_RECEIPT_DELIVERY_OPTION, null);

    LOGGER.info ("ALIAS: " + aPartnership.getSenderX509Alias ());

    final IMessage aMsg = new AS2Message ();
    aMsg.setContentType ("application/xml");
    aMsg.setSubject ("some subject");

    aMsg.attrs ().putIn (CPartnershipIDs.PA_AS2_URL, paAs2Url);

    aMsg.attrs ().putIn (CPartnershipIDs.PID_AS2, pidAs2);
    aMsg.attrs ().putIn (CPartnershipIDs.PID_EMAIL, "email");

    MimeBodyPart aBodyPart = new MimeBodyPart ();
    aBodyPart.setText ("some text from mme part");
    // part.setFileName("/");
    aMsg.setData (aBodyPart);

    aMsg.setPartnership (aPartnership);
    aMsg.setMessageID (aMsg.generateMessageID ());
    LOGGER.info ("msg id: " + aMsg.getMessageID ());

    final AS2Session aSession = new AS2Session ();
    final CertificateFactory aCertFactory = new CertificateFactory ();
    /*
     * filename="%home%/certs.p12" password="test" interval="300"
     */
    // String filename =
    // "/Users/oleo/samples/parfum.spb.ru/as2/openas2/config/certs.p12";
    final String filename = "/Users/oleo/samples/parfum.spb.ru/as2/mendelson/certificates.p12";
    // String filename =
    // "/Users/oleo/samples/parfum.spb.ru/as2/test/test.p12";
    final String password = "test";
    // gwtestfm2i
    // /Users/oleo/Downloads/portecle-1.5.zip

    // /Users/oleo/samples/parfum.spb.ru/as2/test/test.p12

    final StringMap aCertFactorySettings = new StringMap ();
    aCertFactorySettings.putIn (AbstractCertificateFactory.ATTR_TYPE, EKeyStoreType.PKCS12.getID ());
    aCertFactorySettings.putIn (CertificateFactory.ATTR_FILENAME, filename);
    aCertFactorySettings.putIn (AbstractCertificateFactory.ATTR_PASSWORD, password);

    aCertFactory.initDynamicComponent (aSession, aCertFactorySettings);

    // logger.info(cf.getCertificate(msg.getMDN(), Partnership.PTYPE_SENDER));

    // logger.info(cf.getCertificates());

    aSession.setCertificateFactory (aCertFactory);

    final SelfFillingPartnershipFactory aPartnershipFactory = new SelfFillingPartnershipFactory ();
    aSession.setPartnershipFactory (aPartnershipFactory);
    aTestSender.initDynamicComponent (aSession, null);

    LOGGER.info ("is requesting  MDN?: " + aMsg.isRequestingMDN ());
    LOGGER.info ("is async MDN?: " + aMsg.isRequestingAsynchMDN ());
    LOGGER.info ("is rule to receive MDN active?: " + aMsg.partnership ().getAS2ReceiptDeliveryOption ());

    aTestSender.handle (IProcessorSenderModule.DO_SEND, aMsg, null);
    LOGGER.info ("MDN is " + aMsg.getMDN ().toString ());

    LOGGER.info ("message sent" + aMsg.getLoggingText ());

    final IMessageMDN reply = aMsg.getMDN ();

    if (false)
      LOGGER.info ("MDN headers:\n" + reply.headers ().toString ());

    final Enumeration <Header> list2 = reply.getData ().getAllHeaders ();
    final StringBuilder aSB2 = new StringBuilder ("Mime headers:\n");
    while (list2.hasMoreElements ())
    {
      final Header aHeader = list2.nextElement ();
      aSB2.append (aHeader.getName ()).append (" = ").append (aHeader.getValue ()).append ('\n');
    }

    // logger.info(sb2);

    // logger.info(reply.getData().getRawInputStream().toString());
  }

  protected static void checkRequired (@NonNull final IMessage aMsg) throws AS2InvalidParameterException
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
}
