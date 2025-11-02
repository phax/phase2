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
package com.helger.phase2.processor.receiver;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.CheckForSigned;
import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.base.CGlobal;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.session.IAS2Session;
import com.helger.typeconvert.collection.IStringMap;

public abstract class AbstractActivePollingModule extends AbstractActiveReceiverModule
{
  private class PollTask extends TimerTask
  {
    @Override
    public void run ()
    {
      if (setBusy ())
      {
        try
        {
          poll ();
        }
        finally
        {
          // Also in case of exception
          setNotBusy ();
        }
      }
      else
      {
        LOGGER.info ("Miss tick");
      }
    }
  }

  /** The interval in seconds */
  public static final String ATTR_POLLING_INTERVAL = "interval";
  private static final Logger LOGGER = LoggerFactory.getLogger (AbstractActivePollingModule.class);

  private Timer m_aTimer;
  private final AtomicBoolean m_aBusy = new AtomicBoolean (false);

  @Override
  @OverridingMethodsMustInvokeSuper
  public void initDynamicComponent (@NonNull final IAS2Session aSession, @Nullable final IStringMap aOptions)
                                                                                                              throws AS2Exception
  {
    super.initDynamicComponent (aSession, aOptions);
    getAttributeAsStringRequired (ATTR_POLLING_INTERVAL);
  }

  /**
   * Set the interval in seconds.
   *
   * @param nSeconds
   *        Seconds to wait between polling.
   */
  public void setInterval (final long nSeconds)
  {
    attrs ().putIn (ATTR_POLLING_INTERVAL, nSeconds);
  }

  /**
   * @return The seconds between polling operations.
   */
  @CheckForSigned
  public long getInterval ()
  {
    return attrs ().getAsLong (ATTR_POLLING_INTERVAL, 0L);
  }

  public final boolean isBusy ()
  {
    return m_aBusy.get ();
  }

  final boolean setBusy ()
  {
    return m_aBusy.compareAndSet (false, true);
  }

  final void setNotBusy ()
  {
    m_aBusy.set (false);
  }

  /**
   * The abstract message that is called in the defined interval and needs to be overridden by
   * subclasses.
   */
  public abstract void poll ();

  @Override
  public void doStart () throws AS2Exception
  {
    // Schedule an asynchronous task that does the polling
    m_aTimer = new Timer (true);
    m_aTimer.scheduleAtFixedRate (new PollTask (), 0, getInterval () * CGlobal.MILLISECONDS_PER_SECOND);
    LOGGER.info ("Scheduled the polling task to run every " + getInterval () + " seconds");
  }

  @Override
  public void doStop () throws AS2Exception
  {
    if (m_aTimer != null)
    {
      LOGGER.info ("Now stopping the scheduled polling task");
      m_aTimer.cancel ();
      m_aTimer = null;
    }
  }
}
