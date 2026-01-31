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
package com.helger.phase2.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.debug.GlobalDebug;
import com.helger.base.exception.InitializationException;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.http.CHttpHeader;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.phase2.util.AS2Helper;
import com.helger.phase2.util.AS2HttpHelper;
import com.helger.phase2.util.AS2ResourceHelper;
import com.helger.phase2.util.cert.AS2KeyStoreHelper;
import com.helger.security.keystore.EKeyStoreType;
import com.helger.security.keystore.KeyStoreHelper;

import jakarta.mail.internet.MimeBodyPart;

/**
 * Unit test class for class {@link BCCryptoHelper}.
 *
 * @author Philip Helger
 */
public final class BCCryptoHelperTest
{
  private static final String PATH = "src/test/resources/external/mendelson/key3.pfx";
  private static final KeyStore KS = KeyStoreHelper.loadKeyStore (EKeyStoreType.PKCS12, PATH, "test".toCharArray ())
                                                   .getKeyStore ();
  private static final PrivateKeyEntry PKE = KeyStoreHelper.loadPrivateKey (KS, PATH, "key3", "test".toCharArray ())
                                                           .getKeyEntry ();
  private static final Logger LOGGER = LoggerFactory.getLogger (BCCryptoHelperTest.class);

  private static final X509Certificate CERT_ENCRYPT;
  static
  {
    try
    {
      CERT_ENCRYPT = AS2KeyStoreHelper.readX509Certificate ("src/test/resources/external/mendelson/key4.cer");
    }
    catch (final CertificateException ex)
    {
      throw new InitializationException (ex);
    }
  }

  @Test
  public void testCreateKeyStores () throws GeneralSecurityException
  {
    // Ensure each keystore type can be created
    final BCCryptoHelper x = new BCCryptoHelper ();
    for (final EKeyStoreType e : EKeyStoreType.values ())
      if (e.isKeyStorePathRequired ())
      {
        assertNotNull (x.createNewKeyStore (e));
      }
  }

  @Test
  public void testSignWithAllAlgorithms () throws Exception
  {
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText ("Hello world");

    try (AS2ResourceHelper aResHelper = new AS2ResourceHelper ())
    {
      final ICryptoHelper aCryptoHelper = AS2Helper.getCryptoHelper ();
      for (int nIncludeCert = 0; nIncludeCert < 2; ++nIncludeCert)
        for (final ECryptoAlgorithmSign eAlgo : ECryptoAlgorithmSign.values ())
        {
          LOGGER.info ("Trying signing algo " + eAlgo);

          final boolean bIncludeCert = nIncludeCert == 1;
          final MimeBodyPart aSigned = aCryptoHelper.sign (aPart,
                                                           (X509Certificate) PKE.getCertificate (),
                                                           PKE.getPrivateKey (),
                                                           eAlgo,
                                                           bIncludeCert,
                                                           eAlgo.isRFC3851Algorithm (),
                                                           false,
                                                           EContentTransferEncoding.BASE64);
          assertNotNull (aSigned);

          if (!bIncludeCert && GlobalDebug.isDebugMode ())
            try (final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ())
            {
              aSigned.writeTo (aBAOS);
              LOGGER.info (aBAOS.getAsString (StandardCharsets.ISO_8859_1));
            }

          final String [] aContentTypes = aSigned.getHeader (CHttpHeader.CONTENT_TYPE);
          assertNotNull (aContentTypes);
          assertEquals (1, aContentTypes.length);
          final String sContentType = aContentTypes[0];

          final String sExpectedStart = "multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=" +
                                        eAlgo.getMICAlgorithmID () +
                                        "; \r\n\tboundary=\"----=_Part";
          assertTrue ("[" + eAlgo + "] " + sContentType + " does not start with " + sExpectedStart,
                      sContentType.startsWith (sExpectedStart));

          // Verify as well
          LOGGER.info ("  Now verifying result of signing algo " + eAlgo);
          aCryptoHelper.verify (aSigned, (X509Certificate) PKE.getCertificate (), bIncludeCert, true, null, aResHelper);
        }
    }
  }

