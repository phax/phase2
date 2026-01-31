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

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.processor.sender.IProcessorSenderModule;

/**
 * A synchronous, in-memory resender module that has no delay.
 *
 * @author Philip Helger
 * @since 2.2.0
 */
public class ImmediateResenderModule extends AbstractResenderModule
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ImmediateResenderModule.class);

  @Override
  public boolean canHandle (@NonNull final String sAction,
                            @NonNull final IMessage aMsg,
                            @Nullable final Map <String, Object> aOptions)
  {
    return sAction.equals (IProcessorResenderModule.DO_RESEND);
  }

  @Override
  public void handle (@NonNull final String sAction,
                      @NonNull final IMessage aMsg,
                      @Nullable final Map <String, Object> aOptions) throws AS2Exception
  {
    LOGGER.info ("Immediately resending message" + aMsg.getLoggingText ());

    String sResendAction = aOptions == null ? null : (String) aOptions.get (
                                                                            IProcessorResenderModule.OPTION_RESEND_ACTION);
    if (sResendAction == null)
    {
      LOGGER.warn ("The resending action is missing - default to message sending!");
      sResendAction = IProcessorSenderModule.DO_SEND;
    }

    final String sRetries = aOptions == null ? null : (String) aOptions.get (IProcessorResenderModule.OPTION_RETRIES);
    int nRetries;
    if (sRetries != null)
      nRetries = Integer.parseInt (sRetries);
    else
    {
      nRetries = IProcessorResenderModule.DEFAULT_RETRIES;
      LOGGER.warn ("The resending retry count is missing - default to " + nRetries + "!");
    }

    // Update the retries - decrement here
    nRetries--;
    if (aOptions != null)
      aOptions.put (IProcessorResenderModule.OPTION_RETRIES, Integer.toString (nRetries));

    // Send again
    getSession ().getMessageProcessor ().handle (sResendAction, aMsg, aOptions);
  }
}
