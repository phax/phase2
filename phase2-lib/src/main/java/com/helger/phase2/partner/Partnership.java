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
package com.helger.phase2.partner;

import java.io.Serializable;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.equals.EqualsHelper;
import com.helger.base.state.EChange;
import com.helger.base.state.ETriState;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.phase2.crypto.ECompressionType;
import com.helger.phase2.crypto.ECryptoAlgorithmCrypt;
import com.helger.phase2.crypto.ECryptoAlgorithmSign;
import com.helger.typeconvert.collection.IStringMap;
import com.helger.typeconvert.collection.StringMap;

/**
 * This class represents a single partnership. It has a unique name, a set of sender and receiver
 * specific attributes (like AS2 ID, Email and key alias) and a set of generic attributes that are
 * interpreted depending on the context.
 *
 * @author Philip Helger
 */
public class Partnership implements Serializable
{
  public static final String DEFAULT_NAME = "auto-created-dummy";

  private static final String STRING_TRUE = "true";
  private static final String STRING_FALSE = "false";

  private String m_sName;
  private final StringMap m_aSenderAttrs = new StringMap ();
  private final StringMap m_aReceiverAttrs = new StringMap ();
  private final StringMap m_aAttributes = new StringMap ();

  public Partnership (@NonNull final String sName)
  {
    setName (sName);
  }

  /**
   * @return The partnership name. Never <code>null</code>.
   */
  @NonNull
  public final String getName ()
  {
    return m_sName;
  }

  public final void setName (@NonNull final String sName)
  {
    m_sName = ValueEnforcer.notNull (sName, "Name");
  }

  /**
   * Set an arbitrary sender ID.
   *
   * @param sKey
   *        The name of the ID. May not be <code>null</code>.
   * @param sValue
   *        The value to be set. It may be <code>null</code> in which case the attribute is removed.
   */
  public void setSenderID (@NonNull final String sKey, @Nullable final String sValue)
  {
    m_aSenderAttrs.putIn (sKey, sValue);
  }

  /**
   * Set the senders AS2 ID.
   *
   * @param sValue
   *        The value to be set. May be <code>null</code>.
   * @see #getSenderAS2ID()
   * @see #containsSenderAS2ID()
   */
  public void setSenderAS2ID (@Nullable final String sValue)
  {
    setSenderID (CPartnershipIDs.PID_AS2, sValue);
  }

  /**
   * Set the senders X509 alias.
   *
   * @param sValue
   *        The value to be set. May be <code>null</code>.
   * @see #getSenderX509Alias()
   * @see #containsSenderX509Alias()
   */
  public void setSenderX509Alias (@Nullable final String sValue)
  {
    setSenderID (CPartnershipIDs.PID_X509_ALIAS, sValue);
  }

  /**
   * Set the senders email address.
   *
   * @param sValue
   *        The value to be set. May be <code>null</code>.
   * @see #getSenderEmail()
   * @see #containsSenderEmail()
   */
  public void setSenderEmail (@Nullable final String sValue)
  {
    setSenderID (CPartnershipIDs.PID_EMAIL, sValue);
  }

  /**
   * Add all sender IDs provided in the passed map. Existing sender IDs are not altered.
   *
   * @param aMap
   *        The map to use. May be <code>null</code>.
   */
  public void addSenderIDs (@Nullable final Map <String, String> aMap)
  {
    m_aSenderAttrs.putAllIn (aMap);
  }

  /**
   * Get the value of an arbitrary sender ID
   *
   * @param sKey
   *        The name of the ID to query. May be <code>null</code>.
   * @return The contained value if the name is not <code>null</code> and contained in the sender
   *         IDs.
   */
  @Nullable
  public String getSenderID (@Nullable final String sKey)
  {
    return m_aSenderAttrs.getAsString (sKey);
  }

  /**
   * @return the sender's AS2 ID or <code>null</code> if it is not set
   * @see #setSenderAS2ID(String)
   * @see #containsSenderAS2ID()
   */
  @Nullable
  public String getSenderAS2ID ()
  {
    return getSenderID (CPartnershipIDs.PID_AS2);
  }

  /**
   * @return the sender's X509 alias or <code>null</code> if it is not set
   * @see #setSenderX509Alias(String)
   * @see #containsSenderX509Alias()
   */
  @Nullable
  public String getSenderX509Alias ()
  {
    return getSenderID (CPartnershipIDs.PID_X509_ALIAS);
  }

