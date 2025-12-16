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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.function.Consumer;

import org.junit.Ignore;
import org.junit.Test;

import com.helger.base.io.nonblocking.NonBlockingBufferedReader;
import com.helger.base.io.stream.StreamHelper;
import com.helger.http.header.HttpHeaderMap;
import com.helger.io.resource.ClassPathResource;
import com.helger.io.resource.IReadableResource;
import com.helger.phase2.crypto.ICryptoHelper;
import com.helger.phase2.message.AS2Message;
import com.helger.phase2.message.AS2MessageMDN;
import com.helger.phase2.message.IMessageMDN;
import com.helger.phase2.util.AS2Helper;
import com.helger.phase2.util.AS2HttpHelper;
import com.helger.phase2.util.AS2ResourceHelper;
import com.helger.security.certificate.CertificateDecodeHelper;

import jakarta.mail.internet.MimeBodyPart;

public final class ReadMDNFuncTest
{
  @Test
  public void testReadMDN02 () throws Exception
  {
    final String sPrefix = "external/mdn/20190925-david";
    final IReadableResource aHeaderRes = new ClassPathResource (sPrefix + ".header");
    assertTrue (aHeaderRes.exists ());
    final IReadableResource aPayloadRes = new ClassPathResource (sPrefix + ".payload");
    assertTrue (aPayloadRes.exists ());
    final IReadableResource aCertRes = new ClassPathResource (sPrefix + ".pem");
    assertTrue (aCertRes.exists ());

    final HttpHeaderMap aHeaders = new HttpHeaderMap ();
    try (NonBlockingBufferedReader aBR = new NonBlockingBufferedReader (aHeaderRes.getReader (StandardCharsets.ISO_8859_1)))
    {
      String s;
      while ((s = aBR.readLine ()) != null)
      {
        final int i = s.indexOf (':');
        final String sName = s.substring (0, i).trim ();
        final String sValue = s.substring (i + 1).trim ();
        aHeaders.addHeader (sName, sValue);
      }
    }

    if (false)
      assertEquals ("<MOKOsi42435716cf621589dnode1POP000046@sfgt1.unix.fina.hr>",
                    aHeaders.getFirstHeaderValue ("Message-ID"));

    final X509Certificate aCert = new CertificateDecodeHelper ().source (StreamHelper.getAllBytes (aCertRes))
                                                                .pemEncoded (true)
                                                                .getDecodedOrNull ();
    assertNotNull (aCert);

    final AS2Message aMsg = new AS2Message ();

    // Create a MessageMDN and copy HTTP headers
    final IMessageMDN aMDN = new AS2MessageMDN (aMsg);
    aMDN.headers ().addAllHeaders (aHeaders);

    final MimeBodyPart aPart = new MimeBodyPart (AS2HttpHelper.getAsInternetHeaders (aMDN.headers ()),
                                                 StreamHelper.getAllBytes (aPayloadRes));
    assertNotNull (aPart);
    aMsg.getMDN ().setData (aPart);

    final ICryptoHelper aCryptoHelper = AS2Helper.getCryptoHelper ();
    assertTrue (aCryptoHelper.isSigned (aPart));
    assertFalse (aCryptoHelper.isEncrypted (aPart));
    assertFalse (aCryptoHelper.isCompressed (aPart.getContentType ()));

    final Consumer <X509Certificate> aCertHolder = null;
    try (final AS2ResourceHelper aResHelper = new AS2ResourceHelper ())
    {
      AS2Helper.parseMDN (aMsg, aCert, true, aCertHolder, aResHelper);
      fail ();
    }
    catch (final CertificateExpiredException ex)
    {
      // Expired 11.02.2021
      // expected to fail
      if (false)
        ex.printStackTrace ();
    }
  }

  @Test
  @Ignore ("Part does not contain MimeMultipart")
  public void testReadMDNIssue97 () throws Exception
  {
    final String sPrefix = "external/mdn/issue97";
    final IReadableResource aHeaderRes = new ClassPathResource (sPrefix + ".header");
    assertTrue (aHeaderRes.exists ());
    final IReadableResource aPayloadRes = new ClassPathResource (sPrefix + ".payload");
    assertTrue (aPayloadRes.exists ());
    if (false)
    {
      final IReadableResource aCertRes = new ClassPathResource (sPrefix + ".pem");
      assertTrue (aCertRes.exists ());
    }

    final HttpHeaderMap aHeaders = new HttpHeaderMap ();
    try (NonBlockingBufferedReader aBR = new NonBlockingBufferedReader (aHeaderRes.getReader (StandardCharsets.ISO_8859_1)))
    {
      String s;
      while ((s = aBR.readLine ()) != null)
      {
        final int i = s.indexOf (':');
        final String sName = s.substring (0, i).trim ();
        final String sValue = s.substring (i + 1).trim ();
        aHeaders.addHeader (sName, sValue);
      }
    }

    if (false)
      assertEquals ("<MOKOsi42435716cf621589dnode1POP000046@sfgt1.unix.fina.hr>",
                    aHeaders.getFirstHeaderValue ("Message-ID"));

    // final X509Certificate aCert =
    // CertificateHelper.convertStringToCertficateOrNull
    // (StreamHelper.getAllBytesAsString (aCertRes,
    // StandardCharsets.ISO_8859_1));
    // assertNotNull (aCert);

    final AS2Message aMsg = new AS2Message ();

    // Create a MessageMDN and copy HTTP headers
    final IMessageMDN aMDN = new AS2MessageMDN (aMsg);
    aMDN.headers ().addAllHeaders (aHeaders);

    final MimeBodyPart aPart = new MimeBodyPart (AS2HttpHelper.getAsInternetHeaders (aMDN.headers ()),
                                                 StreamHelper.getAllBytes (aPayloadRes));
    assertNotNull (aPart);
    aMsg.getMDN ().setData (aPart);

    final ICryptoHelper aCryptoHelper = AS2Helper.getCryptoHelper ();
    assertTrue (aCryptoHelper.isSigned (aPart));
    assertFalse (aCryptoHelper.isEncrypted (aPart));
    assertFalse (aCryptoHelper.isCompressed (aPart.getContentType ()));

    try (final AS2ResourceHelper aResHelper = new AS2ResourceHelper ())
    {
      final Consumer <X509Certificate> aCertHolder = null;
      AS2Helper.parseMDN (aMsg, null, true, aCertHolder, aResHelper);
    }
  }
}
