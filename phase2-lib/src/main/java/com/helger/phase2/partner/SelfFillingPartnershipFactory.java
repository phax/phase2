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

import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.annotation.style.OverrideOnDemand;
import com.helger.phase2.crypto.ECryptoAlgorithmSign;
import com.helger.phase2.exception.AS2Exception;

/**
 * A special in-memory partnership factory that adds a partnership if it is not existing yet.
 *
 * @author Philip Helger
 */
public class SelfFillingPartnershipFactory extends AbstractPartnershipFactory
{
  /**
   * Ensure that a new partnership is usable. It ensure the following properties present:
   * <ul>
   * <li>Sender X509 alias</li>
   * <li>Receiver X509 alias</li>
   * <li>Unique name</li>
   * <li>Fallback signing algorithm SHA_1</li>
   * </ul>
   *
   * @param aPartnership
   *        The partnership to be used.
   */
  public static void ensureUsablePartnership (@NonNull final Partnership aPartnership)
  {
    // Ensure the X509 key is contained for certificate store alias retrieval
    if (!aPartnership.containsSenderX509Alias ())
      aPartnership.setSenderX509Alias (aPartnership.getSenderAS2ID ());

    if (!aPartnership.containsReceiverX509Alias ())
      aPartnership.setReceiverX509Alias (aPartnership.getReceiverAS2ID ());

    // Ensure a unique name
    if (Partnership.DEFAULT_NAME.equals (aPartnership.getName ()))
      aPartnership.setName (aPartnership.getSenderAS2ID () + "-" + aPartnership.getReceiverAS2ID ());

    // Ensure a signing algorithm is present in the partnership. This is
    // relevant for MIC calculation, so that the headers are included
    // The algorithm itself does not really matter as for sending the algorithm
    // is specified anyway and for the MIC it is specified explicitly
    if (aPartnership.getSigningAlgorithm () == null)
      aPartnership.setSigningAlgorithm (ECryptoAlgorithmSign.DIGEST_SHA_1);
  }

  /**
   * Callback method that is invoked every time a new partnership is automatically added. This
   * method is called BEFORE the main add-process is started.
   *
   * @param aPartnership
   *        The partnership that will be added. Never <code>null</code>.
   * @throws AS2Exception
   *         In case of an error.
   */
  @OverrideOnDemand
  @OverridingMethodsMustInvokeSuper
  protected void onBeforeAddPartnership (@NonNull final Partnership aPartnership) throws AS2Exception
  {
    ensureUsablePartnership (aPartnership);
  }

  @Override
  @NonNull
  public final Partnership getPartnership (@NonNull final Partnership aPartnership) throws AS2Exception
  {
    try
    {
      return super.getPartnership (aPartnership);
    }
    catch (final AS2PartnershipNotFoundException ex)
    {
      onBeforeAddPartnership (aPartnership);

      // Create a new one
      addPartnership (aPartnership);
      return aPartnership;
    }
  }
}
