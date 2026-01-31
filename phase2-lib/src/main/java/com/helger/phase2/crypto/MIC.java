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

import java.util.Arrays;
import java.util.StringTokenizer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.array.ArrayHelper;
import com.helger.base.clone.ICloneable;
import com.helger.base.codec.base64.Base64;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.hashcode.HashCodeGenerator;
import com.helger.base.string.StringHelper;
import com.helger.base.tostring.ToStringGenerator;

/**
 * The MIC (Message Integrity Check) value. Basically a hash value over the message.
 *
 * @author Philip Helger
 * @since 4.4.0
 */
public class MIC implements ICloneable <MIC>
{
  private final byte [] m_aMICBytes;
  private final ECryptoAlgorithmSign m_eDigestAlgorithm;

  public MIC (@NonNull final byte [] aMICBytes, @NonNull final ECryptoAlgorithmSign eDigestAlgorithm)
  {
    ValueEnforcer.notNull (aMICBytes, "MICBytes");
    ValueEnforcer.notNull (eDigestAlgorithm, "DigestAlgorithm");

    m_aMICBytes = aMICBytes;
    m_eDigestAlgorithm = eDigestAlgorithm;
  }

  /**
   * @return The mutual MIC bytes. Handle with care. Never <code>null</code>.
   */
  @NonNull
  public byte [] micBytes ()
  {
    return m_aMICBytes;
  }

  /**
   * @return The algorithm that was used to create the MIC. Never <code>null</code>.
   */
  @NonNull
  public ECryptoAlgorithmSign getDigestAlgorithm ()
  {
    return m_eDigestAlgorithm;
  }

  /**
   * @return This is the Base64-encoded message digest of the specified algorithm. The exact layout
   *         must be <code>&lt;Base64EncodedMIC&gt;, &lt;MICAlgorithmID&gt;</code>
   */
  @NonNull
  @Nonempty
  public String getAsAS2String ()
  {
    return Base64.encodeBytes (m_aMICBytes) + ", " + m_eDigestAlgorithm.getID ();
  }

  @NonNull
  @ReturnsMutableCopy
  public MIC getClone ()
  {
    return new MIC (ArrayHelper.getCopy (m_aMICBytes), m_eDigestAlgorithm);
  }

  /**
   * Special unification for https://github.com/phax/phase2/issues/75
   *
   * @param eAlgorithm
   *        Source algorithm. May not be <code>null</code>.
   * @return The unified algorithm. Never <code>null</code>.
   */
  @SuppressWarnings ("deprecation")
  @NonNull
  private static ECryptoAlgorithmSign _getUnified (@NonNull final ECryptoAlgorithmSign eAlgorithm)
  {
    return switch (eAlgorithm)
    {
      case DIGEST_RSA_MD5 -> ECryptoAlgorithmSign.DIGEST_MD5;
      case DIGEST_RSA_SHA1, DIGEST_SHA1 -> ECryptoAlgorithmSign.DIGEST_SHA_1;
      case DIGEST_SHA256 -> ECryptoAlgorithmSign.DIGEST_SHA_256;
      case DIGEST_SHA384 -> ECryptoAlgorithmSign.DIGEST_SHA_384;
      case DIGEST_SHA512 -> ECryptoAlgorithmSign.DIGEST_SHA_512;
      default -> eAlgorithm;
    };
  }

  @Override
  public boolean equals (final Object o)
  {
    if (o == this)
      return true;
    if (o == null || !getClass ().equals (o.getClass ()))
      return false;
    final MIC rhs = (MIC) o;
    return Arrays.equals (m_aMICBytes, rhs.m_aMICBytes) &&
           _getUnified (m_eDigestAlgorithm).equals (_getUnified (rhs.m_eDigestAlgorithm));
  }

  @Override
  public int hashCode ()
  {
    return new HashCodeGenerator (this).append (m_aMICBytes).append (m_eDigestAlgorithm).getHashCode ();
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("MICBytes", m_aMICBytes)
                                       .append ("DigestAlgorithm", m_eDigestAlgorithm)
                                       .getToString ();
  }

  /**
   * Parse the provided String representation of the MIC into a {@link MIC} object. This is the
   * reverse operation to {@link MIC#getAsAS2String()}.
   *
   * @param sMIC
   *        The MIC string to parse. May be <code>null</code>.
   * @return <code>null</code> if an empty string was provided.
   * @throws IllegalArgumentException
   *         If the layout is invalid and either the bytes could not be Base64 decoded or if an
   *         invalid signing crypto algorithm was used.
   */
  @Nullable
  public static MIC parse (@Nullable final String sMIC)
  {
    if (StringHelper.isEmpty (sMIC))
      return null;

    final StringTokenizer st = new StringTokenizer (sMIC, ", \t\r\n");

    final String sMICBytes = st.nextToken ();
    final byte [] aMICBytes = Base64.safeDecode (sMICBytes);
    if (aMICBytes == null)
      throw new IllegalArgumentException ("Failed to base64 decode MIC '" + sMICBytes + "'");

    if (!st.hasMoreTokens ())
      throw new IllegalArgumentException ("Separator after Base64 bytes is missing");

    final String sAlgorithm = st.nextToken ();
    final ECryptoAlgorithmSign eDigestAlgorithm = ECryptoAlgorithmSign.getFromIDOrNull (sAlgorithm);
    if (eDigestAlgorithm == null)
      throw new IllegalArgumentException ("Failed to parse digest algorithm '" + sAlgorithm + "'");
    return new MIC (aMICBytes, eDigestAlgorithm);
  }
}
