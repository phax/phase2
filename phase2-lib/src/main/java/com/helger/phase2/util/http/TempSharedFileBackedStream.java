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

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.WillClose;
import com.helger.base.io.stream.StreamHelper;
import com.helger.base.numeric.mutable.MutableLong;
import com.helger.base.string.StringHelper;
import com.helger.io.file.FilenameHelper;
import com.helger.phase2.util.AS2IOHelper;

import jakarta.mail.util.SharedFileInputStream;

/**
 * Holder around a {@link SharedFileInputStream} on a temporary file. The temporary file is created
 * from the content of an input stream and is owned exclusively by this holder. Callers obtain
 * stream views via {@link #openStream()} which they may freely {@link InputStream#close() close} —
 * the underlying file stays open until {@link #close()} is invoked on this holder, at which point
 * the shared stream is closed and the temporary file is deleted.
 * <p>
 * Use with try-with-resources to guarantee deterministic cleanup.
 *
 * @since 6.2.0
 */
public final class TempSharedFileBackedStream implements Closeable
{
  private static final Logger LOGGER = LoggerFactory.getLogger (TempSharedFileBackedStream.class);

  private final File m_aTempFile;
  private final SharedFileInputStream m_aSharedIS;
  private final AtomicBoolean m_aClosed = new AtomicBoolean (false);

  private TempSharedFileBackedStream (@NonNull final File aTempFile) throws IOException
  {
    m_aTempFile = aTempFile;
    m_aSharedIS = new SharedFileInputStream (aTempFile);
  }

  /**
   * Returns a fresh, independently positioned view onto the shared file. The returned stream may be
   * safely closed by callers (e.g. MIME parsers, cryptographic stages) without affecting other
   * views or the underlying file.
   *
   * @return a new {@link InputStream} positioned at the start of the file. Never <code>null</code>.
   * @throws IllegalStateException
   *         if this holder has already been closed.
   */
  @NonNull
  public InputStream openStream ()
  {
    if (m_aClosed.get ())
      throw new IllegalStateException ("TempSharedFileBackedStream is already closed");
    // Sub-stream over the entire file. Closing it does NOT close the underlying RandomAccessFile.
    return m_aSharedIS.newStream (0, -1);
  }

  /**
   * @return the temporary {@link File} backing this holder. Never <code>null</code>.
   */
  @NonNull
  public File getTempFile ()
  {
    return m_aTempFile;
  }

  /**
   * Closes the underlying {@link SharedFileInputStream} and deletes the temporary file. Idempotent
   * — subsequent invocations are no-ops.
   */
  @Override
  public void close () throws IOException
  {
    if (m_aClosed.compareAndSet (false, true))
    {
      try
      {
        m_aSharedIS.close ();
      }
      finally
      {
        AS2IOHelper.getFileOperationManager ().deleteFileIfExisting (m_aTempFile);
      }
    }
  }

  /**
   * Stores the content of the input {@link InputStream} in a temporary file (in the system
   * temporary directory) and opens a {@link SharedFileInputStream} on it.
   *
   * @param aIS
   *        {@link InputStream} to read from. May not be <code>null</code>. Will be closed.
   * @param sName
   *        Name fragment used in the temporary file name to link it to the delivered message. May
   *        not be <code>null</code> but may be empty.
   * @return the constructed holder. Never <code>null</code>.
   * @throws IOException
   *         in case of IO error. The temporary file is removed before the exception propagates.
   */
  @NonNull
  public static TempSharedFileBackedStream create (@NonNull @WillClose final InputStream aIS,
                                                   @NonNull final String sName) throws IOException
  {
    final File aDest = storeContentToTempFile (aIS, sName);
    try
    {
      return new TempSharedFileBackedStream (aDest);
    }
    catch (final IOException ex)
    {
      // Constructor failed — do not leak the temp file
      AS2IOHelper.getFileOperationManager ().deleteFileIfExisting (aDest);
      throw ex;
    }
  }

  /**
   * Stores the content of the input {@link InputStream} in a fresh temporary file in the system
   * temporary directory.
   *
   * @param aIS
   *        {@link InputStream} to read from. May not be <code>null</code>. Will be closed.
   * @param sName
   *        Name fragment used in the temporary file name. May not be <code>null</code> but may be
   *        empty.
   * @return The created {@link File}. Never <code>null</code>.
   * @throws IOException
   *         in case of IO error.
   */
  @NonNull
  static File storeContentToTempFile (@NonNull @WillClose final InputStream aIS, @NonNull final String sName)
                                                                                                              throws IOException
  {
    // create temp file and write stream content to it
    // name may contain ":" on Windows and that would fail the tests!
    final String sSuffix = FilenameHelper.getAsSecureValidASCIIFilename (StringHelper.isNotEmpty (sName) ? sName
                                                                                                         : "tmp");
    final File aDestFile = Files.createTempFile ("AS2TempSharedFileIS", sSuffix).toFile ();

    try (final FileOutputStream aOS = new FileOutputStream (aDestFile))
    {
      final MutableLong aCount = new MutableLong (0);
      StreamHelper.copyByteStream ()
                  .from (aIS)
                  .closeFrom (true)
                  .to (aOS)
                  .closeTo (false)
                  .copyByteCount (aCount)
                  .build ();
      // Avoid logging in tests
      if (aCount.longValue () > 1024L)
        LOGGER.info (aCount.longValue () + " bytes copied to " + aDestFile.getAbsolutePath ());
    }
    return aDestFile;
  }
}
