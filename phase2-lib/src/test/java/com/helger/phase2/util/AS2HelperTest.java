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
package com.helger.phase2.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.helger.http.CHttpHeader;
import com.helger.phase2.disposition.DispositionType;
import com.helger.phase2.message.AS2Message;
import com.helger.phase2.message.AS2MessageMDN;
import com.helger.phase2.message.IMessageMDN;
import com.helger.phase2.partner.Partnership;
import com.helger.phase2.partner.SelfFillingPartnershipFactory;
import com.helger.phase2.session.AS2Session;

/**
 * Test class for class {@link AS2Helper}
 */
public final class AS2HelperTest
{
  /**
   * Regression test: createSyncMDN must not throw when aIncomingMIC is null. MIC may legitimately
   * be null for unsigned messages or when an error occurs before MIC computation
   * (AS2DispositionException path).
   *
   * @throws Exception
   *         In case of error
   */
  @Test
  public void testCreateSyncMDNWithNullMIC () throws Exception
  {
    final AS2Session aSession = new AS2Session ();
    aSession.setPartnershipFactory (new SelfFillingPartnershipFactory ());

    final Partnership aPartnership = new Partnership ("test");
    aPartnership.setSenderAS2ID ("sender");
    aPartnership.setReceiverAS2ID ("receiver");

    final AS2Message aMsg = new AS2Message ();
    aMsg.setPartnership (aPartnership);
    aMsg.headers ().setHeader (CHttpHeader.AS2_TO, "sender");
    aMsg.headers ().setHeader (CHttpHeader.MESSAGE_ID, "<test@example>");

    // Must not throw NPE
    final IMessageMDN aMDN = AS2Helper.createSyncMDN (aSession,
                                                      aMsg,
                                                      null,
                                                      DispositionType.createSuccess (),
                                                      "Test MDN");
    assertNotNull (aMDN);
    // No MIC algorithm configured and no Disposition-Notification-Options header
    // -> MDNA_MIC attribute must be absent
    assertNull (aMDN.attrs ().getAsString (AS2MessageMDN.MDNA_MIC));
  }
}
