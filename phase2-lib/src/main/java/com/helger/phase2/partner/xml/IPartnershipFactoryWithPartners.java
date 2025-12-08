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

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.state.EChange;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsSet;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.partner.IPartnershipFactory;

/**
 * This partnership factory extends {@link IPartnershipFactory} by adding "partners". This can be
 * used for providing certain fixed value on a per-partner basis (e.g. email address or X509
 * certificate alias to the key store) without having redundancy data in all partnerships.
 *
 * @author Philip Helger
 */
public interface IPartnershipFactoryWithPartners extends IPartnershipFactory
{
  /**
   * Add a partner.
   *
   * @param aNewPartner
   *        The partner data to be used. May not be <code>null</code>.
   * @throws AS2Exception
   *         Generic error
   */
  void addPartner (@NonNull Partner aNewPartner) throws AS2Exception;

  /**
   * Remove a partner.
   *
   * @param sPartnerName
   *        The name of the partner to be removed.
   * @return {@link EChange#CHANGED} if the partner was successfully removed,
   *         {@link EChange#UNCHANGED} if no such partner exists.
   * @throws AS2Exception
   *         Generic error
   */
  @NonNull
  EChange removePartner (@Nullable String sPartnerName) throws AS2Exception;

  /**
   * Get all the partner data of the partner with the given name.
   *
   * @param sPartnerName
   *        Partner name to search. May be <code>null</code>.
   * @return <code>null</code> if no such partner exists.
   */
  @Nullable
  IPartner getPartnerOfName (@Nullable String sPartnerName);

  /**
   * @return A set with all contained partner names. Never <code>null</code> but maybe empty.
   */
  @NonNull
  @ReturnsMutableCopy
  ICommonsSet <String> getAllPartnerNames ();

  /**
   * @return An (unordered) list of all contained partner data.
   */
  @NonNull
  @ReturnsMutableCopy
  ICommonsList <? extends IPartner> getAllPartners ();
}
