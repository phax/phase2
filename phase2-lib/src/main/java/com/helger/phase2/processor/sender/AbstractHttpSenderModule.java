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
package com.helger.phase2.processor.sender;

import java.io.File;
import java.net.Proxy;
import java.security.GeneralSecurityException;
import java.util.Locale;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.hc.core5.util.Timeout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonempty;
import com.helger.annotation.style.OverrideOnDemand;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.string.StringHelper;
import com.helger.base.system.SystemProperties;
import com.helger.http.EHttpMethod;
import com.helger.http.security.HostnameVerifierVerifyAll;
import com.helger.http.security.TrustManagerTrustAll;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IBaseMessage;
import com.helger.phase2.util.AS2IOHelper;
import com.helger.phase2.util.dump.DefaultHTTPOutgoingDumperFactory;
import com.helger.phase2.util.dump.IHTTPIncomingDumper;
import com.helger.phase2.util.dump.IHTTPOutgoingDumper;
import com.helger.phase2.util.dump.IHTTPOutgoingDumperFactory;
import com.helger.phase2.util.http.AS2HttpClient;
import com.helger.phase2.util.http.HTTPHelper;
import com.helger.phase2.util.http.IAS2OutgoingHttpCallback;
import com.helger.url.protocol.EURLProtocol;

/**
 * Abstract HTTP based sender module
 *
 * @author Philip Helger
 */
public abstract class AbstractHttpSenderModule extends AbstractSenderModule
{
  /** Attribute name for connection timeout in milliseconds */
  public static final String ATTR_CONNECT_TIMEOUT = "connecttimeout";
  /** Attribute name for read timeout in milliseconds */
  public static final String ATTR_RESPONSE_TIMEOUT = "responsetimeout";
  /** Attribute name for quoting header values (boolean) */
  public static final String ATTR_QUOTE_HEADER_VALUES = "quoteheadervalues";

  /** Default connection timeout: 60 seconds */
  public static final Timeout DEFAULT_CONNECT_TIMEOUT = Timeout.ofSeconds (60);
  /** Default read timeout: 60 seconds */
  public static final Timeout DEFAULT_RESPONSE_TIMEOUT = Timeout.ofSeconds (60);
  /** Default quote header values: false */
  public static final boolean DEFAULT_QUOTE_HEADER_VALUES = false;

  private static final Logger LOGGER = LoggerFactory.getLogger (AbstractHttpSenderModule.class);
  private static final IHTTPOutgoingDumperFactory DEFAULT_HTTP_OUTGOING_DUMPER_FACTORY;

  static
  {
    // Set global outgoing dump directory (since v4.0.3)
    // This is contained for backwards compatibility only
    final String sHttpDumpOutgoingDirectory = SystemProperties.getPropertyValueOrNull ("AS2.httpDumpDirectoryOutgoing");
    if (StringHelper.isNotEmpty (sHttpDumpOutgoingDirectory))
    {
      LOGGER.info ("Using '" +
                   sHttpDumpOutgoingDirectory +
                   "' as the global directory to dump outgoing messages (source: system property)");
      final File aDumpDirectory = new File (sHttpDumpOutgoingDirectory);
      AS2IOHelper.getFileOperationManager ().createDirIfNotExisting (aDumpDirectory);
      DEFAULT_HTTP_OUTGOING_DUMPER_FACTORY = new DefaultHTTPOutgoingDumperFactory (aDumpDirectory);
    }
    else
      DEFAULT_HTTP_OUTGOING_DUMPER_FACTORY = null;
  }

  private IHTTPOutgoingDumperFactory m_aHttpOutgoingDumperFactory = DEFAULT_HTTP_OUTGOING_DUMPER_FACTORY;
  private IHTTPIncomingDumper m_aHttpIncomingDumper;
  private IAS2OutgoingHttpCallback m_aOugoingHttpCallback;

  protected AbstractHttpSenderModule ()
  {}

  @Nullable
  public final IHTTPOutgoingDumperFactory getHttpOutgoingDumperFactory ()
  {
    return m_aHttpOutgoingDumperFactory;
  }

  public final void setHttpOutgoingDumperFactory (@Nullable final IHTTPOutgoingDumperFactory aHttpOutgoingDumperFactory)
  {
    m_aHttpOutgoingDumperFactory = aHttpOutgoingDumperFactory;
  }

