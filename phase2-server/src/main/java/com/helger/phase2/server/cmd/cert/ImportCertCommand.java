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
package com.helger.phase2.server.cmd.cert;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import org.jspecify.annotations.NonNull;

import com.helger.base.io.nonblocking.NonBlockingBufferedInputStream;
import com.helger.phase2.cert.IAliasedCertificateFactory;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.WrappedAS2Exception;
import com.helger.phase2.server.cmd.CommandResult;
import com.helger.phase2.server.cmd.ECommandResultType;
import com.helger.phase2.util.AS2Helper;
import com.helger.security.keystore.EKeyStoreType;
import com.helger.security.keystore.IKeyStoreType;

public class ImportCertCommand extends AbstractAliasedCertCommand
{
  @Override
  public String getDefaultDescription ()
  {
    return "Import a certificate into the current certificate store";
  }

  @Override
  public String getDefaultName ()
  {
    return "import";
  }

  @Override
  public String getDefaultUsage ()
  {
    return "import <alias> <filename> [<password>]";
  }

  @Override
  public CommandResult execute (final IAliasedCertificateFactory certFx, final Object [] params) throws AS2Exception
  {
    if (params.length < 2)
    {
      return new CommandResult (ECommandResultType.TYPE_INVALID_PARAM_COUNT, getUsage ());
    }

    final String alias = params[0].toString ();
    final String filename = params[1].toString ();
    String password = null;

    if (params.length > 2)
    {
      password = params[2].toString ();
    }

    try
    {
      if (filename.endsWith (".p12"))
      {
        if (password == null)
        {
          return new CommandResult (ECommandResultType.TYPE_INVALID_PARAM_COUNT, getUsage () + " (Password is required for p12 files)");
        }

        return importPrivateKey (EKeyStoreType.PKCS12, certFx, alias, filename, password);
      }
      return importCert (certFx, alias, filename);
    }
    catch (final Exception ex)
    {
      throw WrappedAS2Exception.wrap (ex);
    }
  }

  @NonNull
  protected CommandResult importCert (final IAliasedCertificateFactory certFx,
                                      final String sAlias,
                                      final String sFilename) throws IOException, CertificateException, AS2Exception
  {
    try (final FileInputStream fis = new FileInputStream (sFilename);
         final NonBlockingBufferedInputStream bis = new NonBlockingBufferedInputStream (fis))
    {
      final CertificateFactory cf = CertificateFactory.getInstance ("X.509");
      while (bis.available () > 0)
      {
        final Certificate aCert = cf.generateCertificate (bis);

        if (aCert instanceof X509Certificate)
        {
          certFx.addCertificate (sAlias, (X509Certificate) aCert, true);

          final CommandResult cmdRes = new CommandResult (ECommandResultType.TYPE_OK, "Certificate(s) imported successfully");
          cmdRes.addResult ("Imported certificate: " + aCert.toString ());
          return cmdRes;
        }
      }

      return new CommandResult (ECommandResultType.TYPE_ERROR, "No valid X509 certificates found");
    }
  }

  protected CommandResult importPrivateKey (@NonNull final IKeyStoreType aKeyStoreType,
                                            final IAliasedCertificateFactory aFactory,
                                            final String sAlias,
                                            final String sFilename,
                                            final String sPassword) throws Exception
  {
    final KeyStore aKeyStore = AS2Helper.getCryptoHelper ().createNewKeyStore (aKeyStoreType);
    try (final InputStream aIS = new FileInputStream (sFilename))
    {
      aKeyStore.load (aIS, sPassword.toCharArray ());
    }

    final Enumeration <String> aliases = aKeyStore.aliases ();
    while (aliases.hasMoreElements ())
    {
      final String sCertAlias = aliases.nextElement ();
      final Certificate aCert = aKeyStore.getCertificate (sCertAlias);
      if (aCert instanceof X509Certificate)
      {
        aFactory.addCertificate (sAlias, (X509Certificate) aCert, true);

        final Key certKey = aKeyStore.getKey (sCertAlias, sPassword.toCharArray ());
        aFactory.addPrivateKey (sAlias, certKey, sPassword);

        return new CommandResult (ECommandResultType.TYPE_OK, "Imported certificate and key: " + aCert.toString ());
      }
    }

    return new CommandResult (ECommandResultType.TYPE_ERROR, "No valid X509 certificates found");
  }
}
