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
package com.helger.phase2.util.http;

import java.io.IOException;
import java.io.InputStream;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.WillCloseWhenClosed;
import com.helger.base.io.stream.WrappedInputStream;

/**
 * Stream to read a chunked body stream. Input stream should be at the beginning of a chunk, i.e. at
 * the body beginning (after the end of headers marker). The resulting stream reads the data through
 * the chunks.
 *
 * @author Ziv Harpaz
 */
public class ChunkedInputStream extends WrappedInputStream
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ChunkedInputStream.class);
  /**
   * Number of bytes left in current chunk
   */
  private int m_nLeft = 0;
  private boolean m_bAfterFirstChunk = false;

  public ChunkedInputStream (@NonNull @WillCloseWhenClosed final InputStream aIS)
  {
    super (aIS);
  }

  @Override
  public final int read () throws IOException
  {
    if (m_nLeft < 0)
      return -1;

    if (m_nLeft == 0)
    {
      final InputStream aIS = getWrappedInputStream ();
      if (m_bAfterFirstChunk)
      {
        // read the CRLF after chunk data
        HTTPHelper.readTillNextLine (aIS);
      }
      else
      {
        m_bAfterFirstChunk = true;
      }

      m_nLeft = HTTPHelper.readChunkLen (aIS);
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Read chunk size: " + m_nLeft);

      // check for end of data
      if (m_nLeft <= 0)
      {
        // No more chunks means EOF
        m_nLeft = -1;
        // mark end of stream
        return -1;
      }
    }
    m_nLeft--;
    return super.read ();
  }

  @Override
  public final int read (@NonNull final byte [] aBuf, final int nOffset, final int nLength) throws IOException
  {
    if (m_nLeft < 0)
      return -1;

    int nReadCount = 0;
    int nRealOffset = nOffset;
    while (nLength > nReadCount)
    {
      if (m_nLeft == 0)
      {
        final InputStream aIS = getWrappedInputStream ();
        if (m_bAfterFirstChunk)
        {
          // read the CRLF after chunk data
          HTTPHelper.readTillNextLine (aIS);
        }
        else
        {
          m_bAfterFirstChunk = true;
        }

        m_nLeft = HTTPHelper.readChunkLen (aIS);
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Read chunk size: " + m_nLeft);

        // check for end of data
        if (m_nLeft <= 0)
        {
          // No more chunks means EOF
          m_nLeft = -1;
          // mark end of stream
          return nReadCount > 0 ? nReadCount : -1;
        }
      }

      final int ret = super.read (aBuf, nRealOffset, Math.min (nLength - nReadCount, m_nLeft));
      nRealOffset += ret;
      m_nLeft -= ret;
      nReadCount += ret;
    }
    return nReadCount;
  }
}