  @Test
  public void testSignWithAllCTEs () throws Exception
  {
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText ("Hello world");

    final ICryptoHelper aCryptoHelper = AS2Helper.getCryptoHelper ();
    for (final EContentTransferEncoding eCTE : EContentTransferEncoding.values ())
    {
      final MimeBodyPart aSigned = aCryptoHelper.sign (aPart,
                                                       (X509Certificate) PKE.getCertificate (),
                                                       PKE.getPrivateKey (),
                                                       ECryptoAlgorithmSign.DIGEST_SHA_512,
                                                       true,
                                                       false,
                                                       false,
                                                       eCTE);
      assertNotNull (aSigned);

      final String [] aContentTypes = aSigned.getHeader (CHttpHeader.CONTENT_TYPE);
      assertNotNull (aContentTypes);
      assertEquals (1, aContentTypes.length);
      final String sContentType = aContentTypes[0];
      final String sExpectedStart = "multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=sha-512; \r\n\tboundary=\"----=_Part";
      assertTrue (sContentType + " does not start with " + sExpectedStart, sContentType.startsWith (sExpectedStart));
    }
  }

  @Test
  public void testSign_Base64 () throws Exception
  {
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText ("Hello world", StandardCharsets.ISO_8859_1.name ());

    final MimeBodyPart aSigned = AS2Helper.getCryptoHelper ()
                                          .sign (aPart,
                                                 (X509Certificate) PKE.getCertificate (),
                                                 PKE.getPrivateKey (),
                                                 ECryptoAlgorithmSign.DIGEST_SHA_256,
                                                 false,
                                                 false,
                                                 false,
                                                 EContentTransferEncoding.BASE64);
    assertNotNull (aSigned);

    final String sBoundary = AS2HttpHelper.parseContentType (aSigned.getContentType ()).getParameter ("boundary");
    assertNotNull (sBoundary);

    try (final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ())
    {
      aSigned.writeTo (aBAOS);

      final String sExpectedStart = "Content-Type: multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=sha-256; \r\n" +
                                    "\tboundary=\"" +
                                    sBoundary +
                                    "\"\r\n" +
                                    "\r\n" +
                                    "--" +
                                    sBoundary +
                                    "\r\n" +
                                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                                    "Content-Transfer-Encoding: 7bit\r\n" +
                                    "\r\n" +
                                    "Hello world\r\n" +
                                    "--" +
                                    sBoundary +
                                    "\r\n" +
                                    "Content-Type: application/pkcs7-signature; name=smime.p7s; smime-type=signed-data\r\n" +
                                    "Content-Transfer-Encoding: base64\r\n" +
                                    "Content-Disposition: attachment; filename=\"smime.p7s\"\r\n" +
                                    "Content-Description: S/MIME Cryptographic Signature\r\n" +
                                    "\r\n" +
                                    "MIAGCSqGSIb3DQEHAqCAMIACAQExDTALBglghkgBZQMEAgEwgAYJKoZIhvcNAQcBAAAxggKkMIIC\r\n" +
                                    "oAIBATCBwzCBujEjMCEGCSqGSIb3DQEJARYUc2VydmljZUBtZW5kZWxzb24uZGUxCzAJBgNVBAYT\r\n" +
                                    "AkRFMQ8wDQYDVQQIDAZCZXJsaW4xDzANBgNVBAcMBkJlcmxpbjEiMCAGA1UECgwZbWVuZGVsc29u\r\n" +
                                    "LWUtY29tbWVyY2UgR21iSDEhMB8GA1UECwwYRG8gbm90IHVzZSBpbiBwcm9kdWN0aW9uMR0wGwYD\r\n" +
                                    "VQQDDBRtZW5kZWxzb24gdGVzdCBrZXkgMwIEWipbHDALBglghkgBZQMEAgGggbQwGAYJKoZIhvcN\r\n";
      final String sExpectedEnd = "\r\n" + "--" + sBoundary + "--\r\n";
      final String sReal = aBAOS.getAsString (StandardCharsets.ISO_8859_1);
      assertTrue (sReal.startsWith (sExpectedStart));
      assertTrue (sReal.endsWith (sExpectedEnd));
    }
  }

