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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.style.ReturnsMutableObject;
import com.helger.base.string.StringHelper;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.http.CHttpHeader;
import com.helger.io.file.FileIOError;
import com.helger.io.file.FilenameHelper;
import com.helger.io.file.SimpleFileIO;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.mail.datasource.ByteArrayDataSource;
import com.helger.mime.CMimeType;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.WrappedAS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.params.AS2InvalidParameterException;
import com.helger.phase2.params.CompositeParameters;
import com.helger.phase2.params.DateParameters;
import com.helger.phase2.params.MessageParameters;
import com.helger.phase2.processor.CFileAttribute;
import com.helger.phase2.processor.sender.IProcessorSenderModule;
import com.helger.phase2.session.IAS2Session;
import com.helger.phase2.util.AS2IOHelper;
import com.helger.typeconvert.collection.IStringMap;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;

public abstract class AbstractDirectoryPollingModule extends AbstractActivePollingModule
{
  /** Path of the directory to poll from. */
  public static final String ATTR_OUTBOX_DIRECTORY = "outboxdir";
  /** Path of the error directory. May contain "date" and "msg" parameters. */
  public static final String ATTR_ERROR_DIRECTORY = "errordir";
  /**
   * Optional filename for storage in the error directory. May contain "date" and "msg" parameters.
   *
   * @since 4.8.0
   */
  public static final String ATTR_STORED_ERROR_FILENAME = "stored_error_filename";
  /** Path of the error directory. May contain "date" and "msg" parameters. */
  public static final String ATTR_SENT_DIRECTORY = "sentdir";
  /**
   * Optional filename for storage in the sent directory. May contain "date" and "msg" parameters.
   *
   * @since 4.8.0
   */
  public static final String ATTR_STORED_SENT_FILENAME = "stored_sent_filename";
  public static final String ATTR_FORMAT = "format";
  public static final String ATTR_DELIMITERS = "delimiters";
  public static final String ATTR_DEFAULTS = "defaults";
  public static final String ATTR_MIMETYPE = "mimetype";
  public static final String ATTR_SENDFILENAME = "sendfilename";

  private static final Logger LOGGER = LoggerFactory.getLogger (AbstractDirectoryPollingModule.class);

  private ICommonsMap <String, Long> m_aTrackedFiles;

  @Override
  public void initDynamicComponent (@NonNull final IAS2Session aSession, @Nullable final IStringMap aOptions)
                                                                                                              throws AS2Exception
  {
    super.initDynamicComponent (aSession, aOptions);
    getAttributeAsStringRequired (ATTR_OUTBOX_DIRECTORY);
    getAttributeAsStringRequired (ATTR_ERROR_DIRECTORY);
  }

  protected boolean checkFile (@NonNull final File aFile)
  {
    if (aFile.exists () && aFile.isFile ())
    {
      try (final FileOutputStream aFOS = new FileOutputStream (aFile, true))
      {
        // check for a write-lock on file, will skip file if it's write locked
        return true;
      }
      catch (final IOException ex)
      {
        // a sharing violation occurred, ignore the file for now
      }
    }
    return false;
  }

  /**
   * @return A map from absolute file path to the size of the file. Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableObject
  public final ICommonsMap <String, Long> trackedFiles ()
  {
    if (m_aTrackedFiles == null)
      m_aTrackedFiles = new CommonsHashMap <> ();
    return m_aTrackedFiles;
  }

  protected void trackFile (@NonNull final File aFile)
  {
    final ICommonsMap <String, Long> aTrackedFiles = trackedFiles ();
    aTrackedFiles.computeIfAbsent (aFile.getAbsolutePath (), k -> Long.valueOf (aFile.length ()));
  }

  protected void scanDirectory (final String sDirectory) throws AS2InvalidParameterException
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Polling - scanning directory '" + sDirectory + "'");

    final File aDir = AS2IOHelper.getDirectoryFile (sDirectory);

    // get a list of entries in the directory
    final File [] aFiles = aDir.listFiles ();
    if (aFiles == null)
    {
      throw new AS2InvalidParameterException ("Error getting list of files in directory",
                                              this,
                                              ATTR_OUTBOX_DIRECTORY,
                                              aDir.getAbsolutePath ());
    }

    // iterator through each entry, and start tracking new files
    if (aFiles.length > 0)
      for (final File aCurrentFile : aFiles)
        if (checkFile (aCurrentFile))
        {
          // start watching the file's size if it's not already being watched
          trackFile (aCurrentFile);
        }
  }

  @NonNull
  protected abstract IMessage createMessage ();

  protected void processFile (@NonNull final File aFile) throws AS2Exception
  {
    LOGGER.info ("processing " + aFile.getAbsolutePath ());

    final IMessage aMsg = createMessage ();
    aMsg.attrs ().putIn (CFileAttribute.MA_FILEPATH, aFile.getAbsolutePath ());
    aMsg.attrs ().putIn (CFileAttribute.MA_FILENAME, aFile.getName ());

    /*
     * asynch mdn logic 2007-03-12 save the file name into message object, it will be stored into
     * pending information file
     */
    aMsg.attrs ().putIn (CFileAttribute.MA_PENDING_FILENAME, aFile.getName ());

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("AS2Message was created");

