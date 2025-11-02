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
package com.helger.phase2.cert;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.OverrideOnDemand;
import com.helger.base.string.StringHelper;
import com.helger.phase2.exception.AS2Exception;

/**
 * An implementation of a file-based certificate factory using a custom key store type. Since v4.6.4
 * this class is derived from {@link AbstractCertificateFactory}.
 *
 * @author Philip Helger
 */
@ThreadSafe
public class CertificateFactory extends AbstractCertificateFactory implements IStorableCertificateFactory
{
  public static final String ATTR_FILENAME = "filename";
  public static final String ATTR_SAVE_CHANGES_TO_FILE = "autosave";

  private static final Logger LOGGER = LoggerFactory.getLogger (CertificateFactory.class);

  public CertificateFactory ()
  {}

  public void setFilename (@Nullable final String sFilename)
  {
    debugLog ( () -> "setFilename (" + sFilename + ")");
    m_aRWLock.writeLocked ( () -> attrs ().putIn (ATTR_FILENAME, sFilename));
  }

  @Nullable
  public String getFilename ()
  {
    debugLog ( () -> "getFilename ()");
    final String ret = m_aRWLock.readLockedGet ( () -> attrs ().getAsString (ATTR_FILENAME));
    debugLog ( () -> "getFilename -> " + ret);
    return ret;
  }

  public void setSaveChangesToFile (final boolean bSaveChangesToFile)
  {
    debugLog ( () -> "setSaveChangesToFile (" + bSaveChangesToFile + ")");
    m_aRWLock.writeLocked ( () -> attrs ().putIn (ATTR_SAVE_CHANGES_TO_FILE, bSaveChangesToFile));
  }

  public boolean isSaveChangesToFile ()
  {
    debugLog ( () -> "isSaveChangesToFile ()");
    final boolean ret = m_aRWLock.readLockedBoolean ( () -> attrs ().getAsBoolean (ATTR_SAVE_CHANGES_TO_FILE,
                                                                                   DEFAULT_SAVE_CHANGES_TO_FILE));
    debugLog ( () -> "isSaveChangesToFile -> " + ret);
    return ret;
  }

  public void reinitKeyStore () throws AS2Exception
  {
    debugLog ( () -> "reinitKeyStore ()");

    // Ensure it is empty
    initEmptyKeyStore ();

    // And than load by filename
    final String sFilename = getFilename ();
    if (StringHelper.isNotEmpty (sFilename))
      load (sFilename, getPassword ());

    debugLog ( () -> "reinitKeyStore -> done");
  }

  /**
   * Custom callback method that is invoked if something changes in the key store. By default the
   * changes are written back to disk.
   *
   * @throws AS2Exception
   *         In case saving fails.
   * @see #isSaveChangesToFile()
   * @see #setSaveChangesToFile(boolean)
   */
  @Override
  @OverrideOnDemand
  protected void onChange () throws AS2Exception
  {
    debugLog ( () -> "onChange ()");
    if (isSaveChangesToFile ())
    {
      final String sFilename = getFilename ();
      if (StringHelper.isNotEmpty (sFilename))
        save (sFilename, getPassword ());
      else
      {
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Something changed in the keystore, but because no filename is present, changes are not saved");
      }
    }
    else
    {
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("Something changed in the keystore, saving of changes is disabled");
    }
    debugLog ( () -> "onChange -> done");
  }
}
