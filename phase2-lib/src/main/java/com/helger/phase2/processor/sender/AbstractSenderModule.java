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
package com.helger.phase2.processor.sender;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonnegative;
import com.helger.base.string.StringParser;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.partner.Partnership;
import com.helger.phase2.processor.module.AbstractProcessorModule;
import com.helger.phase2.processor.resender.IProcessorResenderModule;

/**
 * An abstract sender module that allows for retry. It allows to configure the maximum number of
 * retries.
 *
 * @author Philip Helger
 */
public abstract class AbstractSenderModule extends AbstractProcessorModule implements IProcessorSenderModule
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AbstractSenderModule.class);

  /**
   * How many times should this message be sent?
   *
   * @param aPartnerhsip
   *        Partnership to be used. May be <code>null</code>
   * @param aOptions
   *        Options to choose from. May be <code>null</code>.
   * @return 0 to indicate no retry.
   */
  @Nonnegative
  protected final int getRetryCount (@Nullable final Partnership aPartnerhsip,
                                     @Nullable final Map <String, Object> aOptions)
  {
    int nRetries = -1;

    if (aPartnerhsip != null)
    {
      // Provided in the partnership?
      final String sTriesLeft = aPartnerhsip.getAttribute (IProcessorResenderModule.OPTION_RETRIES);
      final int nRetriesPS = StringParser.parseInt (sTriesLeft, -1);
      if (nRetriesPS >= 0)
        nRetries = nRetriesPS;
    }

    if (aOptions != null)
    {
      // Provided in the options?
      final String sTriesLeft = (String) aOptions.get (IProcessorResenderModule.OPTION_RETRIES);
      final int nRetriesOptions = StringParser.parseInt (sTriesLeft, -1);
      if (nRetriesOptions >= 0)
        if (nRetries < 0)
          nRetries = nRetriesOptions;
        else
        {
          // Use the minimum of partnership and options
          nRetries = Math.min (nRetries, nRetriesOptions);
        }
    }

    {
      // Provided as an attribute?
      final String sTriesLeft = attrs ().getAsString (IProcessorResenderModule.OPTION_RETRIES);
      final int nRetriesAttr = StringParser.parseInt (sTriesLeft, -1);
      if (nRetriesAttr >= 0)
        if (nRetries < 0)
          nRetries = nRetriesAttr;
        else
        {
          // Use the minimum of attribute value, partnership and options
          nRetries = Math.min (nRetries, nRetriesAttr);
        }
    }

    if (nRetries < 0)
    {
      // Not provided. Use default.
      return IProcessorResenderModule.DEFAULT_RETRIES;
    }

    // Never returning negative values
    return nRetries;
  }

  /**
   * @param sResendAction
   *        Handler action name to use. May not be <code>null</code>.
   * @param aMsg
   *        The message to be resend. May be an AS2 message or an MDN.
   * @param aCause
   *        The error cause.
   * @param nTriesLeft
   *        The number of retries left.
   * @return <code>true</code> if the message was scheduled for re-sending.
   * @throws AS2Exception
   *         In case of an error
   */
  protected final boolean doResend (@NonNull final String sResendAction,
                                    @NonNull final IMessage aMsg,
                                    @Nullable final AS2Exception aCause,
                                    final int nTriesLeft) throws AS2Exception
  {
    if (nTriesLeft <= 0)
    {
      LOGGER.info ("Retry count exceeded - no more retries for" + aMsg.getLoggingText ());
      return false;
    }

    final ICommonsMap <String, Object> aOptions = new CommonsHashMap <> ();
    aOptions.put (IProcessorResenderModule.OPTION_CAUSE, aCause);
    aOptions.put (IProcessorResenderModule.OPTION_INITIAL_SENDER, this);
    aOptions.put (IProcessorResenderModule.OPTION_RESEND_ACTION, sResendAction);
    aOptions.put (IProcessorResenderModule.OPTION_RETRIES, Integer.toString (nTriesLeft));
    getSession ().getMessageProcessor ().handle (IProcessorResenderModule.DO_RESEND, aMsg, aOptions);

    LOGGER.info ("Scheduled message for resending" + aMsg.getLoggingText ());
    return true;
  }
}
