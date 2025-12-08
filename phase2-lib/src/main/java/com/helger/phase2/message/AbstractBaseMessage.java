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
package com.helger.phase2.message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonempty;
import com.helger.annotation.style.ReturnsMutableObject;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.http.header.HttpHeaderMap;
import com.helger.phase2.partner.Partnership;
import com.helger.typeconvert.collection.StringMap;

/**
 * Base implementation of {@link IBaseMessage} as the base class for {@link AbstractMessage} and
 * {@link AbstractMessageMDN}.
 *
 * @author Philip Helger
 */
public abstract class AbstractBaseMessage implements IBaseMessage
{
  private StringMap m_aAttributes = new StringMap ();
  private HttpHeaderMap m_aHeaders = new HttpHeaderMap ();
  private Partnership m_aPartnership = Partnership.createPlaceholderPartnership ();

  public AbstractBaseMessage ()
  {}

  private void readObject (@NonNull final ObjectInputStream aOIS) throws IOException, ClassNotFoundException
  {
    // read in attributes
    m_aAttributes = (StringMap) aOIS.readObject ();
    m_aHeaders = (HttpHeaderMap) aOIS.readObject ();

    // read in partnership
    m_aPartnership = (Partnership) aOIS.readObject ();
  }

  private void writeObject (@NonNull final ObjectOutputStream aOOS) throws IOException
  {
    // write attributes
    aOOS.writeObject (m_aAttributes);

    // write headers
    aOOS.writeObject (m_aHeaders);

    // write partnership info
    aOOS.writeObject (m_aPartnership);
  }

  @NonNull
  @ReturnsMutableObject
  public final StringMap attrs ()
  {
    return m_aAttributes;
  }

  @NonNull
  @ReturnsMutableObject
  public final HttpHeaderMap headers ()
  {
    return m_aHeaders;
  }

  public final void setPartnership (@NonNull final Partnership aPartnership)
  {
    ValueEnforcer.notNull (aPartnership, "Partnership");
    m_aPartnership = aPartnership;
  }

  @NonNull
  @ReturnsMutableObject
  public final Partnership partnership ()
  {
    return m_aPartnership;
  }

  @NonNull
  @Nonempty
  public String getLoggingText ()
  {
    final String sMsgID = getMessageID ();
    return " [" + (sMsgID == null ? "no message ID set" : sMsgID) + "]";
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("attributes", m_aAttributes)
                                       .append ("headers", m_aHeaders)
                                       .append ("partnership", m_aPartnership)
                                       .getToString ();
  }
}
