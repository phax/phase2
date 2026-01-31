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
package com.helger.phase2.server.util;

import org.jspecify.annotations.NonNull;

import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.WrappedAS2Exception;

/**
 * emulates StringTokenizer
 *
 * @author joseph mcverry
 */
public class CommandTokenizer
{
  private final String m_sWorkString;
  private int m_nPos = 0;
  private final int m_nLen;

  /**
   * constructor
   *
   * @param inString
   *        in string
   */
  public CommandTokenizer (@NonNull final String inString)
  {
    m_sWorkString = inString;
    m_nLen = m_sWorkString.length ();
  }

  /**
   * any more tokens in String
   *
   * @return true if there are any more tokens
   * @throws AS2Exception
   *         in case another exception occurs
   */
  public boolean hasMoreTokens () throws AS2Exception
  {
    try
    {
      while (m_nPos < m_nLen - 1 && m_sWorkString.charAt (m_nPos) == ' ')
        m_nPos++;

      if (m_nPos < m_nLen)
        return true;

      return false;
    }
    catch (final RuntimeException ex)
    {
      throw WrappedAS2Exception.wrap (ex);
    }
  }

  /**
   * returns the next token, this handles spaces and quotes
   *
   * @return a string
   * @throws AS2Exception
   *         In case a {@link RuntimeException} occurs
   */
  public String nextToken () throws AS2Exception
  {
    try
    {
      while (m_nPos < m_nLen - 1 && m_sWorkString.charAt (m_nPos) == ' ')
        m_nPos++;

      final StringBuilder sb = new StringBuilder ();

      while (m_nPos < m_nLen && m_sWorkString.charAt (m_nPos) != ' ')
      {

        if (m_sWorkString.charAt (m_nPos) == '"')
        {
          m_nPos++;
          while (m_nPos < m_nLen && m_sWorkString.charAt (m_nPos) != '"')
          {
            sb.append (m_sWorkString.charAt (m_nPos));
            m_nPos++;
          }
          m_nPos++;
          return sb.toString ();
        }
        sb.append (m_sWorkString.charAt (m_nPos));
        m_nPos++;
      }

      return sb.toString ();
    }
    catch (final RuntimeException ex)
    {
      throw WrappedAS2Exception.wrap (ex);
    }
  }
}
