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
package com.helger.phase2.client;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.function.Consumer;

import org.apache.hc.core5.util.Timeout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.style.ReturnsMutableObject;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.string.StringHelper;
import com.helger.http.header.HttpHeaderMap;
import com.helger.phase2.CPhase2Info;
import com.helger.phase2.cert.IStorableCertificateFactory;
import com.helger.phase2.crypto.ECompressionType;
import com.helger.phase2.crypto.ECryptoAlgorithmCrypt;
import com.helger.phase2.crypto.ECryptoAlgorithmSign;
import com.helger.phase2.crypto.IMICMatchingHandler;
import com.helger.phase2.disposition.DispositionOptions;
import com.helger.phase2.processor.resender.IProcessorResenderModule;
import com.helger.phase2.processor.sender.AbstractHttpSenderModule;
import com.helger.phase2.util.dump.IHTTPIncomingDumper;
import com.helger.phase2.util.dump.IHTTPOutgoingDumperFactory;
import com.helger.security.keystore.EKeyStoreType;
import com.helger.security.keystore.IKeyStoreType;

/**
 * Settings object for a message delivery.
 *
 * @author oleo Date: May 12, 2010 Time: 5:16:57 PM
 * @author Philip Helger
 */
public class AS2ClientSettings
{
  /**
   * If compression and signing are enabled, compression happens before singing
   */
  public static final boolean DEFAULT_COMPRESS_BEFORE_SIGNING = true;
  /** By default an MDN is requested. */
  public static final boolean DEFAULT_IS_MDN_REQUESTED = true;
  /**
   * The default MDN options to be used.
   *
   * @see #setMDNOptions(DispositionOptions)
   */
  public static final String DEFAULT_MDN_OPTIONS = new DispositionOptions ().setProtocolImportance (DispositionOptions.IMPORTANCE_OPTIONAL)
                                                                            .setProtocol (DispositionOptions.PROTOCOL_PKCS7_SIGNATURE)
                                                                            .setMICAlgImportance (DispositionOptions.IMPORTANCE_OPTIONAL)
                                                                            .setMICAlg (ECryptoAlgorithmSign.DIGEST_SHA_1)
                                                                            .getAsString ();

  /**
   * The default message ID format to use.
   *
   * @see #setMessageIDFormat(String)
   */
  public static final String DEFAULT_MESSAGE_ID_FORMAT = CPhase2Info.NAME +
                                                         "-$date.ddMMuuuuHHmmssZ$-$rand.1234$@$msg.sender.as2_id$_$msg.receiver.as2_id$";
  /** By default no retry happens. */
  public static final int DEFAULT_RETRY_COUNT = IProcessorResenderModule.DEFAULT_RETRIES;
  /** Default connection timeout: 60 seconds */
  public static final Timeout DEFAULT_CONNECT_TIMEOUT = AbstractHttpSenderModule.DEFAULT_CONNECT_TIMEOUT;
  /** Default read timeout: 60 seconds */
  public static final Timeout DEFAULT_RESPONSE_TIMEOUT = AbstractHttpSenderModule.DEFAULT_RESPONSE_TIMEOUT;
  /** Default quote header values: false */
  public static final boolean DEFAULT_QUOTE_HEADER_VALUES = AbstractHttpSenderModule.DEFAULT_QUOTE_HEADER_VALUES;

  private IKeyStoreType m_aKeyStoreType = EKeyStoreType.PKCS12;
  private File m_aKeyStoreFile;
  private byte [] m_aKeyStoreBytes;
  private String m_sKeyStorePassword;
  private boolean m_bSaveKeyStoreChangesToFile = IStorableCertificateFactory.DEFAULT_SAVE_CHANGES_TO_FILE;

  private String m_sSenderEmailAddress;
  private String m_sSenderAS2ID;
  private String m_sSenderKeyAlias;

  private String m_sReceiverAS2ID;
  private String m_sReceiverKeyAlias;
  private String m_sDestinationAS2URL;
  private X509Certificate m_aReceiverCert;

