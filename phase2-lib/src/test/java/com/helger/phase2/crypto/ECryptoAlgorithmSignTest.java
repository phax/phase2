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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.base.string.StringHelper;

/**
 * Unit test class for class {@link ECryptoAlgorithmSign}.
 *
 * @author Philip Helger
 */
public final class ECryptoAlgorithmSignTest
{
  @Test
  public void testBasic ()
  {
    for (final ECryptoAlgorithmSign e : ECryptoAlgorithmSign.values ())
    {
      assertTrue (StringHelper.isNotEmpty (e.getID ()));
      assertNotNull (e.getOID ());
      assertTrue (StringHelper.isNotEmpty (e.getSignAlgorithmName ()));
      assertSame (e, ECryptoAlgorithmSign.getFromIDOrNull (e.getID ()));
      assertSame (e, ECryptoAlgorithmSign.getFromIDOrDefault (e.getID (), ECryptoAlgorithmSign.DIGEST_MD5));
      assertSame (e, ECryptoAlgorithmSign.getFromIDOrThrow (e.getID ()));
    }
    assertSame (ECryptoAlgorithmSign.DIGEST_MD5, ECryptoAlgorithmSign.getFromIDOrNull ("md5"));
    assertSame (ECryptoAlgorithmSign.DIGEST_MD5, ECryptoAlgorithmSign.getFromIDOrNull ("Md5"));
    assertSame (ECryptoAlgorithmSign.DIGEST_MD5, ECryptoAlgorithmSign.getFromIDOrNull ("MD5"));
  }
}
