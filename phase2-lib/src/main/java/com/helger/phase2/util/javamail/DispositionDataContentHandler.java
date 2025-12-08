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
package com.helger.phase2.util.javamail;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.array.ArrayHelper;
import com.helger.base.io.stream.StreamHelper;
import com.helger.mime.CMimeType;
import com.helger.mime.IMimeType;
import com.helger.mime.parse.MimeTypeParser;

import jakarta.activation.ActivationDataFlavor;
import jakarta.activation.DataContentHandler;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;

public class DispositionDataContentHandler implements DataContentHandler
{
  private static final ActivationDataFlavor ADF1;
  private static final ActivationDataFlavor [] ADFS;

  static
  {
    ADF1 = new ActivationDataFlavor (MimeBodyPart.class,
                                     "message/disposition-notification",
                                     "Disposition Notification");
    ADFS = new ActivationDataFlavor [] { ADF1 };
  }

  public DispositionDataContentHandler ()
  {}

  @Nullable
  public byte [] getContent (@NonNull final DataSource aDS) throws IOException
  {
    return StreamHelper.getAllBytes (StreamHelper.getBuffered (aDS.getInputStream ()));
  }

  @Nullable
  public byte [] getTransferData (final ActivationDataFlavor df, @NonNull final DataSource ds) throws IOException
  {
    if (ADF1.equals (df))
      return getContent (ds);
    return null;
  }

  @NonNull
  @ReturnsMutableCopy
  public ActivationDataFlavor [] getTransferDataFlavors ()
  {
    return ArrayHelper.getCopy (ADFS);
  }

  @Nullable
  private static Charset _getCharset (@Nullable final String sMimeType)
  {
    try
    {
      final IMimeType aMimeType = MimeTypeParser.parseMimeType (sMimeType);
      if (aMimeType != null)
      {
        final String sCharset = aMimeType.getParameterValueWithName (CMimeType.PARAMETER_NAME_CHARSET);
        if (sCharset != null)
          return Charset.forName (sCharset);
      }
      // fall through
    }
    catch (final Exception ex)
    {
      // Fall through
    }
    return null;
  }

  public void writeTo (final Object obj, final String sMimeType, @NonNull final OutputStream aOS) throws IOException
  {
    if (obj instanceof MimeBodyPart)
    {
      try
      {
        ((MimeBodyPart) obj).writeTo (aOS);
      }
      catch (final MessagingException me)
      {
        throw new IOException (me);
      }
    }
    else
      if (obj instanceof MimeMultipart)
      {
        try
        {
          ((MimeMultipart) obj).writeTo (aOS);
        }
        catch (final MessagingException me)
        {
          throw new IOException (me);
        }
      }
      else
        if (obj instanceof byte [])
        {
          aOS.write ((byte []) obj);
        }
        else
          if (obj instanceof String)
          {
            final Charset aCharset = _getCharset (sMimeType);
            if (aCharset != null)
              aOS.write (((String) obj).getBytes (aCharset));
            else
              aOS.write (((String) obj).getBytes ());
          }
          else
          {
            throw new IOException ("Unknown object type: " + obj.getClass ().getName ());
          }
  }
}
