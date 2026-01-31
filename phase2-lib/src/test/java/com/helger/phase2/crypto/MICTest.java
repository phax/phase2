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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

/**
 * Test class for class {@link MIC}
 *
 * @author Philip Helger
 */
public final class MICTest
{
  @Test
  public void testBasic ()
  {
    final MIC m1 = new MIC ("abc".getBytes (StandardCharsets.ISO_8859_1), ECryptoAlgorithmSign.DIGEST_SHA_256);
    assertNotNull (m1.getAsAS2String ());
    final MIC m2 = MIC.parse (m1.getAsAS2String ());
    assertEquals (m1, m2);
  }

  @Test
  public void testParseEmpty ()
  {
    assertNull (MIC.parse (null));
    assertNull (MIC.parse (""));
  }

  @Test (expected = IllegalArgumentException.class)
  public void testParseInvalidNoSep ()
  {
    MIC.parse ("VGVzdA==");
  }

  @Test (expected = IllegalArgumentException.class)
  public void testParseInvalidNoAlg ()
  {
    MIC.parse ("VGVzdA==, ");
  }

  @Test (expected = IllegalArgumentException.class)
  public void testParseInvalidInvalidAlg ()
  {
    MIC.parse ("VGVzdA==, blub");
  }

  @SuppressWarnings ("deprecation")
  @Test
  public void testIssue75 ()
  {
    final byte [] aBytes = "abc".getBytes (StandardCharsets.ISO_8859_1);

    MIC m1 = new MIC (aBytes, ECryptoAlgorithmSign.DIGEST_RSA_MD5);
    MIC m2 = new MIC (aBytes, ECryptoAlgorithmSign.DIGEST_MD5);
    assertEquals (m1, m2);

    m1 = new MIC (aBytes, ECryptoAlgorithmSign.DIGEST_RSA_SHA1);
    m2 = new MIC (aBytes, ECryptoAlgorithmSign.DIGEST_SHA1);
    assertEquals (m1, m2);

    m1 = new MIC (aBytes, ECryptoAlgorithmSign.DIGEST_SHA_1);
    m2 = new MIC (aBytes, ECryptoAlgorithmSign.DIGEST_SHA1);
    assertEquals (m1, m2);

    m1 = new MIC (aBytes, ECryptoAlgorithmSign.DIGEST_SHA_256);
    m2 = new MIC (aBytes, ECryptoAlgorithmSign.DIGEST_SHA256);
    assertEquals (m1, m2);

    m1 = new MIC (aBytes, ECryptoAlgorithmSign.DIGEST_SHA_384);
    m2 = new MIC (aBytes, ECryptoAlgorithmSign.DIGEST_SHA384);
    assertEquals (m1, m2);

    m1 = new MIC (aBytes, ECryptoAlgorithmSign.DIGEST_SHA_512);
    m2 = new MIC (aBytes, ECryptoAlgorithmSign.DIGEST_SHA512);
    assertEquals (m1, m2);
  }

}
