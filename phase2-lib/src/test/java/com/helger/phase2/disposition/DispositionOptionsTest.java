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
package com.helger.phase2.disposition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.helger.phase2.exception.AS2Exception;

/**
 * Test class for class {@link DispositionOptions}.
 *
 * @author Philip Helger
 */
public final class DispositionOptionsTest
{
  @Test
  public void testParsing () throws AS2Exception
  {
    DispositionOptions aDO = DispositionOptions.createFromString ("signed-receipt-protocol=optional,pkcs7-signature; signed-receipt-micalg=optional,sha1,md5");
    assertNotNull (aDO);
    assertEquals ("optional", aDO.getProtocolImportance ());
    assertEquals ("pkcs7-signature", aDO.getProtocol ());
    assertEquals ("optional", aDO.getMICAlgImportance ());
    assertEquals ("sha1, md5", aDO.getMICAlgAsString ());
    assertEquals ("signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha1, md5",
                  aDO.getAsString ());

    aDO = DispositionOptions.createFromString ("signed-receipt-protocol=required,pkcs7-signature; signed-receipt-micalg=required,sha1");
    assertNotNull (aDO);
    assertEquals ("required", aDO.getProtocolImportance ());
    assertEquals ("pkcs7-signature", aDO.getProtocol ());
    assertEquals ("required", aDO.getMICAlgImportance ());
    assertEquals ("sha1", aDO.getMICAlgAsString ());
    assertEquals ("signed-receipt-protocol=required, pkcs7-signature; signed-receipt-micalg=required, sha1",
                  aDO.getAsString ());

    // With additional whitespaces
    aDO = DispositionOptions.createFromString ("   signed-receipt-protocol   =   required   ,   pkcs7-signature   ;   signed-receipt-micalg   =   required   ,   sha1");
    assertNotNull (aDO);
    assertEquals ("required", aDO.getProtocolImportance ());
    assertEquals ("pkcs7-signature", aDO.getProtocol ());
    assertEquals ("required", aDO.getMICAlgImportance ());
    assertEquals ("sha1", aDO.getMICAlgAsString ());
    assertEquals ("signed-receipt-protocol=required, pkcs7-signature; signed-receipt-micalg=required, sha1",
                  aDO.getAsString ());

    // Only protocol
    aDO = DispositionOptions.createFromString ("signed-receipt-protocol=required,pkcs7-signature");
    assertNotNull (aDO);
    assertEquals ("required", aDO.getProtocolImportance ());
    assertEquals ("pkcs7-signature", aDO.getProtocol ());
    assertNull (aDO.getMICAlgImportance ());
    assertNull (aDO.getMICAlgAsString ());
    assertEquals ("signed-receipt-protocol=required, pkcs7-signature", aDO.getAsString ());

    // Only micalg
    aDO = DispositionOptions.createFromString ("signed-receipt-micalg=required, sha1, md5");
    assertNotNull (aDO);
    assertNull (aDO.getProtocolImportance ());
    assertNull (aDO.getProtocol ());
    assertEquals ("required", aDO.getMICAlgImportance ());
    assertEquals ("sha1, md5", aDO.getMICAlgAsString ());
    assertEquals ("signed-receipt-micalg=required, sha1, md5", aDO.getAsString ());

    // Only micalg
    aDO = DispositionOptions.createFromString ("signed-receipt-micalg=required, sha512");
    assertNotNull (aDO);
    assertNull (aDO.getProtocolImportance ());
    assertNull (aDO.getProtocol ());
    assertEquals ("required", aDO.getMICAlgImportance ());
    assertEquals ("sha512", aDO.getMICAlgAsString ());
    assertEquals ("signed-receipt-micalg=required, sha512", aDO.getAsString ());

    // Invalid + valid micalg
    aDO = DispositionOptions.createFromString ("signed-receipt-micalg=required, foo, bla, sha-512, zuzu, ,, , , lol, md5, md4, 9876");
    assertNotNull (aDO);
    assertNull (aDO.getProtocolImportance ());
    assertNull (aDO.getProtocol ());
    assertEquals ("required", aDO.getMICAlgImportance ());
    assertEquals ("sha-512, md5", aDO.getMICAlgAsString ());
    assertEquals ("signed-receipt-micalg=required, sha-512, md5", aDO.getAsString ());

    // Invalid micalg
    aDO = DispositionOptions.createFromString ("signed-receipt-micalg=required, bla");
    assertNotNull (aDO);
    assertNull (aDO.getProtocolImportance ());
    assertNull (aDO.getProtocol ());
    assertEquals ("required", aDO.getMICAlgImportance ());
    assertNull (aDO.getMICAlgAsString ());
    assertEquals ("", aDO.getAsString ());
  }
}
