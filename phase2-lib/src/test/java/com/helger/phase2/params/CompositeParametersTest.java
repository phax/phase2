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
package com.helger.phase2.params;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.time.ZonedDateTime;

import org.junit.Test;

import com.helger.datetime.format.PDTFormatter;
import com.helger.datetime.helper.PDTFactory;
import com.helger.phase2.message.AS2Message;

/**
 * Test class for class {@link CompositeParameters}.
 *
 * @author Philip Helger
 */
public final class CompositeParametersTest
{
  @Test
  public void testBasic () throws AS2InvalidParameterException
  {
    final AS2Message aMsg = new AS2Message ();
    aMsg.headers ().addHeader ("message-id", "12345");
    aMsg.partnership ().setSenderAS2ID ("s1");
    aMsg.partnership ().setReceiverAS2ID ("r1");

    final ZonedDateTime aTestDT = PDTFactory.getCurrentZonedDateTime ();
    final CompositeParameters aParams = new CompositeParameters (false).add ("date", new DateParameters (aTestDT))
                                                                       .add ("msg", new MessageParameters (aMsg));

    final String sNow = PDTFormatter.getForPattern ("uuuuMMddhhmmss").format (aTestDT);

    // Note: the date assertions may fail if they are executed exactly at the
    // edge of a second!
    String sName = aParams.format ("$date.uuuuMMddhhmmss$");
    assertEquals (sNow, sName);

    sName = aParams.format ("any$date.uuuuMMddhhmmss$else");
    assertEquals ("any" + sNow + "else", sName);

    sName = aParams.format ("$date.uuuuMMddhhmmss$$date.uuuuMMddhhmmss$");
    assertEquals (sNow + sNow, sName);

    // No placeholders
    assertEquals ("sender.as2_id, receiver.as2_id, headers.message-id",
                  aParams.format ("sender.as2_id, receiver.as2_id, headers.message-id"));

    // With placeholders
    assertEquals ("s1, r1, 12345",
                  aParams.format ("$msg.sender.as2_id$, $msg.receiver.as2_id$, $msg.headers.message-id$"));

    // Unknown placeholders
    try
    {
      aParams.format ("$dummy$");
      fail ();
    }
    catch (final AS2InvalidParameterException ex)
    {}

    // Escaping test
    assertEquals ("$s1", aParams.format ("$$$msg.sender.as2_id$"));
    assertEquals ("$$s1", aParams.format ("$$$$$msg.sender.as2_id$"));
    assertEquals ("s1$", aParams.format ("$msg.sender.as2_id$$$"));
    assertEquals ("s1$$", aParams.format ("$msg.sender.as2_id$$$$$"));
    assertEquals ("s1$r1", aParams.format ("$msg.sender.as2_id$$$$msg.receiver.as2_id$"));
    assertEquals ("s1$$r1", aParams.format ("$msg.sender.as2_id$$$$$$msg.receiver.as2_id$"));
    assertEquals ("$$s1$$r1$$", aParams.format ("$$$$$msg.sender.as2_id$$$$$$msg.receiver.as2_id$$$$$"));
  }

  @Test
  public void testIgnore () throws AS2InvalidParameterException
  {
    final AS2Message aMsg = new AS2Message ();
    aMsg.headers ().addHeader ("message-id", "12345");
    aMsg.partnership ().setSenderAS2ID ("s1");
    aMsg.partnership ().setReceiverAS2ID ("r1");
    final CompositeParameters aParams = new CompositeParameters (true).add ("msg", new MessageParameters (aMsg));

    // No placeholders
    assertEquals ("sender.as2_id, receiver.as2_id, headers.message-id",
                  aParams.format ("sender.as2_id, receiver.as2_id, headers.message-id"));

    // With placeholders
    assertEquals ("s1, r1, 12345",
                  aParams.format ("$msg.sender.as2_id$, $msg.receiver.as2_id$, $msg.headers.message-id$"));

    // Unknown placeholders
    assertEquals ("", aParams.format ("$dummy$"));
    assertEquals ("any", aParams.format ("any$dummy$"));
    assertEquals ("any", aParams.format ("$dummy$any"));
    assertEquals ("foobar", aParams.format ("foo$dummy$bar"));
  }
}
