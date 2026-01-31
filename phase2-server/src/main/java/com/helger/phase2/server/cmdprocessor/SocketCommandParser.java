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
package com.helger.phase2.server.cmdprocessor;

import java.io.CharArrayWriter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.helger.xml.serialize.read.SAXReader;
import com.helger.xml.serialize.read.SAXReaderSettings;

/**
 * used to parse commands from the socket command processor message format
 * &lt;command userid="abc" pasword="xyz"&gt; the actual command&lt;/command&gt;
 *
 * @author joseph mcverry
 */
public class SocketCommandParser extends DefaultHandler
{
  private String m_sUserID;
  private String m_sPassword;
  private String m_sCommandText;

  /** simple string processor */
  private final CharArrayWriter m_aContents = new CharArrayWriter ();

  /**
   * constructor
   */
  public SocketCommandParser ()
  {}

  public void parse (@Nullable final String sInLine)
  {
    m_sUserID = "";
    m_sPassword = "";
    m_sCommandText = "";
    m_aContents.reset ();

    if (sInLine != null)
    {
      SAXReader.readXMLSAX (sInLine,
                            new SAXReaderSettings ().setEntityResolver (this)
                                                    .setDTDHandler (this)
                                                    .setContentHandler (this)
                                                    .setErrorHandler (this));
    }
  }

  /**
   * Method handles #PCDATA
   *
   * @param ch
   *        array
   * @param start
   *        position in array where next has been placed
   * @param length
   *        int
   */
  @Override
  public void characters (final char [] ch, final int start, final int length)
  {
    m_aContents.write (ch, start, length);
  }

  @Override
  public void startElement (final String sURI,
                            final String sLocalName,
                            final String sQName,
                            final Attributes aAttributes) throws SAXException
  {
    if (sQName.equals ("command"))
    {
      m_sUserID = aAttributes.getValue ("id");
      m_sPassword = aAttributes.getValue ("password");
    }
  }

  @Override
  public void endElement (final String sURI, final String sLocalName, @NonNull final String sQName) throws SAXException
  {
    if (sQName.equals ("command"))
    {
      m_sCommandText = m_aContents.toString ();
      m_aContents.reset ();
    }
    else
      m_aContents.flush ();
  }

  public String getCommandText ()
  {
    return m_sCommandText;
  }

  public String getPassword ()
  {
    return m_sPassword;
  }

  public String getUserid ()
  {
    return m_sUserID;
  }
}
