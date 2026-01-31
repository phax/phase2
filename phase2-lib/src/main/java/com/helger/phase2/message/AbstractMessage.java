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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonempty;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.http.CHttpHeader;
import com.helger.phase2.util.http.TempSharedFileInputStream;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;

/**
 * Abstract base implementation of the {@link IMessage} interface.
 *
 * @author Philip Helger
 */
public abstract class AbstractMessage extends AbstractBaseMessage implements IMessage
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AbstractMessage.class);

  private MimeBodyPart m_aData;
  private IMessageMDN m_aMDN;
  private TempSharedFileInputStream m_aTempSharedFileInputStream;

  public AbstractMessage ()
  {}

  private void readObject (@NonNull final ObjectInputStream aOIS) throws IOException, ClassNotFoundException
  {
    try
    {
      // read in mime body
      if (aOIS.read () == 1)
        m_aData = new MimeBodyPart (aOIS);
    }
    catch (final MessagingException ex)
    {
      throw new IOException ("Messaging exception", ex);
    }

    // read in MDN
    m_aMDN = (IMessageMDN) aOIS.readObject ();
    if (m_aMDN != null)
      m_aMDN.setMessage (this);
  }

  private void writeObject (@NonNull final ObjectOutputStream aOOS) throws IOException
  {
    // write the mime body
    // Write to BAOS first to avoid serializing an incomplete object
    try (final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ())
    {
      try
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
      }
      catch (final MessagingException ex)
      {
        throw new IOException ("Messaging exception: " + ex.getMessage ());
      }

      aOOS.write (aBAOS.toByteArray ());
    }

    // write the message's MDN
    aOOS.writeObject (m_aMDN);
  }

  @Nullable
  public final MimeBodyPart getData ()
  {
    return m_aData;
  }

  public final void setData (@Nullable final MimeBodyPart aData)
  {
    // Remember data
    m_aData = aData;

    if (aData != null)
    {
      // Set content type from data
      try
      {
        setContentType (aData.getContentType ());
      }
      catch (final MessagingException ex)
      {
        LOGGER.warn ("Failed to set the Content-Type from the MimeBodyPart. Defaulting to null.");
        setContentType (null);
      }

      // Set content disposition from data
      try
      {
        setContentDisposition (aData.getHeader (CHttpHeader.CONTENT_DISPOSITION, null));
      }
      catch (final MessagingException ex)
      {
        LOGGER.warn ("Failed to set the Content-Disposition from the MimeBodyPart. Defaulting to null.");
        setContentDisposition (null);
      }
    }
  }

  @Nullable
  public final IMessageMDN getMDN ()
  {
    return m_aMDN;
  }

  public final void setMDN (@Nullable final IMessageMDN aMDN)
  {
    m_aMDN = aMDN;
  }

  @Nullable
  public final TempSharedFileInputStream getTempSharedFileInputStream ()
  {
    return m_aTempSharedFileInputStream;
  }

  public final void setTempSharedFileInputStream (@Nullable final TempSharedFileInputStream aTempSharedFileInputStream)
  {
    m_aTempSharedFileInputStream = aTempSharedFileInputStream;
  }

  @NonNull
  @Nonempty
  public String getAsString ()
  {
    // For debug logging it's okay to use '\n' only
    final char cNewLine = '\n';

    final StringBuilder aSB = new StringBuilder ();
    aSB.append ("Message From:").append (partnership ().getAllSenderIDs ());
    aSB.append (cNewLine).append ("To:").append (partnership ().getAllReceiverIDs ());

    aSB.append (cNewLine).append ("Headers:").append (headers ().toString ());
    aSB.append (cNewLine).append ("Attributes:").append (attrs ().toString ());

    final IMessageMDN aMDN = getMDN ();
    if (aMDN != null)
      aSB.append (cNewLine).append ("MDN:").append (aMDN.getAsString ());
    return aSB.toString ();
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("data", m_aData).append ("MDN", m_aMDN).getToString ();
  }
}
