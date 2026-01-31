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
package com.helger.phase2.disposition;

import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.ICommonsList;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.processor.AS2ProcessorException;

/**
 * Exception thrown in case a message disposition contains an error or a warning. The content of
 * {@link #getText()} is send back as the MDN in case of a receiving error.
 *
 * @author Philip Helger
 */
public class AS2DispositionException extends AS2Exception
{
  private final transient DispositionType m_aDisposition;
  private final String m_sText;

  public AS2DispositionException (@NonNull final DispositionType aDisposition,
                                  @Nullable final String sText,
                                  @Nullable final Throwable aCause)
  {
    super (aDisposition.getAsString (), aCause);
    m_aDisposition = aDisposition;
    m_sText = sText;
  }

  /**
   * @return The disposition as provided in the constructor. Never <code>null</code>.
   */
  @NonNull
  public final DispositionType getDisposition ()
  {
    return m_aDisposition;
  }

  @Nullable
  public final String getText ()
  {
    return m_sText;
  }

  @NonNull
  public static AS2DispositionException wrap (@NonNull final Exception ex,
                                              @NonNull final Supplier <DispositionType> aDispositionTypeSupplier,
                                              @NonNull final Supplier <String> aTextSupplier)
  {
    ValueEnforcer.notNull (ex, "Exception");
    ValueEnforcer.notNull (aDispositionTypeSupplier, "DispositionTypeSupplier");
    ValueEnforcer.notNull (aTextSupplier, "TextSupplier");

    if (ex instanceof AS2DispositionException)
      return (AS2DispositionException) ex;

    if (ex instanceof AS2ProcessorException)
    {
      // Special handling for #130
      final ICommonsList <AS2Exception> aCauses = ((AS2ProcessorException) ex).getAllCauses ();
      if (aCauses.size () == 1)
      {
        final AS2Exception aFirst = aCauses.getFirstOrNull ();
        if (aFirst instanceof AS2DispositionException)
          return (AS2DispositionException) aFirst;
      }
    }

    return new AS2DispositionException (aDispositionTypeSupplier.get (), aTextSupplier.get (), ex);
  }
}
