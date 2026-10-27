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
package com.helger.phase2.processor.receiver.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.KeyStore;

import org.junit.Test;

import com.helger.collection.commons.CommonsArrayList;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.phase2.cert.PredefinedCertificateFactory;
import com.helger.phase2.crypto.ECryptoAlgorithmCrypt;
import com.helger.phase2.disposition.AS2DispositionException;
import com.helger.phase2.message.AS2Message;
import com.helger.phase2.processor.receiver.AS2ReceiverModule;
import com.helger.phase2.session.AS2Session;
import com.helger.phase2.util.AS2Helper;
import com.helger.phase2.util.AS2ResourceHelper;
import com.helger.security.keystore.EKeyStoreType;
import com.helger.security.keystore.KeyStoreHelper;

import jakarta.mail.internet.MimeBodyPart;

/**
 * Test class for class {@link AS2ReceiverHandler}.
 *
 * @author Jake Huneau
 */
public final class AS2ReceiverHandlerTest
{
  private static final String KEYSTORE_PATH = "src/test/resources/localhost/certs.p12";
  private static final String KEYSTORE_PASSWORD = "test";
  private static final String ALIAS_A = "openas2a_alias";
  private static final String ALIAS_B = "openas2b_alias";
  private static final String CONTENT = "Hello world";

  private static PredefinedCertificateFactory _createCertFactory () throws Exception
  {
    final KeyStore aKS = KeyStoreHelper.loadKeyStore (EKeyStoreType.PKCS12,
                                                      KEYSTORE_PATH,
                                                      KEYSTORE_PASSWORD.toCharArray ())
                                       .getKeyStore ();
    final PredefinedCertificateFactory ret = new PredefinedCertificateFactory (aKS);
    ret.setPassword (KEYSTORE_PASSWORD);
    return ret;
  }

  private static AS2ReceiverHandler _createHandler (final PredefinedCertificateFactory aCertFactory) throws Exception
  {
    final AS2Session aSession = new AS2Session ();
    aSession.setCertificateFactory (aCertFactory);
    aCertFactory.initDynamicComponent (aSession, null);

    final AS2ReceiverModule aModule = new AS2ReceiverModule ();
    aModule.initDynamicComponent (aSession, null);
    return aModule.createHandler ();
  }

  private static AS2Message _createMessageEncryptedFor (final PredefinedCertificateFactory aCertFactory,
                                                        final String sAlias) throws Exception
  {
    final MimeBodyPart aPart = new MimeBodyPart ();
    aPart.setText (CONTENT);

    final AS2Message ret = new AS2Message ();
    ret.partnership ().setReceiverX509Alias (ALIAS_A);
    ret.setData (AS2Helper.getCryptoHelper ()
                          .encrypt (aPart,
                                    aCertFactory.getCertificate (sAlias),
                                    ECryptoAlgorithmCrypt.CRYPT_AES128_CBC,
                                    EContentTransferEncoding.BASE64));
    return ret;
  }

  @Test
  public void testDecryptWithReceiverAlias () throws Exception
  {
    final PredefinedCertificateFactory aCertFactory = _createCertFactory ();
    final AS2ReceiverHandler aHandler = _createHandler (aCertFactory);
    final AS2Message aMsg = _createMessageEncryptedFor (aCertFactory, ALIAS_A);
    aMsg.partnership ().setAdditionalDecryptX509Aliases (new CommonsArrayList <> (ALIAS_B));

    try (final AS2ResourceHelper aResHelper = new AS2ResourceHelper ())
    {
      aHandler.decrypt (aMsg, aResHelper);
      assertEquals (CONTENT, aMsg.getData ().getContent ());
    }
  }

  @Test
  public void testDecryptWithAdditionalAlias () throws Exception
  {
    final PredefinedCertificateFactory aCertFactory = _createCertFactory ();
    final AS2ReceiverHandler aHandler = _createHandler (aCertFactory);
    // Encrypted for B, while the partnership's receiver alias is A
    final AS2Message aMsg = _createMessageEncryptedFor (aCertFactory, ALIAS_B);
    // Aliases that don't exist are skipped
    aMsg.partnership ().setAdditionalDecryptX509Aliases (new CommonsArrayList <> ("does-not-exist", ALIAS_B));

    try (final AS2ResourceHelper aResHelper = new AS2ResourceHelper ())
    {
      aHandler.decrypt (aMsg, aResHelper);
      assertEquals (CONTENT, aMsg.getData ().getContent ());
      assertTrue (aMsg.attrs ().getAsBoolean (AS2Message.ATTRIBUTE_RECEIVED_ENCRYPTED));
    }
  }

  @Test
  public void testDecryptFailsWithoutMatchingAlias () throws Exception
  {
    final PredefinedCertificateFactory aCertFactory = _createCertFactory ();
    final AS2ReceiverHandler aHandler = _createHandler (aCertFactory);
    final AS2Message aMsg = _createMessageEncryptedFor (aCertFactory, ALIAS_B);

    try (final AS2ResourceHelper aResHelper = new AS2ResourceHelper ())
    {
      // No additional aliases
      try
      {
        aHandler.decrypt (aMsg, aResHelper);
        fail ();
      }
      catch (final AS2DispositionException ex)
      {
        // expected
      }

      // Only additional aliases that can't decrypt the message
      aMsg.partnership ().setAdditionalDecryptX509Aliases (new CommonsArrayList <> ("does-not-exist", ALIAS_A));
      try
      {
        aHandler.decrypt (aMsg, aResHelper);
        fail ();
      }
      catch (final AS2DispositionException ex)
      {
        assertEquals (2, ex.getCause ().getSuppressed ().length);
      }
    }
  }
}
