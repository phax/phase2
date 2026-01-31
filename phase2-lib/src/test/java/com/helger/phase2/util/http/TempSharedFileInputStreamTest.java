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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.base.io.stream.StreamHelper;
import com.helger.io.file.FileOperations;

import jakarta.mail.util.SharedFileInputStream;

/**
 * Test class of class {@link TempSharedFileInputStream}.
 *
 * @author Ziv Harpaz
 */
public final class TempSharedFileInputStreamTest
{
  @Test
  public void testGetTempSharedFileInputStream () throws Exception
  {
    final String inData = "123456";
    try (final InputStream is = new NonBlockingByteArrayInputStream (inData.getBytes ());
         final SharedFileInputStream sis = TempSharedFileInputStream.getTempSharedFileInputStream (is, "myName");
         final NonBlockingByteArrayOutputStream baos = new NonBlockingByteArrayOutputStream ())
    {
      StreamHelper.copyInputStreamToOutputStream (sis, baos);
      final String res = baos.getAsString (StandardCharsets.ISO_8859_1);
      assertEquals ("read the data", inData, res);
      sis.close ();
    }
  }

  @Test
  public void testStoreContentToTempFile () throws Exception
  {
    final String inData = "123456";
    final String name = "xxy";
    try (final InputStream is = new NonBlockingByteArrayInputStream (inData.getBytes (StandardCharsets.ISO_8859_1)))
    {
      final File file = TempSharedFileInputStream.storeContentToTempFile (is, name);
      assertTrue ("Temp file exists", file.exists ());
      assertTrue ("Temp file name includes given name", file.getName ().indexOf (name) > 0);
      FileOperations.deleteFileIfExisting (file);
    }
  }

  @Test
  public void testFinalize () throws Exception
  {
    for (int i = 0; i < 10000; i++)
    {
      final String sSrcData = "123456";
      try (final InputStream is = new NonBlockingByteArrayInputStream (sSrcData.getBytes (StandardCharsets.ISO_8859_1));
           final TempSharedFileInputStream aSharedIS = TempSharedFileInputStream.getTempSharedFileInputStream (is,
                                                                                                               "myName"))
      {
        final int t = aSharedIS.read ();
        assertEquals (t, sSrcData.charAt (0));
        aSharedIS.closeAndDelete ();
      }
    }
  }
}
