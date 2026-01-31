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
package com.helger.phase2.util.cert;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import org.jspecify.annotations.NonNull;

import com.helger.base.io.EAppend;
import com.helger.base.io.stream.StreamHelper;
import com.helger.collection.CollectionFind;
import com.helger.io.file.FileHelper;
import com.helger.phase2.crypto.ICryptoHelper;
import com.helger.security.keystore.IKeyStoreType;

/**
 * KeyStore reader and write class
 *
 * @author Philip Helger
 */
public final class AS2KeyStoreHelper
{
  private AS2KeyStoreHelper ()
  {}

  @NonNull
  public static MockAS2KeyStore readKeyStore (@NonNull final IKeyStoreType aKeyStoreType,
                                          @NonNull final String sFilename,
                                          @NonNull final char [] aPassword,
                                          @NonNull final ICryptoHelper aCryptoHelper) throws Exception
  {
    final InputStream aIS = FileHelper.getInputStream (new File (sFilename));
    if (aIS == null)
      throw new IllegalArgumentException ("Failed to open KeyStore '" + sFilename + "' for reading");

    try
    {
      final KeyStore aKeyStore = aCryptoHelper.loadKeyStore (aKeyStoreType, aIS, aPassword);
      return new MockAS2KeyStore (aKeyStore);
    }
    finally
    {
      StreamHelper.close (aIS);
    }
  }

  public static void writeKeyStore (@NonNull final MockAS2KeyStore aKeyStore,
                                    @NonNull final String sFilename,
                                    @NonNull final char [] aPassword) throws GeneralSecurityException, IOException
  {
    final OutputStream aOS = FileHelper.getOutputStream (new File (sFilename), EAppend.TRUNCATE);
    if (aOS == null)
      throw new IllegalArgumentException ("Failed to open KeyStore '" + sFilename + "' for writing");

    try
    {
      aKeyStore.getKeyStore ().store (aOS, aPassword);
    }
    finally
    {
      StreamHelper.close (aOS);
    }
  }

  @NonNull
  public static X509Certificate readX509Certificate (@NonNull final String sFilename) throws CertificateException
  {
    final InputStream aIS = FileHelper.getInputStream (new File (sFilename));
    if (aIS == null)
      throw new IllegalArgumentException ("Failed to open Certificate file '" + sFilename + "' for reading");

    try
    {
      final CertificateFactory cf = CertificateFactory.getInstance ("X.509");
      final Collection <? extends Certificate> c = cf.generateCertificates (aIS);
      return (X509Certificate) CollectionFind.getFirstElement (c);
    }
    finally
    {
      StreamHelper.close (aIS);
    }
  }
}
