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
package com.helger.phase2.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.angus.mail.util.QPDecoderStream;
import org.eclipse.angus.mail.util.QPEncoderStream;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.CGlobal;
import com.helger.base.codec.base64.Base64;
import com.helger.base.codec.base64.Base64InputStream;
import com.helger.base.codec.base64.Base64OutputStream;
import com.helger.base.string.StringHelper;
import com.helger.base.string.StringRemove;
import com.helger.base.timing.StopWatch;
import com.helger.http.CHttp;
import com.helger.io.file.FileIOError;
import com.helger.io.file.FileOperationManager;
import com.helger.io.file.FilenameHelper;
import com.helger.io.file.LoggingFileOperationCallback;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.processor.receiver.AS2InvalidMessageException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeUtility;

@Immutable
public final class AS2IOHelper
{
  private static final byte [] EOL_BYTES = getAllAsciiBytes (CHttp.EOL);

  // Use a new instance to add the logging
  private static final FileOperationManager FOM = new FileOperationManager ();

  static
  {
    FOM.callbacks ().add (new LoggingFileOperationCallback ());
  }

  private AS2IOHelper ()
  {}

  @NonNull
  public static FileOperationManager getFileOperationManager ()
  {
    return FOM;
  }

  @NonNull
  public static File getDirectoryFile (@NonNull final String sDirectory)
  {
    final File aDir = new File (sDirectory);
    FOM.createDirRecursiveIfNotExisting (aDir);
    return aDir;
  }

  @NonNull
  @Nonempty
  public static String getTransferRate (final long nBytes, @NonNull final StopWatch aSW)
  {
    final StringBuilder aSB = new StringBuilder ();
    aSB.append (nBytes).append (" bytes in ").append (aSW.getMillis () / 1000.0).append (" seconds at ");

    final long nMillis = aSW.getMillis ();
    if (nMillis != 0)
    {
      final double dSeconds = nMillis / 1000.0;
      final long nBytesPerSecond = Math.round (nBytes / dSeconds);
      aSB.append (_getTransferRate (nBytesPerSecond));
    }
    else
    {
      aSB.append (_getTransferRate (nBytes));
    }

    return aSB.toString ();
  }

  @NonNull
  @Nonempty
  private static String _getTransferRate (final long nBytesPerSecond)
  {
    final StringBuilder aSB = new StringBuilder ();
    if (nBytesPerSecond < CGlobal.BYTES_PER_KILOBYTE)
    {
      // < 1024
      aSB.append (nBytesPerSecond).append (" Bps");
    }
    else
    {
      final long nKBytesPerSecond = nBytesPerSecond / CGlobal.BYTES_PER_KILOBYTE;
      if (nKBytesPerSecond < CGlobal.BYTES_PER_KILOBYTE)
      {
        // < 1048576
        aSB.append (nKBytesPerSecond)
           .append ('.')
           .append (nBytesPerSecond % CGlobal.BYTES_PER_KILOBYTE)
           .append (" KBps");
      }
      else
      {
        // >= 1048576
        aSB.append (nKBytesPerSecond / CGlobal.BYTES_PER_KILOBYTE)
           .append ('.')
           .append (nKBytesPerSecond % CGlobal.BYTES_PER_KILOBYTE)
           .append (" MBps");
      }
    }
    return aSB.toString ();
  }

  @NonNull
  public static File getUniqueFile (@NonNull final File aDir, @Nullable final String sFilename)
  {
    final String sBaseFilename = FilenameHelper.getAsSecureValidFilename (sFilename);
    int nCounter = -1;
    while (true)
    {
      final File aTest = new File (aDir,
                                   nCounter == -1 ? sBaseFilename : sBaseFilename + "." + Integer.toString (nCounter));
      if (!aTest.exists ())
        return aTest;

      nCounter++;
    }
  }

  /**
   * move the file to an error directory
   *
   * @param aFile
   *        Source file to move
   * @param sErrorDirectory
   *        Error directory path.
   * @param sTargetFilename
   *        The filename in the error directory.
   * @throws AS2Exception
   *         In case moving failed
   */
  public static void handleError (@NonNull final File aFile,
                                  @NonNull final String sErrorDirectory,
                                  @NonNull @Nonempty final String sTargetFilename) throws AS2Exception
  {
    File aDestFile = null;

    try
    {
      final File aErrorDir = getDirectoryFile (sErrorDirectory);
      aDestFile = new File (aErrorDir, sTargetFilename);

      // move the file
      aDestFile = moveFile (aFile, aDestFile, false, true);
    }
    catch (final IOException ex)
    {
      final AS2InvalidMessageException im = new AS2InvalidMessageException ("Failed to move " +
                                                                            aFile.getAbsolutePath () +
                                                                            " to error directory " +
                                                                            aDestFile.getAbsolutePath ());
      im.initCause (ex);
      throw im;
    }

    // make sure an error of this event is logged
    final AS2InvalidMessageException ex = new AS2InvalidMessageException ("Moved " +
                                                                          aFile.getAbsolutePath () +
                                                                          " to " +
                                                                          aDestFile.getAbsolutePath ());
    ex.terminate ();
  }

