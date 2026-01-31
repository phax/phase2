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
package com.helger.phase2.cert;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.io.EAppend;
import com.helger.io.file.FileHelper;
import com.helger.phase2.exception.AS2Exception;
import com.helger.security.keystore.KeyStoreHelper;

/**
 * Base interface for a certificate factory that can store to a file.
 *
 * @author Philip Helger
 */
public interface IStorableCertificateFactory extends ICertificateFactory
{
  boolean DEFAULT_SAVE_CHANGES_TO_FILE = true;

  void setFilename (@Nullable String sFilename);

  @Nullable
  String getFilename ();

  default void setPassword (@NonNull final char [] aPassword)
  {
    setPassword (new String (aPassword));
  }

  void setPassword (@Nullable String sPassword);

  @Nullable
  char [] getPassword ();

  /**
   * Change the behavior if all changes should trigger a saving to the original file. The default
   * value is {@link #DEFAULT_SAVE_CHANGES_TO_FILE}.
   *
   * @param bSaveChangesToFile
   *        <code>true</code> to enable auto-saving, <code>false</code> to disable it.
   */
  void setSaveChangesToFile (boolean bSaveChangesToFile);

  /**
   * @return <code>true</code> if changes to the key store should be persisted back to the original
   *         file, <code>false</code> if not. The default value is
   *         {@link #DEFAULT_SAVE_CHANGES_TO_FILE}.
   */
  boolean isSaveChangesToFile ();

  /**
   * Shortcut for <code>load (getFilename (), getPassword ());</code>
   *
   * @throws AS2Exception
   *         In case of an internal error
   */
  default void load () throws AS2Exception
  {
    load (getFilename (), getPassword ());
  }

  default void load (@NonNull final String sFilename, @NonNull final char [] aPassword) throws AS2Exception
  {
    InputStream aFIS = null;
    try
    {
      aFIS = KeyStoreHelper.getResourceProvider ().getInputStream (sFilename);
      if (aFIS == null)
        throw new AS2Exception ("Failed to to open input stream from '" + sFilename + "'");
    }
    catch (final RuntimeException ex)
    {
      throw new AS2Exception ("Failed to to open input stream from '" + sFilename + "'", ex);
    }
    load (aFIS, aPassword);
  }

  void load (@NonNull InputStream aIS, @NonNull char [] aPassword) throws AS2Exception;

  /**
   * Shortcut for <code>save (getFilename (), getPassword ());</code>
   *
   * @throws AS2Exception
   *         In case of an internal error
   */
  default void save () throws AS2Exception
  {
    save (getFilename (), getPassword ());
  }

  default void save (@NonNull final String sFilename, @NonNull final char [] aPassword) throws AS2Exception
  {
    // Must be File by default
    final OutputStream fOut = FileHelper.getOutputStream (new File (sFilename), EAppend.TRUNCATE);
    if (fOut == null)
      throw new AS2Exception ("Failed to to open output stream to '" + sFilename + "'");
    save (fOut, aPassword);
  }

  void save (@NonNull OutputStream aOS, @NonNull char [] aPassword) throws AS2Exception;
}
