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

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.WillNotClose;
import com.helger.annotation.concurrent.GuardedBy;
import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.codec.IByteArrayCodec;
import com.helger.base.codec.IdentityCodec;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.base.string.StringHelper;
import com.helger.base.string.StringHex;
import com.helger.base.string.StringParser;
import com.helger.base.system.SystemProperties;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.http.CHttp;
import com.helger.http.CHttpHeader;
import com.helger.http.header.HttpHeaderMap;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.mail.cte.IContentTransferEncoding;
import com.helger.mail.datasource.ByteArrayDataSource;
import com.helger.mail.datasource.IExtendedDataSource;
import com.helger.mail.datasource.InputStreamDataSource;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.util.AS2Helper;
import com.helger.phase2.util.AS2HttpHelper;
import com.helger.phase2.util.AS2IOHelper;
import com.helger.phase2.util.dump.HTTPIncomingDumperDirectoryBased;
import com.helger.phase2.util.dump.IHTTPIncomingDumper;

import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;

/**
 * HTTP utility methods.
 *
 * @author Philip Helger
 */
@ThreadSafe
public final class HTTPHelper
{
  /** The request method used (POST or GET) */
  public static final String MA_HTTP_REQ_TYPE = "HTTP_REQUEST_TYPE";
  /** The request URL used - defaults to "/" */
  public static final String MA_HTTP_REQ_URL = "HTTP_REQUEST_URL";
  /** The HTTP version used. E.g. "HTTP/1.1" */
  public static final String MA_HTTP_REQ_VERSION = "HTTP_REQUEST_VERSION";

  /** The value of the Content-Transfer-Encoding header (if provided) */
  public static final String MA_HTTP_ORIGINAL_CONTENT_TRANSFER_ENCODING = "HTTP_ORIGINAL_CONTENT_TRANSFER_ENCODING";
  /**
   * The original content length before any eventual decoding (only if Content-Transfer-Encoding is
   * provided)
   */
  public static final String MA_HTTP_ORIGINAL_CONTENT_LENGTH = "HTTP_ORIGINAL_CONTENT_LENGTH";

  private static final Logger LOGGER = LoggerFactory.getLogger (HTTPHelper.class);

  private static final SimpleReadWriteLock RW_LOCK = new SimpleReadWriteLock ();
  @GuardedBy ("RW_LOCK")
  private static Supplier <? extends IHTTPIncomingDumper> s_aHTTPIncomingDumperFactory = () -> null;

  static
  {
    // Set global incoming dump directory
    // New property name since v4.0.3
    String sHttpDumpIncomingDirectory = SystemProperties.getPropertyValueOrNull ("AS2.httpDumpDirectoryIncoming");
    if (StringHelper.isEmpty (sHttpDumpIncomingDirectory))
    {
      // Check old name
      sHttpDumpIncomingDirectory = SystemProperties.getPropertyValueOrNull ("AS2.httpDumpDirectory");
      if (StringHelper.isNotEmpty (sHttpDumpIncomingDirectory))
        LOGGER.warn ("You are using a legacy system property name `AS2.httpDumpDirectory`. Please use `AS2.httpDumpDirectoryIncoming` instead.");
    }
    if (StringHelper.isNotEmpty (sHttpDumpIncomingDirectory))
    {
      final File aDumpDirectory = new File (sHttpDumpIncomingDirectory);
      AS2IOHelper.getFileOperationManager ().createDirIfNotExisting (aDumpDirectory);
      setHTTPIncomingDumperFactory ( () -> new HTTPIncomingDumperDirectoryBased (aDumpDirectory));
    }
  }

  private HTTPHelper ()
  {}

  @NonNull
  @ReturnsMutableCopy
  public static ICommonsList <String> getAllHTTPHeaderLines (@NonNull final HttpHeaderMap aHeaders)
  {
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    aHeaders.forEachHeaderLine (ret::add, true);
    return ret;
  }

  /**
   * @return the global dumper for incoming HTTP requests or <code>null</code> if none is present
   * @since 3.0.1
   */
  @Nullable
  public static IHTTPIncomingDumper getHTTPIncomingDumper ()
  {
    return RW_LOCK.readLockedGet ( () -> s_aHTTPIncomingDumperFactory.get ());
  }

  /**
   * @return the global dumper factory for incoming HTTP requests. Never <code>null</code>.
   * @since 4.4.0
   */
  @NonNull
  public static Supplier <? extends IHTTPIncomingDumper> getHTTPIncomingDumperFactory ()
  {
    return RW_LOCK.readLockedGet ( () -> s_aHTTPIncomingDumperFactory);
  }

