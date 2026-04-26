package com.helger.phase2.supplementary.main;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.mime.CMimeType;
import com.helger.phase2.client.AS2Client;
import com.helger.phase2.client.AS2ClientRequest;
import com.helger.phase2.client.AS2ClientResponse;
import com.helger.phase2.client.AS2ClientSettings;
import com.helger.phase2.crypto.ECompressionType;
import com.helger.phase2.crypto.ECryptoAlgorithmCrypt;
import com.helger.phase2.crypto.ECryptoAlgorithmSign;
import com.helger.phase2.disposition.DispositionOptions;
import com.helger.security.keystore.EKeyStoreType;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;

/**
 * Sends a signed+encrypted+compressed AS2 message from openas2a to openas2b at
 * the phase2-demo-spring-boot receiver deployed on Azure Container Apps.
 * <p>
 * The server uses {@code SelfFillingPartnershipFactory}, so no partnership
 * pre-registration is needed. The keystore bundled with the demo is used as
 * both the sender keystore and the source of the receiver public cert.
 */
public final class MainSendToAzureAS2Server
{
  private static final Logger LOGGER = LoggerFactory.getLogger (MainSendToAzureAS2Server.class);

  // Override via -D flags if you redeploy to a different FQDN.
  private static final String RECEIVER_URL = System.getProperty ("as2.url",
      "https://phase2-as2.mangowave-907b9f53.canadacentral.azurecontainerapps.io/as2");

  public static void main (final String [] args) throws Exception
  {
    final File keystoreFile = new File ("../phase2-demo-spring-boot/config/certs.p12");
    final String keystorePassword = "test";
    // AS2 ID must equal the keystore alias because the server uses
    // SelfFillingPartnershipFactory, which sets x509_alias = as2_id.
    final String senderAlias   = "openas2a_alias";
    final String receiverAlias = "openas2b_alias";
    final String senderAs2Id   = senderAlias;
    final String receiverAs2Id = receiverAlias;

    final X509Certificate receiverCert;
    try (FileInputStream in = new FileInputStream (keystoreFile))
    {
      final KeyStore ks = KeyStore.getInstance ("PKCS12");
      ks.load (in, keystorePassword.toCharArray ());
      receiverCert = (X509Certificate) ks.getCertificate (receiverAlias);
      if (receiverCert == null)
        throw new IllegalStateException ("Receiver alias '" + receiverAlias + "' not found in keystore");
    }

    final AS2ClientSettings s = new AS2ClientSettings ();
    s.setKeyStore (EKeyStoreType.PKCS12, keystoreFile, keystorePassword);
    s.setSenderData (senderAs2Id, "sender@example.com", senderAlias);
    s.setReceiverData (receiverAs2Id, receiverAlias, RECEIVER_URL);
    s.setReceiverCertificate (receiverCert);
    s.setPartnershipName (senderAs2Id + "_" + receiverAs2Id);

    final ECryptoAlgorithmSign  signAlgo  = ECryptoAlgorithmSign.DIGEST_SHA_256;
    final ECryptoAlgorithmCrypt cryptAlgo = ECryptoAlgorithmCrypt.CRYPT_AES128_CBC;
    s.setEncryptAndSign (cryptAlgo, signAlgo);
    s.setCompress (ECompressionType.ZLIB, AS2ClientSettings.DEFAULT_COMPRESS_BEFORE_SIGNING);
    s.setMDNOptions (new DispositionOptions ().setMICAlg (signAlgo)
                                              .setMICAlgImportance (DispositionOptions.IMPORTANCE_REQUIRED)
                                              .setProtocol (DispositionOptions.PROTOCOL_PKCS7_SIGNATURE)
                                              .setProtocolImportance (DispositionOptions.IMPORTANCE_REQUIRED));
    s.setMessageIDFormat ("phase2-azure-$date.uuuuMMdd-HHmmssZ$-$rand.1234$@$msg.sender.as2_id$_$msg.receiver.as2_id$");
    s.setConnectTimeout (Timeout.ofSeconds (15));
    s.setResponseTimeout (Timeout.ofSeconds (30));

    final AS2ClientRequest req = new AS2ClientRequest ("Test from phase2 → Azure");
    req.setData (new DataHandler (new FileDataSource (
        new File ("src/test/resources/external/mendelson/testcontent.attachment"))));
    req.setContentType (CMimeType.TEXT_PLAIN.getAsString ());

    LOGGER.info ("Sending to {}", RECEIVER_URL);
    final AS2ClientResponse resp = new AS2Client ().sendSynchronous (s, req);
    if (resp.hasException ())
      LOGGER.error (resp.getAsString ());
    else
      LOGGER.info ("OK: {}", resp.getAsString ());
  }
}
