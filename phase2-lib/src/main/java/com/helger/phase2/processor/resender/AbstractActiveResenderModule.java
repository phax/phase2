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
package com.helger.phase2.processor.resender;

import java.util.Timer;
import java.util.TimerTask;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.base.CGlobal;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.params.AS2InvalidParameterException;
import com.helger.phase2.processor.module.AbstractActiveModule;
import com.helger.phase2.session.IAS2Session;
import com.helger.typeconvert.collection.IStringMap;

/**
 * Base class for an active resender module.
 *
 * @author OpenAS2
 * @author Philip Helger
 */
public abstract class AbstractActiveResenderModule extends AbstractActiveModule implements IProcessorResenderModule
{
  /** The resend delay in seconds */
  public static final String ATTR_RESEND_DELAY_SECONDS = "resenddelay";
  /** The polling interval in seconds */
  public static final String ATTR_POLLING_INTERVAL_SECONDS = "pollinginterval";

  /** The default resend delay in milliseconds (15 minutes) */
  public static final long DEFAULT_RESEND_DELAY_MS = 15 * CGlobal.MILLISECONDS_PER_MINUTE;

  /** The timer default polling interval of 30 seconds. */
  public static final long DEFAULT_POLLING_MS = 30 * CGlobal.MILLISECONDS_PER_SECOND;

  private class ResendPollTask extends TimerTask
  {
    @Override
    public void run ()
    {
      // Call resend of module class
      resend ();
    }
  }

  private Timer m_aTimer;

  /** The timer polling interval in milliseconds. Defaults to 30 seconds. */
  private long m_nPollingMS = DEFAULT_POLLING_MS;

  @Override
  @OverridingMethodsMustInvokeSuper
  public void initDynamicComponent (@NonNull final IAS2Session aSession, @Nullable final IStringMap aParameters)
                                                                                                                 throws AS2Exception
  {
    super.initDynamicComponent (aSession, aParameters);

    if (attrs ().containsKey (ATTR_POLLING_INTERVAL_SECONDS))
    {
      m_nPollingMS = attrs ().getAsLong (ATTR_POLLING_INTERVAL_SECONDS) * CGlobal.MILLISECONDS_PER_SECOND;
      if (m_nPollingMS < 1)
        throw new AS2Exception ("The provided polling milliseconds value is invalid. It must be > 0 but is " +
                                m_nPollingMS);
    }
  }

  /**
   * @return The defined delay until re-send in milliseconds.
   * @throws AS2InvalidParameterException
   *         If an invalid value is configured.
   */
  @Nonnegative
  protected final long getResendDelayMS () throws AS2InvalidParameterException
  {
    if (!attrs ().containsKey (ATTR_RESEND_DELAY_SECONDS))
      return DEFAULT_RESEND_DELAY_MS;
    return getAttributeAsIntRequired (ATTR_RESEND_DELAY_SECONDS) * CGlobal.MILLISECONDS_PER_SECOND;
  }

  public abstract void resend ();

  @Override
  @OverridingMethodsMustInvokeSuper
  public void doStart () throws AS2Exception
  {
    if (m_aTimer != null)
      throw new IllegalStateException ("Resending timer is already running!");

    m_aTimer = new Timer ("Resender", true);
    m_aTimer.scheduleAtFixedRate (new ResendPollTask (), 0, m_nPollingMS);
  }

  @Override
  @OverridingMethodsMustInvokeSuper
  public void doStop () throws AS2Exception
  {
    if (m_aTimer != null)
    {
      m_aTimer.cancel ();
      m_aTimer = null;
    }
  }

  @Override
  public boolean equals (final Object o)
  {
    // New member, no change
    return super.equals (o);
  }

  @Override
  public int hashCode ()
  {
    // New member, no change
    return super.hashCode ();
  }

}