    final CompositeParameters aParams = new CompositeParameters (false).add ("date", new DateParameters ())
                                                                       .add ("msg", new MessageParameters (aMsg));

    try
    {
      updateMessage (aMsg, aFile);
      LOGGER.info ("file assigned to message " + aFile.getAbsolutePath () + aMsg.getLoggingText ());

      if (aMsg.getData () == null)
        throw new AS2InvalidMessageException ("No Data");

      // Transmit the message - requires a module installed that implements the
      // "send" action (like com.helger.phase2.processor.sender.AS2SenderModule)
      getSession ().getMessageProcessor ().handle (IProcessorSenderModule.DO_SEND, aMsg, null);

      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("AS2Message was successfully handled my the MessageProcessor");

      /*
       * asynch mdn logic 2007-03-12 If the return status is pending in msg's attribute "status"
       * then copy the transmitted file to pending folder and wait for the receiver to make another
       * HTTP call to post AsyncMDN
       */
      if (CFileAttribute.MA_STATUS_PENDING.equals (aMsg.attrs ().getAsString (CFileAttribute.MA_STATUS)))
      {
        // Copy the file to the pending folder
        final String sFolderName = AS2IOHelper.getSafeFileAndFolderName (aMsg.partnership ()
                                                                             .getAttribute (CFileAttribute.MA_STATUS_PENDING));
        final String sFilename = FilenameHelper.getAsSecureValidASCIIFilename (aMsg.attrs ()
                                                                                   .getAsString (CFileAttribute.MA_PENDING_FILENAME));
        final File aPendingFile = new File (sFolderName, sFilename);
        final FileIOError aIOErr = AS2IOHelper.getFileOperationManager ().copyFile (aFile, aPendingFile);
        if (aIOErr.isFailure ())
          throw new AS2Exception ("File was successfully sent but not copied to pending folder: " +
                                  aPendingFile +
                                  " - " +
                                  aIOErr.toString ());

        LOGGER.info ("Copied '" +
                     aFile.getAbsolutePath () +
                     "' to pending folder '" +
                     aPendingFile.getAbsolutePath () +
                     "'" +
                     aMsg.getLoggingText ());
      }

      if (attrs ().containsKey (ATTR_SENT_DIRECTORY))
      {
        // If the Sent Directory option is set, move the transmitted file to
        // the sent directory
        File aSentFile = null;
        try
        {
          final String sSentDirectory = AS2IOHelper.getSafeFileAndFolderName (aParams.format (attrs ().getAsString (ATTR_SENT_DIRECTORY)));
          // Default to the original filename
          final String sSentFilename = StringHelper.getNotEmpty (FilenameHelper.getAsSecureValidASCIIFilename (aParams.format (attrs ().getAsString (ATTR_STORED_SENT_FILENAME))),
                                                                 aFile.getName ());
          aSentFile = new File (AS2IOHelper.getDirectoryFile (sSentDirectory), sSentFilename);
          aSentFile = AS2IOHelper.moveFile (aFile, aSentFile, false, true);

          LOGGER.info ("Moved '" +
                       aFile.getAbsolutePath () +
                       "' to '" +
                       aSentFile.getAbsolutePath () +
                       "'" +
                       aMsg.getLoggingText ());
        }
        catch (final IOException ex)
        {
          new AS2Exception ("File was successfully sent but not moved to sent folder: '" +
                            aSentFile.getAbsolutePath () +
                            "'",
                            ex).terminate ();
        }
      }
      else
      {
        // The "Sent Directory" option was not set - so delete the file
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Trying to delete file " + aFile.getAbsolutePath ());

        if (AS2IOHelper.getFileOperationManager ().deleteFileIfExisting (aFile).isFailure ())
        {
          // Delete the file if a sent directory isn't set
          throw new AS2Exception ("File was successfully sent but not deleted: '" + aFile.getAbsolutePath () + "'");
        }
        LOGGER.info ("Deleted file '" + aFile.getAbsolutePath () + "'" + aMsg.getLoggingText ());
      }
    }
    catch (final AS2Exception ex)
    {
      ex.terminate (aFile, aMsg);
      final String sErrorDirectory = AS2IOHelper.getSafeFileAndFolderName (aParams.format (getAttributeAsStringRequired (ATTR_ERROR_DIRECTORY)));
      // Use the source name as the default
      final String sErrorFilename = StringHelper.getNotEmpty (FilenameHelper.getAsSecureValidASCIIFilename (aParams.format (attrs ().getAsString (ATTR_STORED_ERROR_FILENAME))),
                                                              aFile.getName ());
      AS2IOHelper.handleError (aFile, sErrorDirectory, sErrorFilename);
    }
  }

  protected void updateTracking () throws AS2Exception
  {
    // clone the trackedFiles map, iterator through the clone and modify the
    // original to avoid iterator exceptions
    // is there a better way to do this?
    final ICommonsMap <String, Long> aTrackedFiles = trackedFiles ();

    // We need to operate on a copy
    for (final Map.Entry <String, Long> aFileEntry : aTrackedFiles.getClone ().entrySet ())
    {
      // get the file and it's stored length
      final File aFile = new File (aFileEntry.getKey ());
      final long nFileLength = aFileEntry.getValue ().longValue ();

      if (!checkFile (aFile))
      {
        // if the file no longer exists, remove it from the tracker
        aTrackedFiles.remove (aFileEntry.getKey ());
      }
      else
      {
        // if the file length has changed, update the tracker
        final long nNewLength = aFile.length ();
        if (nNewLength != nFileLength)
        {
          aTrackedFiles.put (aFileEntry.getKey (), Long.valueOf (nNewLength));
        }
        else
        {
          // if the file length has stayed the same, process the file and stop
          // tracking it
          try
          {
            processFile (aFile);
          }
          finally
          {
            aTrackedFiles.remove (aFileEntry.getKey ());
          }
        }
      }
    }
  }

  public void updateMessage (@NonNull final IMessage aMsg, @NonNull final File aFile) throws AS2Exception
  {
    final MessageParameters aParams = new MessageParameters (aMsg);

    final String sDefaults = attrs ().getAsString (ATTR_DEFAULTS);
    if (sDefaults != null)
      aParams.setParameters (sDefaults);

    final String sFilename = aFile.getName ();
    final String sFormat = attrs ().getAsString (ATTR_FORMAT);
    if (sFormat != null)
    {
      final String sDelimiters = attrs ().getAsString (ATTR_DELIMITERS, ".-");
      aParams.setParameters (sFormat, sDelimiters, sFilename);
    }

    try
    {
      final byte [] aData = SimpleFileIO.getAllFileBytes (aFile);
      String sContentType = attrs ().getAsString (ATTR_MIMETYPE);
      if (sContentType == null)
      {
        // Default to application/octet-stream
        sContentType = CMimeType.APPLICATION_OCTET_STREAM.getAsString ();
      }
      else
      {
        try
        {
          sContentType = aParams.format (sContentType);
        }
        catch (final AS2InvalidParameterException ex)
        {
          LOGGER.error ("Bad content-type '" + sContentType + "'" + aMsg.getLoggingText ());
          // Default to application/octet-stream
          sContentType = CMimeType.APPLICATION_OCTET_STREAM.getAsString ();
        }
      }

      final ByteArrayDataSource aByteSource = new ByteArrayDataSource (aData, sContentType, null);
      final MimeBodyPart aBody = new MimeBodyPart ();
      aBody.setDataHandler (aByteSource.getAsDataHandler ());

      // Headers must be set AFTER the DataHandler
      final String sCTE = aMsg.partnership ()
                              .getContentTransferEncodingSend (EContentTransferEncoding.AS2_DEFAULT.getID ());
      aBody.setHeader (CHttpHeader.CONTENT_TRANSFER_ENCODING, sCTE);

      // below statement is not filename related, just want to make it
      // consist with the parameter "mimetype="application/EDI-X12""
      // defined in config.xml 2007-06-01
      aBody.setHeader (CHttpHeader.CONTENT_TYPE, sContentType);

      // add below statement will tell the receiver to save the filename
      // as the one sent by sender. 2007-06-01
      final String sSendFilename = attrs ().getAsString (ATTR_SENDFILENAME);
      if ("true".equals (sSendFilename))
      {
        final String sMAFilename = aMsg.attrs ().getAsString (CFileAttribute.MA_FILENAME);
        final String sContentDisposition = "Attachment; filename=\"" + sMAFilename + "\"";
        aBody.setHeader (CHttpHeader.CONTENT_DISPOSITION, sContentDisposition);
        aMsg.setContentDisposition (sContentDisposition);
      }

      aMsg.setData (aBody);
    }
    catch (final MessagingException ex)
    {
      throw WrappedAS2Exception.wrap (ex);
    }

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Updating partnership for AS2 message" + aMsg.getLoggingText ());

    // update the message's partnership with any stored information
    getSession ().getPartnershipFactory ().updatePartnership (aMsg, true);

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Finished updating partnership for AS2 message");

    aMsg.updateMessageID ();

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Updated message ID to " + aMsg.getMessageID ());
  }

  @Override
  public void poll ()
  {
    try
    {
      // scan the directory for new files
      scanDirectory (getAttributeAsStringRequired (ATTR_OUTBOX_DIRECTORY));

      // update tracking info. if a file is ready, process it
      updateTracking ();
    }
    catch (final Exception ex)
    {
      WrappedAS2Exception.wrap (ex).terminate ();
      forceStop (ex);
    }
  }
}
