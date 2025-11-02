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
package com.helger.phase2.partner;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.annotation.concurrent.ELockType;
import com.helger.annotation.concurrent.IsLocked;
import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.OverrideOnDemand;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.state.EChange;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phase2.AbstractDynamicComponent;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.message.IMessageMDN;
import com.helger.phase2.params.MessageParameters;

/**
 * Abstract {@link IPartnershipFactory} implementation using {@link PartnershipMap} as the
 * underlying data storage object.
 *
 * @author Philip Helger
 */
@ThreadSafe
public abstract class AbstractPartnershipFactory extends AbstractDynamicComponent implements IPartnershipFactory
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AbstractPartnershipFactory.class);

  private final PartnershipMap m_aPartnerships = new PartnershipMap ();

  /**
   * Callback method that is invoked, when this object is modified. This method must be overridden
   * to do something useful. A use case scenario could e.g. be automatic storage of changes.
   *
   * @throws AS2Exception
   *         In case anything goes wrong
   */
  @OverrideOnDemand
  @IsLocked (ELockType.WRITE)
  protected void markAsChanged () throws AS2Exception
  {}

  @NonNull
  @OverridingMethodsMustInvokeSuper
  public Partnership getPartnership (@NonNull final Partnership aPartnership) throws AS2Exception
  {
    ValueEnforcer.notNull (aPartnership, "Partnership");

    return m_aRWLock.readLockedGetThrowing ( () -> {
      Partnership aRealPartnership = m_aPartnerships.getPartnershipByName (aPartnership.getName ());
      if (aRealPartnership == null)
      {
        // Found no partnership by name
        aRealPartnership = m_aPartnerships.getPartnershipByID (aPartnership.getAllSenderIDs (),
                                                               aPartnership.getAllReceiverIDs ());
      }

      if (aRealPartnership == null)
        throw new AS2PartnershipNotFoundException (aPartnership);
      return aRealPartnership;
    });
  }

  @Nullable
  public Partnership getPartnershipByName (@Nullable final String sName)
  {
    return m_aRWLock.readLockedGet ( () -> m_aPartnerships.getPartnershipByName (sName));
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsSet <String> getAllPartnershipNames ()
  {
    return m_aRWLock.readLockedGet (m_aPartnerships::getAllPartnershipNames);
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Partnership> getAllPartnerships ()
  {
    return m_aRWLock.readLockedGet (m_aPartnerships::getAllPartnerships);
  }

  @NonNull
  public IPartnershipMap getPartnershipMap ()
  {
    return m_aRWLock.readLockedGet ( () -> m_aPartnerships);
  }

  protected final void setPartnerships (@NonNull final PartnershipMap aPartnerships) throws AS2Exception
  {
    m_aRWLock.writeLockedThrowing ( () -> {
      m_aPartnerships.setPartnerships (aPartnerships);
      markAsChanged ();
    });
  }

  @NonNull
  public final EChange addPartnership (@NonNull final Partnership aPartnership) throws AS2Exception
  {
    return m_aRWLock.writeLockedGetThrowing ( () -> {
      if (m_aPartnerships.addPartnership (aPartnership).isUnchanged ())
        return EChange.UNCHANGED;
      markAsChanged ();
      return EChange.CHANGED;
    });
  }

  @NonNull
  public final EChange removePartnership (@NonNull final Partnership aPartnership) throws AS2Exception
  {
    return m_aRWLock.writeLockedGetThrowing ( () -> {
      if (m_aPartnerships.removePartnership (aPartnership).isUnchanged ())
        return EChange.UNCHANGED;
      markAsChanged ();
      return EChange.CHANGED;
    });
  }

  public void updatePartnership (@NonNull final IMessage aMsg, final boolean bOverwrite) throws AS2Exception
  {
    ValueEnforcer.notNull (aMsg, "Message");

    // Fill in any available partnership information
    final Partnership aPartnership = getPartnership (aMsg.partnership ());

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Updating partnership " + aPartnership);

    // Update partnership data of message with the stored ones
    aMsg.partnership ().copyFrom (aPartnership);

    // Set attributes
    if (bOverwrite)
    {
      final String sSubject = aPartnership.getSubject ();
      if (sSubject != null)
      {
        aMsg.setSubject (new MessageParameters (aMsg).format (sSubject));
      }
    }
  }

  public final void updatePartnership (@NonNull final IMessageMDN aMdn, final boolean bOverwrite) throws AS2Exception
  {
    ValueEnforcer.notNull (aMdn, "MessageMDN");

    // Fill in any available partnership information
    final Partnership aPartnership = getPartnership (aMdn.partnership ());
    aMdn.partnership ().copyFrom (aPartnership);
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
    return ToStringGenerator.getDerived (super.toString ()).append ("Partnerships", m_aPartnerships).getToString ();
  }
}
