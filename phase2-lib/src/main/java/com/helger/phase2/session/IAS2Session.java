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
package com.helger.phase2.session;

import java.net.Proxy;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phase2.IDynamicComponent;
import com.helger.phase2.cert.ICertificateFactory;
import com.helger.phase2.partner.IPartnershipFactory;
import com.helger.phase2.processor.IMessageProcessor;

/**
 * This interface provides configuration and resource information, and a means for components to
 * access the functionality of other components.
 *
 * @author Aaron Silinskas
 * @see IDynamicComponent
 * @see com.helger.phase2.cert.ICertificateFactory
 * @see com.helger.phase2.partner.IPartnershipFactory
 * @see com.helger.phase2.processor.IMessageProcessor
 */
public interface IAS2Session
{
  /**
   * Registers a component to a specified ID.
   *
   * @param sComponentID
   *        registers the component to this ID
   * @param aComponent
   *        component to register
   * @see IDynamicComponent
   * @throws AS2ComponentDuplicateException
   *         In case a component with the same ID is already present
   */
  void addComponent (@NonNull @Nonempty String sComponentID, @NonNull IDynamicComponent aComponent)
                                                                                                    throws AS2ComponentDuplicateException;

  /**
   * Gets the <code>Component</code> currently registered with an ID
   *
   * @param sComponentID
   *        ID to search for
   * @return the component registered to the ID and never <code>null</code>.
   * @throws AS2ComponentNotFoundException
   *         If a component is not registered with the ID
   */
  @NonNull
  IDynamicComponent getComponent (@NonNull @Nonempty String sComponentID) throws AS2ComponentNotFoundException;

  /**
   * Return a map of component ID's to <code>Component</code> objects.
   *
   * @return all registered components, mapped by ID
   */
  @NonNull
  @ReturnsMutableCopy
  ICommonsMap <String, IDynamicComponent> getAllComponents ();

  /**
   * Short-cut method to retrieve a certificate factory.
   *
   * @return the currently registered <code>CertificateFactory</code> component
   * @throws AS2ComponentNotFoundException
   *         If a <code>CertificateFactory</code> component has not been registered
   * @see ICertificateFactory
   * @see IDynamicComponent
   */
  @NonNull
  ICertificateFactory getCertificateFactory () throws AS2ComponentNotFoundException;

  /**
   * Short-cut method to retrieve a partner factory.
   *
   * @return the currently registered <code>PartnerFactory</code> component
   * @throws AS2ComponentNotFoundException
   *         If a <code>PartnerFactory</code> component has not been registered
   * @see IPartnershipFactory
   * @see IDynamicComponent
   */
  @NonNull
  IPartnershipFactory getPartnershipFactory () throws AS2ComponentNotFoundException;

  /**
   * Short-cut method to retrieve a processor.
   *
   * @return the currently registered <code>Processor</code> component
   * @throws AS2ComponentNotFoundException
   *         If a <code>Processor</code> component has not been registered
   * @see IMessageProcessor
   * @see IDynamicComponent
   */
  @NonNull
  IMessageProcessor getMessageProcessor () throws AS2ComponentNotFoundException;

  /**
   * @return <code>true</code> if the certificate used for signing a message should be included in
   *         the signed MIME body part or not. Defaults to <code>true</code>.
   * @see #setCryptoSignIncludeCertificateInBodyPart(boolean)
   */
  boolean isCryptoSignIncludeCertificateInBodyPart ();

  /**
   * Settings flag, whether a the signing certificate should be included in the signed MIME body
   * part or not.
   *
   * @param bCryptoSignIncludeCertificateInBodyPart
   *        <code>true</code> to include the signing certificate in the signed MIME body part,
   *        <code>false</code> to not do so.
   * @see #isCryptoSignIncludeCertificateInBodyPart()
   */
  void setCryptoSignIncludeCertificateInBodyPart (boolean bCryptoSignIncludeCertificateInBodyPart);

  /**
   * @return <code>true</code> if any certificate passed in a message body is used for certificate
   *         verification or <code>false</code> if only the certificate present in the partnership
   *         factory is to be used. Defaults to <code>true</code>.
   * @see #setCryptoVerifyUseCertificateInBodyPart(boolean)
   */
  boolean isCryptoVerifyUseCertificateInBodyPart ();

  /**
   * Settings flag, whether a contained certificate is used for message verification.
   *
   * @param bCryptoVerifyUseCertificateInBodyPart
   *        <code>true</code> if any certificate passed in a message body is used for certificate
   *        verification or <code>false</code> if only the certificate present in the partnership
   *        factory is to be used.
   * @see #isCryptoVerifyUseCertificateInBodyPart()
   */
  void setCryptoVerifyUseCertificateInBodyPart (boolean bCryptoVerifyUseCertificateInBodyPart);

  /**
   * Get the optional HTTP/HTTPS proxy settings to be used for sending AS2 messages and asynchronous
   * MDNs.
   *
   * @return The HTTP/HTTPS proxy object to be used. May be <code>null</code>.
   * @see #setHttpProxy(Proxy)
   */
  @Nullable
  Proxy getHttpProxy ();

  /**
   * Set the optional HTTP/HTTPS proxy settings to be used for sending AS2 messages and asynchronous
   * MDNs.
   *
   * @param aHttpProxy
   *        The HTTP/HTTPS proxy object to be used. May be <code>null</code>.
   * @see #getHttpProxy()
   */
  void setHttpProxy (@Nullable Proxy aHttpProxy);

  /**
   * @return The AS2 version to use. The default is
   *         {@link com.helger.phase2.util.CAS2Header#DEFAULT_AS2_VERSION}. Neither
   *         <code>null</code> nor empty.
   * @since 4.6.1
   */
  @NonNull
  @Nonempty
  String getAS2VersionID ();

  /**
   * @param sAS2Version
   *        Set the AS2 version to use. May neither be <code>null</code> nor empty.
   * @since 4.6.1
   */
  void setAS2VersionID (@NonNull @Nonempty String sAS2Version);
}
