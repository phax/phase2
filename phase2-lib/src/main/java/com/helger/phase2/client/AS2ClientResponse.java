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
package com.helger.phase2.client;

import java.security.cert.X509Certificate;
import java.time.Duration;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.phase2.message.AS2MessageMDN;
import com.helger.phase2.message.IMessageMDN;

/**
 * This class contains the basic content that was received from an AS2 server as a response.
 *
 * @author oleo Date: May 12, 2010 Time: 5:53:45 PM
 * @author Philip Helger
 */
public class AS2ClientResponse
{
  private String m_sOriginalMessageID;
  private Throwable m_aThrowable;
  private IMessageMDN m_aMDN;
  private Duration m_aExecutionDuration;
  private X509Certificate m_aMDNVerificationCertificate;

  /**
   * Default constructor.
   */
  public AS2ClientResponse ()
  {}

  /**
   * Set the message ID of the message that was sent out. Any existing value will be overwritten.
   *
   * @param sOriginalMessageID
   *        The original AS2 message ID. May not be <code>null</code>.
   * @see com.helger.phase2.message.IMessage#getMessageID()
   */
  public void setOriginalMessageID (@NonNull final String sOriginalMessageID)
  {
    ValueEnforcer.notNull (sOriginalMessageID, "OriginalMessageID");
    m_sOriginalMessageID = sOriginalMessageID;
  }

  /**
   * @return The message ID of the original AS2 message. May be <code>null</code> if not set (but
   *         never <code>null</code> when using in conjunction with {@link AS2Client}).
   */
  @Nullable
  public String getOriginalMessageID ()
  {
    return m_sOriginalMessageID;
  }

  /**
   * Set an exception that occurred. Any existing value will be overwritten.
   *
   * @param aThrowable
   *        The raised exception. May not be <code>null</code>.
   */
  public void setException (@NonNull final Throwable aThrowable)
  {
    ValueEnforcer.notNull (aThrowable, "Throwable");
    m_aThrowable = aThrowable;
  }

  /**
   * @return <code>true</code> if an exception is present, <code>false</code> otherwise.
   */
  public boolean hasException ()
  {
    return m_aThrowable != null;
  }

  /**
   * @return The stored exception. May be <code>null</code> if none is present. This usually means
   *         that an MDN is present.
   * @see #hasException()
   * @see #getMDN()
   */
  @Nullable
  public Throwable getException ()
  {
    return m_aThrowable;
  }

  /**
   * Set the retrieved MDN. Any existing value will be overwritten.
   *
   * @param aMDN
   *        The MDN retrieved. May not be <code>null</code>.
   */
  public void setMDN (@NonNull final IMessageMDN aMDN)
  {
    ValueEnforcer.notNull (aMDN, "MDN");
    m_aMDN = aMDN;
  }

  /**
   * @return <code>true</code> if an MDN is present, <code>false</code> otherwise.
   */
  public boolean hasMDN ()
  {
    return m_aMDN != null;
  }

  /**
   * @return The stored MDN. May be <code>null</code> if none is present. Usually this means that an
   *         exception is present.
   * @see #hasMDN()
   * @see #getException()
   */
  @Nullable
  public IMessageMDN getMDN ()
  {
    return m_aMDN;
  }

  /**
   * @return The message ID of the MDN if present, or <code>null</code> if no MDN is present.
   * @see #hasMDN()
   */
  @Nullable
  public String getMDNMessageID ()
  {
    return m_aMDN == null ? null : m_aMDN.getMessageID ();
  }

  /**
   * @return The text of the MDN if present, or <code>null</code> if no MDN is present.
   * @see #hasMDN()
   */
  @Nullable
  public String getMDNText ()
  {
    return m_aMDN == null ? null : m_aMDN.getText ();
  }

  /**
   * @return The disposition of the MDN if present, or <code>null</code> if no MDN is present.
   * @see #hasMDN()
   */
  @Nullable
  public String getMDNDisposition ()
  {
    return m_aMDN == null ? null : m_aMDN.attrs ().getAsString (AS2MessageMDN.MDNA_DISPOSITION);
  }

  /**
   * @return The MDN X509 certificate that was retrieved. May be <code>null</code> if the MDN was
   *         not signed.
   * @since 4.4.1
   */
  @Nullable
  public X509Certificate getMDNVerificationCertificate ()
  {
    return m_aMDNVerificationCertificate;
  }

  /**
   * Set the X509 certificate that was used to verify the MDN.
   *
   * @param aMDNVerificationCertificate
   *        The certificate to be used. May be <code>null</code>.
   */
  public void setMDNVerificationCertificate (@Nullable final X509Certificate aMDNVerificationCertificate)
  {
    m_aMDNVerificationCertificate = aMDNVerificationCertificate;
  }

  /**
   * Set the execution duration of the client request. Any existing value will be overwritten.
   *
   * @param aExecutionDuration
   *        The duration to be set. May not be <code>null</code>.
   */
  public void setExecutionDuration (@NonNull final Duration aExecutionDuration)
  {
    ValueEnforcer.notNull (aExecutionDuration, "ExecutionDuration");
    m_aExecutionDuration = aExecutionDuration;
  }

  /**
   * @return <code>true</code> if an execution duration is present, <code>false</code> otherwise.
   */
  public boolean hasExecutionDuration ()
  {
    return m_aExecutionDuration != null;
  }

  /**
   * @return The execution duration or <code>null</code> if not present. When using
   *         {@link AS2Client} the execution time is always set.
   */
  @Nullable
  public Duration getExecutionDuration ()
  {
    return m_aExecutionDuration;
  }

  /**
   * @return The whole client response in a single string for debugging purposes.
   */
  @NonNull
  public String getAsString ()
  {
    // For logging it's okay to use \n only
    final char cNewLine = '\n';
    final StringBuilder aSB = new StringBuilder ();
    aSB.append (cNewLine);
    if (m_sOriginalMessageID != null)
      aSB.append ("OriginalMessageID: ").append (m_sOriginalMessageID).append (cNewLine);
    if (getMDNMessageID () != null)
      aSB.append ("MDN MessageID: ").append (getMDNMessageID ()).append (cNewLine);
    if (getMDNDisposition () != null)
      aSB.append ("MDN Disposition: ").append (getMDNDisposition ()).append (cNewLine);
    if (hasException ())
      aSB.append ("Error message: ").append (m_aThrowable.getMessage ()).append (cNewLine);
    if (getMDNText () != null)
      aSB.append ("MDN Text: ").append (getMDNText ()).append (cNewLine);
    if (hasExecutionDuration ())
      aSB.append ("Sending duration: ").append (m_aExecutionDuration.toString ()).append (cNewLine);
    return aSB.toString ();
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("OriginalMessageID", m_sOriginalMessageID)
                                       .append ("Throwable", m_aThrowable)
                                       .append ("MDN", m_aMDN)
                                       .append ("MDNVerificationCertificate", m_aMDNVerificationCertificate)
                                       .append ("ExecutionDuration", m_aExecutionDuration)
                                       .getToString ();
  }
}