  private String m_sPartnershipName;
  private ECryptoAlgorithmCrypt m_eCryptAlgo;
  private ECryptoAlgorithmSign m_eSignAlgo;
  private ECompressionType m_eCompressionType;
  private boolean m_bCompressBeforeSigning = DEFAULT_COMPRESS_BEFORE_SIGNING;
  private boolean m_bMDNRequested = DEFAULT_IS_MDN_REQUESTED;
  private String m_sMDNOptions = DEFAULT_MDN_OPTIONS;
  private String m_sAsyncMDNUrl;
  private String m_sMessageIDFormat = DEFAULT_MESSAGE_ID_FORMAT;

  private int m_nRetryCount = DEFAULT_RETRY_COUNT;
  private Timeout m_aConnectTimeout = DEFAULT_CONNECT_TIMEOUT;
  private Timeout m_aResponseTimeout = DEFAULT_RESPONSE_TIMEOUT;
  private boolean m_bQuoteHeaderValues = DEFAULT_QUOTE_HEADER_VALUES;

  private final HttpHeaderMap m_aCustomHeaders = new HttpHeaderMap ();
  private IHTTPOutgoingDumperFactory m_aHttpOutgoingDumperFactory;
  private IHTTPIncomingDumper m_aHttpIncomingDumper;
  private IMICMatchingHandler m_aMICMatchingHandler;
  private Consumer <? super X509Certificate> m_aVerificationCertificateConsumer;

  public AS2ClientSettings ()
  {}

  /**
   * @return The key store type. May not be <code>null</code>.
   * @see #setKeyStore(IKeyStoreType, File, String)
   * @see #setKeyStore(IKeyStoreType, byte[], String)
   */
  @NonNull
  public final IKeyStoreType getKeyStoreType ()
  {
    return m_aKeyStoreType;
  }

  /**
   * @return The key store file. May be <code>null</code> if not yet set. Either File or byte[] may
   *         be set. Never both.
   * @see #setKeyStore(IKeyStoreType, File, String)
   */
  @Nullable
  public final File getKeyStoreFile ()
  {
    return m_aKeyStoreFile;
  }

  /**
   * @return The key store bytes. May be <code>null</code> if not yet set. Either File or byte[] may
   *         be set. Never both.
   * @see #setKeyStore(IKeyStoreType, byte[], String)
   * @since 4.3.1
   */
  @Nullable
  public final byte [] getKeyStoreBytes ()
  {
    return m_aKeyStoreBytes;
  }

  /**
   * @return The key store password. May be <code>null</code> if not yet set.
   * @see #setKeyStore(IKeyStoreType, File, String)
   * @see #setKeyStore(IKeyStoreType, byte[], String)
   */
  @Nullable
  public final String getKeyStorePassword ()
  {
    return m_sKeyStorePassword;
  }

  /**
   * Set the details of the certificate store of the client.
   *
   * @param aKeyStoreType
   *        Key store type. May not be <code>null</code>.
   * @param aFile
   *        The key store file. May not be <code>null</code>.
   * @param sPassword
   *        The password used to open the key store. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public final AS2ClientSettings setKeyStore (@NonNull final IKeyStoreType aKeyStoreType,
                                              @NonNull final File aFile,
                                              @NonNull final String sPassword)
  {
    ValueEnforcer.notNull (aKeyStoreType, "KeyStoreType");
    ValueEnforcer.notNull (aFile, "File");
    ValueEnforcer.notNull (sPassword, "Password");
    m_aKeyStoreType = aKeyStoreType;
    m_aKeyStoreFile = aFile;
    m_aKeyStoreBytes = null;
    m_sKeyStorePassword = sPassword;
    return this;
  }

  /**
   * Set the details of the certificate store of the client. If the keystore is provided as a byte
   * array using this method, changes will NOT be saved.
   *
   * @param aKeyStoreType
   *        Key store type. May not be <code>null</code>.
   * @param aBytes
   *        The key store bytes. May not be <code>null</code>.
   * @param sPassword
   *        The password used to open the key store. May not be <code>null</code>.
   * @return this for chaining
   * @since 4.3.1
   */
  @NonNull
  public final AS2ClientSettings setKeyStore (@NonNull final IKeyStoreType aKeyStoreType,
                                              @NonNull final byte [] aBytes,
                                              @NonNull final String sPassword)
  {
    ValueEnforcer.notNull (aKeyStoreType, "KeyStoreType");
    ValueEnforcer.notNull (aBytes, "Bytes");
    ValueEnforcer.notNull (sPassword, "Password");
    m_aKeyStoreType = aKeyStoreType;
    m_aKeyStoreFile = null;
    m_aKeyStoreBytes = aBytes;
    m_sKeyStorePassword = sPassword;
    return this;
  }

