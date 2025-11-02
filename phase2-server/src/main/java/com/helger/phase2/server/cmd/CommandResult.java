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
package com.helger.phase2.server.cmd;

import java.io.Serializable;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xml.microdom.IMicroContainer;
import com.helger.xml.microdom.MicroContainer;
import com.helger.xml.microdom.serialize.MicroWriter;

@NotThreadSafe
public final class CommandResult
{
  private final ECommandResultType m_eType;
  private final ICommonsList <Serializable> m_aResults = new CommonsArrayList <> ();

  public CommandResult (@NonNull final ECommandResultType eType)
  {
    m_eType = ValueEnforcer.notNull (eType, "Type");
  }

  public CommandResult (@NonNull final ECommandResultType eType, @NonNull final String msg)
  {
    this (eType);
    addResult (msg);
  }

  public CommandResult (final Exception e)
  {
    m_eType = ECommandResultType.TYPE_EXCEPTION;
    addResult (e);
  }

  @NonNull
  public ECommandResultType getType ()
  {
    return m_eType;
  }

  public void addResult (@NonNull final Serializable aResult)
  {
    ValueEnforcer.notNull (aResult, "Result");
    m_aResults.add (aResult);
  }

  public boolean hasNoResult ()
  {
    return m_aResults.isEmpty ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Serializable> getAllResults ()
  {
    return m_aResults.getClone ();
  }

  @NonNull
  public String getResultAsString ()
  {
    final StringBuilder results = new StringBuilder ();
    for (final Serializable x : m_aResults)
      results.append (x.toString ()).append ("\r\n");
    return results.toString ();
  }

  @NonNull
  public String getAsXMLString ()
  {
    final IMicroContainer aCont = new MicroContainer ();
    for (final Serializable x : m_aResults)
      aCont.addElement ("result").addText (x.toString ());
    return MicroWriter.getNodeAsString (aCont);
  }

  @Override
  public String toString ()
  {
    final StringBuilder buf = new StringBuilder ();
    buf.append (m_eType.getText ()).append (":\r\n");
    for (final Serializable aResult : m_aResults)
      buf.append (aResult.toString ()).append ("\r\n");
    return buf.toString ();
  }
}
