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
package com.helger.phase2.message;

import com.helger.http.CHttpHeader;
import com.helger.phase2.util.http.TempSharedFileInputStream;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.mail.internet.MimeBodyPart;

/**
 * Base interface for an AS2 Message
 *
 * @author Philip Helger
 */
public interface IMessage extends IBaseMessage
{
  /**
   * Shortcut for <code>getHeader (CAS2Header.HEADER_AS2_FROM)</code>
   *
   * @return Sender ID
   */
  @Nullable
  default String getAS2From ()
  {
    return getHeader (CHttpHeader.AS2_FROM);
  }

  /**
   * Shortcut for <code>getHeader (CAS2Header.HEADER_AS2_TO)</code>
   *
   * @return Receiver ID
   */
  @Nullable
  default String getAS2To ()
  {
    return getHeader (CHttpHeader.AS2_TO);
  }

  @Nullable
  default String getContentType ()
  {
    return getHeader (CHttpHeader.CONTENT_TYPE);
  }

  default void setContentType (@Nullable final String sContentType)
  {
    headers ().setHeader (CHttpHeader.CONTENT_TYPE, sContentType);
  }

  @Nullable
  default String getContentDisposition ()
  {
    return getHeader (CHttpHeader.CONTENT_DISPOSITION);
  }

  default void setContentDisposition (@Nullable final String sContentDisposition)
  {
    headers ().setHeader (CHttpHeader.CONTENT_DISPOSITION, sContentDisposition);
  }

  @Nullable
  default String getSubject ()
  {
    return getHeader (CHttpHeader.SUBJECT);
  }

  default void setSubject (@Nullable final String sSubject)
  {
    headers ().setHeader (CHttpHeader.SUBJECT, sSubject);
  }

  @Nullable
  MimeBodyPart getData ();

  void setData (@Nonnull MimeBodyPart aData);

  @Nullable
  MimeBodyPart getMICSource ();

  void setMICSource (@Nullable MimeBodyPart aMICSource);

  @Nullable
  IMessageMDN getMDN ();

  void setMDN (@Nullable IMessageMDN aMDN);

  String getProtocol ();

  boolean isRequestingMDN ();

  boolean isRequestingAsynchMDN ();

  @Nullable
  TempSharedFileInputStream getTempSharedFileInputStream ();

  void setTempSharedFileInputStream (@Nullable TempSharedFileInputStream m_TempSharedFileInputStream);
}
