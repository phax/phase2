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
package com.helger.phase2.processor.receiver;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.WillClose;
import com.helger.annotation.style.OverrideOnDemand;
import com.helger.base.concurrent.BasicThreadFactory;
import com.helger.base.io.stream.StreamHelper;
import com.helger.base.lang.clazz.ClassHelper;
import com.helger.base.string.StringHelper;
import com.helger.io.file.FileHelper;
import com.helger.io.file.FilenameHelper;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.WrappedAS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.params.CompositeParameters;
import com.helger.phase2.params.DateParameters;
import com.helger.phase2.params.MessageParameters;
import com.helger.phase2.partner.CPartnershipIDs;
import com.helger.phase2.processor.CNetAttribute;
import com.helger.phase2.processor.receiver.net.INetModuleHandler;
import com.helger.phase2.util.AS2IOHelper;

public abstract class AbstractActiveNetModule extends AbstractActiveReceiverModule
{
  public static final String ATTR_ADDRESS = "address";
  public static final String ATTR_PORT = "port";
  public static final String ATTR_ERROR_DIRECTORY = "errordir";
  public static final String ATTR_ERROR_FORMAT = "errorformat";
  public static final String DEFAULT_ERROR_FORMAT = "$date.uuuuMMddhhmmss$";
  // Since 3.0.4
  public static final String ATTR_ERROR_STORE_BODY = "errorstorebody";
  /** Attribute name for quoting header values (boolean) */
  public static final String ATTR_QUOTE_HEADER_VALUES = "quoteheadervalues";
  /** Default quote header values: false */
  public static final boolean DEFAULT_QUOTE_HEADER_VALUES = false;

  // Macros for responses
  public static final String MSG_SENDER = "$" + MessageParameters.KEY_SENDER + "." + CPartnershipIDs.PID_AS2 + "$";
  public static final String MSG_RECEIVER = "$" + MessageParameters.KEY_RECEIVER + "." + CPartnershipIDs.PID_AS2 + "$";
  public static final String MSG_DATE = "$" + MessageParameters.KEY_HEADERS + ".date" + "$";
  public static final String MSG_SUBJECT = "$" + MessageParameters.KEY_HEADERS + ".subject" + "$";
  public static final String MSG_SOURCE_ADDRESS = "$" +
                                                  MessageParameters.KEY_ATTRIBUTES +
                                                  "." +
                                                  CNetAttribute.MA_SOURCE_IP +
                                                  "$";
  public static final String DP_HEADER = "The message sent to Recipient " +
                                         MSG_RECEIVER +
                                         " on " +
                                         MSG_DATE +
                                         " with Subject " +
                                         MSG_SUBJECT +
                                         " has been received, ";
  // Note: it should read "its integrity", not "it's integrity"!
  public static final String DP_DECRYPTED = DP_HEADER +
                                            "the EDI Interchange was successfully decrypted and it's integrity was verified. ";
  public static final String DP_VERIFIED = DP_DECRYPTED +
                                           "In addition, the sender of the message, Sender " +
                                           MSG_SENDER +
                                           " at Location " +
                                           MSG_SOURCE_ADDRESS +
                                           " was authenticated as the originator of the message. ";

  // Response texts
  public static final String DISP_PARTNERSHIP_NOT_FOUND = DP_HEADER +
                                                          "but the Sender " +
                                                          MSG_SENDER +
                                                          " and/or Recipient " +
                                                          MSG_RECEIVER +
                                                          " are unknown.";
  public static final String DISP_PARSING_MIME_FAILED = DP_HEADER +
                                                        "but an error occured while parsing the MIME content.";
  public static final String DISP_DECRYPTION_ERROR = DP_HEADER + "but an error occured decrypting the content.";
  public static final String DISP_DECOMPRESSION_ERROR = DP_HEADER + "but an error occured decompressing the content.";
  public static final String DISP_VERIFY_SIGNATURE_FAILED = DP_DECRYPTED +
                                                            "Authentication of the originator of the message failed.";
  public static final String DISP_VALIDATION_FAILED = DP_VERIFIED +
                                                      "An error occured while validating the received data.";
  public static final String DISP_STORAGE_FAILED = DP_VERIFIED +
                                                   "An error occured while storing the data to the file system.";
  public static final String DISP_SUCCESS = DP_VERIFIED +
                                            "There is no guarantee however that the EDI Interchange was syntactically correct, or was received by the EDI application/translator.";

