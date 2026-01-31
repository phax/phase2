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

import java.util.StringTokenizer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.phase2.message.IMessageMDN;

/**
 * Access to MDN parameters
 *
 * @author unknown
 */
public class MessageMDNParameters extends AbstractParameterParser
{
  public static final String KEY_MESSAGE = "msg";
  public static final String KEY_SENDER = "sender";
  public static final String KEY_RECEIVER = "receiver";
  public static final String KEY_TEXT = "text";
  public static final String KEY_ATTRIBUTES = "attributes";
  public static final String KEY_HEADERS = "headers";

  private static final Logger LOGGER = LoggerFactory.getLogger (MessageMDNParameters.class);

  private final IMessageMDN m_aMDN;

  public MessageMDNParameters (@NonNull final IMessageMDN aTarget)
  {
    m_aMDN = ValueEnforcer.notNull (aTarget, "Target");
  }

  @Override
  public void setParameter (final String sKey, final String sValue) throws AS2InvalidParameterException
  {
    final StringTokenizer aKeyParts = new StringTokenizer (sKey, ".", false);

    if (aKeyParts.countTokens () < 2)
      throw new AS2InvalidParameterException ("Invalid key format", this, "key", sKey);

    final String sArea = aKeyParts.nextToken ();
    if (sArea.equals (KEY_MESSAGE))
    {
      if (aKeyParts.countTokens () < 3)
        throw new AS2InvalidParameterException ("Invalid key format", this, "key", sKey);

      // Set parameter of message
      final String sMessageKey = aKeyParts.nextToken () + "." + aKeyParts.nextToken ();
      new MessageParameters (m_aMDN.getMessage ()).setParameter (sMessageKey, sValue);
    }
    else
    {
      final String sAreaValue = aKeyParts.nextToken ();
      if (sArea.equals (KEY_TEXT))
        m_aMDN.setText (sValue);
      else
        if (sArea.equals (KEY_ATTRIBUTES))
          m_aMDN.attrs ().putIn (sAreaValue, sValue);
        else
          if (sArea.equals (KEY_HEADERS))
            m_aMDN.headers ().setHeader (sAreaValue, sValue);
          else
            throw new AS2InvalidParameterException ("Invalid area in key", this, "key", sKey);
    }
  }

  @Override
  @Nullable
  public String getParameter (final String sKey) throws AS2InvalidParameterException
  {
    final StringTokenizer aKeyParts = new StringTokenizer (sKey, ".", false);
    if (aKeyParts.countTokens () > 2)
    {
      // Read from message
      final String sSkippedToken = aKeyParts.nextToken ();
      if (!"msg".equals (sSkippedToken))
      {
        LOGGER.warn ("Skipping the token '" +
                     sSkippedToken +
                     "' and accessing the message parameters instead of the MDN property. Please use `msg` as the name of the skipped token to indicate that this is done by purpose.");
      }

      final String sMsgKey = aKeyParts.nextToken () + "." + aKeyParts.nextToken ();
      return new MessageParameters (m_aMDN.getMessage ()).getParameter (sMsgKey);
    }

    if (aKeyParts.countTokens () < 2)
      throw new AS2InvalidParameterException ("Invalid key format", this, "key", sKey);

    final String sArea = aKeyParts.nextToken ();
    final String sAreaValue = aKeyParts.nextToken ();

    if (sArea.equals (KEY_SENDER))
      return m_aMDN.partnership ().getSenderID (sAreaValue);
    if (sArea.equals (KEY_RECEIVER))
      return m_aMDN.partnership ().getReceiverID (sAreaValue);
    if (sArea.equals (KEY_TEXT))
      return m_aMDN.getText ();
    if (sArea.equals (KEY_ATTRIBUTES))
      return m_aMDN.attrs ().getAsString (sAreaValue);
    if (sArea.equals (KEY_HEADERS))
      return m_aMDN.getHeader (sAreaValue);

    throw new AS2InvalidParameterException ("Invalid area in key", this, "key", sKey);
  }
}