  @Nullable
  public final IHTTPOutgoingDumper getHttpOutgoingDumper (@NonNull final IBaseMessage aMsg)
  {
    return m_aHttpOutgoingDumperFactory == null ? null : m_aHttpOutgoingDumperFactory.apply (aMsg);
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

  /**
   * @return The outgoing HTTP callback object. May be <code>null</code>.
   * @since 4.7.1
   */
  @Nullable
  public final IAS2OutgoingHttpCallback getOutgoingHttpCallback ()
  {
    return m_aOugoingHttpCallback;
  }

  /**
   * Set the http communication callback that is invoked with the most crucial data elements for
   * easy logging.
   *
   * @param aRCC
   *        The callback object. May be <code>null</code>.
   * @since 4.7.1
   */
  public final void setOutgoingHttpCallback (@Nullable final IAS2OutgoingHttpCallback aRCC)
  {
    m_aOugoingHttpCallback = aRCC;
  }

  @NonNull
  public final Timeout getConnectTimeout ()
  {
    final long nMS = attrs ().getAsLong (ATTR_CONNECT_TIMEOUT, -1);
    if (nMS >= 0)
      return Timeout.ofMilliseconds (nMS);
    return DEFAULT_CONNECT_TIMEOUT;
  }

  public final void setConnectTimeoutMilliseconds (final long nMS)
  {
    if (nMS < 0)
      attrs ().remove (ATTR_CONNECT_TIMEOUT);
    else
      attrs ().putIn (ATTR_CONNECT_TIMEOUT, nMS);
  }

  @NonNull
  public final Timeout getResponseTimeout ()
  {
    final long nMS = attrs ().getAsLong (ATTR_RESPONSE_TIMEOUT, -1);
    if (nMS >= 0)
      return Timeout.ofMilliseconds (nMS);
    return DEFAULT_RESPONSE_TIMEOUT;
  }

  public final void setResponseTimeoutMilliseconds (final long nMS)
  {
    if (nMS < 0)
      attrs ().remove (ATTR_RESPONSE_TIMEOUT);
    else
      attrs ().putIn (ATTR_RESPONSE_TIMEOUT, nMS);
  }

  public final boolean isQuoteHeaderValues ()
  {
    return attrs ().getAsBoolean (ATTR_QUOTE_HEADER_VALUES, DEFAULT_QUOTE_HEADER_VALUES);
  }

  public final void setQuoteHeaderValues (final boolean bQuoteHeaderValues)
  {
    attrs ().putIn (ATTR_QUOTE_HEADER_VALUES, bQuoteHeaderValues);
  }

  /**
   * Create the {@link SSLContext} to be used for https connections. By default the SSL context will
   * trust all hosts and present no keys. Override this method in a subclass to customize this
   * handling.
   *
   * @return The created {@link SSLContext}. May not be <code>null</code>.
   * @throws GeneralSecurityException
   *         If something internally goes wrong.
   */
  @NonNull
  @OverrideOnDemand
  public SSLContext createSSLContext () throws GeneralSecurityException
  {
    // Trust all server certificates
    final SSLContext aSSLCtx = SSLContext.getInstance ("TLS");
    aSSLCtx.init (null, new TrustManager [] { new TrustManagerTrustAll (false) }, null);
    return aSSLCtx;
  }

  /**
   * Get the hostname verifier to be used. By default an instance of
   * {@link HostnameVerifierVerifyAll} is returned. Override this method to change this default
   * behavior.
   *
   * @return The hostname verifier to be used. If the returned value is <code>null</code> it will
   *         not be applied to the https connection.
   */
  @Nullable
  @OverrideOnDemand
  public HostnameVerifier createHostnameVerifier ()
  {
    return new HostnameVerifierVerifyAll ();
  }

  /**
   * Determine, if the SSL/TLS context should be used or not. By default this returns
   * <code>true</code> if the URL starts with "https".
   *
   * @param sUrl
   *        The URL to which the request is made.
   * @return <code>true</code> to use SSL/TLS, <code>false</code> if not needed.
   */
  @OverrideOnDemand
  public boolean isUseSSL (@NonNull @Nonempty final String sUrl)
  {
    return EURLProtocol.HTTPS.isUsedInURL (sUrl.toLowerCase (Locale.ROOT));
  }

  /**
   * Generate a HttpClient connection. It works with streams and avoids holding whole message in
   * memory. note that bOutput, bInput, and bUseCaches are not supported
   *
   * @param sUrl
   *        URL to connect to
   * @param eRequestMethod
   *        HTTP Request method to use. May not be <code>null</code>.
   * @param aProxy
   *        Optional proxy to use. May be <code>null</code>.
   * @return a {@link AS2HttpClient} object to work with
   * @throws AS2Exception
   *         If something goes wrong
   */
  @NonNull
  public AS2HttpClient getHttpClient (@NonNull @Nonempty final String sUrl,
                                      @NonNull final EHttpMethod eRequestMethod,
                                      @Nullable final Proxy aProxy) throws AS2Exception
  {
    ValueEnforcer.notEmpty (sUrl, "URL");
    final SSLContext aSSLCtx;
    final HostnameVerifier aHV;
    if (isUseSSL (sUrl))
    {
      // Create SSL context and HostnameVerifier
      try
      {
        aSSLCtx = createSSLContext ();
      }
      catch (final GeneralSecurityException ex)
      {
        throw new AS2Exception ("Error creating SSL Context", ex);
      }
      aHV = createHostnameVerifier ();
    }
    else
    {
      aSSLCtx = null;
      aHV = null;
    }
    final Timeout aConnectTimeout = getConnectTimeout ();
    final Timeout aResponseTimeout = getResponseTimeout ();
    return new AS2HttpClient (sUrl, aConnectTimeout, aResponseTimeout, eRequestMethod, aProxy, aSSLCtx, aHV);
  }
}
