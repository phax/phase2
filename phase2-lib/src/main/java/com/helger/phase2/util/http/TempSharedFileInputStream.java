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
package com.helger.phase2.util.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.WillClose;
import com.helger.phase2.util.AS2IOHelper;

import jakarta.mail.util.SharedFileInputStream;

/**
 * Stores the content of the input {@link InputStream} in a temporary file, and opens
 * {@link SharedFileInputStream} on that file. When the stream is closed, the file will be deleted,
 * and the input stream will be closed.
 *
 * @deprecated Use {@link TempSharedFileBackedStream} instead. The new class is a proper
 *             {@link java.io.Closeable} and integrates cleanly with try-with-resources, removing
 *             the need for the old no-op {@link #close()} / explicit {@link #closeAndDelete()}
 *             split that this class required.
 */
@Deprecated (forRemoval = true, since = "6.2.0")
public class TempSharedFileInputStream extends SharedFileInputStream
{
  private static final Logger LOGGER = LoggerFactory.getLogger (TempSharedFileInputStream.class);

  private final File m_aTempFile;

  private TempSharedFileInputStream (@NonNull final File aFile) throws IOException
  {
    super (aFile);
    m_aTempFile = aFile;
    // JVM-shutdown safety net for callers that forget closeAndDelete().
    // (The old finalize()-based net was deprecated and unreliable.)
    aFile.deleteOnExit ();
  }

  /**
   * close - Do nothing, to prevent early close, as the cryptographic processing stages closes their
   * input stream
   */
  @Deprecated (forRemoval = true, since = "6.2.0")
  @Override
  public void close () throws IOException
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("close() called, doing nothing.");
  }

  /**
   * closeAll - closes the input stream, and deletes the backing file
   *
   * @throws IOException
   *         in case of error
   * @since 4.10.2
   */
  @Deprecated (forRemoval = true, since = "6.2.0")
  public void closeAndDelete () throws IOException
  {
    try
    {
      super.close ();
    }
    finally
    {
      AS2IOHelper.getFileOperationManager ().deleteFileIfExisting (m_aTempFile);
    }
  }

  /**
   * Stores the content of the input {@link InputStream} in a temporary file (in the system
   * temporary directory.
   *
   * @param aIS
   *        {@link InputStream} to read from
   * @param sName
   *        name to use in the temporary file to link it to the delivered message. May be null
   * @return The created {@link File}
   * @throws IOException
   *         in case of IO error
   */
  @NonNull
  @Deprecated (forRemoval = true, since = "6.2.0")
  protected static File storeContentToTempFile (@NonNull @WillClose final InputStream aIS, @NonNull final String sName)
                                                                                                                        throws IOException
  {
    return TempSharedFileBackedStream.storeContentToTempFile (aIS, sName);
  }

  /**
   * Stores the content of the input {@link InputStream} in a temporary file (in the system
   * temporary directory, and opens {@link SharedFileInputStream} on that file.
   *
   * @param aIS
   *        {@link InputStream} to read from
   * @param sName
   *        name to use in the temporary file to link it to the delivered message. May be null
   * @return {@link TempSharedFileInputStream} on the created temporary file.
   * @throws IOException
   *         in case of IO error
   * @deprecated Use {@link TempSharedFileBackedStream#create(InputStream, String)} instead.
   */
  @Deprecated (forRemoval = true, since = "6.2.0")
  @NonNull
  public static TempSharedFileInputStream getTempSharedFileInputStream (@NonNull @WillClose final InputStream aIS,
                                                                        @NonNull final String sName) throws IOException
  {
    // IS is closed inside the copying
    final File aDest = storeContentToTempFile (aIS, sName);
    return new TempSharedFileInputStream (aDest);
  }
}