  @Test
  public void testSign_Binary () throws Exception
  {
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText ("Hello world", StandardCharsets.ISO_8859_1.name ());

    final MimeBodyPart aSigned = AS2Helper.getCryptoHelper ()
                                          .sign (aPart,
                                                 (X509Certificate) PKE.getCertificate (),
                                                 PKE.getPrivateKey (),
                                                 ECryptoAlgorithmSign.DIGEST_SHA_256,
                                                 false,
                                                 false,
                                                 false,
                                                 EContentTransferEncoding.BINARY);
    assertNotNull (aSigned);

    final String sBoundary = AS2HttpHelper.parseContentType (aSigned.getContentType ()).getParameter ("boundary");
    assertNotNull (sBoundary);

    try (final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ())
    {
      aSigned.writeTo (aBAOS);

      final String sExpectedStart = "Content-Type: multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=sha-256; \r\n" +
                                    "\tboundary=\"" +
                                    sBoundary +
                                    "\"\r\n" +
                                    "\r\n" +
                                    "--" +
                                    sBoundary +
                                    "\r\n" +
                                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                                    "Content-Transfer-Encoding: 7bit\r\n" +
                                    "\r\n" +
                                    "Hello world\r\n" +
                                    "--" +
                                    sBoundary +
                                    "\r\n" +
                                    "Content-Type: application/pkcs7-signature; name=smime.p7s; smime-type=signed-data\r\n" +
                                    "Content-Transfer-Encoding: binary\r\n" +
                                    "Content-Disposition: attachment; filename=\"smime.p7s\"\r\n" +
                                    "Content-Description: S/MIME Cryptographic Signature\r\n" +
                                    "\r\n";
      final String sExpectedEnd = "\r\n" + "--" + sBoundary + "--\r\n";
      final String sReal = aBAOS.getAsString (StandardCharsets.ISO_8859_1);
      assertTrue (sReal.startsWith (sExpectedStart));
      assertTrue (sReal.endsWith (sExpectedEnd));
    }
  }

  @Test
  public void testSign_QuotedPrintable () throws Exception
  {
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText ("Hello world", StandardCharsets.ISO_8859_1.name ());

    final MimeBodyPart aSigned = AS2Helper.getCryptoHelper ()
                                          .sign (aPart,
                                                 (X509Certificate) PKE.getCertificate (),
                                                 PKE.getPrivateKey (),
                                                 ECryptoAlgorithmSign.DIGEST_SHA_256,
                                                 false,
                                                 false,
                                                 false,
                                                 EContentTransferEncoding.QUOTED_PRINTABLE);
    assertNotNull (aSigned);

    final String sBoundary = AS2HttpHelper.parseContentType (aSigned.getContentType ()).getParameter ("boundary");
    assertNotNull (sBoundary);

    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    aSigned.writeTo (aBAOS);

    final String sExpectedStart = "Content-Type: multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=sha-256; \r\n" +
                                  "\tboundary=\"" +
                                  sBoundary +
                                  "\"\r\n" +
                                  "\r\n" +
                                  "--" +
                                  sBoundary +
                                  "\r\n" +
                                  "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                                  "Content-Transfer-Encoding: 7bit\r\n" +
                                  "\r\n" +
                                  "Hello world\r\n" +
                                  "--" +
                                  sBoundary +
                                  "\r\n" +
                                  "Content-Type: application/pkcs7-signature; name=smime.p7s; smime-type=signed-data\r\n" +
                                  "Content-Transfer-Encoding: quoted-printable\r\n" +
                                  "Content-Disposition: attachment; filename=\"smime.p7s\"\r\n" +
                                  "Content-Description: S/MIME Cryptographic Signature\r\n" +
                                  "\r\n" +
                                  "0=80=06=09*=86H=86=F7\r\n" +
                                  "=01=07=02=A0=800=80=02=01=011\r\n" +
                                  "0=0B=06=09`=86H=01e=03=04=02=010=80=06=09*=86H=86=F7\r\n" +
                                  "=01=07=01=00=001=82=02=A40=82=02=A0=02=01=010=81=C30=81=BA1#0!=06=09*=86H=\r\n" +
                                  "=86=F7\r\n" +
                                  "=01=09=01=16=14service@mendelson.de1=0B0=09=06=03U=04=06=13=02DE1=0F0\r\n" +
                                  "=06=03U=04=08=0C=06Berlin1=0F0\r\n" +
                                  "=06=03U=04=07=0C=06Berlin1\"0 =06=03U=04\r\n" +
                                  "=0C=19mendelson-e-commerce GmbH1!0=1F=06=03U=04=0B=0C=18Do not use in produ=\r\n" +
                                  "ction1=1D0=1B=06=03U=04=03=0C=14mendelson test key 3=02=04Z*[=1C0=0B=06=09`=\r\n" +
                                  "=86H=01e=03=04=02=01=A0=81=B40=18=06=09*=86H=86=F7\r\n" +
                                  "=01=09=031=0B=06=09*=86H=86=F7\r\n" +
                                  "=01=07=010=1C=06=09*=86H=86=F7\r\n" +
                                  "=01=09=051=0F=17\r\n";
    final String sExpectedEnd = "\r\n" + "--" + sBoundary + "--\r\n";
    final String sReal = aBAOS.getAsString (StandardCharsets.ISO_8859_1);
    // assertEquals (sReal, sExpectedStart);
    assertTrue (sReal.startsWith (sExpectedStart));
    assertTrue (sReal.endsWith (sExpectedEnd));
  }

