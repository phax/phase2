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
package com.helger.phase2.util.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Before;
import org.junit.Test;

import com.helger.base.CGlobal;
import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.base.io.stream.StreamHelper;
import com.helger.base.io.stream.StringInputStream;
import com.helger.mail.datasource.IExtendedDataSource;
import com.helger.phase2.message.AS2Message;
import com.helger.phase2.util.dump.IHTTPIncomingDumper;

public final class HTTPHelperTest
{
  private String m_sRegularMessageBody;
  private String m_sRegularMessage;
  private String m_sChunkedMessageBody;
  private String m_sChunkedMessage;
  private String m_sNoLengthMessage;
  private String m_sBadTransferEncodingMessage;

  @Before
  public void init ()
  {
    m_sRegularMessageBody = "------=_Part_1_462911221.1531652105780\r\n" +
                            "Content-Type: application/xml; name=dummy.txt\r\n" +
                            "Content-Transfer-Encoding: 7bit\r\n" +
                            "Content-Disposition: attachment; filename=dummy.txt\r\n" +
                            "\r\n" +
                            "EOF\r\n" +
                            "------=_Part_1_462911221.1531652105780\r\n" +
                            "Content-Type: application/pkcs7-signature; name=smime.p7s; smime-type=signed-data\r\n" +
                            "Content-Transfer-Encoding: base64\r\n" +
                            "Content-Disposition: attachment; filename=\"smime.p7s\"\r\n" +
                            "Content-Description: S/MIME Cryptographic Signature\r\n" +
                            "\r\n" +
                            "MIAGCSqGSIb3DQEHAqCAMIACAQExDzANBglghkgBZQMEAgMFADCABgkqhkiG9w0BBwEAAKCAMIID\r\n" +
                            "2jCCAsKgAwIBAgIJAIIwcTXVbDA0MA0GCSqGSIb3DQEBCwUAMG8xCzAJBgNVBAYTAk5BMRAwDgYD\r\n" +
                            "VQQIDAdDVE1EZW1vMRAwDgYDVQQHDAdDVE1EZW1vMREwDwYDVQQKDAhDVE0gRGVtbzEXMBUGA1UE\r\n" +
                            "CwwOQk1DIENUTURlbW8gQ0ExEDAOBgNVBAMMB01GVGRlbW8wHhcNMTgwNjEzMTYwNzAyWhcNMjUw\r\n" +
                            "NDE3MTYwNzAyWjB3MQswCQYDVQQGEwJOQTEQMA4GA1UECAwHQ1RNRGVtbzEQMA4GA1UEBwwHQ1RN\r\n" +
                            "RGVtbzERMA8GA1UECgwIQ1RNIERlbW8xIDAeBgNVBAsMF0JNQyBDVE1EZW1vIFNlcnZlciBDZXJ0\r\n" +
                            "MQ8wDQYDVQQDDAZ1c2VyX2MwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC2JgAVSBBL\r\n" +
                            "wjClg3sIJN5fj1k66hhUzSoore9gSwafIkmJ3/CxcqSZL/SkpJTRbfaUq9bgT0mTcbCG9isTqWIl\r\n" +
                            "UEVQVrNqteG6hrngOKAWjJL4RrhUfZLDT2EVrQspyj56TXaS3Mt08sqovY+YdsodHINeuie9bSin\r\n" +
                            "DTBKxF36gVQdK3otyG8jOhAtPs2v+E74LUCFzCRx/U9ieOjeUm75wae3IxORyphYPgdQoNTJKaxc\r\n" +
                            "kFXrqVzsdEne5HHYCSgK+mlvbw4pgD7vEckoi5p3HX2cqhJd9JCYNnsMc8jIianKfJIJ4fzeIGck\r\n" +
                            "11oYbSQoFCKhZOq1GUJ4cOiuw8mpAgMBAAGjcTBvMB8GA1UdIwQYMBaAFDZCZ9iMcW+BmfYGWrm4\r\n" +
                            "PmbsJJOEMAkGA1UdEwQCMAAwHQYDVR0OBBYEFN7OX7z1yGQjPM+nNiPvJksCV4MUMAsGA1UdDwQE\r\n" +
                            "AwIE8DAVBgNVHREEDjAMggp0ZXN0U2VuZGVyMA0GCSqGSIb3DQEBCwUAA4IBAQCly1Qpeuw6LrpP\r\n" +
                            "pVcaeW08QZrBRebDD1wNjYh2cLKIm/FNHMWQRBeYwwegTH7DFzVrL8FsTLRf6yXrh0ClW8HKgymS\r\n" +
                            "k8hc364dYRSHtXyqiNTSapZbl5vTIieubC0pMaGnS1tLPIUOgYCqa1FxenMwdXLY8BIt8+wSI+5G\r\n" +
                            "skiwA9R1zUbxa/m5MsavK6o7KikKBagJ45jOzNVtKJVO/2k2PxA7lyYSAsv/G6VWRNqsTFKPR46A\r\n" +
                            "ZegBO4lTJYJ0wnFOUAh3DRPaIjcDsghXXGfhdbiQJHT0YK7ua6yS7n0EJHFo0ZdUw+x3VkavHI0L\r\n" +
                            "RQ5n8rUR32Q8SoYHSAqedN2RAAAxggKAMIICfAIBATB8MG8xCzAJBgNVBAYTAk5BMRAwDgYDVQQI\r\n" +
                            "DAdDVE1EZW1vMRAwDgYDVQQHDAdDVE1EZW1vMREwDwYDVQQKDAhDVE0gRGVtbzEXMBUGA1UECwwO\r\n" +
                            "Qk1DIENUTURlbW8gQ0ExEDAOBgNVBAMMB01GVGRlbW8CCQCCMHE11WwwNDANBglghkgBZQMEAgMF\r\n" +
                            "AKCB1jAYBgkqhkiG9w0BCQMxCwYJKoZIhvcNAQcBMBwGCSqGSIb3DQEJBTEPFw0xODA3MTUxMDU1\r\n" +
                            "MDZaMBwGCSqGSIb3DQEJDzEPMA0wCwYJYIZIAWUDBAIDMC0GCSqGSIb3DQEJNDEgMB4wDQYJYIZI\r\n" +
                            "AWUDBAIDBQChDQYJKoZIhvcNAQENBQAwTwYJKoZIhvcNAQkEMUIEQEU4umZZOH86xvI2gquHoyhF\r\n" +
                            "8odEYrm2GBKfCtKYSRA8oKGXp4bObjishBbPw25nw4j7/+N7iyUhAIUQLj9c6zEwDQYJKoZIhvcN\r\n" +
                            "AQENBQAEggEAhdX1Xg0/OFTt+JEG/JXhl58OqhndKk3QlZ+KvNEVUwaR6KDr/OHataJGzvJvYT0W\r\n" +
                            "WL3DH2w5mxEErGXefIu2FOJaeJJkeoypmpLfEOZAcjfjk2slLGS8pphqSRC7cPjzRhTUvHJkiZSN\r\n" +
                            "vEMnrSe+jUhwt+Cu8UxarMpi59nYCY35XNYN37EFl3XymO/BmzlKH4GNy8Kj2SBTHWR+pwpxQsOF\r\n" +
                            "93At8vbUUcs1ff7WNABg9zOtAckD9rrSMsSl199AoQ7kyYmv7LDJpPFZ1jAxHlXrstjG8ooKZM0i\r\n" +
                            "Nqmslehi0Yk16Mr3DnLtD5ewSNt6NtRnxhHHkzxG/ZC53nE2FgAAAAAAAA==\r\n" +
                            "------=_Part_1_462911221.1531652105780--\r\n";
    m_sRegularMessage = "POST /HttpReceiver HTTP/1.1\r\n" +
                        "content-type: multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=sha-512;    boundary=\"----=_Part_1_462911221.1531652105780\"\r\n" +
                        "subject: [--sign]\r\n" +
                        "message-id: <ph-OpenAS2-15072018135504+0300-0583@testsender_testreceiver>\r\n" +
                        "content-disposition: attachment; filename=dummy.txt\r\n" +
                        "User-Agent: ph-OpenAS2/AS2Sender\r\n" +
                        "Date: Sun, 15 Jul 2018 13:55:06 +0300\r\n" +
                        "Mime-Version: 1.0\r\n" +
                        "AS2-Version: 1.1\r\n" +
                        "Recipient-Address: http://localhost:10080/HttpReceiver\r\n" +
                        "AS2-From: testsender\r\n" +
                        "AS2-To: testreceiver\r\n" +
                        "From: email@example.org\r\n" +
                        "Disposition-Notification-Options: signed-receipt-protocol=required, pkcs7-signature; signed-receipt-micalg=required, sha-384\r\n" +
                        "Cache-Control: no-cache\r\n" +
                        "Pragma: no-cache\r\n" +
                        "Host: localhost:10080\r\n" +
                        "Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2\r\n" +
                        "Connection: keep-alive\r\n" +
                        "Content-Length: 2814\r\n" +
                        "\r\n" +
                        m_sRegularMessageBody;

    m_sChunkedMessageBody = "123456" + ThreadLocalRandom.current ().nextInt ();
    final String sChunkedLength = Integer.toHexString (m_sChunkedMessageBody.length ());
    m_sChunkedMessage = "POST /HttpReceiver HTTP/1.1\r\n" +
                        "Content-Type: multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=sha-512;    boundary=\"----=_Part_1_1029148906.1531651777438\"\r\n" +
                        "Subject: [-Dlog4j2.debug, --stream, --sign]\r\n" +
                        "Message-ID: <ph-OpenAS2-15072018134936+0300-1718@testsender_testreceiver>\r\n" +
                        "Content-Disposition: attachment; filename=dummy.txt\r\n" +
                        "Connection: close, TE\r\n" +
                        "User-Agent: ph-OpenAS2/AS2Sender\r\n" +
                        "Date: Sun, 15 Jul 2018 13:49:37 +0300\r\n" +
                        "Mime-Version: 1.0\r\n" +
                        "AS2-Version: 1.1\r\n" +
                        "Recipient-Address: http://localhost:10080/HttpReceiver\r\n" +
                        "AS2-From: testsender\r\n" +
                        "AS2-To: testreceiver\r\n" +
                        "From: email@example.org\r\n" +
                        "Content-Transfer-Encoding: \r\n" +
                        "Disposition-Notification-Options: signed-receipt-protocol=required, pkcs7-signature; signed-receipt-micalg=required, sha-384\r\n" +
                        "Transfer-Encoding: chunked\r\n" +
                        "Host: localhost:10080\r\n" +
                        "Accept-Encoding: gzip,deflate\r\n" +
                        "\r\n" +
                        sChunkedLength +
                        "\r\n" +
                        m_sChunkedMessageBody +
                        "\r\n" +
                        "0\r\n";
    m_sNoLengthMessage = "POST /HttpReceiver HTTP/1.1\r\n" +
                         "Content-Type: multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=sha-512;    boundary=\"----=_Part_1_1029148906.1531651777438\"\r\n" +
                         "Subject: [-Dlog4j2.debug, --stream, --sign]\r\n" +
                         "Message-ID: <ph-OpenAS2-15072018134936+0300-1718@testsender_testreceiver>\r\n" +
                         "Content-Disposition: attachment; filename=dummy.txt\r\n" +
                         "Connection: close, TE\r\n" +
                         "User-Agent: ph-OpenAS2/AS2Sender\r\n" +
                         "Date: Sun, 15 Jul 2018 13:49:37 +0300\r\n" +
                         "Mime-Version: 1.0\r\n" +
                         "AS2-Version: 1.1\r\n" +
                         "Recipient-Address: http://localhost:10080/HttpReceiver\r\n" +
                         "AS2-From: testsender\r\n" +
                         "AS2-To: testreceiver\r\n" +
                         "From: email@example.org\r\n" +
                         "Content-Transfer-Encoding: \r\n" +
                         "Disposition-Notification-Options: signed-receipt-protocol=required, pkcs7-signature; signed-receipt-micalg=required, sha-384\r\n" +
                         "Host: localhost:10080\r\n" +
                         "Accept-Encoding: gzip,deflate\r\n" +
                         "\r\n" +
                         sChunkedLength +
                         "\r\n" +
                         m_sChunkedMessageBody +
                         "\r\n" +
                         "0\r\n";
    m_sBadTransferEncodingMessage = "POST /HttpReceiver HTTP/1.1\r\n" +
                                    "Content-Type: multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=sha-512;    boundary=\"----=_Part_1_1029148906.1531651777438\"\r\n" +
                                    "Subject: [-Dlog4j2.debug, --stream, --sign]\r\n" +
                                    "Message-ID: <ph-OpenAS2-15072018134936+0300-1718@testsender_testreceiver>\r\n" +
                                    "Content-Disposition: attachment; filename=dummy.txt\r\n" +
                                    "Connection: close, TE\r\n" +
                                    "User-Agent: ph-OpenAS2/AS2Sender\r\n" +
                                    "Date: Sun, 15 Jul 2018 13:49:37 +0300\r\n" +
                                    "Mime-Version: 1.0\r\n" +
                                    "AS2-Version: 1.1\r\n" +
                                    "Recipient-Address: http://localhost:10080/HttpReceiver\r\n" +
                                    "AS2-From: testsender\r\n" +
                                    "AS2-To: testreceiver\r\n" +
                                    "From: email@example.org\r\n" +
                                    "Transfer-Encoding: cXXhunked\r\n" +
                                    "Content-Transfer-Encoding: \r\n" +
                                    "Disposition-Notification-Options: signed-receipt-protocol=required, pkcs7-signature; signed-receipt-micalg=required, sha-384\r\n" +
                                    "Host: localhost:10080\r\n" +
                                    "Accept-Encoding: gzip,deflate\r\n" +
                                    "\r\n" +
                                    sChunkedLength +
                                    "\r\n" +
                                    m_sChunkedMessageBody +
                                    "\r\n" +
                                    "0\r\n";
  }

