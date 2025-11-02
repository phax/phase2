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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.http.CHttpHeader;
import com.helger.phase2.CPhase2Info;
import com.helger.phase2.params.AS2InvalidParameterException;
import com.helger.phase2.params.CompositeParameters;
import com.helger.phase2.params.DateParameters;
import com.helger.phase2.params.MessageParameters;
import com.helger.phase2.params.RandomParameters;

public class AS2Message extends AbstractMessage
{
  /** Indicator whether the message was received (or created) */
  public static final String ATTRIBUTE_RECEIVED = "as2msg.received";
  public static final String ATTRIBUTE_RECEIVED_ENCRYPTED = "as2msg.received.encrypted";
  public static final String ATTRIBUTE_RECEIVED_SIGNED = "as2msg.received.signed";
  /** PEM encoded X509 certificate that was used to verify the signature */
  public static final String ATTRIBUTE_RECEIVED_SIGNATURE_CERTIFICATE = "as2msg.received.signature.certificate";
  public static final String ATTRIBUTE_RECEIVED_COMPRESSED = "as2msg.received.compressed";
  /** Optional attribute storing the created MIC (see #74) */
  public static final String ATTRIBUTE_MIC = "MIC";

  public static final String PROTOCOL_AS2 = "as2";
  public static final String DEFAULT_ID_FORMAT = CPhase2Info.NAME +
                                                 "-$date.ddMMuuuuHHmmssZ$-$rand.1234$@$msg.sender.as2_id$_$msg.receiver.as2_id$";

  public AS2Message ()
  {}

  @NonNull
  @Nonempty
  public final String getProtocol ()
  {
    return PROTOCOL_AS2;
  }

  @Override
  @NonNull
  @Nonempty
  public String generateMessageID ()
  {
    final CompositeParameters aParams = new CompositeParameters (false).add ("date", new DateParameters ())
                                                                       .add ("msg", new MessageParameters (this))
                                                                       .add ("rand", new RandomParameters ());

    final String sIDFormat = partnership ().getMessageIDFormat (DEFAULT_ID_FORMAT);

    final StringBuilder aSB = new StringBuilder ();
    aSB.append ('<');
    try
    {
      aSB.append (aParams.format (sIDFormat));
    }
    catch (final AS2InvalidParameterException ex)
    {
      // useless, but what to do?
      aSB.append (sIDFormat);
    }
    aSB.append ('>');
    return aSB.toString ();
  }

  public boolean isRequestingMDN ()
  {
    // Requesting by partnership?
    if (partnership ().getAS2MDNTo () != null)
      return true;

    // Requesting by request?
    if (containsHeader (CHttpHeader.DISPOSITION_NOTIFICATION_TO))
      return true;

    // Default: no
    return false;
  }

  public boolean isRequestingAsynchMDN ()
  {
    // Requesting by partnership?
    // Same as regular MDN + PA_AS2_RECEIPT_OPTION
    if (partnership ().getAS2MDNTo () != null && partnership ().getAS2ReceiptDeliveryOption () != null)
      return true;

    // Requesting by request?
    // Same as regular MDN + HEADER_RECEIPT_DELIVERY_OPTION
    if (containsHeader (CHttpHeader.DISPOSITION_NOTIFICATION_TO) &&
        containsHeader (CHttpHeader.RECEIPT_DELIVERY_OPTION))
      return true;

    // Default: no
    return false;
  }

  /**
   * @return The URL where to send the async MDN to. May be <code>null</code> if no MDN or a sync
   *         MDN is needed.
   */
  @Nullable
  public String getAsyncMDNurl ()
  {
    // Only this field determines where to send the async MDN to
    return getHeader (CHttpHeader.RECEIPT_DELIVERY_OPTION);
  }
}