  @Test
  public void testEncryptWithAllAlgorithms () throws Exception
  {
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText ("Hello world");

    for (final ECryptoAlgorithmCrypt eAlgo : ECryptoAlgorithmCrypt.values ())
    {
      final MimeBodyPart aEncrypted = AS2Helper.getCryptoHelper ()
                                               .encrypt (aPart, CERT_ENCRYPT, eAlgo, EContentTransferEncoding.BASE64);
      assertNotNull (aEncrypted);

      assertArrayEquals (new String [] { "application/pkcs7-mime; name=\"smime.p7m\"; smime-type=enveloped-data" },
                         aEncrypted.getHeader (CHttpHeader.CONTENT_TYPE));
      assertArrayEquals (new String [] { "attachment; filename=\"smime.p7m\"" },
                         aEncrypted.getHeader (CHttpHeader.CONTENT_DISPOSITION));
    }
  }

  @Test
  public void testEncryptWithAllCTEs () throws Exception
  {
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText ("Hello world");

    for (final EContentTransferEncoding eCTE : EContentTransferEncoding.values ())
    {
      final MimeBodyPart aEncrypted = AS2Helper.getCryptoHelper ()
                                               .encrypt (aPart,
                                                         CERT_ENCRYPT,
                                                         ECryptoAlgorithmCrypt.CRYPT_AES256_GCM,
                                                         eCTE);
      assertNotNull (aEncrypted);

      assertArrayEquals (new String [] { "application/pkcs7-mime; name=\"smime.p7m\"; smime-type=enveloped-data" },
                         aEncrypted.getHeader (CHttpHeader.CONTENT_TYPE));
      assertArrayEquals (new String [] { "attachment; filename=\"smime.p7m\"" },
                         aEncrypted.getHeader (CHttpHeader.CONTENT_DISPOSITION));
    }
  }