  /**
   * Set the global factory for creating dumper for incoming HTTP requests.
   *
   * @param aHttpDumperFactory
   *        The dumper factory to be used. May not be <code>null</code>.
   * @since 3.1.0
   */
  public static void setHTTPIncomingDumperFactory (@NonNull final Supplier <? extends IHTTPIncomingDumper> aHttpDumperFactory)
  {
    ValueEnforcer.notNull (aHttpDumperFactory, "HttpDumperFactory");
    RW_LOCK.writeLocked ( () -> s_aHTTPIncomingDumperFactory = aHttpDumperFactory);
  }

  /**
   * Read headers and payload from the passed input stream provider. For large file support, return
   * {@link DataSource}. If is on, data is not read.
   *
   * @param aRDP
   *        The abstract input stream provider to use. May not be <code>null</code>.
   * @param aResponseHandler
   *        The HTTP response handler to be used. May not be <code>null</code>.
   * @param aMsg
   *        The Message to be filled. May not be <code>null</code>.
   * @param aIncomingDumper
   *        Optional incoming HTTP dumper. May be <code>null</code>.
   * @return A {@link IExtendedDataSource} that holds/refers to the body.
   * @throws IOException
   *         In case of error reading from the InputStream
   * @throws MessagingException
   *         In case header line parsing fails
   */
  @NonNull
  public static IExtendedDataSource readHttpRequest (@NonNull final IAS2HttpRequestDataProvider aRDP,
                                                     @NonNull final IAS2HttpResponseHandler aResponseHandler,
                                                     @NonNull final IMessage aMsg,
                                                     @Nullable final IHTTPIncomingDumper aIncomingDumper) throws IOException,
                                                                                                          MessagingException
  {
    // Request method (e.g. "POST")
    aMsg.attrs ().putIn (MA_HTTP_REQ_TYPE, aRDP.getHttpRequestMethod ());
    // Request URL (e.g. "/as2")
    aMsg.attrs ().putIn (MA_HTTP_REQ_URL, aRDP.getHttpRequestUrl ());
    // HTTP version (e.g. "HTTP/1.1")
    aMsg.attrs ().putIn (MA_HTTP_REQ_VERSION, aRDP.getHttpRequestVersion ());

    // Get the stream to read from
    final InputStream aIS = aRDP.getHttpInputStream ();

    // Parse all HTTP headers from stream
    aMsg.headers ().setAllHeaders (aRDP.getHttpHeaderMap ());

    // Generate DataSource
    // Put received data in a MIME body part
    final String sReceivedContentType = AS2HttpHelper.getCleanContentType (aMsg.getHeader (CHttpHeader.CONTENT_TYPE));

    final byte [] aBytePayload;
    final IExtendedDataSource aPayload;
    final String sContentLength = aMsg.getHeader (CHttpHeader.CONTENT_LENGTH);
    if (sContentLength == null)
    {
      // No "Content-Length" header present
      final InputStream aRealIS;
      final String sTransferEncoding = aMsg.getHeader (CHttpHeader.TRANSFER_ENCODING);
      if (sTransferEncoding != null)
      {
        // Remove all whitespaces in the value
        if (AS2Helper.getWithoutSpaces (sTransferEncoding).equalsIgnoreCase ("chunked"))
        {
          // chunked encoding. Use also file backed stream as the message
          // might be large
          // Application servers like Tomcat handle chunked encoding outside

          final boolean bHandleChunkedEncodingHere = !aRDP.isChunkedEncodingAlreadyProcessed ();
          if (LOGGER.isDebugEnabled ())
            LOGGER.debug ("Handling HTTP chunked Transfer-Encoding request " +
                          (bHandleChunkedEncodingHere ? "in here as chunked encoding" : "as already processed"));

          @WillNotClose
          final TempSharedFileInputStream aSharedIS = TempSharedFileInputStream.getTempSharedFileInputStream (bHandleChunkedEncodingHere ? new ChunkedInputStream (aIS)
                                                                                                                                         : aIS,
                                                                                                              aMsg.getMessageID ());
          aRealIS = aSharedIS;
          aMsg.setTempSharedFileInputStream (aSharedIS);
        }
        else
        {
          // No "Content-Length" and unsupported "Transfer-Encoding"
          sendSimpleHTTPResponse (aResponseHandler, CHttp.HTTP_LENGTH_REQUIRED);
          throw new IOException ("Transfer-Encoding unimplemented: '" + sTransferEncoding + "'");
        }
      }
      else
      {
        // No "Content-Length" and no "Transfer-Encoding"
        sendSimpleHTTPResponse (aResponseHandler, CHttp.HTTP_LENGTH_REQUIRED);
        throw new IOException ("Content-Length is missing and no Transfer-Encoding is specified");
      }

      // Content-length present, or chunked encoding
      aBytePayload = null;
      aPayload = new InputStreamDataSource (aRealIS,
                                            aMsg.getAS2From () == null ? "" : aMsg.getAS2From (),
                                            sReceivedContentType,
                                            true);
    }
    else
    {
      // content-length exists
      // Read the message body - no Content-Transfer-Encoding handling
      // Retrieve the message content
      // FIXME if a value > 2GB comes in, this will fail!!
      final long nContentLength = StringParser.parseLong (sContentLength, -1);
      if (nContentLength < 0 || nContentLength > Integer.MAX_VALUE)
      {
        // Invalid content length (no int or too big)
        sendSimpleHTTPResponse (aResponseHandler, CHttp.HTTP_LENGTH_REQUIRED);
        throw new IOException ("Content-Length '" +
                               sContentLength +
                               "' is invalid. Only values between 0 and " +
                               Integer.MAX_VALUE +
                               " are allowed.");
      }
      aBytePayload = new byte [(int) nContentLength];

      // Closes the original InputStream and that is okay
      try (final DataInputStream aDataIS = new DataInputStream (aIS))
      {
        aDataIS.readFully (aBytePayload);
      }
      aPayload = new ByteArrayDataSource (aBytePayload, sReceivedContentType, null);
    }

    // Dump on demand
    if (aIncomingDumper != null)
    {
      aIncomingDumper.dumpIncomingRequest (getAllHTTPHeaderLines (aRDP.getHttpHeaderMap ()),
                                           aBytePayload != null ? aBytePayload
                                                                : "Payload body was not read yet, and therefore it cannot be dumped (yet) - sorry".getBytes (StandardCharsets.ISO_8859_1),
                                           aMsg);
    }

    return aPayload;

    // Don't close the IS here!
  }

