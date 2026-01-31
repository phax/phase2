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
package com.helger.phase2.util.dump;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.WillCloseWhenClosed;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.stream.StreamHelper;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.http.CHttp;
import com.helger.phase2.message.IBaseMessage;

/**
 * Stream based incoming HTTP dumper.
 *
 * @author Philip Helger
 * @since 4.2.0
 */
public class HTTPIncomingDumperStreamBased implements IHTTPIncomingDumper
{
  private final OutputStream m_aOS;

  public HTTPIncomingDumperStreamBased (@NonNull @WillCloseWhenClosed final OutputStream aOS)
  {
    ValueEnforcer.notNull (aOS, "OutputStream");
    m_aOS = aOS;
  }

  @NonNull
  protected final OutputStream getWrappedOS ()
  {
    return m_aOS;
  }

  public void dumpIncomingRequest (@NonNull final List <String> aHeaderLines,
                                   @NonNull final byte [] aPayload,
                                   @Nullable final IBaseMessage aMsg)
  {
    try
    {
      for (final String sHeaderLine : aHeaderLines)
        m_aOS.write (sHeaderLine.getBytes (CHttp.HTTP_CHARSET));
      if (!aHeaderLines.isEmpty ())
        m_aOS.write (CHttp.EOL.getBytes (CHttp.HTTP_CHARSET));
      m_aOS.write (aPayload);
    }
    catch (final IOException ex)
    {
      throw new UncheckedIOException (ex);
    }
    finally
    {
      StreamHelper.close (m_aOS);
    }
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("OutputStream", m_aOS).getToString ();
  }
}
