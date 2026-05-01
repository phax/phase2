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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.base.io.stream.StreamHelper;

/**
 * Test class of class {@link TempSharedFileBackedStream}.
 *
 * @author Philip Helger
 */
public final class TempSharedFileBackedStreamTest
{
  @Test
  public void testReadViewAndClose () throws Exception
  {
    final String sIn = "123456";
    final File aTempFile;
    try (final InputStream aIS = new NonBlockingByteArrayInputStream (sIn.getBytes (StandardCharsets.ISO_8859_1));
         final TempSharedFileBackedStream aHolder = TempSharedFileBackedStream.create (aIS, "myName");
         final InputStream aView = aHolder.openStream ();
         final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ())
    {
      aTempFile = aHolder.getTempFile ();
      assertNotNull (aTempFile);
      assertTrue ("Temp file exists while holder is open", aTempFile.exists ());

      StreamHelper.copyInputStreamToOutputStream (aView, aBAOS);
      assertEquals ("read the data", sIn, aBAOS.getAsString (StandardCharsets.ISO_8859_1));
    }
    assertFalse ("Temp file is deleted after holder is closed", aTempFile.exists ());
  }

  @Test
  public void testMultipleIndependentViews () throws Exception
  {
    final String sIn = "abcdef";
    try (final InputStream aIS = new NonBlockingByteArrayInputStream (sIn.getBytes (StandardCharsets.ISO_8859_1));
         final TempSharedFileBackedStream aHolder = TempSharedFileBackedStream.create (aIS, "multi"))
    {
      // Closing the first view must not break the second view nor the holder.
      try (final InputStream aView1 = aHolder.openStream ())
      {
        assertEquals ('a', aView1.read ());
      }
      try (final InputStream aView2 = aHolder.openStream ())
      {
        assertEquals ('a', aView2.read ());
        assertEquals ('b', aView2.read ());
      }
      assertTrue ("Temp file still alive after view closes", aHolder.getTempFile ().exists ());
    }
  }

  @Test
  public void testCloseIsIdempotent () throws Exception
  {
    final String sIn = "x";
    final File aTempFile;
    try (final InputStream aIS = new NonBlockingByteArrayInputStream (sIn.getBytes (StandardCharsets.ISO_8859_1)))
    {
      @SuppressWarnings ("resource")
      final TempSharedFileBackedStream aHolder = TempSharedFileBackedStream.create (aIS, "idem");
      aTempFile = aHolder.getTempFile ();
      aHolder.close ();
      assertFalse (aTempFile.exists ());
      // Second close must not throw.
      aHolder.close ();
    }
  }

  @Test
  public void testOpenStreamAfterCloseFails () throws Exception
  {
    final String sIn = "x";
    try (final InputStream aIS = new NonBlockingByteArrayInputStream (sIn.getBytes (StandardCharsets.ISO_8859_1)))
    {
      @SuppressWarnings ("resource")
      final TempSharedFileBackedStream aHolder = TempSharedFileBackedStream.create (aIS, "afterclose");
      aHolder.close ();
      assertThrows (IllegalStateException.class, () -> aHolder.openStream ());
    }
  }

  @Test
  public void testStress () throws Exception
  {
    for (int i = 0; i < 10_000; i++)
    {
      final String sSrcData = "123456";
      try (final InputStream aIS = new NonBlockingByteArrayInputStream (sSrcData.getBytes (StandardCharsets.ISO_8859_1));
           final TempSharedFileBackedStream aHolder = TempSharedFileBackedStream.create (aIS, "stress");
           final InputStream aView = aHolder.openStream ())
      {
        assertEquals (sSrcData.charAt (0), aView.read ());
      }
    }
  }
}
