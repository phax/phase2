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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.timing.StopWatch;
import com.helger.http.CHttpHeader;
import com.helger.http.EHttpMethod;
import com.helger.http.header.HttpHeaderMap;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.phase2.crypto.MIC;
import com.helger.phase2.disposition.DispositionType;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.WrappedAS2Exception;
import com.helger.phase2.message.AS2Message;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.message.IMessageMDN;
import com.helger.phase2.processor.AS2NoModuleException;
import com.helger.phase2.processor.CNetAttribute;
import com.helger.phase2.processor.storage.IProcessorStorageModule;
import com.helger.phase2.session.AS2ComponentNotFoundException;
import com.helger.phase2.util.AS2IOHelper;
import com.helger.phase2.util.AS2ResourceHelper;
import com.helger.phase2.util.CAS2Header;
import com.helger.phase2.util.dump.IHTTPOutgoingDumper;
import com.helger.phase2.util.http.AS2HttpClient;
import com.helger.phase2.util.http.AS2HttpHeaderSetter;

import jakarta.mail.MessagingException;

/**
 * A module to send an asynchronous MDN.
 *
 * @author Philip Helger
 */
public class AsynchMDNSenderModule extends AbstractHttpSenderModule
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AsynchMDNSenderModule.class);

  public AsynchMDNSenderModule ()
  {}

  public boolean canHandle (@NonNull final String sAction,
                            @NonNull final IMessage aMsg,
                            @Nullable final Map <String, Object> aOptions)
  {
    return sAction.equals (IProcessorSenderModule.DO_SEND_ASYNC_MDN) && aMsg instanceof AS2Message;
  }

  private void _sendViaHTTP (@NonNull final AS2Message aMsg,
                             @NonNull final DispositionType aDisposition,
                             @Nullable final IHTTPOutgoingDumper aOutgoingDumper,
                             @NonNull final AS2ResourceHelper aResHelper) throws AS2Exception,
                                                                          IOException,
                                                                          MessagingException
  {
    final IMessageMDN aMdn = aMsg.getMDN ();

    // Create a HTTP connection
    final String sUrl = aMsg.getAsyncMDNurl ();
    final EHttpMethod eRequestMethod = EHttpMethod.POST;
    // MDN is a small message. We will always use CHttp
    final AS2HttpClient aConn = getHttpClient (sUrl, eRequestMethod, getSession ().getHttpProxy ());

    try
    {
      if (aOutgoingDumper != null)
        aOutgoingDumper.start (sUrl, aMsg);

      LOGGER.info ("Connecting to " + sUrl + aMsg.getLoggingText ());

      // Set all custom headers first (so that they are overridden with the
      // mandatory ones in here)
      // Use HttpHeaderMap and not String to ensure name casing is identical!
      final HttpHeaderMap aHeaderMap = aMdn.headers ().getClone ();
      aHeaderMap.setHeader (CHttpHeader.CONNECTION, CAS2Header.DEFAULT_CONNECTION);
      aHeaderMap.setHeader (CHttpHeader.USER_AGENT, CAS2Header.DEFAULT_USER_AGENT);

      final boolean bQuoteHeaderValues = isQuoteHeaderValues ();
      final AS2HttpHeaderSetter aHeaderSetter = new AS2HttpHeaderSetter (aConn, aOutgoingDumper, bQuoteHeaderValues);
      // Copy all the header from mdn to the RequestProperties of conn
      // Unification needed, because original MDN headers may contain newlines
      aHeaderMap.forEachSingleHeader (aHeaderSetter::setHttpHeader, true, false);

      if (aOutgoingDumper != null)
        aOutgoingDumper.finishedHeaders ();

      aMsg.attrs ().putIn (CNetAttribute.MA_DESTINATION_IP, aConn.getURL ().getHost ());
      aMsg.attrs ().putIn (CNetAttribute.MA_DESTINATION_PORT, aConn.getURL ().getPort ());

      final InputStream aMsgIS = aMdn.getData ().getInputStream ();

      // Transfer the data
      final StopWatch aSW = StopWatch.createdStarted ();
      final long nBytes = aConn.send (aMsgIS, (EContentTransferEncoding) null, aOutgoingDumper, aResHelper);
      aSW.stop ();

      LOGGER.info ("AS2 MDN transferred " + AS2IOHelper.getTransferRate (nBytes, aSW) + aMsg.getLoggingText ());

      if (aOutgoingDumper != null)
        aOutgoingDumper.finishedPayload ();

      final int nHttpResponseCode = aConn.getResponseCode ();
      if (getOutgoingHttpCallback () != null)
        getOutgoingHttpCallback ().onOutgoingHttpMessage (false,
                                                          aMsg.getAS2From (),
                                                          aMsg.getAS2To (),
                                                          aMsg.getMessageID (),
                                                          (MIC) null,
                                                          (EContentTransferEncoding) null,
                                                          sUrl,
                                                          nHttpResponseCode);

      // Check the HTTP Response code
      if (AS2HttpClient.isErrorResponseCode (nHttpResponseCode))
      {
        LOGGER.error ("sent AsyncMDN [" +
                      aDisposition.getAsString () +
                      "] Fail(" +
                      nHttpResponseCode +
                      ") " +
                      aMsg.getLoggingText ());
        throw new AS2HttpResponseException (sUrl, nHttpResponseCode, aConn.getResponseMessage ());
      }

      LOGGER.info ("sent AsyncMDN [" +
                   aDisposition.getAsString () +
                   "] OK(" +
                   nHttpResponseCode +
                   ") " +
                   aMsg.getLoggingText ());

      // log & store mdn into backup folder.
      try
      {
        getSession ().getMessageProcessor ().handle (IProcessorStorageModule.DO_STOREMDN, aMsg, null);
      }
      catch (final AS2ComponentNotFoundException | AS2NoModuleException ex)
      {
        // No message processor found
        // Or no module found in message processor
      }
    }
    finally
    {
      aConn.disconnect ();
    }
  }

  public void handle (@NonNull final String sAction,
                      @NonNull final IMessage aBaseMsg,
                      @Nullable final Map <String, Object> aOptions) throws AS2Exception
  {
    try (final AS2ResourceHelper aResHelper = new AS2ResourceHelper ())
    {
      final AS2Message aMsg = (AS2Message) aBaseMsg;

      LOGGER.info ("Async MDN submitted" + aMsg.getLoggingText ());
      final DispositionType aDisposition = DispositionType.createSuccess ();
      final int nRetries = getRetryCount (aMsg.partnership (), aOptions);

      try (final IHTTPOutgoingDumper aOutgoingDumper = getHttpOutgoingDumper (aMsg))
      {
        _sendViaHTTP (aMsg, aDisposition, aOutgoingDumper, aResHelper);
      }
      catch (final AS2HttpResponseException ex)
      {
        LOGGER.error ("Http Response Error " + ex.getMessage ());

        // Resend if the HTTP Response has an error code
        ex.terminate ();

        if (!doResend (IProcessorSenderModule.DO_SEND_ASYNC_MDN, aMsg, ex, nRetries))
          throw ex;
      }
      catch (final IOException ex)
      {
        // Resend if a network error occurs during transmission
        final AS2Exception wioe = WrappedAS2Exception.wrap (ex).setSourceMsg (aMsg).terminate ();

        if (!doResend (IProcessorSenderModule.DO_SEND_ASYNC_MDN, aMsg, wioe, nRetries))
          throw wioe;
      }
      catch (final Exception ex)
      {
        // Propagate error if it can't be handled by a resend
        throw WrappedAS2Exception.wrap (ex);
      }
    }
    finally
    {
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Async MDN message sent");
    }
  }
}