  private static final Logger LOGGER = LoggerFactory.getLogger (AbstractActiveNetModule.class);

  private MainThread m_aMainThread;

  public AbstractActiveNetModule ()
  {}

  @Nullable
  public final String getAddress ()
  {
    return attrs ().getAsString (ATTR_ADDRESS);
  }

  public final void setAddress (@Nullable final String sAddress)
  {
    if (sAddress == null)
      attrs ().remove (ATTR_ADDRESS);
    else
      attrs ().putIn (ATTR_ADDRESS, sAddress);
  }

  public final int getPort ()
  {
    return attrs ().getAsInt (ATTR_PORT, 0);
  }

  public final void setPort (final int nPort)
  {
    if (nPort < 0)
      attrs ().remove (ATTR_PORT);
    else
      attrs ().putIn (ATTR_PORT, nPort);
  }

  @Nullable
  public final String getErrorDirectory ()
  {
    return attrs ().getAsString (ATTR_ERROR_DIRECTORY);
  }

  public final void setErrorDirectory (@Nullable final String sErrorDirectory)
  {
    if (sErrorDirectory == null)
      attrs ().remove (ATTR_ERROR_DIRECTORY);
    else
      attrs ().putIn (ATTR_ERROR_DIRECTORY, sErrorDirectory);
  }

  @NonNull
  public final String getErrorFormat ()
  {
    return attrs ().getAsString (ATTR_ERROR_FORMAT, DEFAULT_ERROR_FORMAT);
  }

  public final void setErrorFormat (@Nullable final String sErrorFormat)
  {
    if (sErrorFormat == null)
      attrs ().remove (ATTR_ERROR_FORMAT);
    else
      attrs ().putIn (ATTR_ERROR_FORMAT, sErrorFormat);
  }

  public final boolean isErrorStoreBody ()
  {
    return attrs ().getAsBoolean (ATTR_ERROR_STORE_BODY, false);
  }

  public final void setErrorStoreBody (final boolean bErrorStoreBody)
  {
    attrs ().putIn (ATTR_ERROR_STORE_BODY, bErrorStoreBody);
  }

  public final boolean isQuoteHeaderValues ()
  {
    return attrs ().getAsBoolean (ATTR_QUOTE_HEADER_VALUES, DEFAULT_QUOTE_HEADER_VALUES);
  }

  public final void setQuoteHeaderValues (final boolean bQuoteHeaderValues)
  {
    attrs ().putIn (ATTR_QUOTE_HEADER_VALUES, bQuoteHeaderValues);
  }

  @Override
  public void doStart () throws AS2Exception
  {
    try
    {
      final String sAddress = getAddress ();
      final int nPort = getPort ();
      m_aMainThread = new MainThread (this, sAddress, nPort);
      m_aMainThread.setUncaughtExceptionHandler (BasicThreadFactory.getDefaultUncaughtExceptionHandler ());
      m_aMainThread.start ();
    }
    catch (final IOException ioe)
    {
      throw WrappedAS2Exception.wrap (ioe);
    }
  }

  @Override
  public void doStop () throws AS2Exception
  {
    if (m_aMainThread != null)
    {
      m_aMainThread.terminate ();
      m_aMainThread = null;
    }
  }

  @NonNull
  public abstract INetModuleHandler createHandler ();

