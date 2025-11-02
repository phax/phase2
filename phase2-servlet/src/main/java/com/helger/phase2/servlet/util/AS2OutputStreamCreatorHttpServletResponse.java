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
package com.helger.phase2.servlet.util;

import java.io.IOException;
import java.io.OutputStream;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.WillNotClose;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.iface.IWriteToStream;
import com.helger.base.io.stream.StreamHelper;
import com.helger.http.header.HttpHeaderMap;
import com.helger.phase2.util.http.IAS2HttpResponseHandler;

import jakarta.servlet.http.HttpServletResponse;

/**
 * An implementation of {@link IAS2HttpResponseHandler} that works upon a
 * {@link HttpServletResponse}.
 *
 * @author Philip Helger
 */
public class AS2OutputStreamCreatorHttpServletResponse implements IAS2HttpResponseHandler
{
  private final HttpServletResponse m_aHttpResponse;
  private final boolean m_bQuoteHeaderValues;

  public AS2OutputStreamCreatorHttpServletResponse (@NonNull final HttpServletResponse aHttpResponse, final boolean bQuoteHeaderValues)
  {
    m_aHttpResponse = ValueEnforcer.notNull (aHttpResponse, "HttpResponse");
    m_bQuoteHeaderValues = bQuoteHeaderValues;
  }

  /**
   * @return <code>true</code> if HTTP header values should be quoted,
   *         <code>false</code> if not.
   * @since 4.4.4
   */
  public final boolean isQuoteHeaderHeaderValues ()
  {
    return m_bQuoteHeaderValues;
  }

  public void sendHttpResponse (@Nonnegative final int nHttpResponseCode,
                                @NonNull final HttpHeaderMap aHeaders,
                                @NonNull @WillNotClose final IWriteToStream aData) throws IOException
  {
    // Set status code
    m_aHttpResponse.setStatus (nHttpResponseCode);

    // Add headers (unify always)
    aHeaders.forEachSingleHeader ( (k, v) -> m_aHttpResponse.addHeader (k, v), true, m_bQuoteHeaderValues);

    // Write response body
    final OutputStream aOS = StreamHelper.getBuffered (m_aHttpResponse.getOutputStream ());
    aData.writeTo (aOS);

    // Don't close the OutputStream - just flush it.
    aOS.flush ();
  }
}