  /**
   * @return the sender's email address or <code>null</code> if it is not set.
   * @see #setSenderEmail(String)
   * @see #containsSenderEmail()
   */
  @Nullable
  public String getSenderEmail ()
  {
    return getSenderID (CPartnershipIDs.PID_EMAIL);
  }

  /**
   * Check if an arbitrary sender ID is present.
   *
   * @param sKey
   *        The name of the ID to query. May be <code>null</code>.
   * @return <code>true</code> if the name is not <code>null</code> and contained in the sender IDs.
   */
  public boolean containsSenderID (@Nullable final String sKey)
  {
    return m_aSenderAttrs.containsKey (sKey);
  }

  /**
   * @return <code>true</code> if the sender's AS2 ID is present, <code>false</code> otherwise.
   * @see #setSenderAS2ID(String)
   * @see #getSenderAS2ID()
   */
  public boolean containsSenderAS2ID ()
  {
    return containsSenderID (CPartnershipIDs.PID_AS2);
  }

  /**
   * @return <code>true</code> if the sender's X509 alias is present, <code>false</code> otherwise.
   * @see #setSenderX509Alias(String)
   * @see #getSenderX509Alias()
   */
  public boolean containsSenderX509Alias ()
  {
    return containsSenderID (CPartnershipIDs.PID_X509_ALIAS);
  }

  /**
   * @return <code>true</code> if the sender's email address is present, <code>false</code>
   *         otherwise.
   * @see #setSenderEmail(String)
   * @see #getSenderEmail()
   */
  public boolean containsSenderEmail ()
  {
    return containsSenderID (CPartnershipIDs.PID_EMAIL);
  }

  /**
   * @return All sender IDs. Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  public IStringMap getAllSenderIDs ()
  {
    return m_aSenderAttrs.getClone ();
  }

  /**
   * Set an arbitrary receiver ID.
   *
   * @param sKey
   *        The name of the ID. May not be <code>null</code>.
   * @param sValue
   *        The value to be set. It may be <code>null</code> in which case the attribute is removed.
   */
  public void setReceiverID (@NonNull final String sKey, @Nullable final String sValue)
  {
    m_aReceiverAttrs.putIn (sKey, sValue);
  }

  /**
   * Set the receivers AS2 ID.
   *
   * @param sValue
   *        The value to be set. May be <code>null</code>.
   * @see #getReceiverAS2ID()
   * @see #containsReceiverAS2ID()
   */
  public void setReceiverAS2ID (@Nullable final String sValue)
  {
    setReceiverID (CPartnershipIDs.PID_AS2, sValue);
  }

  /**
   * Set the receivers X509 alias.
   *
   * @param sValue
   *        The value to be set. May be <code>null</code>.
   * @see #getReceiverX509Alias()
   * @see #containsReceiverX509Alias()
   */
  public void setReceiverX509Alias (@Nullable final String sValue)
  {
    setReceiverID (CPartnershipIDs.PID_X509_ALIAS, sValue);
  }

  /**
   * Set the receivers email address.
   *
   * @param sValue
   *        The value to be set. May be <code>null</code>.
   * @see #getReceiverEmail()
   * @see #containsReceiverEmail()
   */
  public void setReceiverEmail (@Nullable final String sValue)
  {
    setReceiverID (CPartnershipIDs.PID_EMAIL, sValue);
  }

  /**
   * Add all receiver IDs provided in the passed map. Existing receiver IDs are not altered.
   *
   * @param aMap
   *        The map to use. May be <code>null</code>.
   */
  public void addReceiverIDs (@Nullable final Map <String, String> aMap)
  {
    m_aReceiverAttrs.putAllIn (aMap);
  }

  /**
   * Get the value of an arbitrary receiver ID
   *
   * @param sKey
   *        The name of the ID to query. May be <code>null</code>.
   * @return The contained value if the name is not <code>null</code> and contained in the receiver
   *         IDs.
   */
  @Nullable
  public String getReceiverID (@Nullable final String sKey)
  {
    return m_aReceiverAttrs.getAsString (sKey);
  }

  /**
   * @return the receiver's AS2 ID or <code>null</code> if it is not set
   * @see #setReceiverAS2ID(String)
   * @see #containsReceiverAS2ID()
   */
  @Nullable
  public String getReceiverAS2ID ()
  {
    return getReceiverID (CPartnershipIDs.PID_AS2);
  }

