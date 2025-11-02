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

import java.time.ZonedDateTime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.VisibleForTesting;
import com.helger.phase2.util.AS2DateHelper;

public class DateParameters extends AbstractParameterParser
{
  private ZonedDateTime m_aDateTime;

  public DateParameters ()
  {}

  /**
   * Constructor that uses a predefined date and time for consistent tests.
   *
   * @param aZDT
   *        The date time to use. <code>null</code> means "use current date time"
   */
  @VisibleForTesting
  public DateParameters (@Nullable final ZonedDateTime aZDT)
  {
    m_aDateTime = aZDT;
  }

  /**
   * @deprecated Don't call this
   */
  @Override
  @Deprecated (forRemoval = false)
  public void setParameter (final String sKey, final String sValue) throws AS2InvalidParameterException
  {
    throw new AS2InvalidParameterException ("setParameter is not supported", this, sKey, sValue);
  }

  @Override
  @NonNull
  public String getParameter (@Nullable final String sKey) throws AS2InvalidParameterException
  {
    if (sKey == null)
      throw new AS2InvalidParameterException ("Invalid key", this, sKey, null);

    if (m_aDateTime != null)
    {
      // Use predefined date time
      return AS2DateHelper.formatDate (sKey, m_aDateTime);
    }
    return AS2DateHelper.getFormattedDateNow (sKey);
  }
}
