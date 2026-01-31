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
package com.helger.phase2.processor.resender;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.StringTokenizer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.string.StringHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.datetime.helper.PDTFactory;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.WrappedAS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.params.AS2InvalidParameterException;
import com.helger.phase2.params.CompositeParameters;
import com.helger.phase2.params.DateParameters;
import com.helger.phase2.processor.sender.IProcessorSenderModule;
import com.helger.phase2.session.IAS2Session;
import com.helger.phase2.util.AS2DateHelper;
import com.helger.phase2.util.AS2IOHelper;
import com.helger.typeconvert.collection.IStringMap;

/**
 * An asynchronous, persisting, file based, polling resender module. Upon
 * {@link #handle(String, IMessage, Map)} it writes the document into a file and there is a
 * background poller task that checks for resending (see {@link #resend()}). If re-sending fails,
 * the document is moved into an error folder.
 *
 * @author OpenAS2
 * @author Philip Helger
 */
public class DirectoryResenderModule extends AbstractActiveResenderModule
{
  public static final String ATTR_RESEND_DIRECTORY = "resenddir";
  /** The error directory. May contain "date" parameters. */
  public static final String ATTR_ERROR_DIRECTORY = "errordir";
  /**
   * Optional filename for storage in the error directory. May contain "date" parameters.
   *
   * @since 4.8.0
   */
  public static final String ATTR_STORED_ERROR_FILENAME = "stored_error_filename";

  private static final String FILENAME_DATE_FORMAT = "MM-dd-uu-HH-mm-ss";

  private static final Logger LOGGER = LoggerFactory.getLogger (DirectoryResenderModule.class);

  @Override
  public void initDynamicComponent (@NonNull final IAS2Session aSession, @Nullable final IStringMap aOptions)
                                                                                                              throws AS2Exception
  {
    super.initDynamicComponent (aSession, aOptions);
    getAttributeAsStringRequired (ATTR_RESEND_DIRECTORY);
    getAttributeAsStringRequired (ATTR_ERROR_DIRECTORY);
  }

  @Override
  public boolean canHandle (@NonNull final String sAction,
                            @NonNull final IMessage aMsg,
                            @Nullable final Map <String, Object> aOptions)
  {
    return sAction.equals (IProcessorResenderModule.DO_RESEND);
  }

  /**
   * Build the filename for re-sending. The filename consists of the date and time when the document
   * is to be re-send.
   *
   * @return The filename and never <code>null</code>.
   * @throws AS2InvalidParameterException
   *         Only theoretically
   */
  @NonNull
  protected String getFilename () throws AS2InvalidParameterException
  {
    final long nResendDelayMS = getResendDelayMS ();
    return AS2DateHelper.formatDate (FILENAME_DATE_FORMAT,
                                     PDTFactory.getCurrentZonedDateTime ().plus (nResendDelayMS, ChronoUnit.MILLIS));
  }

  @Override
  public void handle (@NonNull final String sAction,
                      @NonNull final IMessage aMsg,
                      @Nullable final Map <String, Object> aOptions) throws AS2Exception
  {
    try
    {
      final File aResendDir = AS2IOHelper.getDirectoryFile (getAttributeAsStringRequired (ATTR_RESEND_DIRECTORY));
      final File aResendFile = AS2IOHelper.getUniqueFile (aResendDir, getFilename ());
      try (final FileOutputStream aFOS = new FileOutputStream (aResendFile);
           final ObjectOutputStream aOOS = new ObjectOutputStream (aFOS))
      {
        String sResendAction = aOptions == null ? null : (String) aOptions.get (
                                                                                IProcessorResenderModule.OPTION_RESEND_ACTION);
        if (sResendAction == null)
        {
          LOGGER.warn ("The resending method is missing - default to message sending!");
          sResendAction = IProcessorSenderModule.DO_SEND;
        }

        String sRetries = aOptions == null ? null : (String) aOptions.get (IProcessorResenderModule.OPTION_RETRIES);
        if (sRetries == null)
        {
          LOGGER.warn ("The resending retry count is missing - default to " +
                       IProcessorResenderModule.DEFAULT_RETRIES +
                       "!");
          sRetries = Integer.toString (IProcessorResenderModule.DEFAULT_RETRIES);
        }

        aOOS.writeObject (sResendAction);
        aOOS.writeObject (sRetries);
        aOOS.writeObject (aMsg);
      }

      LOGGER.info ("Message put in resend queue" + aMsg.getLoggingText ());
    }
    catch (final IOException ioe)
    {
      throw WrappedAS2Exception.wrap (ioe);
    }
  }

