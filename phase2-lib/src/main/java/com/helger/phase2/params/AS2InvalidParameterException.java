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
package com.helger.phase2.params;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.phase2.exception.AS2Exception;

public class AS2InvalidParameterException extends AS2Exception
{
  private final transient Object m_aTarget;
  private final String m_sKey;
  private final String m_sValue;

  public AS2InvalidParameterException (@Nullable final String sMsg,
                                       @Nullable final Object aTarget,
                                       @Nullable final String sKey,
                                       @Nullable final String sValue)
  {
    super (sMsg + " - " + getAsString (sKey, sValue));
    m_aTarget = aTarget;
    m_sKey = sKey;
    m_sValue = sValue;
  }

  public AS2InvalidParameterException (@Nullable final String sMsg)
  {
    super (sMsg);
    m_aTarget = null;
    m_sKey = null;
    m_sValue = null;
  }

  @Nullable
  public String getKey ()
  {
    return m_sKey;
  }

  @Nullable
  public Object getTarget ()
  {
    return m_aTarget;
  }

  @Nullable
  public String getValue ()
  {
    return m_sValue;
  }

  public static void checkValue (@NonNull final Object aTarget,
                                 @NonNull final String sValueName,
                                 @Nullable final Object aValue) throws AS2InvalidParameterException
  {
    if (aValue == null)
      throw new AS2InvalidParameterException ("Value is missing", aTarget, sValueName, null);
  }

  @NonNull
  @Nonempty
  public static String getAsString (@Nullable final String sKey, @Nullable final String sValue)
  {
    return "Invalid parameter value for " + sKey + ": " + sValue;
  }
}
