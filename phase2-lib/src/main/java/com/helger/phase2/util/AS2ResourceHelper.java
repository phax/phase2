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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.concurrent.GuardedBy;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.stream.StreamHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.io.file.FileHelper;
import com.helger.io.file.FileIOError;
import com.helger.phase2.CPhase2Info;

/**
 * A resource manager that keeps track of temporary files and other closables that will be closed
 * when this manager is closed. When calling {@link #createTempFile()} a new filename is created and
 * added to the list. When using {@link #addCloseable(Closeable)} the Closable is added for
 * postponed closing.
 *
 * @author Philip Helger
 * @since 4.5.3
 */
public class AS2ResourceHelper implements Closeable
{
  private static final String TEMP_FILE_PREFIX = "phase2-res-";
  private static final String TEMP_FILE_SUFFIX = ".tmp";
  private static final Logger LOGGER = LoggerFactory.getLogger (AS2ResourceHelper.class);

  private static File s_aTempDir;

  /**
   * @return The temp file directory to use, or <code>null</code> for the system default.
   */
  @Nullable
  public static File getTempDir ()
  {
    return s_aTempDir;
  }

  /**
   * Set a temporary directory to use.
   *
   * @param aTempDir
   *        The directory to use. It must be an existing directory. May be <code>null</code> to use
   *        the system default.
   * @throws IllegalArgumentException
   *         If the directory does not exist
   */
  public static void setTempDir (@Nullable final File aTempDir)
  {
    if (aTempDir != null)
      if (!aTempDir.isDirectory ())
        throw new IllegalArgumentException ("Temporary directory '" +
                                            aTempDir.getAbsolutePath () +
                                            "' is not a directory");
    s_aTempDir = aTempDir;
  }

  private final SimpleReadWriteLock m_aRWLock = new SimpleReadWriteLock ();
  private final AtomicBoolean m_aInClose = new AtomicBoolean (false);
  @GuardedBy ("m_aRWLock")
  private final ICommonsList <File> m_aTempFiles = new CommonsArrayList <> ();
  @GuardedBy ("m_aRWLock")
  private final ICommonsList <Closeable> m_aCloseables = new CommonsArrayList <> ();

  public AS2ResourceHelper ()
  {}

  /**
   * @return A new temporary {@link File} that will be deleted when {@link #close()} is called.
   * @throws IOException
   *         When temp file creation fails.
   * @throws IllegalStateException
   *         If {@link #close()} was already called before
   */
  @NonNull
  public File createTempFile () throws IOException
  {
    if (m_aInClose.get ())
      throw new IllegalStateException ("ResourceManager is already closing/closed!");

    // Create
    final File ret = s_aTempDir != null ? Files.createTempFile (s_aTempDir.toPath (),
                                                                TEMP_FILE_PREFIX,
                                                                TEMP_FILE_SUFFIX).toFile () : Files.createTempFile (
                                                                                                                    TEMP_FILE_PREFIX,
                                                                                                                    TEMP_FILE_SUFFIX)
                                                                                                   .toFile ();
    // And remember
    m_aRWLock.writeLocked ( () -> m_aTempFiles.add (ret));
    return ret;
  }

  /**
   * @return A list of all known temp files. Never <code>null</code> but maybe empty.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <File> getAllTempFiles ()
  {
    return m_aRWLock.readLockedGet (m_aTempFiles::getClone);
  }

  /**
   * Add a new closable for later closing.
   *
   * @param aCloseable
   *        The closable to be closed later. May not be <code>null</code>.
   * @throws IllegalStateException
   *         If {@link #close()} was already called before
   */
  public void addCloseable (@NonNull final Closeable aCloseable)
  {
    ValueEnforcer.notNull (aCloseable, "Closeable");

    if (m_aInClose.get ())
      throw new IllegalStateException ("AS4ResourceHelper is already closing/closed!");

    m_aCloseables.add (aCloseable);
  }

  /**
   * @return A list of all known closables. Never <code>null</code> but maybe empty.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Closeable> getAllCloseables ()
  {
    return m_aRWLock.readLockedGet (m_aCloseables::getClone);
  }

  public void close ()
  {
    // Avoid taking new objects
    // close only once
    if (!m_aInClose.getAndSet (true))
    {
      // Close all closeables before deleting files, because the closables might
      // be the files to be deleted :)
      final ICommonsList <Closeable> aCloseables = m_aRWLock.writeLockedGet ( () -> {
        final ICommonsList <Closeable> ret = m_aCloseables.getClone ();
        m_aCloseables.clear ();
        return ret;
      });
      if (aCloseables.isNotEmpty ())
      {
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Closing " + aCloseables.size () + " " + CPhase2Info.NAME_VERSION + " stream handles");

        for (final Closeable aCloseable : aCloseables)
          StreamHelper.close (aCloseable);
      }

      // Get and delete all temp files
      final ICommonsList <File> aFiles = m_aRWLock.writeLockedGet ( () -> {
        final ICommonsList <File> ret = m_aTempFiles.getClone ();
        m_aTempFiles.clear ();
        return ret;
      });
      if (aFiles.isNotEmpty ())
      {
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Deleting " + aFiles.size () + " temporary " + CPhase2Info.NAME_VERSION + " files");

        for (final File aFile : aFiles)
        {
          if (LOGGER.isDebugEnabled ())
            LOGGER.debug ("Deleting temporary file '" + aFile.getAbsolutePath () + "'");

          final FileIOError aError = AS2IOHelper.getFileOperationManager ().deleteFileIfExisting (aFile);
          if (aError.isFailure ())
            LOGGER.warn ("  Failed to delete temporary " +
                         CPhase2Info.NAME_VERSION +
                         " file " +
                         aFile.getAbsolutePath () +
                         ": " +
                         aError.toString ());
        }
      }
    }
  }

  /**
   * Ensure the provided {@link HttpEntity} can be read more than once. If the provided entity is
   * not repeatable a temporary file is created and a new file-based Http Entity is created.
   *
   * @param aSrcEntity
   *        The source Http entity. May not be <code>null</code>.
   * @return A non-<code>null</code> Http entity that can be read more than once.
   * @throws IOException
   *         on IO error
   */
  @NonNull
  public HttpEntity createRepeatableHttpEntity (@NonNull final HttpEntity aSrcEntity) throws IOException
  {
    ValueEnforcer.notNull (aSrcEntity, "SrcEntity");

    // Do we need to do anything?
    if (aSrcEntity.isRepeatable ())
      return aSrcEntity;

    // First serialize the content once to a file, so that a repeatable entity
    // can be created
    final File aTempFile = createTempFile ();

    LOGGER.info ("Converting " +
                 aSrcEntity +
                 " to a repeatable HTTP entity using file " +
                 aTempFile.getAbsolutePath ());

    try (final OutputStream aOS = FileHelper.getBufferedOutputStream (aTempFile))
    {
      aSrcEntity.writeTo (aOS);
    }

    // Than use the FileEntity as the basis
    return new FileEntity (aTempFile,
                           ContentType.parse (aSrcEntity.getContentType ()),
                           aSrcEntity.getContentEncoding ());
  }
}
