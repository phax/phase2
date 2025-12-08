/*
 * Copyright (C) 2015-2025 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phase2.servlet;

import java.io.InputStream;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonempty;
import com.helger.annotation.WillNotClose;
import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.stream.NonClosingInputStream;
import com.helger.base.io.stream.StreamHelper;
import com.helger.http.header.HttpHeaderMap;
import com.helger.phase2.util.http.IAS2HttpRequestDataProvider;
import com.helger.web.scope.IRequestWebScope;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;

/**
 * Implementation of {@link IAS2HttpRequestDataProvider} based on a
 * {@link ServletRequest} {@link InputStream}.
 *
 * @author Philip Helger
 * @since 4.8.0
 */
@Immutable
final class AS2HttpRequestDataProviderServletRequest implements IAS2HttpRequestDataProvider
{
  private final IRequestWebScope m_aRequestScope;
  private final ServletInputStream m_aRequestIS;

  /**
   * Constructor
   *
   * @param aRequestScope
   *        HTTP Servlet Request. May not be <code>null</code>.
   * @param aRequestIS
   *        Servlet request InputStream to read from. Will not be closed. May
   *        not be <code>null</code>.
   */
  public AS2HttpRequestDataProviderServletRequest (@NonNull final IRequestWebScope aRequestScope,
                                                   @NonNull @WillNotClose final ServletInputStream aRequestIS)
  {
    ValueEnforcer.notNull (aRequestScope, "RequestScope");
    ValueEnforcer.notNull (aRequestIS, "RequestIS");
    m_aRequestScope = aRequestScope;
    m_aRequestIS = aRequestIS;
  }

  /**
   * Will return a buffered, {@link NonClosingInputStream} that when closed,
   * will not close in source stream.
   *
   * @return {@link InputStream}
   */
  @NonNull
  public InputStream getHttpInputStream ()
  {
    // Use "NonClosing" internally to that the returned stream is easily
    // discovered as "buffered"
    return StreamHelper.getBuffered (new NonClosingInputStream (m_aRequestIS));
  }

  public boolean isChunkedEncodingAlreadyProcessed ()
  {
    // Processed by the application server like Tomcat or Jetty outside
    return true;
  }

  @NonNull
  @Nonempty
  public String getHttpRequestMethod ()
  {
    return m_aRequestScope.getHttpMethod ().getName ();
  }

  @NonNull
  @Nonempty
  public String getHttpRequestUrl ()
  {
    return m_aRequestScope.getRequestURIDecoded ();
  }

  @NonNull
  @Nonempty
  public String getHttpRequestVersion ()
  {
    return m_aRequestScope.getHttpVersion ().getName ();
  }

  @NonNull
  public HttpHeaderMap getHttpHeaderMap ()
  {
    return m_aRequestScope.headers ();
  }
}
