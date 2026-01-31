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
package com.helger.phase2.message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.http.CHttp;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;

public abstract class AbstractMessageMDN extends AbstractBaseMessage implements IMessageMDN
{
  private IMessage m_aMessage;
  private MimeBodyPart m_aData;
  private String m_sText;

  public AbstractMessageMDN (@NonNull final IMessage aMsg)
  {
    // Link MDN and message
    setMessage (aMsg);
    aMsg.setMDN (this);
  }

  @NonNull
  public final IMessage getMessage ()
  {
    return m_aMessage;
  }

  public final void setMessage (@NonNull final IMessage aMessage)
  {
    ValueEnforcer.notNull (aMessage, "Message");
    m_aMessage = aMessage;
  }

  @Nullable
  public final MimeBodyPart getData ()
  {
    return m_aData;
  }

  public final void setData (@Nullable final MimeBodyPart aData)
  {
    m_aData = aData;
  }

  @Nullable
  public final String getText ()
  {
    return m_sText;
  }

  public final void setText (@Nullable final String sText)
  {
    m_sText = sText;
  }

  @NonNull
  @Nonempty
  public String getAsString ()
  {
    final StringBuilder aSB = new StringBuilder ();
    aSB.append ("MDN From:")
       .append (partnership ().getAllSenderIDs ().toString ())
       .append (CHttp.EOL)
       .append ("To:")
       .append (partnership ().getAllReceiverIDs ().toString ())
       .append (CHttp.EOL)
       .append ("Headers:")
       .append (headers ().toString ())
       .append (CHttp.EOL)
       .append ("Attributes:")
       .append (attrs ().toString ())
       .append (CHttp.EOL)
       .append ("Text:")
       .append (CHttp.EOL)
       .append (getText ())
       .append (CHttp.EOL);
    return aSB.toString ();
  }

  @Override
  public String toString ()
  {
    // Avoid endless loop when we reference Message which again references MDN
    return ToStringGenerator.getDerived (super.toString ())
                            .append ("Message", m_aMessage != null ? m_aMessage.getMessageID () : null)
                            .append ("Data", m_aData)
                            .append ("Text", m_sText)
                            .getToString ();
  }

  private void readObject (final ObjectInputStream aOIS) throws IOException, ClassNotFoundException
  {
    // read in text
    m_sText = (String) aOIS.readObject ();

    try
    {
      // read in mime body
      if (aOIS.read () == 1)
        m_aData = new MimeBodyPart (aOIS);
      else
        m_aData = null;
    }
    catch (final MessagingException ex)
    {
      throw new IOException ("Messaging exception", ex);
    }
  }

  private void writeObject (@NonNull final ObjectOutputStream aOOS) throws IOException
  {
    // write text
    aOOS.writeObject (m_sText);

    // write the mime body
    try (final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ())
    {
      if (m_aData != null)
      {
        aBAOS.write (1);
        m_aData.writeTo (aBAOS);
      }
      else
      {
        aBAOS.write (0);
      }
      aBAOS.writeTo (aOOS);
    }
    catch (final MessagingException ex)
    {
      throw new IOException ("Messaging exception", ex);
    }
  }
}
