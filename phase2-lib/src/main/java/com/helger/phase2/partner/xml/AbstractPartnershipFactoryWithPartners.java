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
package com.helger.phase2.partner.xml;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.state.EChange;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.partner.AbstractPartnershipFactory;

/**
 * Abstract {@link IPartnershipFactoryWithPartners} implementation based on
 * {@link AbstractPartnershipFactory} using {@link PartnerMap} as the underlying data storage object
 * for the partners.
 *
 * @author Philip Helger
 */
@ThreadSafe
public abstract class AbstractPartnershipFactoryWithPartners extends AbstractPartnershipFactory implements
                                                             IPartnershipFactoryWithPartners
{
  private final PartnerMap m_aPartners = new PartnerMap ();

  protected final void setPartners (@NonNull final PartnerMap aPartners) throws AS2Exception
  {
    m_aRWLock.writeLockedThrowing ( () -> {
      m_aPartners.setPartners (aPartners);
      markAsChanged ();
    });
  }

  public void addPartner (@NonNull final Partner aNewPartner) throws AS2Exception
  {
    m_aRWLock.writeLockedThrowing ( () -> {
      m_aPartners.addPartner (aNewPartner);
      markAsChanged ();
    });
  }

  @NonNull
  public EChange removePartner (@Nullable final String sPartnerName) throws AS2Exception
  {
    return m_aRWLock.writeLockedGetThrowing ( () -> {
      if (m_aPartners.removePartner (sPartnerName).isUnchanged ())
        return EChange.UNCHANGED;
      markAsChanged ();
      return EChange.CHANGED;
    });
  }

  @Nullable
  public Partner getPartnerOfName (@Nullable final String sPartnerName)
  {
    return m_aRWLock.readLockedGet ( () -> m_aPartners.getPartnerOfName (sPartnerName));
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsSet <String> getAllPartnerNames ()
  {
    return m_aRWLock.readLockedGet (m_aPartners::getAllPartnerNames);
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Partner> getAllPartners ()
  {
    return m_aRWLock.readLockedGet (m_aPartners::getAllPartners);
  }

  @NonNull
  public IPartnerMap getPartnerMap ()
  {
    return m_aRWLock.readLockedGet ( () -> m_aPartners);
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
}
