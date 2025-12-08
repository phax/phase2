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

import java.security.cert.X509Certificate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.partner.Partnership;

public class AS2CertificateNotFoundException extends AS2Exception
{
  private final ECertificatePartnershipType m_ePartnershipType;
  private final String m_sAlias;

  public AS2CertificateNotFoundException (@Nullable final ECertificatePartnershipType ePartnershipType,
                                          @NonNull final Partnership aPartnership)
  {
    super ("Type " + ePartnershipType + ": no alias found for partnership " + aPartnership);
    m_ePartnershipType = ePartnershipType;
    m_sAlias = null;
  }

  public AS2CertificateNotFoundException (@Nullable final ECertificatePartnershipType ePartnershipType,
                                          @Nullable final String sAlias)
  {
    super ("Type " + ePartnershipType + ": no such alias '" + sAlias + "'");
    m_ePartnershipType = ePartnershipType;
    m_sAlias = sAlias;
  }

  public AS2CertificateNotFoundException (@Nullable final X509Certificate aCert)
  {
    super ("Certificate not in store '" + aCert + "'");
    m_ePartnershipType = null;
    m_sAlias = null;
  }

  @Nullable
  public ECertificatePartnershipType getPartnershipType ()
  {
    return m_ePartnershipType;
  }

  @Nullable
  public String getAlias ()
  {
    return m_sAlias;
  }
}
