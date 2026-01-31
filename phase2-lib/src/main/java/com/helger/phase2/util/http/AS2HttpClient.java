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
package com.helger.phase2.util.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.Timeout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonempty;
import com.helger.annotation.Nonnegative;
import com.helger.annotation.WillClose;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.stream.CountingInputStream;
import com.helger.base.io.stream.StreamHelper;
import com.helger.http.CHttp;
import com.helger.http.EHttpMethod;
import com.helger.http.header.HttpHeaderMap;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.util.AS2IOHelper;
import com.helger.phase2.util.AS2ResourceHelper;
import com.helger.phase2.util.dump.IHTTPOutgoingDumper;

import jakarta.mail.MessagingException;

/**
 * Http connection, Implemented as HttpClient.
 *
 * @author Ziv Harpaz
 * @author Philip Helger
 */
public class AS2HttpClient
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AS2HttpClient.class);

  private final ClassicRequestBuilder m_aRequestBuilder;
  private final CloseableHttpClient m_aCloseableHttpClient;
  private CloseableHttpResponse m_aCloseableHttpResponse;

  /**
   * Set {@link Proxy} into {@link RequestConfig.Builder}
   *
   * @param aProxy
   *        My by <code>null</code>, in such case nothing is done.
   * @return The http host to use as proxy. May be <code>null</code>.
   */
  @Nullable
  private static HttpHost _getProxyHost (@NonNull final Proxy aProxy)
  {
    try
    {
      final SocketAddress aSocketAddress = aProxy.address ();
      if (aSocketAddress instanceof InetSocketAddress)
      {
        final InetSocketAddress aISocketAdress = (InetSocketAddress) aSocketAddress;
        final InetAddress aInetAddr = aISocketAdress.getAddress ();
        if (aInetAddr != null)
        {
          return new HttpHost (aInetAddr, aISocketAdress.getPort ());
        }

        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("No address in proxy:" +
                        aProxy.address () +
                        "-" +
                        (null != aProxy.type () ? aProxy.type ().name () : "null"));
      }
    }
    catch (final RuntimeException ex)
    {
      LOGGER.error ("Exception while setting proxy. Continue without proxy. Proxy: " +
                    aProxy.address () +
                    "-" +
                    (null != aProxy.type () ? aProxy.type ().name () : "null"),
                    ex);
    }
    return null;
  }

  public AS2HttpClient (@NonNull @Nonempty final String sUrl,
                        @NonNull final Timeout aConnectTimeout,
                        @NonNull final Timeout aResponseTimeout,
                        @NonNull final EHttpMethod eRequestMethod,
                        @Nullable final Proxy aProxy,
                        @Nullable final SSLContext aSSLContext,
                        @Nullable final HostnameVerifier aHostnameVerifier)
  {
    // Connection details
    final ConnectionConfig.Builder aConnectionConfigBuilder = ConnectionConfig.custom ()
                                                                              .setConnectTimeout (aConnectTimeout);
    final PoolingHttpClientConnectionManagerBuilder aConnMgrBuilder = PoolingHttpClientConnectionManagerBuilder.create ()
                                                                                                               .setDefaultConnectionConfig (aConnectionConfigBuilder.build ());
    if (aSSLContext != null)
    {
      // Apply TLS configuration
      final TlsSocketStrategy aTlsSocketFactory = new DefaultClientTlsStrategy (aSSLContext, aHostnameVerifier);
      aConnMgrBuilder.setTlsSocketStrategy (aTlsSocketFactory);
    }

    HttpRoutePlanner aRoutePlanner = null;
    if (aProxy != null)
    {
      // Apply proxy configuration
      final HttpHost aProxyHost = _getProxyHost (aProxy);
      if (aProxyHost != null)
      {
        aRoutePlanner = new DefaultProxyRoutePlanner (aProxyHost, DefaultSchemePortResolver.INSTANCE);
      }
    }

    // Request configuration
    final RequestConfig.Builder aRequestConfBuilder = RequestConfig.custom ()
                                                                   .setCookieSpec (StandardCookieSpec.STRICT)
                                                                   .setResponseTimeout (aResponseTimeout)
                                                                   .setCircularRedirectsAllowed (false);

    final HttpClientBuilder aClientBuilder = HttpClientBuilder.create ()
                                                              .setDefaultRequestConfig (aRequestConfBuilder.build ())
                                                              .setConnectionManager (aConnMgrBuilder.build ())
                                                              .setRoutePlanner (aRoutePlanner);

    m_aCloseableHttpClient = aClientBuilder.build ();
    m_aRequestBuilder = ClassicRequestBuilder.create (eRequestMethod.getName ()).setUri (sUrl);
  }

  /**
   * Set an HTTP header (replacing existing value). No modification or check on name or value
   * happens.
   *
   * @param sName
   *        Header name. May not be <code>null</code>.
   * @param sValue
   *        Header value. May not be <code>null</code>.
   */
  public void setHttpHeader (@NonNull final String sName, @NonNull final String sValue)
  {
    ValueEnforcer.notNull (sName, "Name");
    ValueEnforcer.notNull (sValue, "Value");

    m_aRequestBuilder.setHeader (sName, sValue);
  }

  /**
   * @return The URL to send to. Should be the same as the one passed in the constructor. Never
   *         <code>null</code>.
   * @throws AS2Exception
   *         in case of error (e.g. if the URI could not be converted to a URL).
   */
  @NonNull
  public URL getURL () throws AS2Exception
  {
    final URI aURI = m_aRequestBuilder.getUri ();
    try
    {
      return aURI.toURL ();
    }
    catch (final MalformedURLException ex)
    {
      LOGGER.error ("Failed to get URL from connection, URI: " + aURI.toASCIIString (), ex);
      throw new AS2Exception (ex.getCause ());
    }
  }

  /**
   * @param aISToSend
   *        InputStream to send. May not be <code>null</code>.
   * @param eCTE
   *        Content-Transfer-Encoding to be used. May not be <code>null</code>.
   * @param aOutgoingDumper
   *        Optional outgoing dumper
   * @param aResHelper
   *        Resource helper
   * @return bytes sent. Must be &ge; 0.
   * @throws IOException
   *         In case of error
   */
  @Nonnegative
  public long send (@NonNull @WillClose final InputStream aISToSend,
                    @Nullable final EContentTransferEncoding eCTE,
                    @Nullable final IHTTPOutgoingDumper aOutgoingDumper,
                    @NonNull final AS2ResourceHelper aResHelper) throws IOException
  {
    try (final CountingInputStream aCIS = new CountingInputStream (aISToSend))
    {
      final AbstractHttpEntity aISE = new AbstractHttpEntity ((ContentType) null, eCTE != null ? eCTE.getID () : null)
      {
        public void close ()
        {
          // empty
        }

        @Override
        public InputStream getContent () throws IOException
        {
          // Only writeTo should be used from the outside
          throw new UnsupportedOperationException ();
        }

        public long getContentLength ()
        {
          return -1L;
        }

        public boolean isStreaming ()
        {
          return true;
        }

        @Override
        public void writeTo (@NonNull final OutputStream aOS) throws IOException
        {
          // Use MIME encoding here
          try (final OutputStream aDebugOS = aOutgoingDumper != null ? aOutgoingDumper.getDumpOS (aOS) : aOS)
          {
            try (final OutputStream aEncodedOS = eCTE != null ? AS2IOHelper
                                                                           .getContentTransferEncodingAwareOutputStream (aDebugOS,
                                                                                                                         eCTE.getID ())
                                                              : aDebugOS)
            {
              StreamHelper.copyByteStream ().from (aCIS).closeFrom (true).to (aEncodedOS).closeTo (false).build ();
            }
          }
          catch (final MessagingException ex)
          {
            throw new IllegalStateException ("Failed to encode OutputStream with CTE '" + eCTE + "'", ex);
          }
        }
      };
      // Use a temporary file to get the Content length
      final HttpEntity aEntity = aResHelper.createRepeatableHttpEntity (aISE);
      m_aRequestBuilder.setEntity (aEntity);
      final ClassicHttpRequest aHttpUriRequest = m_aRequestBuilder.build ();

      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Performing HttpRequest to '" + aHttpUriRequest.toString () + "'");

      m_aCloseableHttpResponse = m_aCloseableHttpClient.execute (aHttpUriRequest);
      return aCIS.getBytesRead ();
    }
  }

  /**
   * Get InputStream
   *
   * @return InputStream to read response body from
   * @throws AS2Exception
   *         in case of error
   * @throws IOException
   *         in case of IO error
   */
  public InputStream getInputStream () throws AS2Exception, IOException
  {
    // message was not sent yet, not response
    if (m_aCloseableHttpResponse == null)
      throw new AS2Exception ("No response as message was yet sent");

    return m_aCloseableHttpResponse.getEntity ().getContent ();
  }

  /**
   * @return response HTTP Status as int
   * @throws AS2Exception
   *         in case of error
   */
  public int getResponseCode () throws AS2Exception
  {
    // message was not sent yet, not response
    if (m_aCloseableHttpResponse == null)
      throw new AS2Exception ("No response as message was yet sent");

    return m_aCloseableHttpResponse.getCode ();
  }

  /**
   * @return the response message
   * @throws AS2Exception
   *         in case of error
   */
  public String getResponseMessage () throws AS2Exception
  {
    // message was not sent yet, not response
    if (m_aCloseableHttpResponse == null)
      throw new AS2Exception ("No response as message was yet sent");

    return m_aCloseableHttpResponse.getReasonPhrase ();
  }

  @NonNull
  @ReturnsMutableCopy
  public HttpHeaderMap getResponseHeaderFields () throws AS2Exception
  {
    // message was not sent yet, not response
    if (m_aCloseableHttpResponse == null)
      throw new AS2Exception ("No response as message was yet sent");

    final HttpHeaderMap ret = new HttpHeaderMap ();
    final Header [] aHeaders = m_aCloseableHttpResponse.getHeaders ();
    if (aHeaders != null)
      for (final Header aHeader : aHeaders)
        ret.addHeader (aHeader.getName (), aHeader.getValue ());
    return ret;
  }

  /**
   * Close the connection
   */
  public void disconnect ()
  {
    try
    {
      if (m_aCloseableHttpResponse != null)
        m_aCloseableHttpResponse.close ();
      if (m_aCloseableHttpClient != null)
        m_aCloseableHttpClient.close ();
    }
    catch (final Exception ex)
    {
      LOGGER.error ("Exception while closing HttpClient connection: " + this.toString (), ex);
    }
  }

  /**
   * This method determines if something is an HTTP error or not. The following HTTP status codes
   * are interpreted as success: 200, 201, 202, 204 and 206.
   *
   * @param nResponseCode
   *        The HTTP status code to check.
   * @return <code>true</code> if it is an error, <code>false</code> on success.
   */
  public static boolean isErrorResponseCode (final int nResponseCode)
  {
    // Accept most of 2xx HTTP response codes
    return nResponseCode != CHttp.HTTP_OK &&
           nResponseCode != CHttp.HTTP_CREATED &&
           nResponseCode != CHttp.HTTP_ACCEPTED &&
           nResponseCode != CHttp.HTTP_NO_CONTENT &&
           nResponseCode != CHttp.HTTP_PARTIAL_CONTENT;
  }
}