  private static final IAS2HttpResponseHandler MOCK_RH = (nHttpResponseCode, aHeaders, aData) -> {};
  private static final IHTTPIncomingDumper INCOMING_DUMPER = null;

  public void testReadChunkLenEOS () throws Exception
  {
    final InputStream aIS = new StringInputStream ("1", StandardCharsets.UTF_8);
    final int nLen = HTTPHelper.readChunkLen (aIS);
    assertEquals (1, nLen);
  }

  @Test
  public void testReadChunkLenWithHeader () throws Exception
  {
    final NonBlockingByteArrayInputStream noNewLine = new NonBlockingByteArrayInputStream ("1A;name=value\r\n".getBytes (StandardCharsets.UTF_8));
    final int res = HTTPHelper.readChunkLen (noNewLine);
    assertEquals ("Chunk size with header", 26, res);
  }

  @Test
  public void testReadChunkLenNoHeader () throws Exception
  {
    final NonBlockingByteArrayInputStream noNewLine = new NonBlockingByteArrayInputStream ("1f\n".getBytes (StandardCharsets.UTF_8));
    final int res = HTTPHelper.readChunkLen (noNewLine);
    assertEquals ("Chunk size with header", 31, res);
  }

  @Test
  public void testReadChunkLenEmpty () throws Exception
  {
    final NonBlockingByteArrayInputStream noNewLine = new NonBlockingByteArrayInputStream ("\n".getBytes (StandardCharsets.UTF_8));
    final int res = HTTPHelper.readChunkLen (noNewLine);
    assertEquals ("Chunk size with header", 0, res);
  }

