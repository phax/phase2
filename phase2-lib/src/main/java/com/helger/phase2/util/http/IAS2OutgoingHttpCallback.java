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
package com.helger.phase2.util.http;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.phase2.crypto.MIC;

/**
 * Remote communication callback for easy logging of remove interactions.
 *
 * @author Philip Helger
 * @since 4.7.1
 */
public interface IAS2OutgoingHttpCallback
{
  /**
   * Notify on outgoing messages.
   *
   * @param bIsMessage
   *        <code>true</code> if it is a message that was sent out, <code>false</code> if it was an
   *        MDN.
   * @param sSenderAS2ID
   *        The AS2 ID of the sender. May be <code>null</code>.
   * @param sReceiverAS2ID
   *        The AS2 ID of the receiver. May be <code>null</code>.
   * @param sAS2MessageID
   *        The AS2 message ID of the outgoing message. May be <code>null</code>.
   * @param aMIC
   *        The MIC that was calculated for the message. Only set for messages. May be
   *        <code>null</code>.
   * @param eCTE
   *        The content transfer encoding uses for the message. Only set for messages. May be
   *        <code>null</code>.
   * @param aURL
   *        The URL the message was sent to. Never <code>null</code>.
   * @param nHttpResponseCode
   *        The HTTP response code received.
   */
  void onOutgoingHttpMessage (boolean bIsMessage,
                              @Nullable String sSenderAS2ID,
                              @Nullable String sReceiverAS2ID,
                              @Nullable String sAS2MessageID,
                              @Nullable MIC aMIC,
                              @Nullable EContentTransferEncoding eCTE,
                              @NonNull String aURL,
                              int nHttpResponseCode);
}
