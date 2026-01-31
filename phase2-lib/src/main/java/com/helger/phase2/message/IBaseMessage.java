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

import java.io.Serializable;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.annotation.style.ReturnsMutableObject;
import com.helger.http.CHttpHeader;
import com.helger.http.header.HttpHeaderMap;
import com.helger.phase2.partner.Partnership;
import com.helger.typeconvert.collection.IStringMap;

/**
 * Base interface for {@link IMessage} and {@link IMessageMDN}. Must be Serializable, so that
 * writing to disk for re-sending works.
 *
 * @author Philip Helger
 */
public interface IBaseMessage extends Serializable
{
  /**
   * @return Mutable custom attribute map. Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableObject
  IStringMap attrs ();

  /**
   * @return Mutable HTTP header map. Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableObject
  HttpHeaderMap headers ();

  @Nullable
  default String getHeader (@NonNull final String sName)
  {
    return getHeaderCombined (sName, ", ");
  }

  @Nullable
  default String getHeaderCombined (@NonNull final String sName, @NonNull final String sDelimiter)
  {
    return headers ().getHeaderCombined (sName, sDelimiter);
  }

  @Nullable
  default String getHeaderOrDefault (@NonNull final String sName, @Nullable final String sDefault)
  {
    final String ret = getHeader (sName);
    return ret != null ? ret : sDefault;
  }

  default boolean containsHeader (@Nullable final String sName)
  {
    return headers ().containsHeaders (sName);
  }

  /**
   * @return Special message ID header
   */
  @Nullable
  default String getMessageID ()
  {
    return getHeader (CHttpHeader.MESSAGE_ID);
  }

  /**
   * Set special message ID header
   *
   * @param sMessageID
   *        Message ID
   */
  default void setMessageID (@Nullable final String sMessageID)
  {
    headers ().setHeader (CHttpHeader.MESSAGE_ID, sMessageID);
  }

  @NonNull
  String generateMessageID ();

  /**
   * Shortcut for <code>setMessageID (generateMessageID ())</code>
   */
  default void updateMessageID ()
  {
    setMessageID (generateMessageID ());
  }

  @NonNull
  @ReturnsMutableObject
  Partnership partnership ();

  void setPartnership (@NonNull Partnership aPartnership);

  @NonNull
  @Nonempty
  String getLoggingText ();

  @NonNull
  @Nonempty
  String getAsString ();
}
