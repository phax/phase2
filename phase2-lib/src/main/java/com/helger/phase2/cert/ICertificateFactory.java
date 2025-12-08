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
package com.helger.phase2.cert;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.phase2.IDynamicComponent;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IBaseMessage;

/**
 * Base interface for a certificate factory.
 *
 * @author unknown
 */
public interface ICertificateFactory extends IDynamicComponent
{
  /**
   * Get the certificate of the specified type for the partnership defined in the provided message
   *
   * @param aMsg
   *        Message to get the partnership from. May not be <code>null</code>.
   * @param ePartnershipType
   *        Sender or receiver?
   * @return Never <code>null</code>-
   * @throws AS2Exception
   *         In case of error
   * @throws AS2CertificateNotFoundException
   *         If no certificate is present
   */
  @NonNull
  X509Certificate getCertificate (@NonNull IBaseMessage aMsg, @NonNull ECertificatePartnershipType ePartnershipType)
                                                                                                                     throws AS2Exception;

  /**
   * Get the certificate of the specified type for the partnership defined in the provided message
   *
   * @param aMsg
   *        Message to get the partnership from. May not be <code>null</code>.
   * @param ePartnershipType
   *        Sender or receiver?
   * @return <code>null</code> if no such alias or certificate exists.
   * @throws AS2Exception
   *         In case of error
   */
  @Nullable
  default X509Certificate getCertificateOrNull (@NonNull final IBaseMessage aMsg,
                                                @NonNull final ECertificatePartnershipType ePartnershipType) throws AS2Exception
  {
    try
    {
      return getCertificate (aMsg, ePartnershipType);
    }
    catch (final AS2CertificateNotFoundException ex)
    {
      // No such certificate
      return null;
    }
  }

  @NonNull
  PrivateKey getPrivateKey (@Nullable X509Certificate aCert) throws AS2Exception;
}
