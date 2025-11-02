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
package com.helger.phase2.disposition;

import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.equals.EqualsHelper;
import com.helger.base.string.StringHelper;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;

/**
 * Contains the disposition type for creating the MDN. That one determines if a message processed
 * successfully or not.
 *
 * @author Philip Helger
 */
@Immutable
public class DispositionType
{
  public static final String ACTION_AUTOMATIC_ACTION = "automatic-action";
  public static final String MDNACTION_MDN_SENT_AUTOMATICALLY = "MDN-sent-automatically";
  public static final String STATUS_PROCESSED = "processed";
  public static final String STATUS_MODIFIER_ERROR = "Error";
  public static final String STATUS_MODIFIER_WARNING = "Warning";

  private final String m_sAction;
  private final String m_sMDNAction;
  private final String m_sStatus;
  private final String m_sStatusDescription;
  private final String m_sStatusModifier;

  public DispositionType (@NonNull final String sAction,
                          @NonNull final String sMDNAction,
                          @NonNull final String sStatus,
                          @Nullable final String sStatusModifier,
                          @Nullable final String sStatusDescription)
  {
    ValueEnforcer.notNull (sAction, "Action");
    ValueEnforcer.notNull (sMDNAction, "MDNAction");
    ValueEnforcer.notNull (sStatus, "Status");

    m_sAction = sAction;
    m_sMDNAction = sMDNAction;
    m_sStatus = sStatus;
    m_sStatusModifier = sStatusModifier;
    m_sStatusDescription = sStatusDescription;
  }

  @NonNull
  public final String getAction ()
  {
    return m_sAction;
  }

  @NonNull
  public final String getMDNAction ()
  {
    return m_sMDNAction;
  }

  @NonNull
  public final String getStatus ()
  {
    return m_sStatus;
  }

  @Nullable
  public final String getStatusDescription ()
  {
    return m_sStatusDescription;
  }

  @Nullable
  public final String getStatusModifier ()
  {
    return m_sStatusModifier;
  }

  /**
   * @return <code>true</code> if it is an error, <code>false</code> if not (maybe success or
   *         warning).
   */
  public boolean isError ()
  {
    return EqualsHelper.equalsIgnoreCase (m_sStatusModifier, STATUS_MODIFIER_ERROR);
  }

  /**
   * @return <code>true</code> if it is a warning, <code>false</code> if not (maybe success or
   *         error).
   */
  public boolean isWarning ()
  {
    return EqualsHelper.equalsIgnoreCase (m_sStatusModifier, STATUS_MODIFIER_WARNING);
  }

  /**
   * Throws a {@link AS2DispositionException} if the message is a warning or an error. If the
   * disposition is fine this method simply returns.
   *
   * @param aSrcMsg
   *        The source message. May not be <code>null</code>.
   * @param sText
   *        The disposition text to use. May not be <code>null</code>.
   * @throws AS2DispositionException
   *         The checked exception.
   * @since 4.10.0 this method has parameters
   */
  public void validate (@NonNull final IMessage aSrcMsg, @NonNull final String sText) throws AS2DispositionException
  {
    if (isWarning ())
    {
      final AS2DispositionException ex = new AS2DispositionException (this, sText, null);
      AS2Exception.log (ex.getClass (), true, ex.getMessage (), null, aSrcMsg, null);
    }
    else
    {
      // Errors are thrown
      if (!m_sStatus.equalsIgnoreCase (STATUS_PROCESSED))
        throw new AS2DispositionException (this, sText, null);

      if (isError ())
        throw new AS2DispositionException (this, sText, null);
    }
  }

  @NonNull
  public String getAsString ()
  {
    final StringBuilder aSB = new StringBuilder ();
    aSB.append (m_sAction).append ('/').append (m_sMDNAction).append ("; ").append (m_sStatus);
    if (m_sStatusModifier != null)
    {
      aSB.append ('/').append (m_sStatusModifier).append (':');
      if (StringHelper.isNotEmpty (m_sStatusDescription))
        aSB.append (' ').append (m_sStatusDescription);
    }
    return aSB.toString ();
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("Action", m_sAction)
                                       .append ("MDNAction", m_sMDNAction)
                                       .append ("Status", m_sStatus)
                                       .append ("StatusModifier", m_sStatusModifier)
                                       .append ("StatusDescription", m_sStatusDescription)
                                       .getToString ();
  }

  @NonNull
  public static DispositionType createFromString (@Nullable final String sDisposition) throws AS2Exception
  {
    if (StringHelper.isEmpty (sDisposition))
      throw new AS2Exception ("Disposition type is empty");

    try
    {
      final StringTokenizer aDispTokens = new StringTokenizer (sDisposition, "/;:", false);
      final String sAction = aDispTokens.nextToken ().toLowerCase (Locale.US);
      final String sMDNAction = aDispTokens.nextToken ().toLowerCase (Locale.US);
      final String sStatus = aDispTokens.nextToken ().trim ().toLowerCase (Locale.US);

      String sStatusModifier = null;
      String sStatusDescription = null;
      if (aDispTokens.hasMoreTokens ())
      {
        sStatusModifier = aDispTokens.nextToken ().trim ().toLowerCase (Locale.US);
        if (aDispTokens.hasMoreTokens ())
          sStatusDescription = aDispTokens.nextToken ().trim ().toLowerCase (Locale.US);
      }
      return new DispositionType (sAction, sMDNAction, sStatus, sStatusModifier, sStatusDescription);
    }
    catch (final NoSuchElementException ex)
    {
      throw new AS2Exception ("Invalid disposition type format '" + sDisposition + "'", ex);
    }
  }

  /**
   * @return A success disposition without additional information. Never <code>null</code>.
   */
  @NonNull
  public static DispositionType createSuccess ()
  {
    return new DispositionType (ACTION_AUTOMATIC_ACTION,
                                MDNACTION_MDN_SENT_AUTOMATICALLY,
                                STATUS_PROCESSED,
                                null,
                                null);
  }

  /**
   * @param sStatusDescription
   *        The status description to be used. May not be <code>null</code>.
   * @return An error disposition with the modifier {@link #STATUS_MODIFIER_ERROR} and the provided
   *         status description.
   */
  @NonNull
  public static DispositionType createError (@NonNull final String sStatusDescription)
  {
    return new DispositionType (ACTION_AUTOMATIC_ACTION,
                                MDNACTION_MDN_SENT_AUTOMATICALLY,
                                STATUS_PROCESSED,
                                STATUS_MODIFIER_ERROR,
                                sStatusDescription);
  }
}
