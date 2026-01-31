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
package com.helger.phase2.supplementary.functest;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.codec.base64.Base64;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.base.io.stream.StreamHelper;
import com.helger.base.io.stream.StringInputStream;
import com.helger.base.string.StringHelper;
import com.helger.http.CHttp;
import com.helger.http.CHttpHeader;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.mail.datasource.ByteArrayDataSource;
import com.helger.phase2.util.AS2HttpHelper;
import com.helger.phase2.util.AS2IOHelper;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeBodyPart;

/**
 * Special tests to see how {@link MimeBodyPart} works :)
 *
 * @author Philip Helger
 */
public final class MimeBodyPartFuncTest
{
  private static final Logger LOGGER = LoggerFactory.getLogger (MimeBodyPartFuncTest.class);

  @Test
  public void testWriteContentTransferEncoding8Bit () throws MessagingException, IOException
  {
    final String sTestMsg = "Test message\nLine 2\n\nLine 4\nEOF";

    // Build message content
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText (sTestMsg, StandardCharsets.ISO_8859_1.name ());
    aPart.addHeader ("x-custom", "junit");
    aPart.addHeader (CHttpHeader.CONTENT_TYPE, "text/plain");
    aPart.addHeader (CHttpHeader.CONTENT_TRANSFER_ENCODING, EContentTransferEncoding._8BIT.getID ());
    aPart.addHeader (CHttpHeader.CONTENT_LENGTH, Integer.toString (sTestMsg.length ()));

    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    aPart.writeTo (aBAOS);
    StreamHelper.close (aBAOS);

    final String sMsgPart = aBAOS.getAsString (StandardCharsets.ISO_8859_1);
    if (false)
      LOGGER.info (sMsgPart);

    assertTrue (sMsgPart, sMsgPart.contains ("Content-Type: text/plain"));
    assertTrue (sMsgPart, sMsgPart.contains ("Content-Transfer-Encoding: 8bit"));
    assertTrue (sMsgPart, sMsgPart.contains ("x-custom: junit"));
    assertTrue (sMsgPart, sMsgPart.contains (sTestMsg));
  }

  @Test
  public void testWriteContentTransferEncodingBase64 () throws MessagingException, IOException
  {
    final String sTestMsg = "Test message\nLine 2\n\nLine 4\nEOF";
    final String sEncodedMsg = Base64.safeEncode (sTestMsg, StandardCharsets.ISO_8859_1);

    // Build message content
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText (sTestMsg, StandardCharsets.ISO_8859_1.name ());
    aPart.addHeader ("x-custom", "junit");
    aPart.addHeader (CHttpHeader.CONTENT_TYPE, "text/plain");
    aPart.addHeader (CHttpHeader.CONTENT_TRANSFER_ENCODING, EContentTransferEncoding.BASE64.getID ());
    aPart.addHeader (CHttpHeader.CONTENT_LENGTH, Integer.toString (sEncodedMsg.length ()));

    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    aPart.writeTo (aBAOS);
    StreamHelper.close (aBAOS);

    final String sMsgPart = aBAOS.getAsString (StandardCharsets.ISO_8859_1);
    if (false)
      LOGGER.info (sMsgPart);

    assertTrue (sMsgPart, sMsgPart.contains ("Content-Type: text/plain"));
    assertTrue (sMsgPart, sMsgPart.contains ("Content-Transfer-Encoding: base64"));
    assertTrue (sMsgPart, sMsgPart.contains ("x-custom: junit"));
    assertTrue (sMsgPart, sMsgPart.contains (sEncodedMsg));
  }

  @Test
  public void testReadContentTransferEncodingBase64 () throws MessagingException, IOException
  {
    final String sHTTP = "Content-Type: text/plain" +
                         CHttp.EOL +
                         "Content-Transfer-Encoding: base64" +
                         CHttp.EOL +
                         "x-custom: junit" +
                         CHttp.EOL +
                         "Content-Length: 44" +
                         CHttp.EOL +
                         CHttp.EOL +
                         "VGVzdCBtZXNzYWdlCkxpbmUgMgoKTGluZSA0CkVPRg==" +
                         CHttp.EOL;
    InputStream aIS = new StringInputStream (sHTTP, StandardCharsets.ISO_8859_1);

    // Parse all HTTP headers from stream
    final InternetHeaders aHeaders = new InternetHeaders (aIS);
    final String sCTE = aHeaders.getHeader (CHttpHeader.CONTENT_TRANSFER_ENCODING)[0];

    if (StringHelper.isNotEmpty (sCTE))
      aIS = AS2IOHelper.getContentTransferEncodingAwareInputStream (aIS, sCTE);

    // Read the payload
    final byte [] aData = StreamHelper.getAllBytes (aIS);

    // Extract content type
    final String sReceivedContentType = AS2HttpHelper.getCleanContentType (aHeaders.getHeader (CHttpHeader.CONTENT_TYPE)[0]);

    final MimeBodyPart aReceivedPart = new MimeBodyPart ();
    aReceivedPart.setDataHandler (new ByteArrayDataSource (aData, sReceivedContentType, null).getAsDataHandler ());
    aReceivedPart.setHeader ("x-received", "true");

    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    aReceivedPart.writeTo (aBAOS);
    StreamHelper.close (aBAOS);

    final String sMsgPart = aBAOS.getAsString (StandardCharsets.ISO_8859_1);
    if (true)
      LOGGER.info (sMsgPart);
  }
}
