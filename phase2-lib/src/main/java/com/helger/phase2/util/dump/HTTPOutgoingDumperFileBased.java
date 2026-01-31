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
package com.helger.phase2.util.dump;

import java.io.File;

import org.jspecify.annotations.NonNull;

import com.helger.base.tostring.ToStringGenerator;
import com.helger.io.file.FileHelper;
import com.helger.phase2.message.IBaseMessage;

/**
 * Directory based outgoing HTTP dumper.
 *
 * @author Philip Helger
 * @since 3.0.1
 */
public class HTTPOutgoingDumperFileBased extends HTTPOutgoingDumperStreamBased
{
  private final File m_aDumpFile;

  /**
   * @param aDumpDirectory
   *        Base directory
   * @param aMsg
   *        Message to be dumped
   */
  public HTTPOutgoingDumperFileBased (@NonNull final File aDumpDirectory, @NonNull final IBaseMessage aMsg)
  {
    this (new File (aDumpDirectory, "as2-outgoing-" + Long.toString (System.currentTimeMillis ()) + ".http"));
  }

  /**
   * @param aDumpFile
   *        The file to dump to.
   */
  public HTTPOutgoingDumperFileBased (@NonNull final File aDumpFile)
  {
    super (FileHelper.getBufferedOutputStream (aDumpFile));
    m_aDumpFile = aDumpFile;
  }

  @NonNull
  public File getDumpFile ()
  {
    return m_aDumpFile;
  }

  @Override
  public String toString ()
  {
    return ToStringGenerator.getDerived (super.toString ()).append ("DumpFile", m_aDumpFile).getToString ();
  }
}
