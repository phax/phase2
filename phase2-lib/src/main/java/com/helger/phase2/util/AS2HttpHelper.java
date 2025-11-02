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
package com.helger.phase2.util;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.codec.impl.RFC2616Codec;
import com.helger.base.string.StringHelper;
import com.helger.http.header.HttpHeaderMap;

import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.ParseException;

@Immutable
public final class AS2HttpHelper
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AS2HttpHelper.class);

  private AS2HttpHelper ()
  {}

  @Nullable
  public static ContentType parseContentType (@Nullable final String sContentType)
  {
    if (StringHelper.isNotEmpty (sContentType))
    {
      // Auto decode is possible
      final String sRealContentType = RFC2616Codec.isMaybeEncoded (sContentType) ? new RFC2616Codec ().getDecodedAsString (sContentType)
                                                                                 : sContentType;
      try
      {
        return new ContentType (sRealContentType);
      }
      catch (final ParseException ex)
      {
        // Something went wrong
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Error parsing Content-Type '" + sContentType + "'", ex);
      }
    }
    return null;
  }

  @Nullable
  public static String getCleanContentType (@Nullable final String sContentType)
  {
    final ContentType aCT = parseContentType (sContentType);
    return aCT != null ? aCT.toString () : null;
  }

  @NonNull
  public static InternetHeaders getAsInternetHeaders (@NonNull final HttpHeaderMap aHeaders)
  {
    final InternetHeaders ret = new InternetHeaders ();
    // No unification/no masking
    aHeaders.forEachSingleHeader (ret::addHeader, false, false);
    return ret;
  }
}
