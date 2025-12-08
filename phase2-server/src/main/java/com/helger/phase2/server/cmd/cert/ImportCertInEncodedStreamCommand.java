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
package com.helger.phase2.server.cmd.cert;

import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.jspecify.annotations.NonNull;

import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.phase2.cert.IAliasedCertificateFactory;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.WrappedAS2Exception;
import com.helger.phase2.server.cmd.CommandResult;
import com.helger.phase2.server.cmd.ECommandResultType;
import com.helger.phase2.server.util.ByteCoder;

public class ImportCertInEncodedStreamCommand extends AbstractAliasedCertCommand
{
  @Override
  public String getDefaultDescription ()
  {
    return "Import a certificate into the current certificate store using an encoded byte stream";
  }

  @Override
  public String getDefaultName ()
  {
    return "importbystream";
  }

  @Override
  public String getDefaultUsage ()
  {
    return "importbybstream <alias> <encodedCertificateStream>";
  }

  @Override
  public CommandResult execute (final IAliasedCertificateFactory certFx, final Object [] params) throws AS2Exception
  {
    if (params.length != 2)
    {
      return new CommandResult (ECommandResultType.TYPE_INVALID_PARAM_COUNT, getUsage ());
    }

    try
    {
      return _importCert (certFx, params[0].toString (), params[1].toString ());
    }
    catch (final Exception ex)
    {
      throw WrappedAS2Exception.wrap (ex);
    }
  }

  @NonNull
  private static CommandResult _importCert (final IAliasedCertificateFactory certFx,
                                            final String alias,
                                            final String encodedCert) throws CertificateException, AS2Exception
  {
    final byte [] aBytes = ByteCoder.decode (encodedCert).getBytes ();
    try (final NonBlockingByteArrayInputStream bais = new NonBlockingByteArrayInputStream (aBytes))
    {
      final CertificateFactory cf = CertificateFactory.getInstance ("X.509");
      while (bais.available () > 0)
      {
        final Certificate cert = cf.generateCertificate (bais);
        if (cert instanceof X509Certificate)
        {
          certFx.addCertificate (alias, (X509Certificate) cert, true);

          final CommandResult cmdRes = new CommandResult (ECommandResultType.TYPE_OK, "Certificate(s) imported successfully");
          cmdRes.addResult ("Imported certificate: " + cert.toString ());
          return cmdRes;
        }
      }
    }

    return new CommandResult (ECommandResultType.TYPE_ERROR, "No valid X509 certificates found");
  }
}