  public void handleError (@NonNull final IMessage aMsg, @NonNull final AS2Exception aSrcEx)
  {
    if (LOGGER.isTraceEnabled ())
      LOGGER.trace ("Handling error in " +
                    ClassHelper.getClassLocalName (this.getClass ()) +
                    " for message with ID " +
                    aMsg.getMessageID () +
                    " and exception " +
                    ClassHelper.getClassLocalName (aSrcEx.getClass ()) +
                    " with error " +
                    aSrcEx.getMessage ());

    aSrcEx.terminate (aMsg);

    try
    {
      final CompositeParameters aParams = new CompositeParameters (false).add ("date", new DateParameters ())
                                                                         .add ("msg", new MessageParameters (aMsg));

      final String sErrorFilename = aParams.format (getErrorFormat ());
      final String sErrorDirectory = aParams.format (getErrorDirectory ());
      if (StringHelper.isNotEmpty (sErrorDirectory))
      {
        final File aMsgErrorFile = AS2IOHelper.getUniqueFile (AS2IOHelper.getDirectoryFile (sErrorDirectory),
                                                              FilenameHelper.getAsSecureValidFilename (sErrorFilename));
        // Default false for backwards compatibility reason
        final boolean bStoreBody = isErrorStoreBody ();

        try (final OutputStream aFOS = FileHelper.getOutputStream (aMsgErrorFile))
        {
          final String sMsgText = aMsg.getAsString ();
          aFOS.write (sMsgText.getBytes ());

          // Also store the body of the source message?
          if (bStoreBody)
            StreamHelper.copyInputStreamToOutputStream (aMsg.getData ().getInputStream (), aFOS);
        }

        // make sure an error of this event is logged
        new AS2InvalidMessageException ("Stored invalid message to " + aMsgErrorFile.getAbsolutePath ()).terminate ();
      }
      else
      {
        LOGGER.warn ("No error directory present, so ignoring the error");
      }
    }
    catch (final Exception ex)
    {
      WrappedAS2Exception.wrap (ex).terminate (aMsg);
    }
  }

  @Override
  public boolean equals (final Object o)
  {
    // New member, no change
    return super.equals (o);
  }

  @Override
  public int hashCode ()
  {
    // New member, no change
    return super.hashCode ();
  }

  protected static final class ConnectionThread extends Thread
  {
    private final AbstractActiveNetModule m_aOwner;
    private final Socket m_aSocket;

    public ConnectionThread (@NonNull final AbstractActiveNetModule aOwner, @NonNull @WillClose final Socket aSocket)
    {
      super ("AS2ConnectionThread-" + ClassHelper.getClassLocalName (aOwner));
      m_aOwner = aOwner;
      m_aSocket = aSocket;
    }

    @Override
    public void run ()
    {
      LOGGER.info ("AS2ConnectionThread: run");

      m_aOwner.createHandler ().handle (m_aOwner, m_aSocket);

      try
      {
        m_aSocket.close ();
      }
      catch (final IOException ex)
      {
        WrappedAS2Exception.wrap (ex).terminate ();
      }
      finally
      {
        LOGGER.info ("AS2ConnectionThread: done running");
      }
    }
  }

  protected static class MainThread extends Thread
  {
    private final AbstractActiveNetModule m_aOwner;
    private final ServerSocket m_aServerSocket;
    private volatile boolean m_bTerminated;

    public MainThread (@NonNull final AbstractActiveNetModule aOwner,
                       @Nullable final String sAddress,
                       @Nonnegative final int nPort) throws IOException
    {
      super ("AS2MainThread-" + ClassHelper.getClassLocalName (aOwner));
      m_aOwner = aOwner;
      m_aServerSocket = new ServerSocket ();
      final InetSocketAddress aAddr = sAddress == null ? new InetSocketAddress (nPort) : new InetSocketAddress (
                                                                                                                sAddress,
                                                                                                                nPort);
      m_aServerSocket.bind (aAddr);

      LOGGER.info ("Inited " + getName () + " at " + aAddr);
    }

    @OverrideOnDemand
    protected Socket createAcceptSocket () throws IOException
    {
      final Socket aSocket = m_aServerSocket.accept ();
      aSocket.setSoLinger (true, 60);
      return aSocket;
    }

    @Override
    public void run ()
    {
      LOGGER.info ("AS2MainThread: run");
      while (!m_bTerminated && !isInterrupted ())
      {
        try
        {
          final Socket aSocket = createAcceptSocket ();
          new ConnectionThread (m_aOwner, aSocket).start ();
        }
        catch (final Exception ex)
        {
          if (!m_bTerminated)
            m_aOwner.forceStop (ex);
        }
      }

      LOGGER.info ("AS2MainThread: done running");
    }

    public void terminate ()
    {
      if (!m_bTerminated)
      {
        m_bTerminated = true;
        if (m_aServerSocket != null)
          try
          {
            m_aServerSocket.close ();
          }
          catch (final IOException ex)
          {
            m_aOwner.forceStop (ex);
          }
      }
    }
  }
}