  @Test (expected = EOFException.class)
  public void testReadChunkLenTotallyEmpty () throws Exception
  {
    final NonBlockingByteArrayInputStream aIS = new NonBlockingByteArrayInputStream (CGlobal.EMPTY_BYTE_ARRAY);
    HTTPHelper.readChunkLen (aIS);
    fail ("Expected EOFException");
  }

  @Test
  public void testReadChunkLenCrap () throws Exception
  {
    // Don't use UTF-8 to avoid multi-byte handling for this test
    final NonBlockingByteArrayInputStream aIS = new NonBlockingByteArrayInputStream ("xyz\u0000\u00ff".getBytes (StandardCharsets.ISO_8859_1));
    final int res = HTTPHelper.readChunkLen (aIS);
    assertEquals (0, res);
  }

  @Test
  public void testReadHttpRequestRegularMessage () throws Exception
  {
    final AS2Message aMsg = new AS2Message ();
    final IAS2HttpRequestDataProvider aMockProvider = AS2HttpRequestDataProviderInputStream.createForUtf8 (m_sRegularMessage);
    final IExtendedDataSource aDS = HTTPHelper.readHttpRequest (aMockProvider, MOCK_RH, aMsg, INCOMING_DUMPER);
    assertNotNull (aDS);

    assertEquals ("<ph-OpenAS2-15072018135504+0300-0583@testsender_testreceiver>", aMsg.getMessageID ());
    final String sReadPayload = StreamHelper.getAllBytesAsString (aDS.getInputStream (), StandardCharsets.US_ASCII);
    assertEquals (m_sRegularMessageBody, sReadPayload);
  }

