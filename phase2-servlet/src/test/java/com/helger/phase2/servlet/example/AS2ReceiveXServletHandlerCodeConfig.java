/*
 * Copyright (C) 2015-2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phase2.servlet.example;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.ICommonsMap;
import com.helger.phase2.cert.CertificateFactory;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.partner.SelfFillingPartnershipFactory;
import com.helger.phase2.processor.DefaultMessageProcessor;
import com.helger.phase2.processor.sender.AsynchMDNSenderModule;
import com.helger.phase2.servlet.AbstractAS2ReceiveBaseXServletHandler;
import com.helger.phase2.servlet.AbstractAS2ReceiveXServletHandler;
import com.helger.phase2.servlet.util.AS2ServletMDNReceiverModule;
import com.helger.phase2.servlet.util.AS2ServletReceiverModule;
import com.helger.phase2.session.AS2Session;
import com.helger.security.keystore.EKeyStoreType;

import jakarta.servlet.ServletException;

/**
 * A special {@link AbstractAS2ReceiveBaseXServletHandler} with a code based
 * configuration. This is contained as an example only.
 *
 * @author Philip Helger
 */
public class AS2ReceiveXServletHandlerCodeConfig extends AbstractAS2ReceiveXServletHandler
{

  @Override
  @NonNull
  protected AS2Session createAS2Session (@NonNull final ICommonsMap <String, String> aInitParams) throws AS2Exception, ServletException
  {
    final AS2Session aSession = new AS2Session ();
    {
      // Create CertificateFactory
      final CertificateFactory aCertFactory = new CertificateFactory ();
      aCertFactory.setKeyStoreType (EKeyStoreType.PKCS12);
      aCertFactory.setFilename ("clientCertificate.jks");
      aCertFactory.setPassword ("test1234");
      aCertFactory.setSaveChangesToFile (false);
      aCertFactory.initDynamicComponent (aSession, null);
      aSession.setCertificateFactory (aCertFactory);
    }

    {
      // Create PartnershipFactory
      final SelfFillingPartnershipFactory aPartnershipFactory = new SelfFillingPartnershipFactory ();
      aPartnershipFactory.initDynamicComponent (aSession, null);
      aSession.setPartnershipFactory (aPartnershipFactory);
    }

    {
      // Create MessageProcessor
      final DefaultMessageProcessor aMessageProcessor = new DefaultMessageProcessor ();
      aMessageProcessor.setPendingMDNFolder ("data/pendingMDN");
      aMessageProcessor.setPendingMDNInfoFolder ("data/pendinginfoMDN");
      aMessageProcessor.initDynamicComponent (aSession, null);
      aSession.setMessageProcessor (aMessageProcessor);

      /**
       * Required to receive messages port is required internally - simply
       * ignore it for servlets
       */
      {
        final AS2ServletReceiverModule aModule = new AS2ServletReceiverModule ();
        aModule.setErrorDirectory ("data/inbox/error");
        aModule.setErrorFormat ("$msg.sender.as2_id$, $msg.receiver.as2_id$, $msg.headers.message-id$");
        aModule.initDynamicComponent (aSession, null);
        aMessageProcessor.addModule (aModule);
      }

      /** Only needed to receive asynchronous MDNs */
      {
        final AS2ServletMDNReceiverModule aModule = new AS2ServletMDNReceiverModule ();
        aModule.initDynamicComponent (aSession, null);
        aMessageProcessor.addModule (aModule);
      }

      /** Below module is used to send async mdn */
      {
        final AsynchMDNSenderModule aModule = new AsynchMDNSenderModule ();
        aModule.initDynamicComponent (aSession, null);
        aMessageProcessor.addModule (aModule);
      }

      /** A module storing the message. */
      {
        final MyHandlerModule aModule = new MyHandlerModule ();
        aModule.initDynamicComponent (aSession, null);
        aMessageProcessor.addModule (aModule);
      }

      // Add further modules if you need them
    }

    return aSession;
  }
}
