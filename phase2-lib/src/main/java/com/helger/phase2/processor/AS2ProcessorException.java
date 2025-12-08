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
package com.helger.phase2.processor;

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonempty;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.lang.clazz.ClassHelper;
import com.helger.base.rt.StackTraceHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.http.CHttp;
import com.helger.phase2.exception.AS2Exception;

/**
 * An exception thrown the an {@link IMessageProcessor} has caught exceptions.
 *
 * @author Philip Helger
 */
public class AS2ProcessorException extends AS2Exception
{
  private final transient IMessageProcessor m_aProcessor;
  private final ICommonsList <AS2Exception> m_aCauses;

  public AS2ProcessorException (@NonNull final IMessageProcessor aProcessor,
                                @NonNull @Nonempty final List <? extends AS2Exception> aCauses)
  {
    super ("Processor '" +
           ClassHelper.getClassLocalName (aProcessor) +
           "' threw " +
           (aCauses.size () == 1 ? "exception:" : "exceptions:"));
    ValueEnforcer.notNull (aProcessor, "Processor");
    ValueEnforcer.notEmptyNoNullValue (aCauses, "causes");

    m_aProcessor = aProcessor;
    m_aCauses = new CommonsArrayList <> (aCauses);
  }

  @NonNull
  public final IMessageProcessor getProcessor ()
  {
    return m_aProcessor;
  }

  @NonNull
  private static String _getMessage (@NonNull @Nonempty final Iterable <? extends Throwable> aCauses,
                                     final boolean bAddStackTrace)
  {
    final StringBuilder aSB = new StringBuilder ();
    for (final Throwable aCause : aCauses)
    {
      // Issue 90: all newlines should be CRLF
      aSB.append (CHttp.EOL);
      if (bAddStackTrace)
      {
        // This includes the exception message
        aSB.append (StackTraceHelper.getStackAsString (aCause, true, CHttp.EOL));
      }
      else
        aSB.append (aCause.getMessage ());
    }
    return aSB.toString ();
  }

  @Override
  public String getMessage ()
  {
    return getMessage (true);
  }

  @NonNull
  public String getMessage (final boolean bAddStackTrace)
  {
    return super.getMessage () + _getMessage (m_aCauses, bAddStackTrace);
  }

  /**
   * Get all causes.<br>
   * Note: before v4.10.0 this was a list of Throwable
   *
   * @return A list of all causing exceptions. Never <code>null</code> nor empty.
   */
  @NonNull
  @Nonempty
  @ReturnsMutableCopy
  public final ICommonsList <AS2Exception> getAllCauses ()
  {
    return m_aCauses.getClone ();
  }

  /**
   * @return A short version of "toString" without exception stack traces. Never <code>null</code>.
   */
  @NonNull
  public String getShortToString ()
  {
    return getClass ().getName () + ": " + getMessage (false);
  }
}