  /**
   * @return the receiver's X509 alias or <code>null</code> if it is not set
   * @see #setReceiverX509Alias(String)
   * @see #containsReceiverX509Alias()
   */
  @Nullable
  public String getReceiverX509Alias ()
  {
    return getReceiverID (CPartnershipIDs.PID_X509_ALIAS);
  }

  /**
   * @return the receiver's email address or <code>null</code> if it is not set.
   * @see #setReceiverEmail(String)
   * @see #containsReceiverEmail()
   */
  @Nullable
  public String getReceiverEmail ()
  {
    return getReceiverID (CPartnershipIDs.PID_EMAIL);
  }

  /**
   * Check if an arbitrary receiver ID is present.
   *
   * @param sKey
   *        The name of the ID to query. May be <code>null</code>.
   * @return <code>true</code> if the name is not <code>null</code> and contained in the receiver
   *         IDs.
   */
  public boolean containsReceiverID (@Nullable final String sKey)
  {
    return m_aReceiverAttrs.containsKey (sKey);
  }

  /**
   * @return <code>true</code> if the receiver's AS2 ID is present, <code>false</code> otherwise.
   * @see #setReceiverAS2ID(String)
   * @see #getReceiverAS2ID()
   */
  public boolean containsReceiverAS2ID ()
  {
    return containsReceiverID (CPartnershipIDs.PID_AS2);
  }

  /**
   * @return <code>true</code> if the receiver's X509 alias is present, <code>false</code>
   *         otherwise.
   * @see #setReceiverX509Alias(String)
   * @see #getReceiverX509Alias()
   */
  public boolean containsReceiverX509Alias ()
  {
    return containsReceiverID (CPartnershipIDs.PID_X509_ALIAS);
  }

  /**
   * @return <code>true</code> if the receiver's email address is present, <code>false</code>
   *         otherwise.
   * @see #setReceiverEmail(String)
   * @see #getReceiverEmail()
   */
  public boolean containsReceiverEmail ()
  {
    return containsReceiverID (CPartnershipIDs.PID_EMAIL);
  }

  /**
   * @return All receiver IDs. Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  public IStringMap getAllReceiverIDs ()
  {
    return m_aReceiverAttrs.getClone ();
  }

  /**
   * Set an arbitrary partnership attribute.
   *
   * @param sKey
   *        The key to be used. May not be <code>null</code>.
   * @param sValue
   *        The value to be used. If <code>null</code> an existing attribute with the provided name
   *        will be removed.
   * @return {@link EChange#CHANGED} if something changed. Never <code>null</code>.
   */
  @NonNull
  public EChange setAttribute (@NonNull final String sKey, @Nullable final String sValue)
  {
    if (sValue == null)
      return m_aAttributes.removeObject (sKey);
    return m_aAttributes.putIn (sKey, sValue);
  }

  /**
   * Get the value associated with the given attribute name.
   *
   * @param sKey
   *        Attribute name to search. May be <code>null</code>.
   * @return <code>null</code> if the attribute name was <code>null</code> or if no such attribute
   *         is contained.
   * @see #getAttribute(String, String)
   */
  @Nullable
  public String getAttribute (@Nullable final String sKey)
  {
    return m_aAttributes.getAsString (sKey);
  }

  /**
   * Get the value associated with the given attribute name or the default values.
   *
   * @param sKey
   *        Attribute name to search. May be <code>null</code>.
   * @param sDefault
   *        Default value to be returned if no such attribute is present.
   * @return The provided default value if the attribute name was <code>null</code> or if no such
   *         attribute is contained.
   * @see #getAttribute(String)
   */
  @Nullable
  public String getAttribute (@Nullable final String sKey, @Nullable final String sDefault)
  {
    return m_aAttributes.getAsString (sKey, sDefault);
  }

  @Nullable
  public String getAS2URL ()
  {
    return getAttribute (CPartnershipIDs.PA_AS2_URL);
  }