  @NonNull
  public static File moveFile (@NonNull final File aSrc,
                               @NonNull final File aDestFile,
                               final boolean bOverwrite,
                               final boolean bRename) throws IOException
  {
    File aRealDestFile = aDestFile;
    if (!bOverwrite && aRealDestFile.exists ())
    {
      if (!bRename)
        throw new IOException ("File already exists: " + aRealDestFile);
      aRealDestFile = getUniqueFile (aRealDestFile.getAbsoluteFile ().getParentFile (), aRealDestFile.getName ());
    }

    // Copy
    FileIOError aIOErr = FOM.copyFile (aSrc, aRealDestFile);
    if (aIOErr.isFailure ())
      throw new IOException ("Copy failed: " + aIOErr.toString ());

    // Delete old
    aIOErr = FOM.deleteFile (aSrc);
    if (aIOErr.isFailure ())
    {
      FOM.deleteFile (aRealDestFile);
      throw new IOException ("Move failed, unable to delete " + aSrc + ": " + aIOErr.toString ());
    }
    return aRealDestFile;
  }

  @Nullable
  public static String getFilenameFromMessageID (@NonNull final String sMessageID)
  {
    // Remove angle brackets manually
    String s = StringRemove.removeAll (sMessageID, '<');
    s = StringRemove.removeAll (s, '>');
    return FilenameHelper.getAsSecureValidASCIIFilename (s);
  }

  @Nullable
  public static String getSafeFileAndFolderName (@Nullable final String s)
  {
    if (StringHelper.isEmpty (s))
      return s;

    final File aBase = new File (FilenameHelper.getPathUsingUnixSeparator (s));

    final StringBuilder aSB = new StringBuilder ();
    File f = aBase;
    while (f != null)
    {
      final String sName = f.getName ();
      if (sName.length () == 0)
      {
        // drive letter on Windows
        aSB.insert (0, FilenameHelper.getPathUsingUnixSeparator (f.getPath ()));
      }
      else
      {
        // Any path component
        final String sSecuredName = FilenameHelper.getAsSecureValidASCIIFilename (sName);
        aSB.insert (0, StringHelper.getNotNull (sSecuredName, "") + FilenameHelper.UNIX_SEPARATOR);
      }
      f = f.getParentFile ();
    }
    // Cut the last separator
    return aSB.deleteCharAt (aSB.length () - 1).toString ();
  }

  @NonNull
  @ReturnsMutableCopy
  public static byte [] getAllAsciiBytes (@NonNull final String sString)
  {
    final char [] aChars = sString.toCharArray ();
    final int nLength = aChars.length;
    final byte [] ret = new byte [nLength];
    for (int i = 0; i < nLength; i++)
      ret[i] = (byte) aChars[i];
    return ret;
  }

  @NonNull
  public static OutputStream getContentTransferEncodingAwareOutputStream (@NonNull final OutputStream aOS,
                                                                          @Nullable final String sEncoding) throws MessagingException
  {
    if (false)
    {
      // Original code
      // The problem with this Base64Encoder, is the trailing "\r\n"
      return MimeUtility.encode (aOS, sEncoding);
    }

    if (sEncoding == null)
    {
      // Return as-is
      return aOS;
    }

    if (sEncoding.equalsIgnoreCase ("base64"))
    {
      // Use this Base64 OS
      final Base64OutputStream ret = new Base64OutputStream (aOS, Base64.ENCODE | Base64.DO_BREAK_LINES);
      // Important, use "\r\n" instead of "\n"
      ret.setNewLineBytes (EOL_BYTES);
      return ret;
    }

    if (sEncoding.equalsIgnoreCase ("quoted-printable"))
      return new QPEncoderStream (aOS);

    if (sEncoding.equalsIgnoreCase ("binary") ||
        sEncoding.equalsIgnoreCase ("7bit") ||
        sEncoding.equalsIgnoreCase ("8bit"))
    {
      // Return as-is
      return aOS;
    }

    throw new MessagingException ("Unknown Content-Transfer-Encoding '" + sEncoding + "'");
  }

  @NonNull
  public static InputStream getContentTransferEncodingAwareInputStream (@NonNull final InputStream aIS,
                                                                        @Nullable final String sEncoding) throws MessagingException
  {
    if (false)
    {
      // Original code
      return MimeUtility.decode (aIS, sEncoding);
    }

    if (sEncoding == null)
    {
      // Return as-is
      return aIS;
    }

    if (sEncoding.equalsIgnoreCase ("base64"))
      return new Base64InputStream (aIS);

    if (sEncoding.equalsIgnoreCase ("quoted-printable"))
      return new QPDecoderStream (aIS);

    if (sEncoding.equalsIgnoreCase ("binary") ||
        sEncoding.equalsIgnoreCase ("7bit") ||
        sEncoding.equalsIgnoreCase ("8bit"))
    {
      // Return as-is
      return aIS;
    }

    throw new MessagingException ("Unknown Content-Transfer-Encoding '" + sEncoding + "'");
  }
}
