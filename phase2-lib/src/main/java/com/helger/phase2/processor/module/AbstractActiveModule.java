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
package com.helger.phase2.processor.module;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.OverrideOnDemand;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.AS2UnsupportedException;
import com.helger.phase2.message.IMessage;

/**
 * Abstract base implementation for {@link IProcessorActiveModule} derived from
 * {@link AbstractProcessorModule}.
 *
 * @author Philip Helger
 */
public abstract class AbstractActiveModule extends AbstractProcessorModule implements IProcessorActiveModule
{
  private final AtomicBoolean m_aRunning = new AtomicBoolean (false);

  public final boolean isRunning ()
  {
    return m_aRunning.get ();
  }

  private void _setRunning (final boolean bRunning)
  {
    m_aRunning.set (bRunning);
  }

  @OverrideOnDemand
  public boolean canHandle (@NonNull final String sAction,
                            @NonNull final IMessage aMsg,
                            @Nullable final Map <String, Object> aOptions)
  {
    return false;
  }

  public void handle (@NonNull final String sAction,
                      @NonNull final IMessage aMsg,
                      @Nullable final Map <String, Object> aOptions) throws AS2Exception
  {
    throw new AS2UnsupportedException ("Active modules don't handle anything by default");
  }

  public void forceStop (@Nullable final Exception aCause)
  {
    new AS2ForcedStopException (aCause).terminate ();

    try
    {
      stop ();
    }
    catch (final AS2Exception ex)
    {
      ex.terminate ();
    }
  }

  /**
   * Implement the internal start logic.
   *
   * @throws AS2Exception
   *         In case of an error.
   */
  public abstract void doStart () throws AS2Exception;

  public void start () throws AS2Exception
  {
    _setRunning (true);
    doStart ();
  }

  /**
   * Implement the internal stop logic.
   *
   * @throws AS2Exception
   *         In case of an error.
   */
  public abstract void doStop () throws AS2Exception;

  public void stop () throws AS2Exception
  {
    _setRunning (false);
    doStop ();
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

  @Override
  public String toString ()
  {
    return ToStringGenerator.getDerived (super.toString ()).append ("Running", isRunning ()).getToString ();
  }
}
