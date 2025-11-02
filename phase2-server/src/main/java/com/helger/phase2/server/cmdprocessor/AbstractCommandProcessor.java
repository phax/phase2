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
package com.helger.phase2.server.cmdprocessor;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.annotation.style.ReturnsMutableObject;
import com.helger.annotation.style.UnsupportedOperation;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.lang.clazz.ClassHelper;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phase2.IDynamicComponent;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.server.cmd.ICommand;
import com.helger.phase2.server.cmd.ICommandRegistry;
import com.helger.phase2.session.IAS2Session;
import com.helger.typeconvert.collection.StringMap;

public abstract class AbstractCommandProcessor implements ICommandProcessor, IDynamicComponent, Runnable
{
  private final StringMap m_aAttrs = new StringMap ();
  private final ICommonsOrderedMap <String, ICommand> m_aCommands = new CommonsLinkedHashMap <> ();
  private volatile boolean m_bTerminated = false;

  public AbstractCommandProcessor ()
  {}

  @NonNull
  @ReturnsMutableObject
  public final StringMap attrs ()
  {
    return m_aAttrs;
  }

  @NonNull
  public String getName ()
  {
    return ClassHelper.getClassLocalName (this);
  }

  @UnsupportedOperation
  public IAS2Session getSession ()
  {
    throw new UnsupportedOperationException ("No session available!");
  }

  public void init () throws AS2Exception
  {}

  @NonNull
  @ReturnsMutableCopy
  public ICommonsMap <String, ICommand> getAllCommands ()
  {
    return m_aCommands.getClone ();
  }

  @Nullable
  public ICommand getCommand (final String name)
  {
    return m_aCommands.get (name);
  }

  public boolean isTerminated ()
  {
    return m_bTerminated;
  }

  @UnsupportedOperation
  public void processCommand () throws AS2Exception
  {
    throw new AS2Exception ("super class method call, not initialized correctly");
  }

  public void addCommands (@NonNull final ICommandRegistry aCommandRegistry)
  {
    ValueEnforcer.notNull (aCommandRegistry, "CommandRegistry");
    m_aCommands.putAll (aCommandRegistry.getAllCommands ());
  }

  public void terminate ()
  {
    m_bTerminated = true;
  }
}