  /**
   * @return <code>true</code> if key store changes should be written back to the file,
   *         <code>false</code> if not.
   */
  public final boolean isSaveKeyStoreChangesToFile ()
  {
    return m_bSaveKeyStoreChangesToFile;
  }

  /**
   * Change the behavior if all changes to the keystore should trigger a saving to the original
   * file.
   *
   * @param bSaveKeyStoreChangesToFile
   *        <code>true</code> if key store changes should be written back to the file,
   *        <code>false</code> if not.
   * @return this for chaining
   */
  @NonNull
  public final AS2ClientSettings setSaveKeyStoreChangesToFile (final boolean bSaveKeyStoreChangesToFile)
  {
    m_bSaveKeyStoreChangesToFile = bSaveKeyStoreChangesToFile;
    return this;
  }

  /**
   * @return The sender AS2 ID. May be <code>null</code> if not set.
   * @see #setSenderData(String, String, String)
   */
  @Nullable
  public final String getSenderAS2ID ()
  {
    return m_sSenderAS2ID;
  }

  /**
   * @return The sender's email address. May be <code>null</code> if not set.
   * @see #setSenderData(String, String, String)
   */
  @Nullable
  public final String getSenderEmailAddress ()
  {
    return m_sSenderEmailAddress;
  }

  /**
   * @return The senders key alias in the keystore. May be <code>null</code> if not set.
   * @see #setSenderData(String, String, String)
   * @see #setKeyStore(IKeyStoreType, File, String)
   */
  @Nullable
  public final String getSenderKeyAlias ()
  {
    return m_sSenderKeyAlias;
  }

  /**
   * Set the sender data.
   *
   * @param sAS2ID
   *        Sender AS2 ID. May not be <code>null</code>.
   * @param sEmailAddress
   *        Sender email address. May not be <code>null</code>.
   * @param sKeyAlias
   *        Alias into the keystore for identifying the sender's key. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public final AS2ClientSettings setSenderData (@NonNull final String sAS2ID,
                                                @NonNull final String sEmailAddress,
                                                @NonNull final String sKeyAlias)
  {
    m_sSenderAS2ID = ValueEnforcer.notNull (sAS2ID, "AS2ID");
    m_sSenderEmailAddress = ValueEnforcer.notNull (sEmailAddress, "EmailAddress");
    m_sSenderKeyAlias = ValueEnforcer.notNull (sKeyAlias, "KeyAlias");
    return this;
  }

  /**
   * @return The receiver AS2 ID. May be <code>null</code> if not set.
   * @see #setReceiverData(String, String, String)
   */
  @Nullable
  public final String getReceiverAS2ID ()
  {
    return m_sReceiverAS2ID;
  }

  /**
   * @return The receivers key alias in the keystore. May be <code>null</code> if not set.
   * @see #setReceiverData(String, String, String)
   * @see #setKeyStore(IKeyStoreType, File, String)
   */
  @Nullable
  public final String getReceiverKeyAlias ()
  {
    return m_sReceiverKeyAlias;
  }

  /**
   * @return The destination URL to send the request to. May be <code>null</code> if not set.
   * @see #setReceiverData(String, String, String)
   */
  @Nullable
  public final String getDestinationAS2URL ()
  {
    return m_sDestinationAS2URL;
  }

