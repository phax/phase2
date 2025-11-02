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
package com.helger.phase2.session;

import java.net.Proxy;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.annotation.style.ReturnsMutableObject;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phase2.IDynamicComponent;
import com.helger.phase2.cert.ICertificateFactory;
import com.helger.phase2.partner.IPartnershipFactory;
import com.helger.phase2.processor.IMessageProcessor;
import com.helger.phase2.util.CAS2Header;
import com.helger.phase2.util.javamail.DispositionDataContentHandler;

import jakarta.activation.CommandMap;
import jakarta.activation.MailcapCommandMap;

/**
 * Default implementation of {@link IAS2Session}
 *
 * @author Philip Helger
 */
@NotThreadSafe
public class AS2Session implements IAS2Session
{
  public static final String COMPONENT_ID_CERTIFICATE_FACTORY = "certificatefactory";
  public static final String COMPONENT_ID_PARTNERSHIP_FACTORY = "partnershipfactory";
  public static final String COMPONENT_ID_MESSAGE_PROCESSOR = "message-processor";

  public static final boolean DEFAULT_CRYPTO_SIGN_INCLUDE_CERTIFICATE_IN_BODY_PART = true;
  public static final boolean DEFAULT_CRYPTO_VERIFY_USE_CERTIFICATE_IN_BODY_PART = true;

  private final ICommonsMap <String, IDynamicComponent> m_aComponents = new CommonsHashMap <> ();
  private boolean m_bCryptoSignIncludeCertificateInBodyPart = DEFAULT_CRYPTO_SIGN_INCLUDE_CERTIFICATE_IN_BODY_PART;
  private boolean m_bCryptoVerifyUseCertificateInBodyPart = DEFAULT_CRYPTO_VERIFY_USE_CERTIFICATE_IN_BODY_PART;
  private Proxy m_aHttpProxy;
  private String m_sAS2VersionID = CAS2Header.DEFAULT_AS2_VERSION;

  public static void makeAS2CommandMapChanges ()
  {
    /*
     * Adds a group of content handlers to the Mailcap <code>CommandMap</code>. These handlers are
     * used by the JavaMail API to encode and decode information of specific mime types.
     */
    final MailcapCommandMap aCommandMap = (MailcapCommandMap) CommandMap.getDefaultCommandMap ();
    aCommandMap.addMailcap ("message/disposition-notification;; x-java-content-handler=" +
                            DispositionDataContentHandler.class.getName ());
    CommandMap.setDefaultCommandMap (aCommandMap);
  }

  /**
   * Constructor
   */
  public AS2Session ()
  {
    makeAS2CommandMapChanges ();
  }

  public final void addComponent (@NonNull @Nonempty final String sComponentID,
                                  @NonNull final IDynamicComponent aComponent) throws AS2ComponentDuplicateException
  {
    ValueEnforcer.notEmpty (sComponentID, "ComponentID");
    ValueEnforcer.notNull (aComponent, "Component");
    if (m_aComponents.containsKey (sComponentID))
      throw new AS2ComponentDuplicateException (sComponentID);
    m_aComponents.put (sComponentID, aComponent);
  }

  public void setCertificateFactory (@NonNull final ICertificateFactory aCertFactory) throws AS2ComponentDuplicateException
  {
    addComponent (COMPONENT_ID_CERTIFICATE_FACTORY, aCertFactory);
  }

  public void setPartnershipFactory (@NonNull final IPartnershipFactory aPartnershipFactory) throws AS2ComponentDuplicateException
  {
    addComponent (COMPONENT_ID_PARTNERSHIP_FACTORY, aPartnershipFactory);
  }

  public void setMessageProcessor (@NonNull final IMessageProcessor aMsgProcessor) throws AS2ComponentDuplicateException
  {
    addComponent (COMPONENT_ID_MESSAGE_PROCESSOR, aMsgProcessor);
  }

