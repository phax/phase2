/*
 * Copyright (C) 2018-2026 Philip Helger (www.helger.com)
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
package com.helger.as2demo.springboot;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.helger.phase2.cert.IKeyStoreCertificateFactory;
import com.helger.phase2.client.AS2Client;
import com.helger.phase2.client.AS2ClientRequest;
import com.helger.phase2.client.AS2ClientResponse;
import com.helger.phase2.client.AS2ClientSettings;
import com.helger.phase2.crypto.ECompressionType;
import com.helger.phase2.crypto.ECryptoAlgorithmCrypt;
import com.helger.phase2.crypto.ECryptoAlgorithmSign;
import com.helger.phase2.disposition.DispositionOptions;
import com.helger.security.keystore.EKeyStoreType;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;

/**
 * Phase 1 stub: a thin REST wrapper around {@link AS2Client#sendSynchronous}.
 *
 * <p>Auth: matches the {@code X-API-Key} header against the {@code TENANT_API_KEY}
 * env var. When the env var is unset the controller fails closed (503).
 *
 * <p>Sender identity (AS2 ID, alias) and keystore details are read from env vars
 * with sensible defaults that match the existing single-tenant FGF deployment;
 * the caller specifies the partner per-request. Real per-tenant partnership
 * lookup against a registry comes in a later phase.
 */
@RestController
@RequestMapping ("/tenant-api")
public class OutboundSendController
{
  private static final Logger LOGGER = LoggerFactory.getLogger (OutboundSendController.class);

  private final String m_sExpectedApiKey = System.getenv ("TENANT_API_KEY");
  private final String m_sKeystorePath = System.getenv ()
                                               .getOrDefault ("PHASE2_OUTBOUND_KEYSTORE_FILE", "config/certs.p12");
  private final String m_sKeystorePassword = System.getenv ()
                                                   .getOrDefault ("PHASE2_OUTBOUND_KEYSTORE_PASSWORD", "test");
  private final String m_sSenderAs2Id = System.getenv ().getOrDefault ("PHASE2_AS2_ID", "PHASE2");
  private final String m_sSenderAlias = System.getenv ()
                                              .getOrDefault ("PHASE2_AS2_ALIAS", m_sSenderAs2Id);

  @PostMapping ("/send")
  public ResponseEntity <Map <String, Object>> send (@RequestHeader (value = "X-API-Key",
                                                                     required = false) final String apiKey,
                                                     @RequestBody final SendRequest req)
  {
    if (m_sExpectedApiKey == null || m_sExpectedApiKey.isBlank ())
    {
      LOGGER.warn ("Outbound send rejected: TENANT_API_KEY env var is not configured");
      return ResponseEntity.status (HttpStatus.SERVICE_UNAVAILABLE).body (errorBody ("outbound API not configured"));
    }
    if (apiKey == null || !m_sExpectedApiKey.equals (apiKey))
      return ResponseEntity.status (HttpStatus.UNAUTHORIZED).body (errorBody ("invalid API key"));

    final String validation = validate (req);
    if (validation != null)
      return ResponseEntity.badRequest ().body (errorBody (validation));

    try
    {
      final AS2ClientResponse resp = doSend (req);
      final Map <String, Object> body = new LinkedHashMap <> ();
      if (resp.hasException ())
      {
        body.put ("status", "failed");
        body.put ("error", resp.getAsString ());
        return ResponseEntity.status (HttpStatus.BAD_GATEWAY).body (body);
      }
      body.put ("status", "ok");
      body.put ("mdnMessageId", resp.getMDNMessageID ());
      body.put ("mdnDisposition", resp.getMDNDisposition ());
      body.put ("mdnText", resp.getMDNText ());
      return ResponseEntity.ok (body);
    }
    catch (final Exception ex)
    {
      LOGGER.error ("Outbound send failed for partner {}", req.partnerAs2Id, ex);
      return ResponseEntity.status (HttpStatus.INTERNAL_SERVER_ERROR)
                           .body (errorBody (ex.getClass ().getSimpleName () + ": " + ex.getMessage ()));
    }
  }

  private static String validate (final SendRequest req)
  {
    if (req == null)
      return "missing body";
    if (isBlank (req.partnerAs2Id))
      return "partnerAs2Id is required";
    if (isBlank (req.partnerAlias))
      return "partnerAlias is required";
    if (isBlank (req.partnerEndpointUrl))
      return "partnerEndpointUrl is required";
    if (isBlank (req.payloadBase64))
      return "payloadBase64 is required";
    if (isBlank (req.contentType))
      return "contentType is required";
    return null;
  }

