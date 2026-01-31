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
package com.helger.phase2.partner;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.state.EChange;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phase2.IDynamicComponent;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.message.IMessageMDN;

/**
 * This is the base interface for a partnership factory (it is more manager but who cares). It
 * consists of partnerships represented by {@link Partnership} objects.
 *
 * @author original author unknown
 * @author joseph mcverry
 * @author Philip Helger
 */
public interface IPartnershipFactory extends IDynamicComponent
{
  /**
   * Add a partnership.
   *
   * @param aPartnership
   *        The partnership to be added. May not be <code>null</code>. The name of the partnership
   *        must be unique so that it gets added.
   * @return {@link EChange#CHANGED} if adding was successfully, {@link EChange#UNCHANGED} if the
   *         name is already contained.
   * @throws AS2Exception
   *         Generic error
   */
  @NonNull
  EChange addPartnership (@NonNull Partnership aPartnership) throws AS2Exception;

  /**
   * Remove the specified partnership.
   *
   * @param aPartnership
   *        The partnership to be removed.
   * @return {@link EChange#CHANGED} if removal was successful, {@link EChange#UNCHANGED} if no such
   *         partnership to be removed is present.
   * @throws AS2Exception
   *         Generic error
   */
  @NonNull
  EChange removePartnership (@NonNull Partnership aPartnership) throws AS2Exception;

  /**
   * Get the partnership identified by the provided stub partnership.
   *
   * @param aPartnership
   *        Stub partnership which must contain either a name or a set of sender and receiver IDs.
   * @return The Partnership as stored in this factory. Never <code>null</code>.
   * @throws AS2Exception
   *         If no partnership matching the provided stub partnership can be found.
   */
  @NonNull
  Partnership getPartnership (@NonNull Partnership aPartnership) throws AS2Exception;

  /**
   * Find an existing partnership by its name.
   *
   * @param sName
   *        The partnership name to be looked up. May be <code>null</code>.
   * @return <code>null</code> if no such partnership exists.
   */
  @Nullable
  Partnership getPartnershipByName (@Nullable String sName);

  /**
   * @return A set with all contained partnership names. Never <code>null</code> but maybe empty.
   */
  @NonNull
  @ReturnsMutableCopy
  ICommonsSet <String> getAllPartnershipNames ();

  /**
   * @return A list of all contained partnerships. Never <code>null</code> but maybe empty.
   */
  @NonNull
  @ReturnsMutableCopy
  ICommonsList <Partnership> getAllPartnerships ();

  /**
   * Looks up and fills in any header info for a specific msg's partnership.
   *
   * @param aMsg
   *        The message in which the partnership should be updated. May not be <code>null</code> and
   *        must already contain a partnership with at least name or sender and receiver IDs.
   * @param bOverwrite
   *        <code>true</code> to also set the subject of the message with the subject stored in the
   *        partnership.
   * @throws AS2Exception
   *         In case of an error
   * @see #getPartnership(Partnership)
   */
  void updatePartnership (@NonNull IMessage aMsg, boolean bOverwrite) throws AS2Exception;

  /**
   * Looks up and fills in any header info for a specific MDN's partnership
   *
   * @param aMdn
   *        The MDN of which the partnership information should be updated. May not be
   *        <code>null</code> and must already contain a partnership with at least name or sender
   *        and receiver IDs.
   * @param bOverwrite
   *        has no effect currently
   * @throws AS2Exception
   *         In case of an error
   * @see #getPartnership(Partnership)
   */
  void updatePartnership (@NonNull IMessageMDN aMdn, boolean bOverwrite) throws AS2Exception;
}
