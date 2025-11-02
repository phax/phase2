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
package com.helger.phase2.server.util;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

import org.jspecify.annotations.NonNull;

import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.cache.regex.RegExHelper;

/**
 * @author joseph mcverry
 */
public final class ByteCoder
{
  private ByteCoder ()
  {}

  @NonNull
  public static String encode (@NonNull final String inStr)
  {
    final StringBuilder aSB = new StringBuilder (inStr.length () * 3);
    for (final byte element : inStr.getBytes (StandardCharsets.ISO_8859_1))
    {
      // Ensure unsigned int
      aSB.append ('.').append (element & 0xff).append ('.');
    }
    return aSB.toString ();
  }

  @NonNull
  public static String decode (@NonNull final String inStr)
  {
    try (final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream (inStr.length () / 3))
    {
      final Matcher aMatcher = RegExHelper.getMatcher (".[0-9]+.", inStr);
      while (aMatcher.find ())
      {
        final String sMatch = aMatcher.group ();
        // Ensure unsigned int
        final byte me = (byte) (Integer.parseInt (sMatch.substring (1, sMatch.length () - 1)) & 0xff);
        aBAOS.write (me);
      }
      return aBAOS.getAsString (StandardCharsets.ISO_8859_1);
    }
  }
}
