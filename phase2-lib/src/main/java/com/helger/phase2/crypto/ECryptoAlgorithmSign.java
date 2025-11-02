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
package com.helger.phase2.crypto;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.annotation.misc.DevelopersNote;
import com.helger.base.lang.EnumHelper;

/**
 * This enum contains all signing supported crypto algorithms. The algorithms contained in here may
 * not be used for encryption of anything. See {@link ECryptoAlgorithmCrypt} for encryption
 * algorithms.<br>
 * Note: Mendelson uses the RFC 5751 identifiers.
 *
 * @author Philip Helger
 */
public enum ECryptoAlgorithmSign implements ICryptoAlgorithm
{
  /**
   * See compatibility note in RFC 5751, section 3.4.3.1
   */
  @Deprecated (forRemoval = false)
  @DevelopersNote ("Use DIGEST_MD5 instead")
  DIGEST_RSA_MD5("rsa-md5", "md5", PKCSObjectIdentifiers.md5, "MD5WITHRSA"),

  /**
   * See compatibility note in RFC 5751, section 3.4.3.1
   */
  @Deprecated (forRemoval = false)
  @DevelopersNote ("Use DIGEST_SHA1 or DIGEST_SHA_1 instead")
  DIGEST_RSA_SHA1("rsa-sha1", "sha1", OIWObjectIdentifiers.idSHA1, "SHA1WITHRSA"),

  /**
   * Same for RFC 3851 and RFC 5751
   */
  DIGEST_MD5 ("md5", "md5", PKCSObjectIdentifiers.md5, "MD5WITHRSA"),

  /**
   * Old version as of RFC 3851.
   */
  @Deprecated (forRemoval = false)
  @DevelopersNote ("Use DIGEST_SHA_1 instead")
  DIGEST_SHA1("sha1", "sha1", OIWObjectIdentifiers.idSHA1, "SHA1WITHRSA"),

  /**
   * Old version as of RFC 3851.
   */
  @Deprecated (forRemoval = false)
  @DevelopersNote ("Use DIGEST_SHA_256 instead")
  DIGEST_SHA256("sha256", "sha256", NISTObjectIdentifiers.id_sha256, "SHA256WITHRSA"),

  /**
   * Old version as of RFC 3851.
   */
  @Deprecated (forRemoval = false)
  @DevelopersNote ("Use DIGEST_SHA_384 instead")
  DIGEST_SHA384("sha384", "sha384", NISTObjectIdentifiers.id_sha384, "SHA384WITHRSA"),

  /**
   * Old version as of RFC 3851.
   */
  @Deprecated (forRemoval = false)
  @DevelopersNote ("Use DIGEST_SHA_512 instead")
  DIGEST_SHA512("sha512", "sha512", NISTObjectIdentifiers.id_sha512, "SHA512WITHRSA"),

  /**
   * New version as of RFC 5751.
   */
  DIGEST_SHA_1 ("sha-1", "sha-1", OIWObjectIdentifiers.idSHA1, "SHA1WITHRSA"),

  /**
   * New version as of RFC 5751.
   */
  DIGEST_SHA_224 ("sha-224", "sha-224", NISTObjectIdentifiers.id_sha224, "SHA224WITHRSA"),

  /**
   * New version as of RFC 5751.
   */
  DIGEST_SHA_256 ("sha-256", "sha-256", NISTObjectIdentifiers.id_sha256, "SHA256WITHRSA"),

  /**
   * New version as of RFC 5751.
   */
  DIGEST_SHA_384 ("sha-384", "sha-384", NISTObjectIdentifiers.id_sha384, "SHA384WITHRSA"),

  /**
   * New version as of RFC 5751.
   */
  DIGEST_SHA_512 ("sha-512", "sha-512", NISTObjectIdentifiers.id_sha512, "SHA512WITHRSA"),

  /**
   * Non-compliant version of SHA-224 algorithm
   *
   * @since 5.0.1 and 5.1.1
   */
  DIGEST_SHA2_224 ("sha2_224", "sha-224", NISTObjectIdentifiers.id_sha224, "SHA224WITHRSA"),

  /**
   * Non-compliant version of SHA-256 algorithm
   *
   * @since 5.0.1 and 5.1.1
   */
  DIGEST_SHA2_256 ("sha2_256", "sha-256", NISTObjectIdentifiers.id_sha256, "SHA256WITHRSA"),

  /**
   * Non-compliant version of SHA-384 algorithm
   *
   * @since 5.0.1 and 5.1.1
   */
  DIGEST_SHA2_384 ("sha2_384", "sha-384", NISTObjectIdentifiers.id_sha384, "SHA384WITHRSA"),

  /**
   * Non-compliant version of SHA-512 algorithm
   *
   * @since 5.0.1 and 5.1.1
   */
  DIGEST_SHA2_512 ("sha2_512", "sha-512", NISTObjectIdentifiers.id_sha512, "SHA512WITHRSA"),

  /**
   * RSASSA-PSS with digest algorithm SHA224.
   *
   * @since 5.0.0
   */
  RSASSA_PSS_WITH_SHA224 ("rsassa-pss-sha-224", "sha-224", NISTObjectIdentifiers.id_sha224, "SHA224WITHRSAANDMGF1"),

  /**
   * RSASSA-PSS with digest algorithm SHA256
   *
   * @since 5.0.0
   */
  RSASSA_PSS_WITH_SHA256 ("rsassa-pss-sha-256", "sha-256", NISTObjectIdentifiers.id_sha256, "SHA256WITHRSAANDMGF1"),

  /**
   * RSASSA-PSS with digest algorithm SHA384
   *
   * @since 5.0.0
   */
  RSASSA_PSS_WITH_SHA384 ("rsassa-pss-sha-384", "sha-384", NISTObjectIdentifiers.id_sha384, "SHA384WITHRSAANDMGF1"),

