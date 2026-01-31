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
package com.helger.phase2.crypto;

import java.security.MessageDigest;
import java.util.Arrays;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.array.ArrayHelper;

/**
 * A logging wrapper around a {@link MessageDigest}. For debugging purposes only.
 *
 * @author Philip Helger
 */
final class LoggingMessageDigest extends MessageDigest
{
  private static final Logger LOGGER = LoggerFactory.getLogger (LoggingMessageDigest.class);
  private final MessageDigest m_aMD;

  LoggingMessageDigest (@NonNull final MessageDigest aMD)
  {
    super (aMD.getAlgorithm ());
    m_aMD = aMD;
  }

  @Override
  protected void engineUpdate (final byte nInput)
  {
    LOGGER.info ("update(1): " + nInput);
    m_aMD.update (nInput);
  }

  @Override
  protected void engineUpdate (final byte [] aInput, final int nOffset, final int nLen)
  {
    LOGGER.info ("update(" + nLen + "): " + Arrays.toString (ArrayHelper.getCopy (aInput, nOffset, nLen)));
    m_aMD.update (aInput, nOffset, nLen);
  }

  @Override
  protected byte [] engineDigest ()
  {
    final byte [] ret = m_aMD.digest ();
    LOGGER.info ("digest=" + Arrays.toString (ret));
    return ret;
  }

  @Override
  protected void engineReset ()
  {
    LOGGER.info ("reset");
    m_aMD.reset ();
  }
}
