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
package com.helger.phase2.util.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.StringTokenizer;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonempty;
import com.helger.annotation.WillNotClose;
import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.base.io.stream.NonClosingInputStream;
import com.helger.base.io.stream.StreamHelper;
import com.helger.http.header.HttpHeaderMap;

import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetHeaders;

/**
 * Implementation of {@link IAS2HttpRequestDataProvider} based on a {@link Socket}
 * {@link InputStream}.
 *
 * @author Philip Helger
 */
@Immutable
public class AS2HttpRequestDataProviderInputStream implements IAS2HttpRequestDataProvider
{
  @WillNotClose
  private final InputStream m_aIS;
  private final String m_sHttpRequestMethod;
  private final String m_sHttpRequestUrl;
  private final String m_sHttpRequestVersion;
  private final HttpHeaderMap m_aHttpHeaders = new HttpHeaderMap ();

  /**
   * Read the first line of the HTTP request InputStream and parse out HTTP method (e.g. "GET" or
   * "POST"), request URL (e.g "/as2") and HTTP version (e.g. "HTTP/1.1")
   *
   * @param aIS
   *        Stream to read the first line from
   * @return An array with 3 elements, containing method, URL and HTTP version
   * @throws IOException
   *         In case of IO error
   */
  @NonNull
  @Nonempty
  private static String [] _readRequestInfo (@NonNull final InputStream aIS) throws IOException
  {
    int nByteBuf = aIS.read ();
    final StringBuilder aSB = new StringBuilder ();
    while (nByteBuf != -1 && nByteBuf != '\r')
    {
      aSB.append ((char) nByteBuf);
      nByteBuf = aIS.read ();
    }
    if (nByteBuf != -1)
    {
      // read in the \n following the "\r"
      aIS.read ();
    }

    final StringTokenizer aTokens = new StringTokenizer (aSB.toString (), " ");
    final int nTokenCount = aTokens.countTokens ();
    if (nTokenCount >= 3)
    {
      // Return all tokens
      final String [] aRequestParts = new String [nTokenCount];
      for (int i = 0; i < nTokenCount; i++)
        aRequestParts[i] = aTokens.nextToken ();
      return aRequestParts;
    }

    if (nTokenCount == 2)
    {
      // Default the request URL to "/"
      final String [] aRequestParts = new String [3];
      aRequestParts[0] = aTokens.nextToken ();
      aRequestParts[1] = "/";
      aRequestParts[2] = aTokens.nextToken ();
      return aRequestParts;
    }
    throw new IOException ("Invalid HTTP Request (" + aSB.toString () + ")");
  }

  /**
   * Constructor
   *
   * @param aIS
   *        InputStream to read from. May not be <code>null</code>.
   * @throws IOException
   *         If reading from the Socket fails
   * @throws MessagingException
   *         If reading the HTTP headers failed
   */
  public AS2HttpRequestDataProviderInputStream (@NonNull @WillNotClose final InputStream aIS) throws IOException,
                                                                                              MessagingException
  {
    ValueEnforcer.notNull (aIS, "InputStream");

    m_aIS = aIS;

    // Read the HTTP meta data first line
    final String [] aRequest = _readRequestInfo (m_aIS);
    m_sHttpRequestMethod = aRequest[0];
    m_sHttpRequestUrl = aRequest[1];
    m_sHttpRequestVersion = aRequest[2];

    // Read the HTTP headers next
    // Parse all HTTP headers from stream
    final InternetHeaders aHeaders = new InternetHeaders (m_aIS);
    // Convert to header map
    final Enumeration <Header> aEnum = aHeaders.getAllHeaders ();
    while (aEnum.hasMoreElements ())
    {
      final Header aHeader = aEnum.nextElement ();
      m_aHttpHeaders.addHeader (aHeader.getName (), aHeader.getValue ());
    }
  }

  /**
   * Will return a buffered, {@link NonClosingInputStream} that when closed, will not close in
   * source stream. This is useful when working with <code>java.net.SocketInputStream</code> as
   * close() on a socket stream closes the {@link Socket}
   *
   * @return {@link InputStream}
   * @throws IOException
   *         in case of error
   */
  @NonNull
  public InputStream getHttpInputStream () throws IOException
  {
    // Use "NonClosing" internally to that the returned stream is easily
    // discovered as "buffered"
    return StreamHelper.getBuffered (new NonClosingInputStream (m_aIS));
  }

  public boolean isChunkedEncodingAlreadyProcessed ()
  {
    // Procesing must happen inside the library
    return false;
  }

  @NonNull
  public String getHttpRequestMethod ()
  {
    return m_sHttpRequestMethod;
  }

  @NonNull
  public String getHttpRequestUrl ()
  {
    return m_sHttpRequestUrl;
  }

  @NonNull
  public String getHttpRequestVersion ()
  {
    return m_sHttpRequestVersion;
  }

  @NonNull
  public HttpHeaderMap getHttpHeaderMap ()
  {
    return m_aHttpHeaders;
  }

  @NonNull
  public static AS2HttpRequestDataProviderInputStream createForUtf8 (@NonNull final String s) throws IOException,
                                                                                              MessagingException
  {
    final byte [] b = s.getBytes (StandardCharsets.UTF_8);
    return new AS2HttpRequestDataProviderInputStream (new NonBlockingByteArrayInputStream (b));
  }
}
