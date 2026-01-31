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
package com.helger.phase2.cert;

import java.security.KeyStore;

import org.jspecify.annotations.NonNull;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.phase2.exception.AS2Exception;

/**
 * An example implementation of a Certificate factory that uses an external {@link KeyStore}.
 * Nevertheless the password to load the key store must be set via {@link #setPassword(String)}.
 *
 * @author Philip Helger
 * @since 4.6.4
 */
public class PredefinedCertificateFactory extends AbstractCertificateFactory
{
  // Separate member to avoid that it gets overridden
  private final KeyStore m_aCtorKeyStore;

  public PredefinedCertificateFactory (@NonNull final KeyStore aKeyStore)
  {
    ValueEnforcer.notNull (aKeyStore, "KeyStore");
    m_aCtorKeyStore = aKeyStore;
  }

  @NonNull
  public final KeyStore getKeyStoreFromCtor ()
  {
    return m_aCtorKeyStore;
  }

  public void reinitKeyStore () throws AS2Exception
  {
    debugLog ( () -> "reinitKeyStore ()");

    setKeyStore (m_aCtorKeyStore);

    debugLog ( () -> "reinitKeyStore -> done");
  }
}
