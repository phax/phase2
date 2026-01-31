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

import java.net.Proxy;
import java.security.cert.X509Certificate;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.annotation.style.OverrideOnDemand;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.base.lang.clazz.FactoryNewInstance;
import com.helger.base.timing.StopWatch;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phase2.cert.AS2CertificateExistsException;
import com.helger.phase2.cert.AbstractCertificateFactory;
import com.helger.phase2.cert.CertificateFactory;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.AS2Message;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.partner.CPartnershipIDs;
import com.helger.phase2.partner.Partnership;
import com.helger.phase2.partner.SelfFillingPartnershipFactory;
import com.helger.phase2.processor.DefaultMessageProcessor;
import com.helger.phase2.processor.IMessageProcessor;
import com.helger.phase2.processor.resender.IProcessorResenderModule;
import com.helger.phase2.processor.resender.ImmediateResenderModule;
import com.helger.phase2.processor.sender.AS2SenderModule;
import com.helger.phase2.processor.sender.IProcessorSenderModule;
import com.helger.phase2.session.AS2Session;
import com.helger.security.certificate.CertificateDecodeHelper;
import com.helger.typeconvert.collection.StringMap;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;

/**
 * A simple client that allows for sending AS2 Messages and retrieving of synchronous MDNs.
 *
 * @author Philip Helger
 */
