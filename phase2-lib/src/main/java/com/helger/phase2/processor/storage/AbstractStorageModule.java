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
package com.helger.phase2.processor.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.annotation.WillClose;
import com.helger.annotation.style.OverrideOnDemand;
import com.helger.base.charset.CharsetHelper;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.stream.StreamHelper;
import com.helger.base.system.SystemHelper;
import com.helger.io.file.FilenameHelper;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.params.AS2InvalidParameterException;
import com.helger.phase2.processor.module.AbstractProcessorModule;
import com.helger.phase2.session.IAS2Session;
import com.helger.phase2.util.AS2IOHelper;
import com.helger.typeconvert.collection.IStringMap;

public abstract class AbstractStorageModule extends AbstractProcessorModule implements IProcessorStorageModule
{
  public static final String ATTR_FILENAME = "filename";
  public static final String ATTR_PROTOCOL = "protocol";
  public static final String ATTR_TEMPDIR = "tempdir";
  public static final String ATTR_CHARSET = "charset";

  private final String m_sModuleAction;

  protected AbstractStorageModule (@NonNull @Nonempty final String sModuleAction)
  {
    m_sModuleAction = ValueEnforcer.notEmpty (sModuleAction, "ModuleAction");
  }

  @Nullable
  public final String getFilename ()
  {
    return attrs ().getAsString (ATTR_FILENAME);
  }

  public final void setFilename (@Nullable final String sFilename)
  {
    if (sFilename == null)
      attrs ().remove (ATTR_FILENAME);
    else
      attrs ().putIn (ATTR_FILENAME, sFilename);
  }

  @Nullable
  public final String getProtocol ()
  {
    return attrs ().getAsString (ATTR_PROTOCOL);
  }

  public final void setProtocol (@Nullable final String sProtocol)
  {
    if (sProtocol == null)
      attrs ().remove (ATTR_PROTOCOL);
    else
      attrs ().putIn (ATTR_PROTOCOL, sProtocol);
  }

  @Nullable
  public final String getTempDir ()
  {
    return attrs ().getAsString (ATTR_TEMPDIR);
  }

  public final void setTempDir (@Nullable final String sTempDir)
  {
    if (sTempDir == null)
      attrs ().remove (ATTR_TEMPDIR);
    else
      attrs ().putIn (ATTR_TEMPDIR, sTempDir);
  }

  @Nullable
  public final String getCharsetName ()
  {
    return attrs ().getAsString (ATTR_CHARSET);
  }

  /**
   * @return The charset configured via {@link #ATTR_CHARSET} parameter or the system default. Never
   *         <code>null</code>.
   */
  @NonNull
  protected Charset getCharset ()
  {
    final Charset aCharset = CharsetHelper.getCharsetFromNameOrNull (getCharsetName ());
    return aCharset != null ? aCharset : SystemHelper.getSystemCharset ();
  }

  public final void setCharsetName (@Nullable final String sCharsetName)
  {
    if (sCharsetName == null)
      attrs ().remove (ATTR_CHARSET);
    else
      attrs ().putIn (ATTR_CHARSET, sCharsetName);
  }

  public final boolean canHandle (@NonNull final String sAction,
                                  @NonNull final IMessage aMsg,
                                  @Nullable final Map <String, Object> aOptions)
  {
    if (!sAction.equals (m_sModuleAction))
      return false;

    // Usually "as2"
    final String sModProtocol = getProtocol ();
    if (sModProtocol == null)
      return false;
    return sModProtocol.equals (aMsg.getProtocol ());
  }

  @Override
  public final void initDynamicComponent (@NonNull final IAS2Session aSession, @Nullable final IStringMap aOptions)
                                                                                                                    throws AS2Exception
  {
    super.initDynamicComponent (aSession, aOptions);
    getAttributeAsStringRequired (ATTR_FILENAME);
  }

  protected abstract String getFilename (IMessage aMsg, String sFileParam) throws AS2InvalidParameterException;

  /**
   * Not final - see #105
   *
   * @param aMsg
   *        The source message
   * @param sFileParam
   *        The parameter name including the filename
   * @return File The {@link File} to be used
   * @since 2007-06-01
   * @throws IOException
   *         In case of IO error
   * @throws AS2Exception
   *         In case of error
   */
  @NonNull
  @OverrideOnDemand
  protected File getFile (@NonNull final IMessage aMsg, @Nullable final String sFileParam) throws IOException,
                                                                                           AS2Exception
  {
    final String sFilename = getFilename (aMsg, sFileParam);

    // make sure the parent directories exist
    final File aFile = new File (sFilename);
    AS2IOHelper.getFileOperationManager ().createDirRecursiveIfNotExisting (aFile.getParentFile ());
    // don't overwrite existing files
    return AS2IOHelper.getUniqueFile (aFile.getParentFile (),
                                      FilenameHelper.getAsSecureValidFilename (aFile.getName ()));
  }

  private static void _writeStreamToFile (@NonNull @WillClose final InputStream aIS, @NonNull final File aDestination)
                                                                                                                       throws IOException
  {
    final FileOutputStream aOS = new FileOutputStream (aDestination);
    if (StreamHelper.copyInputStreamToOutputStreamAndCloseOS (aIS, aOS).isFailure ())
      throw new IOException ("Failed to write content to " + aDestination.getAbsolutePath ());
  }

  protected void store (@NonNull final File aMsgFile, @NonNull @WillClose final InputStream aIS) throws IOException
  {
    final String sTempDirname = getTempDir ();
    if (sTempDirname != null)
    {
      // write the data to a temporary directory first
      final File aTempDir = AS2IOHelper.getDirectoryFile (sTempDirname);
      final File aTempFile = AS2IOHelper.getUniqueFile (aTempDir, aMsgFile.getName ());
      _writeStreamToFile (aIS, aTempFile);

      // copy the temp file over to the destination
      AS2IOHelper.moveFile (aTempFile, aMsgFile, true, true);
    }
    else
    {
      // Write directly to the destination file
      _writeStreamToFile (aIS, aMsgFile);
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
}
