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
package com.helger.phase2.processor.resender;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonempty;
import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.datetime.helper.PDTFactory;
import com.helger.phase2.message.IMessage;

/**
 * This class represents a single in-memory item to be resend.
 *
 * @author Philip Helger
 * @since 2.2.0
 */
@Immutable
public class ResendItem
{
  private final String m_sResendAction;
  private final int m_nRetries;
  private final IMessage m_aMsg;
  private final LocalDateTime m_aEarliestResendDT;

  public ResendItem (@NonNull @Nonempty final String sResendAction,
                     @Nonnegative final int nRetries,
                     @NonNull final IMessage aMsg,
                     @Nonnegative final long nResendDelayMS)
  {
    m_sResendAction = ValueEnforcer.notEmpty (sResendAction, "ResendAction");
    m_nRetries = ValueEnforcer.isGE0 (nRetries, "Retries");
    m_aMsg = ValueEnforcer.notNull (aMsg, "Message");
    ValueEnforcer.isGE0 (nResendDelayMS, "ResendDelayMS");
    m_aEarliestResendDT = PDTFactory.getCurrentLocalDateTime ().plus (nResendDelayMS, ChronoUnit.MILLIS);
  }

  /**
   * @return The internal action to be taken.
   */
  @NonNull
  @Nonempty
  public String getResendAction ()
  {
    return m_sResendAction;
  }

  /**
   * @return The number of retries already performed (does not include the original try!)
   */
  @Nonnegative
  public int getRetries ()
  {
    return m_nRetries;
  }

  /**
   * @return The message to be resend
   */
  @NonNull
  public IMessage getMessage ()
  {
    return m_aMsg;
  }

  /**
   * @return The date the resend must not happen before
   */
  @NonNull
  public LocalDateTime getEarliestResendDate ()
  {
    return m_aEarliestResendDT;
  }

  /**
   * @return <code>true</code> if this message can be resend now.
   */
  public boolean isTimeToSend ()
  {
    return m_aEarliestResendDT.compareTo (PDTFactory.getCurrentLocalDateTime ()) <= 0;
  }
}
