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
package com.helger.phase2.params;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.string.StringHelper;
import com.helger.base.string.StringReplace;
import com.helger.phase2.message.IMessage;

import jakarta.mail.internet.ContentDisposition;

public class MessageParameters extends AbstractParameterParser
{
  public static final String KEY_SENDER = "sender";
  public static final String KEY_RECEIVER = "receiver";
  public static final String KEY_ATTRIBUTES = "attributes";
  public static final String KEY_HEADERS = "headers";
  public static final String KEY_CONTENT_DISPOSITION = "content-disposition";

  public static final String ATTR_STORED_FILE_NAME = "storedfilename";

  private final IMessage m_aTarget;

  public MessageParameters (@NonNull final IMessage aTarget)
  {
    m_aTarget = ValueEnforcer.notNull (aTarget, "Target");
  }

  @Override
  public void setParameter (@NonNull final String sKey, @Nullable final String sValue)
                                                                                       throws AS2InvalidParameterException
  {
    final String [] aKeyParts = StringHelper.getExplodedArray ('.', sKey, 2);
    if (aKeyParts.length != 2)
      throw new AS2InvalidParameterException ("Invalid key format", this, sKey, null);

    final String sArea = aKeyParts[0];
    final String sAreaID = aKeyParts[1];

    if (sArea.equals (KEY_SENDER))
      m_aTarget.partnership ().setSenderID (sAreaID, sValue);
    else
      if (sArea.equals (KEY_RECEIVER))
        m_aTarget.partnership ().setReceiverID (sAreaID, sValue);
      else
        if (sArea.equals (KEY_ATTRIBUTES))
          m_aTarget.attrs ().putIn (sAreaID, sValue);
        else
          if (sArea.equals (KEY_HEADERS))
            m_aTarget.headers ().setHeader (sAreaID, sValue);
          else
            throw new AS2InvalidParameterException ("Invalid area in key", this, sKey, null);
  }

  @NonNull
  @Nonempty
  private String _getContentDispositionFilename ()
  {
    String sReturnFilename = "noContentDispositionFilename";
    final String sFilename = m_aTarget.getContentDisposition ();
    if (StringHelper.isNotEmpty (sFilename))
    {
      try
      {
        final ContentDisposition aContentDisposition = new ContentDisposition (sFilename);
        sReturnFilename = aContentDisposition.getParameter ("filename");
      }
      catch (final Exception ex)
      {
        throw new IllegalStateException ("Error parsing parameter", ex);
      }
    }
    return sReturnFilename;
  }

  @Override
  @Nullable
  public String getParameter (@NonNull final String sKey) throws AS2InvalidParameterException
  {
    final String [] aKeyParts = StringHelper.getExplodedArray ('.', sKey);
    if (aKeyParts.length != 2)
      throw new AS2InvalidParameterException ("Invalid key format", this, sKey, null);

    final String sArea = aKeyParts[0];
    final String sAreaValue = aKeyParts[1];

    if (sArea.equals (KEY_SENDER))
      return m_aTarget.partnership ().getSenderID (sAreaValue);

    if (sArea.equals (KEY_RECEIVER))
      return m_aTarget.partnership ().getReceiverID (sAreaValue);

    if (sArea.equals (KEY_ATTRIBUTES))
      return m_aTarget.attrs ().getAsString (sAreaValue);

    if (sArea.equals (KEY_HEADERS))
      return m_aTarget.getHeader (sAreaValue);

    if (sArea.equals (KEY_CONTENT_DISPOSITION) && sAreaValue.equals ("filename"))
      return _getContentDispositionFilename ();

    throw new AS2InvalidParameterException ("Invalid area in key", this, sKey, null);
  }

  @Nullable
  public static String getEscapedString (@Nullable final String sUnescaped)
  {
    // Based on https://github.com/phax/phase2/pull/19
    return StringReplace.replaceAll (sUnescaped, "$", "$$");
  }
}
