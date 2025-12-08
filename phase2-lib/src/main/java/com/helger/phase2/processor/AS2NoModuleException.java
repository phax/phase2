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

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.annotation.style.ReturnsMutableObject;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;

/**
 * AS2 exception that is thrown if no handling module was found.
 *
 * @author Philip Helger
 */
public class AS2NoModuleException extends AS2Exception
{
  private final transient ICommonsMap <String, Object> m_aOptions;
  private final IMessage m_aMsg;
  private final String m_sAction;

  public AS2NoModuleException (@Nullable final String sAction,
                               @Nullable final IMessage aMsg,
                               @Nullable final Map <String, Object> aOptions)
  {
    super (getAsString (sAction, aMsg, aOptions));
    m_sAction = sAction;
    m_aMsg = aMsg;
    m_aOptions = new CommonsHashMap <> (aOptions);
  }

  @Nullable
  public final String getAction ()
  {
    return m_sAction;
  }

  @Nullable
  public final IMessage getMsg ()
  {
    return m_aMsg;
  }

  @Nullable
  @ReturnsMutableObject
  public final ICommonsMap <String, Object> options ()
  {
    return m_aOptions;
  }

  @NonNull
  public String getAsString ()
  {
    return getAsString (m_sAction, m_aMsg, m_aOptions);
  }

  @NonNull
  @Nonempty
  protected static String getAsString (@Nullable final String sAction,
                                       @Nullable final IMessage aMsg,
                                       @Nullable final Map <String, Object> aOptions)
  {
    return "AS2NoModuleException: Requested action '" +
           sAction +
           "'; Message: " +
           aMsg +
           (aOptions != null ? "; Options " + aOptions : "");
  }
}
