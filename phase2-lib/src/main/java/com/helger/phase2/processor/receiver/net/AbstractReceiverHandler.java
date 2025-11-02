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
package com.helger.phase2.processor.receiver.net;

import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.phase2.util.dump.IHTTPIncomingDumper;
import com.helger.phase2.util.http.HTTPHelper;

/**
 * Abstract base class for Message and MDN receive handlers.
 *
 * @author Philip Helger
 */
public abstract class AbstractReceiverHandler implements INetModuleHandler
{
  private Consumer <X509Certificate> m_aVerificationCertificateConsumer;
  private IHTTPIncomingDumper m_aHttpIncomingDumper;

  /**
   * @return The consumer for the effective certificate upon signature verification. May be
   *         <code>null</code>. The default is <code>null</code>.
   * @since 4.4.1
   */
  @Nullable
  public final Consumer <X509Certificate> getVerificationCertificateConsumer ()
  {
    return m_aVerificationCertificateConsumer;
  }

  /**
   * Set the consumer for the effective certificate upon signature verification.
   *
   * @param aVerificationCertificateConsumer
   *        The consumer to be used. May be <code>null</code>.
   * @return this for chaining
   * @since 4.4.1
   */
  @NonNull
  public final AbstractReceiverHandler setVerificationCertificateConsumer (@Nullable final Consumer <X509Certificate> aVerificationCertificateConsumer)
  {
    m_aVerificationCertificateConsumer = aVerificationCertificateConsumer;
    return this;
  }

  /**
   * @return The specific incoming dumper of this receiver. May be <code>null</code>.
   * @since v4.4.5
   */
  @Nullable
  public final IHTTPIncomingDumper getHttpIncomingDumper ()
  {
    return m_aHttpIncomingDumper;
  }

  /**
   * Get the customized incoming dumper, falling back to the global incoming dumper if no specific
   * dumper is set.
   *
   * @return The effective incoming dumper. May be <code>null</code>.
   * @since v4.4.5
   */
  @Nullable
  public final IHTTPIncomingDumper getEffectiveHttpIncomingDumper ()
  {
    // Dump on demand
    IHTTPIncomingDumper ret = m_aHttpIncomingDumper;
    if (ret == null)
    {
      // Fallback to global dumper
      ret = HTTPHelper.getHTTPIncomingDumper ();
    }
    return ret;
  }

  /**
   * Set the specific incoming dumper of this receiver. If this is set, it overrides the global
   * dumper.
   *
   * @param aHttpIncomingDumper
   *        The specific incoming dumper to be used. May be <code>null</code>.
   * @since v4.4.5
   */
  public final void setHttpIncomingDumper (@Nullable final IHTTPIncomingDumper aHttpIncomingDumper)
  {
    m_aHttpIncomingDumper = aHttpIncomingDumper;
  }

  @NonNull
  @Nonempty
  public String getClientInfo (@NonNull final Socket aSocket)
  {
    return aSocket.getInetAddress ().getHostAddress () + ":" + aSocket.getPort ();
  }

}