  @NonNull
  public static DataSource readAndDecodeHttpRequest (@NonNull final IAS2HttpRequestDataProvider aRDP,
                                                     @NonNull final IAS2HttpResponseHandler aResponseHandler,
                                                     @NonNull final IMessage aMsg,
                                                     @Nullable final IHTTPIncomingDumper aIncomingDumper) throws IOException,
                                                                                                          MessagingException
  {
    // Main read
    DataSource aPayload = HTTPHelper.readHttpRequest (aRDP, aResponseHandler, aMsg, aIncomingDumper);

    // Check the transfer encoding of the request. If none is provided, check
    // the partnership for a default one. If none is in the partnership used the
    // default one
    final String sCTE = aMsg.partnership ()
                            .getContentTransferEncodingReceive (EContentTransferEncoding.AS2_DEFAULT.getID ());
    final String sContentTransferEncoding = aMsg.getHeaderOrDefault (CHttpHeader.CONTENT_TRANSFER_ENCODING, sCTE);
    if (StringHelper.isNotEmpty (sContentTransferEncoding))
    {
      final IContentTransferEncoding aCTE = EContentTransferEncoding.getFromIDCaseInsensitiveOrNull (sContentTransferEncoding);
      if (aCTE == null)
      {
        LOGGER.warn ("Unsupported Content-Transfer-Encoding '" + sContentTransferEncoding + "' is used - ignoring!");
      }
      else
      {
        // Decode data if necessary
        final IByteArrayCodec aCodec = aCTE.createCodec ();

        // TODO: Handle decoding when large file support is on
        if (!(aCodec instanceof IdentityCodec <?>) && aPayload instanceof final ByteArrayDataSource aBADS)
        {
          byte [] aActualBytes = aBADS.directGetBytes ();
          // Remember original length before continuing
          final int nOriginalContentLength = aActualBytes.length;

          LOGGER.info ("Incoming message uses Content-Transfer-Encoding '" + sContentTransferEncoding + "' - decoding");
          aActualBytes = aCodec.getDecoded (aActualBytes);
          aPayload = new ByteArrayDataSource (aActualBytes, aPayload.getContentType (), aPayload.getName ());

          // Remember that we potentially did something
          aMsg.attrs ().putIn (MA_HTTP_ORIGINAL_CONTENT_TRANSFER_ENCODING, sContentTransferEncoding);
          aMsg.attrs ().putIn (MA_HTTP_ORIGINAL_CONTENT_LENGTH, nOriginalContentLength);
        }
      }
    }

    return aPayload;
  }

