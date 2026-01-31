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

import org.jspecify.annotations.NonNull;

import com.helger.phase2.exception.AS2Exception;

/**
 * Special {@link AS2Exception} with HTTP URL details
 *
 * @author Philip Helger
 */
public class AS2HttpResponseException extends AS2Exception
{
  private final String m_sURL;
  private final int m_nCode;
  private final String m_sMessage;

  /**
   * Constructor
   *
   * @param sUrl
   *        The URL that caused the error.
   * @param nCode
   *        The HTTP status code.
   * @param sMessage
   *        The HTTP status message
   */
  public AS2HttpResponseException (@NonNull final String sUrl, final int nCode, @NonNull final String sMessage)
  {
    super ("Http Response from " + sUrl + ": " + nCode + " - " + sMessage);
    m_sURL = sUrl;
    m_nCode = nCode;
    m_sMessage = sMessage;
  }

  /**
   * @return The URL that failed. Never <code>null</code>.
   */
  @NonNull
  public String getUrl ()
  {
    return m_sURL;
  }

  /**
   * @return The HTTP status code retrieved.
   */
  public int getCode ()
  {
    return m_nCode;
  }

  /**
   * @return The HTTP status text retrieved.
   */
  @Override
  public String getMessage ()
  {
    return m_sMessage;
  }
}