  @NonNull
  public EChange setAS2URL (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_AS2_URL, sValue);
  }

  /**
   * @return The URL to send the MDN to. May be <code>null</code>.
   * @see #getAS2ReceiptDeliveryOption()
   */
  @Nullable
  public String getAS2MDNTo ()
  {
    return getAttribute (CPartnershipIDs.PA_AS2_MDN_TO);
  }

  /**
   * Set the URL to send the MDN to. For async MDN also call
   * {@link #setAS2ReceiptDeliveryOption(String)}.
   *
   * @param sValue
   *        The async MDN URL. May be <code>null</code>.
   * @return {@link EChange}.
   * @see #setAS2ReceiptDeliveryOption(String)
   */
  @NonNull
  public EChange setAS2MDNTo (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_AS2_MDN_TO, sValue);
  }

  /**
   * @return The MDN options corresponding to the <code>Disposition-Notification-Options</code>
   *         header. May be <code>null</code>.
   */
  @Nullable
  public String getAS2MDNOptions ()
  {
    return getAttribute (CPartnershipIDs.PA_AS2_MDN_OPTIONS);
  }

  /**
   * Set the MDN options corresponding to the <code>Disposition-Notification-Options</code> header.
   *
   * @param sValue
   *        The value to be set. May be <code>null</code>.
   * @return {@link EChange}
   */
  @NonNull
  public EChange setAS2MDNOptions (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_AS2_MDN_OPTIONS, sValue);
  }

  /**
   * @return The return URL for async MDN when sending messages. May be <code>null</code>.
   * @see #getAS2MDNTo()
   * @since 3.0.4
   */
  @Nullable
  public String getAS2ReceiptDeliveryOption ()
  {
    return getAttribute (CPartnershipIDs.PA_AS2_RECEIPT_DELIVERY_OPTION);
  }

  /**
   * Set the return URL for async MDNs when sending messages. When setting it, also set
   * {@link #setAS2MDNTo(String)}.
   *
   * @param sValue
   *        The async MDN url. May be <code>null</code>.
   * @return {@link EChange}
   * @see #setAS2MDNTo(String)
   * @since 3.0.4
   */
  @NonNull
  public EChange setAS2ReceiptDeliveryOption (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_AS2_RECEIPT_DELIVERY_OPTION, sValue);
  }

  @Nullable
  public String getMessageIDFormat (@Nullable final String sDefault)
  {
    return getAttribute (CPartnershipIDs.PA_MESSAGEID_FORMAT, sDefault);
  }

  @NonNull
  public EChange setMessageIDFormat (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_MESSAGEID_FORMAT, sValue);
  }

  @Nullable
  public String getMDNSubject ()
  {
    return getAttribute (CPartnershipIDs.PA_MDN_SUBJECT);
  }

  @NonNull
  public EChange setMDNSubject (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_MDN_SUBJECT, sValue);
  }

  public boolean isBlockErrorMDN ()
  {
    return m_aAttributes.containsKey (CPartnershipIDs.PA_BLOCK_ERROR_MDN);
  }

  @NonNull
  public EChange setBlockErrorMDN (final boolean bBlock)
  {
    return setAttribute (CPartnershipIDs.PA_BLOCK_ERROR_MDN, bBlock ? STRING_TRUE : null);
  }

  @Nullable
  public String getDateFormat (@Nullable final String sDefault)
  {
    return getAttribute (CPartnershipIDs.PA_DATE_FORMAT, sDefault);
  }

  @NonNull
  public EChange setDateFormat (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_DATE_FORMAT, sValue);
  }

  @Nullable
  public String getEncryptAlgorithm ()
  {
    return getAttribute (CPartnershipIDs.PA_ENCRYPT);
  }

  @NonNull
  public EChange setEncryptAlgorithm (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_ENCRYPT, sValue);
  }

  @NonNull
  public EChange setEncryptAlgorithm (@Nullable final ECryptoAlgorithmCrypt eValue)
  {
    return setEncryptAlgorithm (eValue == null ? null : eValue.getID ());
  }

  @Nullable
  public String getSigningAlgorithm ()
  {
    return getAttribute (CPartnershipIDs.PA_SIGN);
  }

  @NonNull
  public EChange setSigningAlgorithm (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_SIGN, sValue);
  }

  @NonNull
  public EChange setSigningAlgorithm (@Nullable final ECryptoAlgorithmSign eValue)
  {
    return setSigningAlgorithm (eValue == null ? null : eValue.getID ());
  }

  @Nullable
  public String getProtocol ()
  {
    return getAttribute (CPartnershipIDs.PA_PROTOCOL);
  }

  @NonNull
  public EChange setProtocol (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_PROTOCOL, sValue);
  }

  @Nullable
  public String getSubject ()
  {
    return getAttribute (CPartnershipIDs.PA_SUBJECT);
  }

  @NonNull
  public EChange setSubject (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_SUBJECT, sValue);
  }

  /**
   * Get the Content-Transfer-Encoding for sending messages.
   *
   * @param sDefault
   *        Default to be returned if none is present. May be <code>null</code>.
   * @return The partnership Content-Transfer-Encoding or the provided default value.
   */
  @Nullable
  public String getContentTransferEncodingSend (@Nullable final String sDefault)
  {
    return getAttribute (CPartnershipIDs.PA_CONTENT_TRANSFER_ENCODING_SEND, sDefault);
  }

  /**
   * Set the Content-Transfer-Encoding for sending messages.
   *
   * @param sValue
   *        The value for this partnership. May be <code>null</code>.
   * @return {@link EChange}
   */
  @NonNull
  public EChange setContentTransferEncodingSend (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_CONTENT_TRANSFER_ENCODING_SEND, sValue);
  }

  /**
   * Set the Content-Transfer-Encoding for sending messages.
   *
   * @param eCTE
   *        The value for this partnership. May be <code>null</code>.
   * @return {@link EChange}
   */
  @NonNull
  public EChange setContentTransferEncodingSend (@Nullable final EContentTransferEncoding eCTE)
  {
    return setContentTransferEncodingSend (eCTE != null ? eCTE.getID () : null);
  }

  /**
   * Get the Content-Transfer-Encoding for receiving messages.
   *
   * @param sDefault
   *        Default to be returned if none is present. May be <code>null</code>.
   * @return The partnership Content-Transfer-Encoding or the provided default value.
   */
  @Nullable
  public String getContentTransferEncodingReceive (@Nullable final String sDefault)
  {
    return getAttribute (CPartnershipIDs.PA_CONTENT_TRANSFER_ENCODING_RECEIVE, sDefault);
  }

  /**
   * Set the Content-Transfer-Encoding for receiving messages.
   *
   * @param sValue
   *        The value for this partnership. May be <code>null</code>.
   * @return {@link EChange}
   */
  @NonNull
  public EChange setContentTransferEncodingReceive (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_CONTENT_TRANSFER_ENCODING_RECEIVE, sValue);
  }

  /**
   * Set the Content-Transfer-Encoding for receiving messages.
   *
   * @param eCTE
   *        The value for this partnership. May be <code>null</code>.
   * @return {@link EChange}
   */
  @NonNull
  public EChange setContentTransferEncodingReceive (@Nullable final EContentTransferEncoding eCTE)
  {
    return setContentTransferEncodingReceive (eCTE != null ? eCTE.getID () : null);
  }

  @Nullable
  public String getCompressionType ()
  {
    return getAttribute (CPartnershipIDs.PA_COMPRESSION_TYPE);
  }

  @NonNull
  public EChange setCompressionType (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_COMPRESSION_TYPE, sValue);
  }

  @NonNull
  public EChange setCompressionType (@Nullable final ECompressionType eValue)
  {
    return setCompressionType (eValue == null ? null : eValue.getID ());
  }

  @Nullable
  public String getCompressionMode ()
  {
    return getAttribute (CPartnershipIDs.PA_COMPRESSION_MODE);
  }

  @NonNull
  public EChange setCompressionMode (@Nullable final String sValue)
  {
    return setAttribute (CPartnershipIDs.PA_COMPRESSION_MODE, sValue);
  }

  public boolean isCompressBeforeSign ()
  {
    return !CPartnershipIDs.COMPRESS_AFTER_SIGNING.equals (getCompressionMode ());
  }

  @NonNull
  public EChange setCompressionModeCompressAfterSigning ()
  {
    return setCompressionMode (CPartnershipIDs.COMPRESS_AFTER_SIGNING);
  }

  @NonNull
  public EChange setCompressionModeCompressBeforeSigning ()
  {
    return setCompressionMode (CPartnershipIDs.COMPRESS_BEFORE_SIGNING);
  }

  public boolean isForceDecrypt ()
  {
    return STRING_TRUE.equals (getAttribute (CPartnershipIDs.PA_FORCE_DECRYPT));
  }

  @NonNull
  public EChange setForceDecrypt (final boolean bValue)
  {
    return setAttribute (CPartnershipIDs.PA_FORCE_DECRYPT, Boolean.toString (bValue));
  }

  public boolean isDisableDecrypt ()
  {
    return STRING_TRUE.equals (getAttribute (CPartnershipIDs.PA_DISABLE_DECRYPT));
  }

  @NonNull
  public EChange setDisableDecrypt (final boolean bValue)
  {
    return setAttribute (CPartnershipIDs.PA_DISABLE_DECRYPT, Boolean.toString (bValue));
  }

  public boolean isForceVerify ()
  {
    return STRING_TRUE.equals (getAttribute (CPartnershipIDs.PA_FORCE_VERIFY));
  }

  @NonNull
  public EChange setForceVerify (final boolean bValue)
  {
    return setAttribute (CPartnershipIDs.PA_FORCE_VERIFY, Boolean.toString (bValue));
  }

  public boolean isDisableVerify ()
  {
    return STRING_TRUE.equals (getAttribute (CPartnershipIDs.PA_DISABLE_VERIFY));
  }

  @NonNull
  public EChange setDisableVerify (final boolean bValue)
  {
    return setAttribute (CPartnershipIDs.PA_DISABLE_VERIFY, Boolean.toString (bValue));
  }

  @NonNull
  private static ETriState _getAsTriState (@Nullable final String sValue)
  {
    if (STRING_TRUE.equals (sValue))
      return ETriState.TRUE;
    if (STRING_FALSE.equals (sValue))
      return ETriState.FALSE;
    return ETriState.UNDEFINED;
  }

  @NonNull
  public ETriState getIncludeCertificateInSignedContent ()
  {
    final String sValue = getAttribute (CPartnershipIDs.PA_SIGN_INCLUDE_CERT_IN_BODY_PART);
    return _getAsTriState (sValue);
  }

  @NonNull
  public EChange setIncludeCertificateInSignedContent (@NonNull final ETriState eValue)
  {
    return setAttribute (CPartnershipIDs.PA_SIGN_INCLUDE_CERT_IN_BODY_PART,
                         eValue.isUndefined () ? null : Boolean.toString (eValue.getAsBooleanValue ()));
  }

  @NonNull
  public ETriState getVerifyUseCertificateInBodyPart ()
  {
    final String sValue = getAttribute (CPartnershipIDs.PA_VERIFY_USE_CERT_IN_BODY_PART);
    return _getAsTriState (sValue);
  }

  @NonNull
  public EChange setVerifyUseCertificateInBodyPart (@NonNull final ETriState eValue)
  {
    return setAttribute (CPartnershipIDs.PA_VERIFY_USE_CERT_IN_BODY_PART,
                         eValue.isUndefined () ? null : Boolean.toString (eValue.getAsBooleanValue ()));
  }

  public boolean isDisableDecompress ()
  {
    return STRING_TRUE.equals (getAttribute (CPartnershipIDs.PA_DISABLE_DECOMPRESS));
  }

  @NonNull
  public EChange setDisableDecompress (final boolean bValue)
  {
    return setAttribute (CPartnershipIDs.PA_DISABLE_DECOMPRESS, Boolean.toString (bValue));
  }

  /**
   * @return <code>true</code> if the "old" RFC 3851 MIC algorithm names (e.g. <code>sha1</code>)
   *         should be used, <code>false</code> if the new RFC 5751 MIC algorithm names (e.g.
   *         <code>sha-1</code>) should be used. Default is <code>false</code>.
   * @since 2.2.7
   */
  public boolean isRFC3851MICAlgs ()
  {
    return m_aAttributes.getAsBoolean (CPartnershipIDs.PA_RFC3851_MICALGS, false);
  }

  /**
   * Enable or disable the usage of the old RFC 3851 MIC algorithm names. By default this is
   * <code>false</code>.
   *
   * @param bValue
   *        <code>true</code> if the "old" RFC 3851 MIC algorithm names (e.g. <code>sha1</code>)
   *        should be used, <code>false</code> if the new RFC 5751 MIC algorithm names (e.g.
   *        <code>sha-1</code>) should be used. Default is <code>false</code>.
   * @return {@link EChange}.
   * @since 2.2.7
   */
  @NonNull
  public EChange setRFC3851MICAlgs (final boolean bValue)
  {
    return setAttribute (CPartnershipIDs.PA_RFC3851_MICALGS, Boolean.toString (bValue));
  }

  /**
   * @return if <code>true</code>, the CMS attribute "AlgorithmProtect" will be removed. This is
   *         needed in compatibility with e.g. IBM Sterling. Default value is <code>false</code>.
   *         See Issue #137.
   * @since 4.10.1
   */
  public boolean isRemoveCmsAlgorithmProtect ()
  {
    return m_aAttributes.getAsBoolean (CPartnershipIDs.PA_REMOVE_CMS_ALOGIRTHM_PROTECT, false);
  }

  /**
   * Enable or disable the removal of the CMS attribute "AlgorithmProtect". By default this is
   * <code>false</code>. This is needed in compatibility with e.g. IBM Sterling. Default value is
   * <code>false</code>. See Issue #137.
   *
   * @param bValue
   *        <code>true</code> to remove the attribute, <code>false</code> to keep it.
   * @return {@link EChange}.
   * @since 4.10.1
   */
  @NonNull
  public EChange setRemoveCmsAlgorithmProtect (final boolean bValue)
  {
    return setAttribute (CPartnershipIDs.PA_REMOVE_CMS_ALOGIRTHM_PROTECT, Boolean.toString (bValue));
  }

  /**
   * @return A copy of all contained attributes. Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  public IStringMap getAllAttributes ()
  {
    return m_aAttributes.getClone ();
  }

  /**
   * Add all provided attributes. existing attributes are not altered.
   *
   * @param aAttributes
   *        The attributes to be added. May be <code>null</code>. If a <code>null</code> value is
   *        contained in the map, the respective attribute will be removed.
   */
  public void addAllAttributes (@Nullable final Map <String, String> aAttributes)
  {
    m_aAttributes.putAllIn (aAttributes);
  }

  /**
   * Check if sender and receiver IDs of this partnership match the ones of the provided
   * partnership.
   *
   * @param aPartnership
   *        The partnership to compare to. May not be <code>null</code>.
   * @return <code>true</code> if sender and receiver IDs of this partnership are present in the
   *         sender and receiver IDs of the provided partnership.
   */
  public boolean matches (@NonNull final Partnership aPartnership)
  {
    ValueEnforcer.notNull (aPartnership, "Partnership");
    return compareIDs (m_aSenderAttrs, aPartnership.m_aSenderAttrs) &&
           compareIDs (m_aReceiverAttrs, aPartnership.m_aReceiverAttrs);
  }

  /**
   * Check if all values from the left side are also present on the right side.
   *
   * @param aIDs
   *        The source map which must be fully contained in the aCompareTo map
   * @param aCompareTo
   *        The map to compare to. May not be <code>null</code>. It may contain more attributes than
   *        aIDs but must at least contain the same ones.
   * @return <code>true</code> if aIDs is not empty and all values from aIDs are also present in
   *         aCompareTo, <code>false</code> otherwise.
   */
  protected boolean compareIDs (@NonNull final IStringMap aIDs, @NonNull final IStringMap aCompareTo)
  {
    if (aIDs.isEmpty ())
      return false;

    for (final Map.Entry <String, String> aEntry : aIDs.entrySet ())
    {
      final String sCurrentValue = aEntry.getValue ();
      final String sCompareValue = aCompareTo.getAsString (aEntry.getKey ());
      if (!EqualsHelper.equals (sCurrentValue, sCompareValue))
        return false;
    }
    return true;
  }

  /**
   * Set all fields of this partnership with the data from the provided partnership. Name, sender
   * IDs, receiver IDs and attributes are fully overwritten!
   *
   * @param aPartnership
   *        The partnership to copy the data from. May not be <code>null</code>.
   */
  public void copyFrom (@NonNull final Partnership aPartnership)
  {
    ValueEnforcer.notNull (aPartnership, "Partnership");

    // Avoid doing something
    if (aPartnership != this)
    {
      m_sName = aPartnership.getName ();
      m_aSenderAttrs.putAllIn (aPartnership.m_aSenderAttrs);
      m_aReceiverAttrs.putAllIn (aPartnership.m_aReceiverAttrs);
      m_aAttributes.putAllIn (aPartnership.m_aAttributes);
    }
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("name", m_sName)
                                       .append ("senderIDs", m_aSenderAttrs)
                                       .append ("receiverIDs", m_aReceiverAttrs)
                                       .append ("attributes", m_aAttributes)
                                       .getToString ();
  }

  @NonNull
  public static Partnership createPlaceholderPartnership ()
  {
    return new Partnership (DEFAULT_NAME);
  }
}
