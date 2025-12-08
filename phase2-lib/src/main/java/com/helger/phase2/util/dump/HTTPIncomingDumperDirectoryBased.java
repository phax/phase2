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
package com.helger.phase2.util.dump;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.http.CHttp;
import com.helger.io.file.FileHelper;
import com.helger.phase2.message.IBaseMessage;

/**
 * Directory based incoming HTTP dumper.
 *
 * @author Philip Helger
 * @since 3.0.1
 */
public class HTTPIncomingDumperDirectoryBased implements IHTTPIncomingDumper
{
  private static final Logger LOGGER = LoggerFactory.getLogger (HTTPIncomingDumperDirectoryBased.class);

  private final File m_aDumpDirectory;

  public HTTPIncomingDumperDirectoryBased (@NonNull final File aDumpDirectory)
  {
    ValueEnforcer.notNull (aDumpDirectory, "DumpDirectory");
    ValueEnforcer.isTrue (FileHelper.existsDir (aDumpDirectory),
                          () -> "DumpDirectory " + aDumpDirectory + " does not exist!");
    m_aDumpDirectory = aDumpDirectory;
  }

  @NonNull
  public File getDumpDirectory ()
  {
    return m_aDumpDirectory;
  }

  /**
   * The filename to be used to store the request in the folder provided in the constructor.
   *
   * @param nIndex
   *        Unique index to avoid duplicate filenames
   * @return The local filename without any path
   */
  @NonNull
  protected String getStoreFilename (final int nIndex)
  {
    return "as2-incoming-" + Long.toString (System.currentTimeMillis ()) + "-" + nIndex + ".http";
  }

  protected void writeToFile (@NonNull final File aDestinationFile,
                              @NonNull final List <String> aHeaderLines,
                              @NonNull final byte [] aPayload,
                              @Nullable final IBaseMessage aMsg)
  {
    LOGGER.info ("Dumping incoming HTTP request to file " + aDestinationFile.getAbsolutePath ());
    try (final OutputStream aOS = FileHelper.getOutputStream (aDestinationFile))
    {
      for (final String sHeaderLine : aHeaderLines)
        aOS.write ((sHeaderLine + CHttp.EOL).getBytes (CHttp.HTTP_CHARSET));

      // empty line
      aOS.write (CHttp.EOL.getBytes (CHttp.HTTP_CHARSET));

      // Add payload
      aOS.write (aPayload);
    }
    catch (final IOException ex)
    {
      LOGGER.error ("Failed to dump HTTP request to file " +
                    aDestinationFile.getAbsolutePath () +
                    " and message stub " +
                    aMsg,
                    ex);
    }
  }

  public void dumpIncomingRequest (@NonNull final List <String> aHeaderLines,
                                   @NonNull final byte [] aPayload,
                                   @Nullable final IBaseMessage aMsg)
  {
    // Ensure a unique filename
    File aDestinationFile;
    int nIndex = 0;
    do
    {
      aDestinationFile = new File (m_aDumpDirectory, getStoreFilename (nIndex));
      nIndex++;
      if (nIndex > 100)
        throw new IllegalStateException ("Avoid endless loop to store message!");
    } while (aDestinationFile.exists ());

    writeToFile (aDestinationFile, aHeaderLines, aPayload, aMsg);
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("DumpDirectory", m_aDumpDirectory).getToString ();
  }
}
