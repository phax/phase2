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
package com.helger.phase2.crypto;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.WillNotClose;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.util.AS2ResourceHelper;
import com.helger.security.keystore.IKeyStoreType;

import jakarta.mail.internet.MimeBodyPart;

/**
 * Base interface for all crypto related methods in this project.
 *
 * @author Philip Helger
 */
public interface ICryptoHelper
{
  /**
   * @param aKeyStoreType
   *        Key store type to use. May not be <code>null</code>.
   * @return A new key store.
   * @throws GeneralSecurityException
   *         In case something goes wrong.
   */
  @NonNull
  KeyStore createNewKeyStore (@NonNull IKeyStoreType aKeyStoreType) throws GeneralSecurityException;

  /**
   * Load a key store from the specified input stream.
   *
   * @param aKeyStoreType
   *        Key store type to use. May not be <code>null</code>.
   * @param aIS
   *        The input stream to load the key store from. May not be <code>null</code>.
   * @param aPassword
   *        The password to be used for loading. May not be <code>null</code>.
   * @return The loaded key store and never <code>null</code>.
   * @throws Exception
   *         In case loading fails.
   */
  @NonNull
  KeyStore loadKeyStore (@NonNull IKeyStoreType aKeyStoreType,
                         @NonNull @WillNotClose InputStream aIS,
                         @NonNull char [] aPassword) throws Exception;

  /**
   * Check if the passed MIME body part is encrypted. The default implementation checks if the base
   * type of the content type is "application/pkcs7-mime" and if the parameter "smime-type" has the
   * value "enveloped-data".
   *
   * @param aPart
   *        The part to be checked.
   * @return <code>true</code> if it is encrypted, <code>false</code> otherwise.
   * @throws Exception
   *         In case something goes wrong.
   */
  boolean isEncrypted (@NonNull MimeBodyPart aPart) throws Exception;

  /**
   * Check if the passed MIME body part is signed. The default implementation checks if the base
   * type of the content type is "multipart/signed".
   *
   * @param aPart
   *        The part to be checked.
   * @return <code>true</code> if it is signed, <code>false</code> otherwise.
   * @throws Exception
   *         In case something goes wrong.
   */
  boolean isSigned (@NonNull MimeBodyPart aPart) throws Exception;

  /**
   * Check if the passed content type indicates compression. The default implementation checks if
   * the parameter "smime-type" has the value "compressed-data".
   *
   * @param sContentType
   *        The content type to be checked. May not be <code>null</code>.
   * @return <code>true</code> if it is compressed, <code>false</code> otherwise.
   * @throws AS2Exception
   *         In case something goes wrong.
   */
  boolean isCompressed (@NonNull String sContentType) throws AS2Exception;

  /**
   * Calculate the MIC
   *
   * @param aPart
   *        MIME part to calculate the MIC from. May not be <code>null</code>.
   * @param eDigestAlgorithm
   *        The digest algorithm to be used. May not be <code>null</code>.
   * @param bIncludeHeaders
   *        <code>true</code> if the MIME headers should be included, <code>false</code> if only the
   *        content should be used.
   * @return The calculated MIC and never <code>null</code>.
   * @throws Exception
   *         In case something goes wrong.
   */
  @NonNull
  MIC calculateMIC (@NonNull MimeBodyPart aPart,
                    @NonNull ECryptoAlgorithmSign eDigestAlgorithm,
                    boolean bIncludeHeaders) throws Exception;

  @NonNull
  MimeBodyPart encrypt (@NonNull MimeBodyPart aPart,
                        @NonNull X509Certificate aCert,
                        @NonNull ECryptoAlgorithmCrypt eAlgorithm,
                        @NonNull EContentTransferEncoding eCTE) throws Exception;

  @NonNull
  MimeBodyPart decrypt (@NonNull MimeBodyPart aPart,
                        @NonNull X509Certificate aCert,
                        @NonNull PrivateKey aKey,
                        boolean bForceDecrypt,
                        @NonNull AS2ResourceHelper aResHelper) throws Exception;

  /**
   * Sign a MIME body part.
   *
   * @param aPart
   *        MIME body part to be signed. May not be <code>null</code>.
   * @param aCert
   *        The certificate that should be added to the signed information. May not be
   *        <code>null</code>.
   * @param aKey
   *        Private key to be used for signing. May not be <code>null</code>.
   * @param eAlgorithm
   *        The algorithm to be used for signing. May not be <code>null</code>.
   * @param bIncludeCertificateInSignedContent
   *        <code>true</code> if the passed certificate should be part of the signed content,
   *        <code>false</code> if the certificate should not be put in the content. E.g. for PEPPOL
   *        this must be <code>true</code>.
   * @param bUseOldRFC3851MicAlgs
   *        <code>true</code> to use the old RFC 3851 MIC algorithm names (e.g. <code>sha1</code>),
   *        <code>false</code> to use the new RFC 5751 MIC algorithm names (e.g.
   *        <code>sha-1</code>).
   * @param bRemoveCmsAlgorithmProtect
   *        if <code>true</code>, the CMS attribute "AlgorithmProtect" will be removed. This is
   *        needed in compatibility with e.g. IBM Sterling. Default value should be
   *        <code>false</code>. Since 4.10.1. See Issue #137.
   * @param eCTE
   *        The Content-Transfer-Encoding to be used. May not be <code>null</code>.
   * @return The signed MIME body part. Never <code>null</code>.
   * @throws Exception
   *         In case something goes wrong.
   */
  @NonNull
  MimeBodyPart sign (@NonNull MimeBodyPart aPart,
                     @NonNull X509Certificate aCert,
                     @NonNull PrivateKey aKey,
                     @NonNull ECryptoAlgorithmSign eAlgorithm,
                     boolean bIncludeCertificateInSignedContent,
                     boolean bUseOldRFC3851MicAlgs,
                     boolean bRemoveCmsAlgorithmProtect,
                     @NonNull EContentTransferEncoding eCTE) throws Exception;

  /**
   * Verify the specified Mime Body part against the part certificate
   *
   * @param aPart
   *        Original part
   * @param aCert
   *        Certificate to check against or <code>null</code> if the certificate provided in the
   *        message should be used.
   * @param bUseCertificateInBodyPart
   *        If <code>true</code> any certificate that is passed in the body part is used for
   *        verification. If <code>false</code> only the provided certificate is used.
   * @param bForceVerifySigned
   *        <code>true</code> to force verification of signature even if the Content-Type header
   *        does not indicate so.
   * @param aEffectiveCertificateConsumer
   *        An optional consumer that takes the effective certificate that was used for
   *        verification. May be <code>null</code>.
   * @param aResHelper
   *        The resource helper to use. May not be <code>null</code>.
   * @return The signed content. Never <code>null</code>.
   * @throws Exception
   *         In case something goes wrong.
   * @since 4.4.1
   */
  @NonNull
  MimeBodyPart verify (@NonNull MimeBodyPart aPart,
                       @Nullable X509Certificate aCert,
                       boolean bUseCertificateInBodyPart,
                       boolean bForceVerifySigned,
                       @Nullable Consumer <X509Certificate> aEffectiveCertificateConsumer,
                       @Nullable Consumer <MimeBodyPart> aMICSourceConsumer,
                       @NonNull AS2ResourceHelper aResHelper) throws Exception;
}