  /**
   * Set the receiver data.
   *
   * @param sAS2ID
   *        Receiver AS2 ID. May not be <code>null</code>.
   * @param sKeyAlias
   *        Alias into the keystore for identifying the receivers certificate. May not be
   *        <code>null</code>.
   * @param sAS2URL
   *        Destination URL to send the request to. May not be <code>null</code> .
   * @return this for chaining
   */
  @NonNull
  public final AS2ClientSettings setReceiverData (@NonNull final String sAS2ID,
                                                  @NonNull final String sKeyAlias,
                                                  @NonNull final String sAS2URL)
  {
    m_sReceiverAS2ID = ValueEnforcer.notNull (sAS2ID, "AS2ID");
    m_sReceiverKeyAlias = ValueEnforcer.notNull (sKeyAlias, "KeyAlias");
    m_sDestinationAS2URL = ValueEnforcer.notNull (sAS2URL, "AS2URL");
    return this;
  }

  /**
   * @return The explicit certificate of the recipient. This might be used to dynamically add it to
   *         the certificate factory for dynamic partnership handling (like in PEPPOL). May be
   *         <code>null</code>.
   * @see #setReceiverCertificate(X509Certificate)
   */
  @Nullable
  public final X509Certificate getReceiverCertificate ()
  {
    return m_aReceiverCert;
  }

  /**
   * Explicitly set the receiver certificate to be used. This might be used to dynamically add it to
   * the certificate factory for dynamic partnership handling (like in PEPPOL).
   *
   * @param aReceiverCertificate
   *        The receiver certificate. May be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public final AS2ClientSettings setReceiverCertificate (@Nullable final X509Certificate aReceiverCertificate)
  {
    m_aReceiverCert = aReceiverCertificate;
    return this;
  }

  /**
   * @return The algorithm used to encrypt the message. May be <code>null</code> if not set.
   * @see #setEncryptAndSign(ECryptoAlgorithmCrypt, ECryptoAlgorithmSign)
   */
  @Nullable
  public final ECryptoAlgorithmCrypt getCryptAlgo ()
  {
    return m_eCryptAlgo;
  }

  /**
   * @return The ID of the algorithm used to encrypt the message. May be <code>null</code> if not
   *         set.
   * @see #setEncryptAndSign(ECryptoAlgorithmCrypt, ECryptoAlgorithmSign)
   */
  @Nullable
  public final String getCryptAlgoID ()
  {
    return m_eCryptAlgo == null ? null : m_eCryptAlgo.getID ();
  }

  /**
   * @return The algorithm used to sign the message. May be <code>null</code> if not set.
   * @see #setEncryptAndSign(ECryptoAlgorithmCrypt, ECryptoAlgorithmSign)
   */
  @Nullable
  public final ECryptoAlgorithmSign getSignAlgo ()
  {
    return m_eSignAlgo;
  }

  /**
   * @return The ID of the algorithm used to sign the message. May be <code>null</code> if not set.
   * @see #setEncryptAndSign(ECryptoAlgorithmCrypt, ECryptoAlgorithmSign)
   */
  @Nullable
  public final String getSignAlgoID ()
  {
    return m_eSignAlgo == null ? null : m_eSignAlgo.getID ();
  }

  /**
   * Set the encryption and signing algorithms to use.
   *
   * @param eCryptAlgo
   *        The encryption algorithm. May be <code>null</code> to indicate that the message should
   *        not be encrypted.
   * @param eSignAlgo
   *        The signing algorithm. May be <code>null</code> to indicate that the message should not
   *        be signed.
   * @return this for chaining.
   */
  @NonNull
  public final AS2ClientSettings setEncryptAndSign (@Nullable final ECryptoAlgorithmCrypt eCryptAlgo,
                                                    @Nullable final ECryptoAlgorithmSign eSignAlgo)
  {
    m_eCryptAlgo = eCryptAlgo;
    m_eSignAlgo = eSignAlgo;
    return this;
  }

  /**
   * @return The compression type used to compress the message. May be <code>null</code> to indicate
   *         no compression.
   * @see #setCompress(ECompressionType, boolean)
   */
  @Nullable
  public final ECompressionType getCompressionType ()
  {
    return m_eCompressionType;
  }

  /**
   * Check if compress before sign or sign before compress is used. This flag is only evaluated if
   * {@link #getCompressionType()} is not <code>null</code>.
   *
   * @return <code>true</code> to compress before signing, <code>false</code> to sign before
   *         compressing
   * @see #setCompress(ECompressionType, boolean)
   */
  public final boolean isCompressBeforeSigning ()
  {
    return m_bCompressBeforeSigning;
  }