  protected boolean isTimeToSend (@NonNull final File aCurrentFile)
  {
    try
    {
      final StringTokenizer aFileTokens = new StringTokenizer (aCurrentFile.getName (), ".", false);
      final LocalDateTime aTimestamp = AS2DateHelper.parseDate (FILENAME_DATE_FORMAT, aFileTokens.nextToken ());
      return aTimestamp.isBefore (PDTFactory.getCurrentLocalDateTime ());
    }
    catch (final Exception ex)
    {
      return true;
    }
  }

  protected void resendFile (@NonNull final File aFile) throws AS2Exception
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Processing " + aFile.getAbsolutePath ());

    final CompositeParameters aParams = new CompositeParameters (false).add ("date", new DateParameters ());
    IMessage aMsg = null;
    try
    {
      try
      {
        final String sResendAction;
        String sRetries;
        try (final FileInputStream aFIS = new FileInputStream (aFile);
             final ObjectInputStream aOIS = new ObjectInputStream (aFIS))
        {
          sResendAction = (String) aOIS.readObject ();
          sRetries = (String) aOIS.readObject ();
          aMsg = (IMessage) aOIS.readObject ();
        }

        // Decrement retries
        sRetries = Integer.toString (Integer.parseInt (sRetries) - 1);

        // Transmit the message
        LOGGER.info ("loaded message for resend." + aMsg.getLoggingText ());

        final ICommonsMap <String, Object> aOptions = new CommonsHashMap <> ();
        aOptions.put (IProcessorResenderModule.OPTION_RETRIES, sRetries);
        getSession ().getMessageProcessor ().handle (sResendAction, aMsg, aOptions);

        if (AS2IOHelper.getFileOperationManager ().deleteFile (aFile).isFailure ())
        {
          // Delete the file, sender will re-queue if the transmission fails
          // again
          throw new AS2Exception ("File was successfully sent but not deleted: " + aFile.getAbsolutePath ());
        }

        LOGGER.info ("deleted " + aFile.getAbsolutePath () + aMsg.getLoggingText ());
      }
      catch (final IOException | ClassNotFoundException ex)
      {
        // caught 3 lines below
        throw WrappedAS2Exception.wrap (ex);
      }
    }
    catch (final AS2Exception ex)
    {
      ex.terminate (aFile, aMsg);

      final String sErrorDirectory = aParams.format (getAttributeAsStringRequired (ATTR_ERROR_DIRECTORY));
      // Use the source name as the default
      final String sErrorFilename = StringHelper.getNotEmpty (aParams.format (attrs ().getAsString (ATTR_STORED_ERROR_FILENAME)),
                                                              aFile.getName ());
      AS2IOHelper.handleError (aFile, sErrorDirectory, sErrorFilename);
    }
  }

  /**
   * @return A list with all files that are ready to be resend.
   * @throws AS2InvalidParameterException
   *         In case the directory listing fails
   */
  @NonNull
  @ReturnsMutableCopy
  protected ICommonsList <File> scanDirectory () throws AS2InvalidParameterException
  {
    final File aResendDir = AS2IOHelper.getDirectoryFile (getAttributeAsStringRequired (ATTR_RESEND_DIRECTORY));

    final File [] aFiles = aResendDir.listFiles ();
    if (aFiles == null)
    {
      throw new AS2InvalidParameterException ("Error getting list of files in directory",
                                              this,
                                              ATTR_RESEND_DIRECTORY,
                                              aResendDir.getAbsolutePath ());
    }

    final ICommonsList <File> ret = new CommonsArrayList <> ();
    if (aFiles.length > 0)
      for (final File aCurrentFile : aFiles)
        if (aCurrentFile.exists () && aCurrentFile.isFile () && aCurrentFile.canWrite () && isTimeToSend (aCurrentFile))
          ret.add (aCurrentFile);
    return ret;
  }

  @Override
  public void resend ()
  {
    try
    {
      // get a list of files that need to be sent now
      final ICommonsList <File> aSendFiles = scanDirectory ();

      // iterator through and send each file
      for (final File aCurrentFile : aSendFiles)
        resendFile (aCurrentFile);
    }
    catch (final AS2Exception ex)
    {
      ex.terminate ();
      forceStop (ex);
    }
  }
}