  /**
   * RSASSA-PSS with digest algorithm SHA512
   *
   * @since 5.0.0
   */
  RSASSA_PSS_WITH_SHA512 ("rsassa-pss-sha-512", "sha-512", NISTObjectIdentifiers.id_sha512, "SHA512WITHRSAANDMGF1"),

  /**
   * RSASSA-PSS with digest algorithm SHA3-224
   *
   * @since 5.0.0
   */
  RSASSA_PSS_WITH_SHA3_224 ("rsassa-pss-sha3-224",
                            "sha3-224",
                            NISTObjectIdentifiers.id_sha3_224,
                            "SHA3-224WITHRSAANDMGF1"),

  /**
   * RSASSA-PSS with digest algorithm SHA3-256
   *
   * @since 5.0.0
   */
  RSASSA_PSS_WITH_SHA3_256 ("rsassa-pss-sha3-256",
                            "sha3-256",
                            NISTObjectIdentifiers.id_sha3_256,
                            "SHA3-256WITHRSAANDMGF1"),

  /**
   * RSASSA-PSS with digest algorithm SHA3-384
   *
   * @since 5.0.0
   */
  RSASSA_PSS_WITH_SHA3_384 ("rsassa-pss-sha3-384",
                            "sha3-384",
                            NISTObjectIdentifiers.id_sha3_384,
                            "SHA3-384WITHRSAANDMGF1"),

  /**
   * RSASSA-PSS with digest algorithm SHA3-512
   *
   * @since 5.0.0
   */
  RSASSA_PSS_WITH_SHA3_512 ("rsassa-pss-sha3-512",
                            "sha3-512",
                            NISTObjectIdentifiers.id_sha3_512,
                            "SHA3-512WITHRSAANDMGF1");

  public static final ECryptoAlgorithmSign DEFAULT_RFC_3851 = DIGEST_SHA1;
  public static final ECryptoAlgorithmSign DEFAULT_RFC_5751 = DIGEST_SHA_256;

  private final String m_sID;
  private final String m_sMicAlgID;
  private final ASN1ObjectIdentifier m_aDigestOID;
  private final String m_sBCAlgorithmName;

  ECryptoAlgorithmSign (@NonNull @Nonempty final String sID,
                        @NonNull @Nonempty final String sMicAlgID,
                        @NonNull final ASN1ObjectIdentifier aDigestOID,
                        @NonNull @Nonempty final String sBCAlgorithmName)
  {
    m_sID = sID;
    m_sMicAlgID = sMicAlgID;
    m_aDigestOID = aDigestOID;
    m_sBCAlgorithmName = sBCAlgorithmName;
    // Make sure the algorithm name exists (throws on error)
    new DefaultSignatureAlgorithmIdentifierFinder ().find (sBCAlgorithmName);
  }

  /**
   * Returns the Signature ID as the combination of the signature algorithm and the digest
   * algorithm.
   */
  @NonNull
  @Nonempty
  public String getID ()
  {
    return m_sID;
  }

  /**
   * @return the MIC algorithm ID. That is the value that is added into the "micalg" Content-Type
   *         parameter of the "Content-Type" header.
   */
  @NonNull
  @Nonempty
  public String getMICAlgorithmID ()
  {
    return m_sMicAlgID;
  }

  /**
   * The OID with which the message digest is created. Never <code>null</code>.
   */
  @NonNull
  public ASN1ObjectIdentifier getOID ()
  {
    return m_aDigestOID;
  }

  /**
   * @return The algorithm name to be used for BouncyCastle to do the SMIME packaging.
   */
  @NonNull
  @Nonempty
  public String getSignAlgorithmName ()
  {
    return m_sBCAlgorithmName;
  }

  /**
   * @return <code>true</code> if this is an algorithm defined by RFC 3851, <code>false</code>
   *         otherwise. Please note that some algorithms are contained in both algorithm sets!
   * @since 4.2.0
   */
  public boolean isRFC3851Algorithm ()
  {
    return this == DIGEST_RSA_MD5 ||
           this == DIGEST_RSA_SHA1 ||
           this == DIGEST_MD5 ||
           this == DIGEST_SHA1 ||
           this == DIGEST_SHA256 ||
           this == DIGEST_SHA384 ||
           this == DIGEST_SHA512;
  }

  /**
   * @return <code>true</code> if this is an algorithm defined by RFC 5751, <code>false</code>
   *         otherwise. Please note that some algorithms are contained in both algorithm sets!
   * @since 4.2.0
   */
  public boolean isRFC5751Algorithm ()
  {
    return this == DIGEST_MD5 ||
           this == DIGEST_SHA_1 ||
           this == DIGEST_SHA_256 ||
           this == DIGEST_SHA_384 ||
           this == DIGEST_SHA_512;
  }

  @Nullable
  public static ECryptoAlgorithmSign getFromIDOrNull (@Nullable final String sID)
  {
    // Case insensitive for #32
    return EnumHelper.getFromIDCaseInsensitiveOrNull (ECryptoAlgorithmSign.class, sID);
  }

  @NonNull
  public static ECryptoAlgorithmSign getFromIDOrThrow (@Nullable final String sID)
  {
    // Case insensitive for #32
    return EnumHelper.getFromIDCaseInsensitiveOrThrow (ECryptoAlgorithmSign.class, sID);
  }

  @Nullable
  public static ECryptoAlgorithmSign getFromIDOrDefault (@Nullable final String sID,
                                                         @Nullable final ECryptoAlgorithmSign eDefault)
  {
    // Case insensitive for #32
    return EnumHelper.getFromIDCaseInsensitiveOrDefault (ECryptoAlgorithmSign.class, sID, eDefault);
  }
}