  /**
   * Enable or disable the compression of the message. Note: compression requires the receiver to
   * support AS2 version 1.1!
   *
   * @param eCompressionType
   *        The compression type to use. Pass <code>null</code> to not compress the message (that is
   *        also the default).
   * @param bCompressBeforeSigning
   *        <code>true</code> to compress the data before it is signed, <code>false</code> to sign
   *        first and than compress the message. The default is <code>true</code>.
   * @return this for chaining
   */
  @NonNull
  public final AS2ClientSettings setCompress (@Nullable final ECompressionType eCompressionType,
                                              final boolean bCompressBeforeSigning)
  {
    m_eCompressionType = eCompressionType;
    m_bCompressBeforeSigning = bCompressBeforeSigning;
    return this;
  }

  /**
   * @return The partnership name to be used. May be <code>null</code> if not set.
   * @see #setPartnershipName(String)
   */
  @Nullable
  public final String getPartnershipName ()
  {
    return m_sPartnershipName;
  }

  /**
   * Set the name of the partnership for lookup and dynamic creation.
   *
   * @param sPartnershipName
   *        The partnership name. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public final AS2ClientSettings setPartnershipName (@NonNull final String sPartnershipName)
  {
    m_sPartnershipName = ValueEnforcer.notNull (sPartnershipName, "PartnershipName");
    return this;
  }

  /**
   * @return <code>true</code> if an MDN is requested at all (sync or async), <code>false</code> if
   *         not.
   * @since 4.2.0
   */
  public final boolean isMDNRequested ()
  {
    return m_bMDNRequested;
  }

  /**
   * Determine if an MDN is requested at all.
   *
   * @param bMDNRequested
   *        <code>true</code> to request an MDN (is the default), <code>false</code> to not request
   *        one.
   * @return this for chaining
   * @since 4.2.0
   */
  @NonNull
  public final AS2ClientSettings setMDNRequested (final boolean bMDNRequested)
  {
    m_bMDNRequested = bMDNRequested;
    return this;
  }

  /**
   * Get the current MDN options. Since 3.0.4 the MDN options (corresponding to the
   * 'Disposition-Notification-Options' header) may be <code>null</code>.
   *
   * @return The MDN options (<code>Disposition-Notification-Options</code> header) to be used. May
   *         be <code>null</code>. The default is defined in {@link #DEFAULT_MDN_OPTIONS}.
   * @see #setMDNOptions(DispositionOptions)
   * @see #setMDNOptions(String)
   */
  @Nullable
  public final String getMDNOptions ()
  {
    return m_sMDNOptions;
  }

  /**
   * @return <code>true</code> if MDN options are specified (the default), <code>false</code> if
   *         not.
   * @since 3.0.4
   */
  public final boolean hasMDNOptions ()
  {
    return m_sMDNOptions != null;
  }

  /**
   * Set the MDN options to be used. Since 3.0.4 the MDN options (corresponding to the
   * 'Disposition-Notification-Options' header) may be <code>null</code>.
   *
   * @param sMDNOptions
   *        The <code>Disposition-Notification-Options</code> String to be used. May be
   *        <code>null</code>.
   * @return this for chaining
   * @see #setMDNOptions(DispositionOptions)
   */
  @NonNull
  public final AS2ClientSettings setMDNOptions (@Nullable final String sMDNOptions)
  {
    m_sMDNOptions = sMDNOptions;
    return this;
  }

  /**
   * Set the MDN options to be used.
   *
   * @param aDispositionOptions
   *        The <code>Disposition-Notification-Options</code> structured object to be used. May not
   *        be <code>null</code>.
   * @return this for chaining
   * @see #setMDNOptions(String)
   */
  @NonNull
  public final AS2ClientSettings setMDNOptions (@NonNull final DispositionOptions aDispositionOptions)
  {
    ValueEnforcer.notNull (aDispositionOptions, "DispositionOptions");
    return setMDNOptions (aDispositionOptions.getAsString ());
  }