  @Test
  public void testReadHttpRequestStreamMessage () throws Exception
  {
    final AS2Message aMsg = new AS2Message ();
    final IAS2HttpRequestDataProvider aMockProvider = AS2HttpRequestDataProviderInputStream.createForUtf8 (m_sChunkedMessage);
    final IExtendedDataSource aDS = HTTPHelper.readHttpRequest (aMockProvider, MOCK_RH, aMsg, INCOMING_DUMPER);
    assertNotNull (aDS);

    assertEquals ("<ph-OpenAS2-15072018134936+0300-1718@testsender_testreceiver>", aMsg.getMessageID ());
    final String sReadPayload = StreamHelper.getAllBytesAsString (aDS.getInputStream (), StandardCharsets.US_ASCII);
    assertEquals (m_sChunkedMessageBody, sReadPayload);
  }

  @Test
  public void testNoLengthMessageRegular () throws Exception
  {
    // non stream
    final AS2Message aMsg = new AS2Message ();
    final IAS2HttpRequestDataProvider aMockProvider = AS2HttpRequestDataProviderInputStream.createForUtf8 (m_sNoLengthMessage);
    try
    {
      HTTPHelper.readHttpRequest (aMockProvider, MOCK_RH, aMsg, INCOMING_DUMPER);
      fail ();
    }
    catch (final IOException ex)
    {
      assertEquals ("Content-Length is missing and no Transfer-Encoding is specified", ex.getMessage ());
    }
  }

  @Test
  public void testBadTransferEncodingMessageRegular () throws Exception
  {
    // stream
    final AS2Message aMsg = new AS2Message ();
    final IAS2HttpRequestDataProvider aMockProvider = AS2HttpRequestDataProviderInputStream.createForUtf8 (m_sBadTransferEncodingMessage);
    try
    {
      HTTPHelper.readHttpRequest (aMockProvider, MOCK_RH, aMsg, INCOMING_DUMPER);
      fail ();
    }
    catch (final IOException ex)
    {
      assertEquals ("Transfer-Encoding unimplemented: 'cXXhunked'", ex.getMessage ());
    }
  }
}
