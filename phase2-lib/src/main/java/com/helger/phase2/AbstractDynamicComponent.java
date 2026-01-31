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
package com.helger.phase2;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.annotation.concurrent.GuardedBy;
import com.helger.annotation.style.ReturnsMutableObject;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.lang.clazz.ClassHelper;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.params.AS2InvalidParameterException;
import com.helger.phase2.session.IAS2Session;
import com.helger.typeconvert.collection.IStringMap;
import com.helger.typeconvert.collection.StringMap;

/**
 * Abstract implementation of {@link IDynamicComponent}.
 *
 * @author Philip Helger
 */
public abstract class AbstractDynamicComponent implements IDynamicComponent
{
  protected final SimpleReadWriteLock m_aRWLock = new SimpleReadWriteLock ();
  @GuardedBy ("m_aRWLock")
  private final StringMap m_aAttrs = new StringMap ();
  private IAS2Session m_aSession;

  @NonNull
  @ReturnsMutableObject
  public final StringMap attrs ()
  {
    return m_aAttrs;
  }

  @Nullable
  public String getName ()
  {
    return ClassHelper.getClassLocalName (this);
  }

  @NonNull
  public final String getAttributeAsStringRequired (@NonNull final String sKey) throws AS2InvalidParameterException
  {
    final String sValue = m_aRWLock.readLockedGet ( () -> attrs ().getAsString (sKey));
    if (sValue == null)
      throw new AS2InvalidParameterException ("Parameter not found", this, sKey, null);
    return sValue;
  }

  public final int getAttributeAsIntRequired (@NonNull final String sKey) throws AS2InvalidParameterException
  {
    final int nValue = m_aRWLock.readLockedInt ( () -> attrs ().getAsInt (sKey, Integer.MIN_VALUE));
    if (nValue == Integer.MIN_VALUE)
      throw new AS2InvalidParameterException ("Parameter not found", this, sKey, null);
    return nValue;
  }

  @NonNull
  public final IAS2Session getSession ()
  {
    if (m_aSession == null)
      throw new IllegalStateException ("No AS2 session present so far!");
    return m_aSession;
  }

  @OverridingMethodsMustInvokeSuper
  public void initDynamicComponent (@NonNull final IAS2Session aSession, @Nullable final IStringMap aParameters)
                                                                                                                 throws AS2Exception
  {
    m_aSession = ValueEnforcer.notNull (aSession, "Session");
    m_aRWLock.writeLockedGet ( () -> attrs ().putAllIn (aParameters));
  }

  @Override
  public boolean equals (final Object o)
  {
    // New member, no change
    return super.equals (o);
  }

  @Override
  public int hashCode ()
  {
    // New member, no change
    return super.hashCode ();
  }

  @Override
  public String toString ()
  {
    // do not add "session" - this may lead to an endless loop
    return ToStringGenerator.getDerived (super.toString ()).getToString ();
  }
}
