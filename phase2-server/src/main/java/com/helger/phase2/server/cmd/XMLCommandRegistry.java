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
package com.helger.phase2.server.cmd;

import java.io.File;
import java.io.InputStream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.io.file.FileHelper;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.server.app.session.AS2ServerXMLSession;
import com.helger.phase2.session.IAS2Session;
import com.helger.phase2.util.AS2XMLHelper;
import com.helger.typeconvert.collection.IStringMap;
import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.IMicroElement;
import com.helger.xml.microdom.serialize.MicroReader;

public class XMLCommandRegistry extends BaseCommandRegistry
{
  public static final String ATTR_FILENAME = "filename";

  @Override
  public void initDynamicComponent (@NonNull final IAS2Session aSession, @Nullable final IStringMap aParameters) throws AS2Exception
  {
    super.initDynamicComponent (aSession, aParameters);

    refresh ();
  }

  protected void loadCommand (final IMicroElement eCommand, @Nullable final MultiCommand aParent) throws AS2Exception
  {
    final IAS2Session aSession = getSession ();
    final String sBaseDirectory = aSession instanceof AS2ServerXMLSession ? ((AS2ServerXMLSession) aSession).getBaseDirectory () : null;
    final ICommand aCommand = AS2XMLHelper.createComponent (eCommand, ICommand.class, aSession, sBaseDirectory);
    if (aParent != null)
      aParent.getCommands ().add (aCommand);
    else
      addCommand (aCommand);
  }

  protected void loadMultiCommand (@NonNull final IMicroElement aCommand, @Nullable final MultiCommand parent) throws AS2Exception
  {
    final MultiCommand cmd = new MultiCommand ();
    cmd.initDynamicComponent (getSession (), AS2XMLHelper.getAllAttrsWithLowercaseName (aCommand));

    if (parent != null)
      parent.getCommands ().add (cmd);
    else
      addCommand (cmd);

    for (final IMicroElement aChildElement : aCommand.getAllChildElements ())
    {
      final String sChildName = aChildElement.getNodeName ();

      if (sChildName.equals ("command"))
        loadCommand (aChildElement, cmd);
      else
        if (sChildName.equals ("multicommand"))
          loadMultiCommand (aChildElement, cmd);
        else
          throw new AS2Exception ("Undefined child tag: " + sChildName);
    }
  }

  public void load (@NonNull final InputStream in) throws AS2Exception
  {
    final IMicroDocument aDoc = MicroReader.readMicroXML (in);
    final IMicroElement eRoot = aDoc.getDocumentElement ();

    clearCommands ();

    for (final IMicroElement eElement : eRoot.getAllChildElements ())
    {
      final String sNodeName = eElement.getTagName ();
      if (sNodeName.equals ("command"))
        loadCommand (eElement, null);
      else
        if (sNodeName.equals ("multicommand"))
          loadMultiCommand (eElement, null);
        else
          throw new AS2Exception ("Undefined tag: " + sNodeName);
    }
  }

  public void refresh () throws AS2Exception
  {
    final String sFilename = getAttributeAsStringRequired (ATTR_FILENAME);
    load (FileHelper.getInputStream (new File (sFilename)));
  }
}
