/*
 * Copyright (C) 2015-2026 Philip Helger (www.helger.com)
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

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.annotation.style.OverrideOnDemand;
import com.helger.base.timing.StopWatch;
import com.helger.collection.commons.ICommonsMap;
import com.helger.http.EHttpMethod;
import com.helger.http.EHttpVersion;
import com.helger.mail.datasource.ByteArrayDataSource;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.AS2Message;
import com.helger.phase2.processor.CNetAttribute;
import com.helger.phase2.servlet.util.AS2OutputStreamCreatorHttpServletResponse;
import com.helger.phase2.session.AS2Session;
import com.helger.phase2.util.AS2IOHelper;
import com.helger.phase2.util.dump.IHTTPIncomingDumper;
import com.helger.phase2.util.http.HTTPHelper;
import com.helger.phase2.util.http.IAS2HttpResponseHandler;
import com.helger.servlet.ServletHelper;
import com.helger.web.scope.IRequestWebScope;
import com.helger.xservlet.handler.IXServletHandler;

import jakarta.activation.DataSource;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * This is the base XServlet handler that takes AS2 messages and MDNs. This
 * class contains a lot of methods that may be overridden.
 *
 * @author Philip Helger
 * @since 4.6.4
 */
public abstract class AbstractAS2ReceiveBaseXServletHandler implements IXServletHandler
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AbstractAS2ReceiveBaseXServletHandler.class);

  private AS2Session m_aSession;
  private IHTTPIncomingDumper m_aHttpIncomingDumper;

  /**
   * Create the AS2 session to be used based on the provided configuration file.
   *
   * @param aInitParams
   *        The init params of the servlet to use. Never <code>null</code>.
   * @return The created session. May not be <code>null</code>.
   * @throws AS2Exception
   *         In case something goes wrong when initializing the session
   * @throws ServletException
   *         In case an overriding methods wants to throw a different exception
   */
  @NonNull
  @OverrideOnDemand
  protected abstract AS2Session createAS2Session (@NonNull ICommonsMap <String, String> aInitParams) throws AS2Exception,
                                                                                                     ServletException;

  @Override
  @OverridingMethodsMustInvokeSuper
  public void onServletInit (@NonNull final ICommonsMap <String, String> aInitParams) throws ServletException
  {
    try
    {
      // E.g. read the configuration from a file
      m_aSession = createAS2Session (aInitParams);
      // Don't start active modules to avoid connecting to a port!
    }
    catch (final AS2Exception ex)
    {
      throw new ServletException ("Failed to init AS2 configuration", ex);
    }
  }

  /**
   * @return The AS2 session that was created in initialization. Never
   *         <code>null</code>.
   * @throws IllegalStateException
   *         In case initialization failed
   */
  @NonNull
  protected final AS2Session getSession ()
  {
    if (m_aSession == null)
      throw new IllegalStateException ("This servlet was not initialized properly! No AS2 session is present.");
    return m_aSession;
  }

  /**
   * @return The specific incoming dumper of this servlet. May be
   *         <code>null</code>.
   * @since v4.4.5
   */
  @Nullable
  public final IHTTPIncomingDumper getHttpIncomingDumper ()
  {
    return m_aHttpIncomingDumper;
  }

  /**
   * Get the customized incoming dumper, falling back to the global incoming
   * dumper if no specific dumper is set.
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
   * Set the specific incoming dumper of this servlet. If this is set, it
   * overrides the global dumper.
   *
   * @param aHttpIncomingDumper
   *        The specific incoming dumper to be used. May be <code>null</code>.
   * @since v4.4.5
   */
  public final void setHttpIncomingDumper (@Nullable final IHTTPIncomingDumper aHttpIncomingDumper)
  {
    m_aHttpIncomingDumper = aHttpIncomingDumper;
  }

  protected abstract boolean isQuoteHeaderValues ();

  /**
   * Main handling method
   *
   * @param sClientInfo
   *        Client info for logging
   * @param aMsgData
   *        Message payload
   * @param aMsg
   *        AS2 message object
   * @param aResponseHandler
   *        The response handler for sending back the MDN
   * @throws ServletException
   *         In case of an error
   */
  protected abstract void handleIncomingMessage (@NonNull final String sClientInfo,
                                                 @NonNull final DataSource aMsgData,
                                                 @NonNull final AS2Message aMsg,
                                                 @NonNull final IAS2HttpResponseHandler aResponseHandler) throws ServletException;

  public final void onRequest (@NonNull final HttpServletRequest aHttpRequest,
                               @NonNull final HttpServletResponse aHttpResponse,
                               @NonNull final EHttpVersion eHttpVersion,
                               @NonNull final EHttpMethod eHttpMethod,
                               @NonNull final IRequestWebScope aRequestScope) throws ServletException, IOException
  {
    // Handle the incoming message, and return the MDN if necessary
    final String sClientInfo = aHttpRequest.getRemoteAddr () + ":" + aHttpRequest.getRemotePort ();

    LOGGER.info ("Starting to handle incoming AS2 request - " + sClientInfo);

    // Create empty message
    final AS2Message aMsg = new AS2Message ();
    aMsg.attrs ().putIn (CNetAttribute.MA_SOURCE_IP, aHttpRequest.getRemoteAddr ());
    aMsg.attrs ().putIn (CNetAttribute.MA_SOURCE_PORT, aHttpRequest.getRemotePort ());
    aMsg.attrs ().putIn (CNetAttribute.MA_DESTINATION_IP, aHttpRequest.getLocalAddr ());
    aMsg.attrs ().putIn (CNetAttribute.MA_DESTINATION_PORT, aHttpRequest.getLocalPort ());

    // Request type (e.g. "POST")
    aMsg.attrs ().putIn (HTTPHelper.MA_HTTP_REQ_TYPE, aHttpRequest.getMethod ());
    // Request URL (e.g. "/as2")
    aMsg.attrs ().putIn (HTTPHelper.MA_HTTP_REQ_URL, ServletHelper.getRequestRequestURI (aHttpRequest));

    // Add all request headers to the AS2 message
    aMsg.headers ().setAllHeaders (aRequestScope.headers ());

    // Build the handler that performs the response handling
    final boolean bQuoteHeaderValues = isQuoteHeaderValues ();
    final AS2OutputStreamCreatorHttpServletResponse aResponseHandler = new AS2OutputStreamCreatorHttpServletResponse (aHttpResponse,
                                                                                                                      bQuoteHeaderValues);

    // Read the S/MIME content in a byte array - memory!
    // Chunked encoding was already handled, so read "as-is"
    final long nContentLength = aHttpRequest.getContentLengthLong ();
    if (nContentLength > Integer.MAX_VALUE)
      throw new IllegalStateException ("Currently only payload with up to 2GB can be handled! This request has " +
                                       nContentLength +
                                       " bytes.");

    // Open it once, and close it at the end
    try (final ServletInputStream aRequestIS = aHttpRequest.getInputStream ())
    {
      // Time the transmission
      final StopWatch aSW = StopWatch.createdStarted ();

      DataSource aMsgDataSource = null;
      try
      {
        // Read in the message request, headers, and data
        final IHTTPIncomingDumper aIncomingDumper = getEffectiveHttpIncomingDumper ();
        aMsgDataSource = HTTPHelper.readAndDecodeHttpRequest (new AS2HttpRequestDataProviderServletRequest (aRequestScope,
                                                                                                            aRequestIS),
                                                              aResponseHandler,
                                                              aMsg,
                                                              aIncomingDumper);
      }
      catch (final Exception ex)
      {
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Failed to read Servlet Request", ex);
        AS2Exception.log (ex.getClass (),
                          true,
                          "Failed to read Servlet Request: " + ex.getMessage (),
                          null,
                          null,
                          ex.getCause ());
      }

      aSW.stop ();

      if (aMsgDataSource == null)
      {
        LOGGER.error ("Not having a data source to operate on");
      }
      else
      {
        if (aMsgDataSource instanceof ByteArrayDataSource)
        {
          final ByteArrayDataSource aBADS = (ByteArrayDataSource) aMsgDataSource;
          LOGGER.info ("received " +
                       AS2IOHelper.getTransferRate (aBADS.directGetBytes ().length, aSW) +
                       " from " +
                       sClientInfo +
                       aMsg.getLoggingText ());
        }
        else
        {
          LOGGER.info ("received message from " +
                       sClientInfo +
                       aMsg.getLoggingText () +
                       " in " +
                       aSW.getMillis () +
                       " ms");
        }

        handleIncomingMessage (sClientInfo, aMsgDataSource, aMsg, aResponseHandler);
      }
    }
  }
}