  @Test
  public void testEncryptCTE_Base64 () throws Exception
  {
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText ("Hello world");

    final MimeBodyPart aEncrypted = AS2Helper.getCryptoHelper ()
                                             .encrypt (aPart,
                                                       CERT_ENCRYPT,
                                                       ECryptoAlgorithmCrypt.CRYPT_3DES,
                                                       EContentTransferEncoding.BASE64);
    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    aEncrypted.writeTo (aBAOS);

    final String sExpectedStart = "Content-Type: application/pkcs7-mime; name=\"smime.p7m\"; smime-type=enveloped-data\r\n" +
                                  "Content-Transfer-Encoding: base64\r\n" +
                                  "Content-Disposition: attachment; filename=\"smime.p7m\"\r\n" +
                                  "Content-Description: S/MIME Encrypted Message\r\n" +
                                  "\r\n" +
                                  "MIAGCSqGSIb3DQEHA6CAMIACAQAxggHgMIIB3AIBADCBwzCBujEjMCEGCSqGSIb3DQEJARYUc2Vy\r\n" +
                                  "dmljZUBtZW5kZWxzb24uZGUxCzAJBgNVBAYTAkRFMQ8wDQYDVQQIDAZCZXJsaW4xDzANBgNVBAcM\r\n" +
                                  "BkJlcmxpbjEiMCAGA1UECgwZbWVuZGVsc29uLWUtY29tbWVyY2UgR21iSDEhMB8GA1UECwwYRG8g\r\n" +
                                  "bm90IHVzZSBpbiBwcm9kdWN0aW9uMR0wGwYDVQQDDBRtZW5kZWxzb24gdGVzdCBrZXkgNAIEWipb\r\n";
    final String sReal = aBAOS.getAsString (StandardCharsets.ISO_8859_1);
    // assertEquals (sReal, sExpectedStart);
    assertTrue (sReal.startsWith (sExpectedStart));
  }

  @Test
  public void testEncryptCTE_Binary () throws Exception
  {
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText ("Hello world");

    final MimeBodyPart aEncrypted = AS2Helper.getCryptoHelper ()
                                             .encrypt (aPart,
                                                       CERT_ENCRYPT,
                                                       ECryptoAlgorithmCrypt.CRYPT_3DES,
                                                       EContentTransferEncoding.BINARY);
    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    aEncrypted.writeTo (aBAOS);

    final String sExpectedStart = "Content-Type: application/pkcs7-mime; name=\"smime.p7m\"; smime-type=enveloped-data\r\n" +
                                  "Content-Transfer-Encoding: binary\r\n" +
                                  "Content-Disposition: attachment; filename=\"smime.p7m\"\r\n" +
                                  "Content-Description: S/MIME Encrypted Message\r\n" +
                                  "\r\n";
    final String sReal = aBAOS.getAsString (StandardCharsets.ISO_8859_1);
    assertTrue (sReal.startsWith (sExpectedStart));
  }

  @Test
  public void testEncryptCTE_QuotedPrintable () throws Exception
  {
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText ("Hello world");

    final MimeBodyPart aEncrypted = AS2Helper.getCryptoHelper ()
                                             .encrypt (aPart,
                                                       CERT_ENCRYPT,
                                                       ECryptoAlgorithmCrypt.CRYPT_3DES,
                                                       EContentTransferEncoding.QUOTED_PRINTABLE);
    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    aEncrypted.writeTo (aBAOS);

    final String sExpectedStart = "Content-Type: application/pkcs7-mime; name=\"smime.p7m\"; smime-type=enveloped-data\r\n" +
                                  "Content-Transfer-Encoding: quoted-printable\r\n" +
                                  "Content-Disposition: attachment; filename=\"smime.p7m\"\r\n" +
                                  "Content-Description: S/MIME Encrypted Message\r\n" +
                                  "\r\n" +
                                  "0=80=06=09*=86H=86=F7\r\n" +
                                  "=01=07=03=A0=800=80=02=01=001=82=01=E00=82=01=DC=02=01=000=81=C30=81=BA1#0!=\r\n" +
                                  "=06=09*=86H=86=F7\r\n" +
                                  "=01=09=01=16=14service@mendelson.de1=0B0=09=06=03U=04=06=13=02DE1=0F0\r\n" +
                                  "=06=03U=04=08=0C=06Berlin1=0F0\r\n" +
                                  "=06=03U=04=07=0C=06Berlin1\"0 =06=03U=04\r\n" +
                                  "=0C=19mendelson-e-commerce GmbH1!0=1F=06=03U=04=0B=0C=18Do not use in produ=\r\n" +
                                  "ction1=1D0=1B=06=03U=04=03=0C=14mendelson test key 4=02=04Z*[=C80\r\n" +
                                  "=06=09*=86H=86=F7\r\n";
    final String sReal = aBAOS.getAsString (StandardCharsets.ISO_8859_1);
    // assertEquals (sReal, sExpectedStart);
    assertTrue (sReal.startsWith (sExpectedStart));
  }
}