  private AS2ClientResponse doSend (final SendRequest req) throws Exception
  {
    final byte [] payload = Base64.getDecoder ().decode (req.payloadBase64);
    final File keystoreFile = new File (m_sKeystorePath);

    // Reuse the AS2 session's already-loaded keystore for partner-cert lookup
    // — auto-reloaded on the CertificateFactory's interval (config.xml:25).
    final KeyStore ks = ((IKeyStoreCertificateFactory) ServletConfig.AS2_SESSION.getCertificateFactory ()).getKeyStore ();
    final X509Certificate partnerCert = (X509Certificate) ks.getCertificate (req.partnerAlias);
    if (partnerCert == null)
      throw new IllegalStateException ("Partner alias '" + req.partnerAlias + "' not found in keystore");

    final ECryptoAlgorithmSign signAlgo = ECryptoAlgorithmSign.DIGEST_SHA_256;
    final ECryptoAlgorithmCrypt cryptAlgo = ECryptoAlgorithmCrypt.CRYPT_AES128_CBC;

    final AS2ClientSettings settings = new AS2ClientSettings ();
    settings.setKeyStore (EKeyStoreType.PKCS12, keystoreFile, m_sKeystorePassword);
    settings.setSenderData (m_sSenderAs2Id, "noreply@example.com", m_sSenderAlias);
    settings.setReceiverData (req.partnerAs2Id, req.partnerAlias, req.partnerEndpointUrl);
    settings.setReceiverCertificate (partnerCert);
    settings.setEncryptAndSign (cryptAlgo, signAlgo);
    settings.setCompress (ECompressionType.ZLIB, AS2ClientSettings.DEFAULT_COMPRESS_BEFORE_SIGNING);
    settings.setMDNOptions (new DispositionOptions ().setMICAlg (signAlgo)
                                                     .setMICAlgImportance (DispositionOptions.IMPORTANCE_REQUIRED)
                                                     .setProtocol (DispositionOptions.PROTOCOL_PKCS7_SIGNATURE)
                                                     .setProtocolImportance (DispositionOptions.IMPORTANCE_REQUIRED));
    settings.setMessageIDFormat ("phase2-$date.uuuuMMdd-HHmmssZ$-$rand.1234$@$msg.sender.as2_id$_$msg.receiver.as2_id$");
    settings.setConnectTimeout (Timeout.ofSeconds (15));
    settings.setResponseTimeout (Timeout.ofSeconds (30));

    if (!isBlank (req.messageId))
      settings.setMessageIDFormat (req.messageId);

    final String subject = isBlank (req.subject) ? "AS2 outbound from " + m_sSenderAs2Id : req.subject;
    final AS2ClientRequest as2Req = new AS2ClientRequest (subject);
    as2Req.setData (new DataHandler (new ByteArrayDataSource (payload, req.contentType)));
    as2Req.setContentType (req.contentType);

    return new AS2Client ().sendSynchronous (settings, as2Req);
  }

  private static Map <String, Object> errorBody (final String msg)
  {
    final Map <String, Object> body = new LinkedHashMap <> ();
    body.put ("status", "error");
    body.put ("error", msg);
    return body;
  }

  private static boolean isBlank (final String s)
  {
    return s == null || s.isBlank ();
  }

  /** JSON request body. Field names match the JSON keys. */
  public static final class SendRequest
  {
    public String partnerAs2Id;
    public String partnerAlias;
    public String partnerEndpointUrl;
    public String payloadBase64;
    public String contentType;
    public String subject;
    public String messageId;
  }

  /** Minimal in-memory DataSource so we don't pull jakarta.mail just for ByteArrayDataSource. */
  private static final class ByteArrayDataSource implements DataSource
  {
    private final byte [] m_aBytes;
    private final String m_sContentType;

    ByteArrayDataSource (final byte [] bytes, final String contentType)
    {
      m_aBytes = bytes;
      m_sContentType = contentType;
    }

    @Override
    public InputStream getInputStream ()
    {
      return new ByteArrayInputStream (m_aBytes);
    }

    @Override
    public OutputStream getOutputStream () throws IOException
    {
      throw new IOException ("read-only");
    }

    @Override
    public String getContentType ()
    {
      return m_sContentType;
    }

    @Override
    public String getName ()
    {
      return "outbound-payload";
    }
  }
}