public class AS2Client
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AS2Client.class);
  private Supplier <AS2SenderModule> m_aAS2SenderModuleFactory = FactoryNewInstance.create (AS2SenderModule.class,
                                                                                            true);
  // proxy is not serializable
  private Proxy m_aHttpProxy;

  public AS2Client ()
  {}

  /**
   * Set the factory to create {@link AS2SenderModule} objects internally. By default a new instance
   * of {@link AS2SenderModule} is created so you don't need to call this method.
   *
   * @param aAS2SenderModuleFactory
   *        The factory to be used. May not be <code>null</code>.
   * @return this for chaining
   */
  @NonNull
  public AS2Client setAS2SenderModuleFactory (@NonNull final Supplier <AS2SenderModule> aAS2SenderModuleFactory)
  {
    m_aAS2SenderModuleFactory = ValueEnforcer.notNull (aAS2SenderModuleFactory, "AS2SenderModuleFactory");
    return this;
  }

  /**
   * @return The current HTTP proxy used. Defaults to <code>null</code>.
   */
  @Nullable
  public Proxy getHttpProxy ()
  {
    return m_aHttpProxy;
  }

  /**
   * Set the proxy server for transmission.
   *
   * @param aHttpProxy
   *        The proxy to use. May be <code>null</code> to indicate no proxy.
   * @return this for chaining
   */
  @NonNull
  public AS2Client setHttpProxy (@Nullable final Proxy aHttpProxy)
  {
    m_aHttpProxy = aHttpProxy;
    return this;
  }

  /**
   * Create a new {@link Partnership} object that is later used for message creation. If you
   * override this method, please ensure to call this class' version of the method first.
   *
   * @param aSettings
   *        The current client settings. Never <code>null</code>.
   * @return Non-<code>null</code> Partnership.
   */
  @NonNull
  @OverrideOnDemand
  @OverridingMethodsMustInvokeSuper
  protected Partnership buildPartnership (@NonNull final AS2ClientSettings aSettings)
  {
    final Partnership aPartnership = new Partnership (aSettings.getPartnershipName ());

    aPartnership.setSenderAS2ID (aSettings.getSenderAS2ID ());
    aPartnership.setSenderX509Alias (aSettings.getSenderKeyAlias ());
    aPartnership.setSenderEmail (aSettings.getSenderEmailAddress ());

    aPartnership.setReceiverAS2ID (aSettings.getReceiverAS2ID ());
    aPartnership.setReceiverX509Alias (aSettings.getReceiverKeyAlias ());

    aPartnership.setAS2URL (aSettings.getDestinationAS2URL ());
    aPartnership.setEncryptAlgorithm (aSettings.getCryptAlgo ());
    aPartnership.setSigningAlgorithm (aSettings.getSignAlgo ());
    aPartnership.setProtocol (AS2Message.PROTOCOL_AS2);
    aPartnership.setMessageIDFormat (aSettings.getMessageIDFormat ());

    if (aSettings.isMDNRequested ())
    {
      // We want an MDN

      // This field must be set to a valid email address
      aPartnership.setAS2MDNTo (aSettings.getSenderEmailAddress ());

      // signed MDN requested?
      aPartnership.setAS2MDNOptions (aSettings.getMDNOptions ());

      if (aSettings.isAsyncMDNRequested ())
      {
        // We want an async MDN
        aPartnership.setAS2ReceiptDeliveryOption (aSettings.getAsyncMDNUrl ());
      }
      else
      {
        // We want an sync MDN
        // This field must be null - otherwise async MDN!
        aPartnership.setAS2ReceiptDeliveryOption (null);
      }
    }
    else
    {
      // We don't want an MDN
      aPartnership.setAS2MDNTo (null);
      aPartnership.setAS2ReceiptDeliveryOption (null);
      aPartnership.setAS2MDNOptions (null);
    }

    if (aSettings.getCompressionType () != null)
    {
      aPartnership.setCompressionType (aSettings.getCompressionType ());
      aPartnership.setCompressionMode (aSettings.isCompressBeforeSigning () ? CPartnershipIDs.COMPRESS_BEFORE_SIGNING
                                                                            : CPartnershipIDs.COMPRESS_AFTER_SIGNING);
    }

    return aPartnership;
  }

  @NonNull
  @OverrideOnDemand
  protected AS2Message createAS2MessageObj ()
  {
    return new AS2Message ();
  }

  @NonNull
  @OverrideOnDemand
  @OverridingMethodsMustInvokeSuper
  protected AS2Message createMessage (@NonNull final Partnership aPartnership, @NonNull final AS2ClientRequest aRequest)
                                                                                                                         throws MessagingException
  {
    final AS2Message aMsg = createAS2MessageObj ();
    aMsg.setContentType (aRequest.getContentType ());
    aMsg.setSubject (aRequest.getSubject ());
    aMsg.setPartnership (aPartnership);
    aMsg.setMessageID (aMsg.generateMessageID ());

    aMsg.attrs ().putIn (CPartnershipIDs.PA_AS2_URL, aPartnership.getAS2URL ());
    aMsg.attrs ().putIn (CPartnershipIDs.PID_AS2, aPartnership.getReceiverAS2ID ());
    aMsg.attrs ().putIn (CPartnershipIDs.PID_EMAIL, aPartnership.getSenderEmail ());

    // Build message content
    final MimeBodyPart aPart = new MimeBodyPart ();
    aRequest.applyDataOntoMimeBodyPart (aPart);
    aMsg.setData (aPart);

    return aMsg;
  }

  /**
   * @return The certificate factory instance to be used. May not be <code>null</code>.
   */
  @NonNull
  @OverrideOnDemand
  protected CertificateFactory createCertificateFactory ()
  {
    return new CertificateFactory ();
  }

  /**
   * This method initializes the certificate factory. If you override this method, please make sure
   * that you call <code>aSession.setCertificateFactory (aCertFactory);</code>.
   *
   * @param aSettings
   *        The AS2 client settings. Never <code>null</code>.
   * @param aSession
   *        The AS2 session to be initialized. Never <code>null</code>.
   * @throws AS2Exception
   *         In case of error
   */
  @OverrideOnDemand
  protected void initCertificateFactory (@NonNull final AS2ClientSettings aSettings, @NonNull final AS2Session aSession)
                                                                                                                         throws AS2Exception
  {
    final StringMap aParams = new StringMap ();
    // TYPE is the only parameter that must be present in initDynamicComponents
    aParams.putIn (AbstractCertificateFactory.ATTR_TYPE, aSettings.getKeyStoreType ().getID ());

    final CertificateFactory aCertFactory = createCertificateFactory ();
    aCertFactory.initDynamicComponent (aSession, aParams);

    if (aSettings.getKeyStoreFile () != null)
    {
      LOGGER.info ("Loading AS2 client keystore from file " + aSettings.getKeyStoreFile ());

      aCertFactory.setFilename (aSettings.getKeyStoreFile ().getAbsolutePath ());
      aCertFactory.setPassword (aSettings.getKeyStorePassword ());
      aCertFactory.setSaveChangesToFile (aSettings.isSaveKeyStoreChangesToFile ());
      aCertFactory.load ();
    }
    else
      if (aSettings.getKeyStoreBytes () != null && aSettings.getKeyStorePassword () != null)
      {
        LOGGER.info ("Loading AS2 client keystore from byte array. No changes will be saved.");

        aCertFactory.setPassword (aSettings.getKeyStorePassword ());
        aCertFactory.setSaveChangesToFile (false);
        try (final NonBlockingByteArrayInputStream aBAIS = new NonBlockingByteArrayInputStream (aSettings.getKeyStoreBytes ()))
        {
          aCertFactory.load (aBAIS, aSettings.getKeyStorePassword ().toCharArray ());
        }
      }
      else
      {
        LOGGER.warn ("No AS2 client keystore data was provided. Signing and encryption/decryption will most likely fail.");

        // No file provided - no storage
        aCertFactory.setSaveChangesToFile (false);
      }

    if (aSettings.getReceiverCertificate () != null)
    {
      // Dynamically add recipient certificate if provided
      try
      {
        aCertFactory.addCertificate (aSettings.getReceiverKeyAlias (), aSettings.getReceiverCertificate (), false);
      }
      catch (final AS2CertificateExistsException ex)
      {
        // ignore
      }
    }
    aSession.setCertificateFactory (aCertFactory);
  }

  /**
   * Init the partnership factory. If you override this method, please make sure that you call
   * <code>aSession.setPartnershipFactory (aPartnershipFactory);</code>.
   *
   * @param aSession
   *        The AS2 session to be filled. Never <code>null</code>.
   * @throws AS2Exception
   *         In case of error
   */
  @OverrideOnDemand
  protected void initPartnershipFactory (@NonNull final AS2Session aSession) throws AS2Exception
  {
    // Use a self-filling in-memory partnership factory
    final SelfFillingPartnershipFactory aPartnershipFactory = new SelfFillingPartnershipFactory ();
    aPartnershipFactory.initDynamicComponent (aSession, null);
    aSession.setPartnershipFactory (aPartnershipFactory);
  }

  /**
   * Init the message processor. If you override this method, please make sure that you call
   * <code>aSession.setMessageProcessor (aMessageProcessor);</code>.
   *
   * @param aSession
   *        The AS2 session to be filled. Never <code>null</code>.
   * @throws AS2Exception
   *         In case of error
   */
  @OverrideOnDemand
  protected void initMessageProcessor (@NonNull final AS2Session aSession) throws AS2Exception
  {
    final IMessageProcessor aMessageProcessor = new DefaultMessageProcessor ();
    aMessageProcessor.initDynamicComponent (aSession, null);
    aSession.setMessageProcessor (aMessageProcessor);
  }

  /**
   * Create an empty response object that is to be filled.
   *
   * @return The empty response object and never <code>null</code>.
   */
  @NonNull
  @OverrideOnDemand
  protected AS2ClientResponse createResponse ()
  {
    return new AS2ClientResponse ();
  }

  /**
   * Create the AS2 session to be used. This method must ensure an eventually needed proxy is set.
   *
   * @return The new AS2 session and never <code>null</code>.
   */
  @NonNull
  @OverrideOnDemand
  protected AS2Session createSession ()
  {
    final AS2Session ret = new AS2Session ();
    ret.setHttpProxy (m_aHttpProxy);
    return ret;
  }

  /**
   * Callback method that is invoked before the main sending. This may be used to customize the
   * message.
   *
   * @param aSettings
   *        Client settings. Never <code>null</code>.
   * @param aSession
   *        Current session. Never <code>null</code>.
   * @param aMsg
   *        Current message. Never <code>null</code>.
   */
  @OverrideOnDemand
  protected void beforeSend (@NonNull final AS2ClientSettings aSettings,
                             @NonNull final AS2Session aSession,
                             @NonNull final IMessage aMsg)
  {}

  /**
   * Send the AS2 message synchronously
   *
   * @param aSettings
   *        The settings to be used. May not be <code>null</code>.
   * @param aRequest
   *        The request data to be send. May not be <code>null</code>.
   * @return The response object. Never <code>null</code>.
   */
  @NonNull
  public AS2ClientResponse sendSynchronous (@NonNull final AS2ClientSettings aSettings,
                                            @NonNull final AS2ClientRequest aRequest)
  {
    ValueEnforcer.notNull (aSettings, "ClientSettings");
    ValueEnforcer.notNull (aRequest, "ClientRequest");

    final AS2ClientResponse aResponse = createResponse ();
    IMessage aMsg = null;
    final StopWatch aSW = StopWatch.createdStarted ();
    try
    {
      final Partnership aPartnership = buildPartnership (aSettings);

      aMsg = createMessage (aPartnership, aRequest);
      aResponse.setOriginalMessageID (aMsg.getMessageID ());

      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("MessageID to send: " + aMsg.getMessageID ());

      final boolean bHasRetries = aSettings.getRetryCount () > 0;

      // Start a new session
      final AS2Session aSession = createSession ();

      initCertificateFactory (aSettings, aSession);
      initPartnershipFactory (aSession);
      initMessageProcessor (aSession);

      if (bHasRetries)
      {
        // Use synchronous no-delay resender
        final IProcessorResenderModule aResender = new ImmediateResenderModule ();
        aResender.initDynamicComponent (aSession, null);
        aSession.getMessageProcessor ().addModule (aResender);
      }

      aSession.getMessageProcessor ().startActiveModules ();
      try
      {
        // Invoke callback
        beforeSend (aSettings, aSession, aMsg);

        // Build options map for "handle"
        final ICommonsMap <String, Object> aHandleOptions = new CommonsHashMap <> ();
        if (bHasRetries)
          aHandleOptions.put (IProcessorResenderModule.OPTION_RETRIES, Integer.toString (aSettings.getRetryCount ()));

        // Content-Transfer-Encoding is a partnership property
        aPartnership.setContentTransferEncodingSend (aRequest.getContentTransferEncoding ());
        aPartnership.setContentTransferEncodingReceive (aRequest.getContentTransferEncoding ());

        // And create a sender module that directly sends the message
        // The message processor registration is required for the resending
        // feature
        final AS2SenderModule aSender = m_aAS2SenderModuleFactory.get ();
        aSender.initDynamicComponent (aSession, null);
        // Set connect and read timeout
        aSender.setConnectTimeoutMilliseconds (aSettings.getConnectTimeout ().toMilliseconds ());
        aSender.setResponseTimeoutMilliseconds (aSettings.getResponseTimeout ().toMilliseconds ());
        aSender.setQuoteHeaderValues (aSettings.isQuoteHeaderValues ());
        // Don't accidentally overwrite dumper factory set by system properties
        if (aSettings.getHttpOutgoingDumperFactory () != null)
          aSender.setHttpOutgoingDumperFactory (aSettings.getHttpOutgoingDumperFactory ());
        aSender.setHttpIncomingDumper (aSettings.getHttpIncomingDumper ());
        if (aSettings.getMICMatchingHandler () != null)
          aSender.setMICMatchingHandler (aSettings.getMICMatchingHandler ());
        aSender.setVerificationCertificateConsumer (aSettings.getVerificationCertificateConsumer ());

        // Add all custom headers
        aMsg.headers ().setAllHeaders (aSettings.customHeaders ());

        // Added sender as processor
        aSession.getMessageProcessor ().addModule (aSender);

        // Main sending
        aSender.handle (IProcessorSenderModule.DO_SEND, aMsg, aHandleOptions);
      }
      finally
      {
        aSession.getMessageProcessor ().stopActiveModules ();
      }
    }
    catch (final Exception ex)
    {
      LOGGER.error ("Error sending AS2 message", ex);
      aResponse.setException (ex);
    }
    finally
    {
      if (aMsg != null && aMsg.getMDN () != null)
      {
        // May be present, even in case of an exception
        aResponse.setMDN (aMsg.getMDN ());

        // Remember the certificate that was used to verify the MDN
        final String sReceivedCert = aMsg.attrs ().getAsString (AS2Message.ATTRIBUTE_RECEIVED_SIGNATURE_CERTIFICATE);
        if (sReceivedCert != null)
        {
          final X509Certificate aReceivedCert = new CertificateDecodeHelper ().source (sReceivedCert)
                                                                              .pemEncoded (true)
                                                                              .getDecodedOrNull ();
          aResponse.setMDNVerificationCertificate (aReceivedCert);
        }
      }
    }

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Response retrieved: " + aResponse.getAsString ());

    aResponse.setExecutionDuration (aSW.stopAndGetDuration ());
    return aResponse;
  }
}