  /**
   * @return The URL for the asynchronous MDN. If this is <code>null</code> than a synchronous MDN
   *         is requested. By default a synchronous MDN is requested.
   * @since 3.0.4
   */
  @Nullable
  public final String getAsyncMDNUrl ()
  {
    return m_sAsyncMDNUrl;
  }

  /**
   * @return <code>true</code> if an asynchronous MDN is requested, <code>false</code> if not
   *         (default).
   * @since 3.0.4
   * @see #getAsyncMDNUrl()
   */
  public final boolean isAsyncMDNRequested ()
  {
    return StringHelper.isNotEmpty (m_sAsyncMDNUrl);
  }

  /**
   * Set the asynchronous MDN URL to be used.
   *
   * @param sAsyncMDNUrl
   *        May be <code>null</code> in which case a synchronous MDN is requested (which is also the
   *        default).
   * @return this for chaining
   * @since 3.0.4
   */
  @NonNull
  public final AS2ClientSettings setAsyncMDNUrl (@Nullable final String sAsyncMDNUrl)
  {
    m_sAsyncMDNUrl = sAsyncMDNUrl;
    return this;
  }

  /**
   * @return The message ID format to use. Never <code>null</code>. It defaults to
   *         {@value #DEFAULT_MESSAGE_ID_FORMAT}.
   * @see #DEFAULT_MESSAGE_ID_FORMAT
   * @see #setMessageIDFormat(String)
   */
  @NonNull
  public final String getMessageIDFormat ()
  {
    return m_sMessageIDFormat;
  }

  /**
   * Set the Message ID format. This string may contain placeholders as supported by the
   * <code>com.helger.as2lib.params</code> parameters parsers.
   *
   * @param sMessageIDFormat
   *        The message ID format to use. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public final AS2ClientSettings setMessageIDFormat (@NonNull final String sMessageIDFormat)
  {
    m_sMessageIDFormat = ValueEnforcer.notNull (sMessageIDFormat, "MessageIDFormat");
    return this;
  }

  /**
   * @return The number of retries to be performed. May be &le; 0 meaning that no retry will happen.
   *         The default value is {@link #DEFAULT_RETRY_COUNT}.
   * @see #setRetryCount(int)
   */
  public final int getRetryCount ()
  {
    return m_nRetryCount;
  }

  /**
   * Set the retry count for sending,
   *
   * @param nRetryCount
   *        Sending retry count. Values &le; 0 mean "no retry".
   * @return this for chaining
   * @see #getRetryCount()
   */
  @NonNull
  public final AS2ClientSettings setRetryCount (final int nRetryCount)
  {
    m_nRetryCount = nRetryCount;
    return this;
  }

  /**
   * @return The connection timeout. The default value is {@link #DEFAULT_CONNECT_TIMEOUT}.
   * @since 3.0.2
   */
  public final Timeout getConnectTimeout ()
  {
    return m_aConnectTimeout;
  }

  /**
   * Set the connect timeout.
   *
   * @param aConnectTimeout
   *        Connect timeout. May not be <code>null</code>.
   * @return this for chaining
   * @see #getConnectTimeout()
   * @since 3.0.2
   */
  @NonNull
  public final AS2ClientSettings setConnectTimeout (@NonNull final Timeout aConnectTimeout)
  {
    ValueEnforcer.notNull (aConnectTimeout, "ConnectTimeout");
    m_aConnectTimeout = aConnectTimeout;
    return this;
  }

  /**
   * @return The response/read timeout. The default value is {@link #DEFAULT_RESPONSE_TIMEOUT}.
   * @since 3.0.2
   */
  @NonNull
  public final Timeout getResponseTimeout ()
  {
    return m_aResponseTimeout;
  }

  /**
   * Set the response/read timeout.
   *
   * @param aResponseTimeout
   *        Response timeout. May not be <code>null</code>.
   * @return this for chaining
   * @see #getResponseTimeout()
   * @since 3.0.2
   */
  @NonNull
  public final AS2ClientSettings setResponseTimeout (@NonNull final Timeout aResponseTimeout)
  {
    ValueEnforcer.notNull (aResponseTimeout, "ResponseTimeout");
    m_aResponseTimeout = aResponseTimeout;
    return this;
  }

