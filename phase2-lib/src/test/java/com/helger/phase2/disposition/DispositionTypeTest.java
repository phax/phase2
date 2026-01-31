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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.annotation.concurrent.Immutable;
import com.helger.phase2.exception.AS2Exception;

/**
 * Test class for class {@link DispositionType}.
 *
 * @author Philip Helger
 */
@Immutable
public final class DispositionTypeTest
{
  @Test
  public void testSuccess () throws AS2Exception
  {
    final DispositionType a = DispositionType.createSuccess ();
    assertEquals (DispositionType.ACTION_AUTOMATIC_ACTION, a.getAction ());
    assertEquals (DispositionType.MDNACTION_MDN_SENT_AUTOMATICALLY, a.getMDNAction ());
    assertEquals (DispositionType.STATUS_PROCESSED, a.getStatus ());
    assertNull (a.getStatusDescription ());
    assertNull (a.getStatusModifier ());

    final DispositionType a2 = DispositionType.createFromString (a.getAsString ());
    assertTrue (a.getAsString ().equalsIgnoreCase (a2.getAsString ()));
  }

  @Test
  public void testError () throws AS2Exception
  {
    final DispositionType a = DispositionType.createError ("Bla Blu");
    assertEquals (DispositionType.ACTION_AUTOMATIC_ACTION, a.getAction ());
    assertEquals (DispositionType.MDNACTION_MDN_SENT_AUTOMATICALLY, a.getMDNAction ());
    assertEquals (DispositionType.STATUS_PROCESSED, a.getStatus ());
    assertEquals (DispositionType.STATUS_MODIFIER_ERROR, a.getStatusModifier ());
    assertEquals ("Bla Blu", a.getStatusDescription ());

    final DispositionType a2 = DispositionType.createFromString (a.getAsString ());
    assertTrue (a.getAsString ().equalsIgnoreCase (a2.getAsString ()));
  }

  @Test
  public void testCrappy () throws AS2Exception
  {
    final DispositionType a = new DispositionType ("a", "b", "c", "d", "e");
    assertEquals ("a", a.getAction ());
    assertEquals ("b", a.getMDNAction ());
    assertEquals ("c", a.getStatus ());
    assertEquals ("d", a.getStatusModifier ());
    assertEquals ("e", a.getStatusDescription ());

    final DispositionType a2 = DispositionType.createFromString (a.getAsString ());
    assertTrue (a.getAsString ().equalsIgnoreCase (a2.getAsString ()));
  }
}