  /**
   * Send a simple HTTP response that only contains the HTTP status code and the respective
   * descriptive text. An empty header map us used.
   *
   * @param aResponseHandler
   *        The response handler to be used.
   * @param nResponseCode
   *        The HTTP response code to use.
   * @throws IOException
   *         In case sending fails for whatever reason
   */
  public static void sendSimpleHTTPResponse (@NonNull final IAS2HttpResponseHandler aResponseHandler,
                                             @Nonnegative final int nResponseCode) throws IOException
  {
    try (final NonBlockingByteArrayOutputStream aData = new NonBlockingByteArrayOutputStream ())
    {
      final String sHTTPLine = Integer.toString (nResponseCode) +
                               " " +
                               CHttp.getHttpResponseMessage (nResponseCode) +
                               CHttp.EOL;
      aData.write (sHTTPLine.getBytes (CHttp.HTTP_CHARSET));

      aResponseHandler.sendHttpResponse (nResponseCode, new HttpHeaderMap (), aData);
    }
  }

  @NonNull
  private static String _debugByte (final int n)
  {
    if (n >= 0x20 && n <= 0x7e)
      return "'" + Character.toString ((char) n) + "'";
    return "0x" + StringHex.getHexStringLeadingZero (n & 0xff, 2);
  }

  /**
   * Read chunk size (including the newline ending it). Discard any other data, e.g. headers that my
   * be there.
   *
   * @param aIS
   *        - input stream to read from
   * @return Chunk length. Should always be &ge; 0.
   * @throws IOException
   *         if stream ends during chunk length read
   */
  @Nonnegative
  public static int readChunkLen (@NonNull @WillNotClose final InputStream aIS) throws IOException
  {
    int nRes = 0;
    boolean bHeadersStarted = false;
    boolean bWarningEmitted = false;
    final int nMaxByteBuffer = 128;
    int nBytesRead = 0;
    try (final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream (nMaxByteBuffer))
    {
      for (;;)
      {
        int b = aIS.read ();
        if (b < 0)
        {
          // We reached the end of the input but got at least one char
          if (nBytesRead > 0)
            break;
          throw new EOFException ("EOF while reading HTTP chunk length");
        }

        nBytesRead++;
        // Remember what we read but avoid unnecessary memory consumption
        if (aBAOS.size () < nMaxByteBuffer)
          aBAOS.write (b & 0xff);

        if (b == '\n')
        {
          // We found the newline
          break;
        }

        if (b >= 'a' && b <= 'f')
          b -= ('a' - 10);
        else
          if (b >= 'A' && b <= 'F')
            b -= ('A' - 10);
          else
            if (b >= '0' && b <= '9')
              b -= '0';
            else
              if (b == ';')
              {
                // Afterwards, any char may appear until \n
                bHeadersStarted = true;
              }
              else
                if (b == '\r')
                {
                  // Ignore - comes before \n
                  continue;
                }
                else
                {
                  // Unsupported char
                  if (!bHeadersStarted)
                  {
                    if (!bWarningEmitted)
                    {
                      LOGGER.warn ("Found unsupported byte " +
                                   _debugByte (b) +
                                   " when trying to read HTTP chunk length." +
                                   " This will most likely lead to an error processing the incoming AS2 message.");
                      bWarningEmitted = true;
                    }
                  }
                  continue;
                }
        if (!bHeadersStarted)
          nRes = (nRes * 16) + b;
      }

      if (bWarningEmitted)
      {
        // Maximum per byte: ", 0xffff" - 8 chars
        final StringBuilder aSB = new StringBuilder (aBAOS.size () * 8);
        for (final byte b : aBAOS.toByteArray ())
        {
          if (aSB.length () > 0)
            aSB.append (", ");
          aSB.append (_debugByte (b));
        }
        final boolean bIsPartial = nBytesRead > aBAOS.size ();
        LOGGER.warn ("An incoming AS2 message with HTTP Chunked Encoding had issues reading the 'chunk length'." +
                     " A total of " +
                     nBytesRead +
                     " bytes were read, the determined chunk length is " +
                     nRes +
                     ". Processing of the message might fail. " +
                     (bIsPartial ? "Partial" : "Full") +
                     " debug info: " +
                     aSB.toString ());
      }
    }
    return nRes;
  }

  /**
   * Read up to (and including) CRLF.
   *
   * @param aIS
   *        input stream to read from
   * @throws IOException
   *         if stream ends during chunk length read
   */
  public static void readTillNextLine (@NonNull @WillNotClose final InputStream aIS) throws IOException
  {
    int nSkipped = 0;
    while (true)
    {
      final int ch = aIS.read ();
      if (ch < 0)
        throw new EOFException ("EOF while reading until next newline character");
      if (ch == '\n')
        break;
      nSkipped++;
    }
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Skipped " + nSkipped + " bytes until newline");
  }
}