  /**
   * @return <code>true</code> if HTTP header values should be quoted according to RFC 2616,
   *         <code>false</code> if not.
   * @since 4.4.2
   */
  public final boolean isQuoteHeaderValues ()
  {
    return m_bQuoteHeaderValues;
  }

  /**
   * Set whether HTTP header values for outgoing messages should be quoted or not according to RFC
   * 2616. By default the headers are not quoted, as this might be an interoperability issue.
   *
   * @param bQuoteHeaderValues
   *        <code>true</code> if quoting should be enabled, <code>false</code> if not.
   * @return this for chaining
   * @since 4.4.2
   */
  @NonNull
  public final AS2ClientSettings setQuoteHeaderValues (final boolean bQuoteHeaderValues)
  {
    m_bQuoteHeaderValues = bQuoteHeaderValues;
    return this;
  }

  /**
   * @return The outgoing dumper factory. May be <code>null</code>.
   * @since 4.4.0
   */
  @Nullable
  public final IHTTPOutgoingDumperFactory getHttpOutgoingDumperFactory ()
  {
    return m_aHttpOutgoingDumperFactory;
  }

  /**
   * Set the HTTP outgoing dumper factory.
   *
   * @param aHttpOutgoingDumperFactory
   *        The factory to be used. May be <code>null</code>.
   * @return this for chaining
   * @since 4.4.0
   */
  @NonNull
  public final AS2ClientSettings setHttpOutgoingDumperFactory (@Nullable final IHTTPOutgoingDumperFactory aHttpOutgoingDumperFactory)
  {
    m_aHttpOutgoingDumperFactory = aHttpOutgoingDumperFactory;
    return this;
  }

  /**
   * @return The incoming dumper. May be <code>null</code>.
   * @since 4.4.5
   */
  @Nullable
  public final IHTTPIncomingDumper getHttpIncomingDumper ()
  {
    return m_aHttpIncomingDumper;
  }

  /**
   * Set the HTTP incoming dumper.
   *
   * @param aHttpIncomingDumper
   *        The dumper to be used. May be <code>null</code>.
   * @return this for chaining
   * @since 4.4.5
   */
  @NonNull
  public final AS2ClientSettings setHttpIncomingDumper (@Nullable final IHTTPIncomingDumper aHttpIncomingDumper)
  {
    m_aHttpIncomingDumper = aHttpIncomingDumper;
    return this;
  }

  /**
   * @return The mutable custom header map. Never <code>null</code>.
   * @since 3.0.5
   */
  @NonNull
  @ReturnsMutableObject
  public final HttpHeaderMap customHeaders ()
  {
    return m_aCustomHeaders;
  }

  /**
   * @return An optional MIC Matching handler. May be <code>null</code>.
   * @since 4.4.5
   */
  @Nullable
  public final IMICMatchingHandler getMICMatchingHandler ()
  {
    return m_aMICMatchingHandler;
  }

  /**
   * Set a custom MIC matching handler
   *
   * @param aMICMatchingHandler
   *        The handler to be used. May be <code>null</code>.
   * @return this for chaining
   * @since 4.4.5
   */
  @NonNull
  public final AS2ClientSettings setMICMatchingHandler (@Nullable final IMICMatchingHandler aMICMatchingHandler)
  {
    m_aMICMatchingHandler = aMICMatchingHandler;
    return this;
  }

  /**
   * @return The custom verification certificate consumer to be used. May be <code>null</code>.
   * @since 4.4.5
   */
  @Nullable
  public final Consumer <? super X509Certificate> getVerificationCertificateConsumer ()
  {
    return m_aVerificationCertificateConsumer;
  }

  /**
   * Set a custom MIC matching handler
   *
   * @param aVerificationCertificateConsumer
   *        The factory to be used. May be <code>null</code>.
   * @return this for chaining
   * @since 4.4.5
   */
  @NonNull
  public final AS2ClientSettings setVerificationCertificateConsumer (@Nullable final Consumer <? super X509Certificate> aVerificationCertificateConsumer)
  {
    m_aVerificationCertificateConsumer = aVerificationCertificateConsumer;
    return this;
  }
}
