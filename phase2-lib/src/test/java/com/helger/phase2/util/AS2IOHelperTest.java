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
package com.helger.phase2.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Test class for class {@link AS2IOHelper}.
 *
 * @author Philip Helger
 */
public final class AS2IOHelperTest
{
  @Test
  public void testGetFilenameFromMessageID ()
  {
    assertNull (AS2IOHelper.getFilenameFromMessageID (""));
    assertNull (AS2IOHelper.getFilenameFromMessageID ("<<<<>>>>"));
    assertEquals ("a", AS2IOHelper.getFilenameFromMessageID ("a"));
    assertEquals ("a", AS2IOHelper.getFilenameFromMessageID ("<a>"));
    assertEquals ("a", AS2IOHelper.getFilenameFromMessageID ("<<<<<a>>>>>"));
    assertEquals ("a@b.c", AS2IOHelper.getFilenameFromMessageID ("<<<<<a@b.c>>>>>"));
  }

  @Test
  public void testGetSafeFileAndFolderName ()
  {
    assertNull (AS2IOHelper.getSafeFileAndFolderName (null));
    assertEquals ("", AS2IOHelper.getSafeFileAndFolderName (""));
    assertEquals ("abc", AS2IOHelper.getSafeFileAndFolderName ("abc"));
    assertEquals ("abc/def", AS2IOHelper.getSafeFileAndFolderName ("abc/def"));
    assertEquals ("abc/def", AS2IOHelper.getSafeFileAndFolderName ("abc\\def"));
    assertEquals ("abc/def/blub", AS2IOHelper.getSafeFileAndFolderName ("abc/def\\blub"));
    assertEquals ("abc_/d_ef/g_hi", AS2IOHelper.getSafeFileAndFolderName ("abc</d|ef\\g*hi"));

    assertEquals ("/abc", AS2IOHelper.getSafeFileAndFolderName ("/abc"));
    assertEquals ("/abc", AS2IOHelper.getSafeFileAndFolderName ("\\abc"));
    assertEquals ("abc", AS2IOHelper.getSafeFileAndFolderName ("abc/"));
    assertEquals ("abc", AS2IOHelper.getSafeFileAndFolderName ("abc\\"));
    assertEquals ("/abc", AS2IOHelper.getSafeFileAndFolderName ("/abc/"));
    assertEquals ("/abc", AS2IOHelper.getSafeFileAndFolderName ("/abc\\"));
    assertEquals ("/abc", AS2IOHelper.getSafeFileAndFolderName ("\\abc/"));
    assertEquals ("/abc", AS2IOHelper.getSafeFileAndFolderName ("\\abc\\"));
    assertEquals ("z:/abc", AS2IOHelper.getSafeFileAndFolderName ("z:\\abc\\"));
    assertEquals ("z:/abc/test.txt", AS2IOHelper.getSafeFileAndFolderName ("z:\\abc/test.txt"));
    assertEquals ("z:/a_bc/tes_t.txt", AS2IOHelper.getSafeFileAndFolderName ("z:\\a*bc/tesät.txt"));
    assertEquals ("z:/com1", AS2IOHelper.getSafeFileAndFolderName ("z:\\com1"));
    assertEquals ("z:/_com2", AS2IOHelper.getSafeFileAndFolderName ("z:\\com2"));
  }
}