  @NonNull
  public final IDynamicComponent getComponent (@NonNull @Nonempty final String sComponentID) throws AS2ComponentNotFoundException
  {
    ValueEnforcer.notEmpty (sComponentID, "ComponentID");
    final IDynamicComponent aComponent = m_aComponents.get (sComponentID);
    if (aComponent == null)
      throw new AS2ComponentNotFoundException (sComponentID);
    return aComponent;
  }

  /**
   * All modifications done here, have impact on the whole session
   *
   * @return The mutual component map. Never <code>null</code> handle with care.
   */
  @NonNull
  @ReturnsMutableObject
  protected final ICommonsMap <String, IDynamicComponent> components ()
  {
    return m_aComponents;
  }

  @NonNull
  @ReturnsMutableCopy
  public final ICommonsMap <String, IDynamicComponent> getAllComponents ()
  {
    return m_aComponents.getClone ();
  }

  @NonNull
  public final ICertificateFactory getCertificateFactory () throws AS2ComponentNotFoundException
  {
    return (ICertificateFactory) getComponent (COMPONENT_ID_CERTIFICATE_FACTORY);
  }

  @NonNull
  public final IPartnershipFactory getPartnershipFactory () throws AS2ComponentNotFoundException
  {
    return (IPartnershipFactory) getComponent (COMPONENT_ID_PARTNERSHIP_FACTORY);
  }

  @NonNull
  public final IMessageProcessor getMessageProcessor () throws AS2ComponentNotFoundException
  {
    return (IMessageProcessor) getComponent (COMPONENT_ID_MESSAGE_PROCESSOR);
  }

  public final boolean isCryptoSignIncludeCertificateInBodyPart ()
  {
    return m_bCryptoSignIncludeCertificateInBodyPart;
  }

  public final void setCryptoSignIncludeCertificateInBodyPart (final boolean bCryptoSignIncludeCertificateInBodyPart)
  {
    m_bCryptoSignIncludeCertificateInBodyPart = bCryptoSignIncludeCertificateInBodyPart;
  }

  public final boolean isCryptoVerifyUseCertificateInBodyPart ()
  {
    return m_bCryptoVerifyUseCertificateInBodyPart;
  }

  public final void setCryptoVerifyUseCertificateInBodyPart (final boolean bCryptoVerifyUseCertificateInBodyPart)
  {
    m_bCryptoVerifyUseCertificateInBodyPart = bCryptoVerifyUseCertificateInBodyPart;
  }

  @Nullable
  public final Proxy getHttpProxy ()
  {
    return m_aHttpProxy;
  }

  public final void setHttpProxy (@Nullable final Proxy aHttpProxy)
  {
    m_aHttpProxy = aHttpProxy;
  }

  @NonNull
  @Nonempty
  public final String getAS2VersionID ()
  {
    return m_sAS2VersionID;
  }

  public final void setAS2VersionID (@NonNull @Nonempty final String sAS2Version)
  {
    ValueEnforcer.notEmpty (sAS2Version, "AS2Version");
    m_sAS2VersionID = sAS2Version;
  }

  @OverridingMethodsMustInvokeSuper
  public void resetToDefault ()
  {
    // Clear old stuff
    components ().clear ();
    setCryptoSignIncludeCertificateInBodyPart (DEFAULT_CRYPTO_SIGN_INCLUDE_CERTIFICATE_IN_BODY_PART);
    setCryptoVerifyUseCertificateInBodyPart (DEFAULT_CRYPTO_VERIFY_USE_CERTIFICATE_IN_BODY_PART);
    setHttpProxy (null);
    setAS2VersionID (CAS2Header.DEFAULT_AS2_VERSION);
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("Components", m_aComponents)
                                       .append ("CryptoSignIncludeCertificateInBodyPart",
                                                m_bCryptoSignIncludeCertificateInBodyPart)
                                       .append ("CryptoVerifyUseCertificateInBodyPart",
                                                m_bCryptoVerifyUseCertificateInBodyPart)
                                       .append ("HttpProxy", m_aHttpProxy)
                                       .append ("AS2VersionID", m_sAS2VersionID)
                                       .getToString ();
  }
}
