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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.RecipientId;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientId;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.bouncycastle.mail.smime.SMIMEEnvelopedParser;
import org.bouncycastle.mail.smime.SMIMEException;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.mail.smime.SMIMESignedParser;
import org.bouncycastle.mail.smime.SMIMEUtil;
import org.bouncycastle.mail.smime.util.FileBackedMimeBodyPart;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonempty;
import com.helger.annotation.WillNotClose;
import com.helger.annotation.style.CodingStyleguideUnaware;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.equals.EqualsHelper;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.base.io.stream.NullOutputStream;
import com.helger.base.lang.clazz.ClassHelper;
import com.helger.base.string.StringHelper;
import com.helger.base.system.SystemProperties;
import com.helger.bc.PBCProvider;
import com.helger.collection.CollectionFind;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.datetime.helper.PDTFactory;
import com.helger.http.CHttp;
import com.helger.http.CHttpHeader;
import com.helger.io.file.FileHelper;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.util.AS2HttpHelper;
import com.helger.phase2.util.AS2IOHelper;
import com.helger.phase2.util.AS2ResourceHelper;
import com.helger.security.keystore.IKeyStoreType;

import jakarta.activation.CommandMap;
import jakarta.activation.MailcapCommandMap;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;

/**
 * Implementation of {@link ICryptoHelper} based on BouncyCastle.
 *
 * @author Philip Helger
 */
public class BCCryptoHelper implements ICryptoHelper
{
  public static final String DEFAULT_SECURITY_PROVIDER_NAME;
  private static final Logger LOGGER = LoggerFactory.getLogger (BCCryptoHelper.class);
  private static final File DUMP_DECRYPTED_DIR_PATH;
  private static final byte [] EOL_BYTES = AS2IOHelper.getAllAsciiBytes (CHttp.EOL);

  static
  {
    // Differentiate between BC and BC FIPS
    String sProvName;
    try
    {
      // Try regular BC first
      final Class <?> aBCClass = Class.forName ("org.bouncycastle.jce.provider.BouncyCastleProvider");
      // Use for correct initialization
      sProvName = PBCProvider.getProvider ().getName ();
      if (Security.getProvider (sProvName) == null)
      {
        // Create and add a new one
        Security.addProvider ((Provider) aBCClass.getConstructor ().newInstance ());
      }
    }
    catch (final Exception ex1)
    {
      try
      {
        // BC FIPS seconds
        final Class <?> aBCFPClass = Class.forName ("org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider");
        sProvName = "BCFIPS";
        if (Security.getProvider (sProvName) == null)
        {
          // Create and add a new one
          Security.addProvider ((Provider) aBCFPClass.getConstructor ().newInstance ());
        }
      }
      catch (final Exception ex2)
      {
        throw new IllegalStateException ("Neither regular BouncyCastle nor BouncyCastle FIPS are in the classpath",
                                         ex2);
      }
    }
    DEFAULT_SECURITY_PROVIDER_NAME = sProvName;
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Using security provider '" + DEFAULT_SECURITY_PROVIDER_NAME + "'.");

    final String sDumpDecryptedDirectory = SystemProperties.getPropertyValueOrNull ("AS2.dumpDecryptedDirectory");
    if (StringHelper.isNotEmpty (sDumpDecryptedDirectory))
    {
      DUMP_DECRYPTED_DIR_PATH = new File (sDumpDecryptedDirectory);
      AS2IOHelper.getFileOperationManager ().createDirIfNotExisting (DUMP_DECRYPTED_DIR_PATH);

      LOGGER.info ("Using directory " +
                   DUMP_DECRYPTED_DIR_PATH.getAbsolutePath () +
                   " to dump all decrypted AS2 body parts to.");
    }
    else
      DUMP_DECRYPTED_DIR_PATH = null;
  }

  private String m_sSecurityProviderName = DEFAULT_SECURITY_PROVIDER_NAME;

  public BCCryptoHelper ()
  {
    final MailcapCommandMap aCommandMap = (MailcapCommandMap) CommandMap.getDefaultCommandMap ();
    aCommandMap.addMailcap ("application/pkcs7-signature;; x-java-content-handler=" +
                            org.bouncycastle.mail.smime.handlers.pkcs7_signature.class.getName ());
    aCommandMap.addMailcap ("application/pkcs7-mime;; x-java-content-handler=" +
                            org.bouncycastle.mail.smime.handlers.pkcs7_mime.class.getName ());
    aCommandMap.addMailcap ("application/x-pkcs7-signature;; x-java-content-handler=" +
                            org.bouncycastle.mail.smime.handlers.x_pkcs7_signature.class.getName ());
    aCommandMap.addMailcap ("application/x-pkcs7-mime;; x-java-content-handler=" +
                            org.bouncycastle.mail.smime.handlers.x_pkcs7_mime.class.getName ());
    aCommandMap.addMailcap ("multipart/signed;; x-java-content-handler=" +
                            org.bouncycastle.mail.smime.handlers.multipart_signed.class.getName ());
    CommandMap.setDefaultCommandMap (aCommandMap);
  }

  /**
   * @return The security provider name to use. <code>BC</code> by default.
   * @since 4.2.0
   */
  @NonNull
  @Nonempty
  public String getSecurityProviderName ()
  {
    return m_sSecurityProviderName;
  }

  /**
   * Set the security provider name to use.
   *
   * @param sSecurityProviderName
   *        The provider name. May neither be <code>null</code> nor empty.
   * @return this for chaining
   * @since 4.2.0
   */
  @NonNull
  public BCCryptoHelper setSecurityProviderName (@NonNull @Nonempty final String sSecurityProviderName)
  {
    ValueEnforcer.notEmpty (sSecurityProviderName, "SecurityProviderName");
    m_sSecurityProviderName = sSecurityProviderName;
    return this;
  }

  @NonNull
  public KeyStore createNewKeyStore (@NonNull final IKeyStoreType aKeyStoreType) throws GeneralSecurityException
  {
    try
    {
      // Try with BouncyCastle first (e.g. PKCS12)
      // Important, because JDK PKCS12 is partially case insensitive
      return aKeyStoreType.getKeyStore (m_sSecurityProviderName);
    }
    catch (final Exception ex)
    {
      // Try native (e.g. for JKS)
      return aKeyStoreType.getKeyStore ();
    }
  }

  @NonNull
  public KeyStore loadKeyStore (@NonNull final IKeyStoreType aKeyStoreType,
                                @Nullable @WillNotClose final InputStream aIS,
                                @NonNull final char [] aPassword) throws Exception
  {
    final KeyStore aKeyStore = createNewKeyStore (aKeyStoreType);
    if (aIS != null)
      aKeyStore.load (aIS, aPassword);
    return aKeyStore;
  }

  public boolean isEncrypted (@NonNull final MimeBodyPart aPart) throws MessagingException
  {
    ValueEnforcer.notNull (aPart, "Part");

    // Content-Type is sthg like this if encrypted:
    // application/pkcs7-mime; name=smime.p7m; smime-type=enveloped-data
    final ContentType aContentType = AS2HttpHelper.parseContentType (aPart.getContentType ());
    if (aContentType == null)
      return false;

    final String sBaseType = aContentType.getBaseType ().toLowerCase (Locale.US);
    if (!sBaseType.equals ("application/pkcs7-mime"))
      return false;

    final String sSmimeType = aContentType.getParameter ("smime-type");
    return sSmimeType != null && sSmimeType.equalsIgnoreCase ("enveloped-data");
  }

  public boolean isSigned (@NonNull final MimeBodyPart aPart) throws MessagingException
  {
    ValueEnforcer.notNull (aPart, "Part");

    final ContentType aContentType = AS2HttpHelper.parseContentType (aPart.getContentType ());
    if (aContentType == null)
      return false;

    final String sBaseType = aContentType.getBaseType ();
    return sBaseType.equalsIgnoreCase ("multipart/signed");
  }

  public boolean isCompressed (@NonNull final String sContentType) throws AS2Exception
  {
    ValueEnforcer.notNull (sContentType, "ContentType");

    // Content-Type is sthg like this if compressed:
    // application/pkcs7-mime; smime-type=compressed-data; name=smime.p7z
    final ContentType aContentType = AS2HttpHelper.parseContentType (sContentType);
    if (aContentType == null)
      return false;

    final String sSmimeType = aContentType.getParameter ("smime-type");
    return sSmimeType != null && sSmimeType.equalsIgnoreCase ("compressed-data");
  }

  @NonNull
  public MIC calculateMIC (@NonNull final MimeBodyPart aPart,
                           @NonNull final ECryptoAlgorithmSign eDigestAlgorithm,
                           final boolean bIncludeHeaders) throws GeneralSecurityException,
                                                          MessagingException,
                                                          IOException
  {
    ValueEnforcer.notNull (aPart, "MimeBodyPart");
    ValueEnforcer.notNull (eDigestAlgorithm, "DigestAlgorithm");

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("BCCryptoHelper.calculateMIC (" +
                    eDigestAlgorithm +
                    " [" +
                    eDigestAlgorithm.getOID ().getId () +
                    "], " +
                    bIncludeHeaders +
                    ")");

    final ASN1ObjectIdentifier aMICAlg = eDigestAlgorithm.getOID ();

    MessageDigest aMessageDigest = MessageDigest.getInstance (aMICAlg.getId (), m_sSecurityProviderName);
    if (false)
    {
      // Enable for development debugging only
      aMessageDigest = new LoggingMessageDigest (aMessageDigest);
    }

    if (bIncludeHeaders)
    {
      // Start hashing the header
      final Enumeration <String> aHeaderLines = aPart.getAllHeaderLines ();
      while (aHeaderLines.hasMoreElements ())
      {
        final String sHeaderLine = aHeaderLines.nextElement ();

        aMessageDigest.update (AS2IOHelper.getAllAsciiBytes (sHeaderLine));
        aMessageDigest.update (EOL_BYTES);

        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Using header line '" + sHeaderLine + "' for MIC calculation");
      }

      // The CRLF separator between header and content
      aMessageDigest.update (EOL_BYTES);
    }

    final String sMICEncoding = aPart.getEncoding ();
    if (LOGGER.isDebugEnabled ())
    {
      LOGGER.debug (sMICEncoding != null ? "Using Content-Transfer-Encoding '" + sMICEncoding + "' for MIC calculation"
                                         : "Using no specific Content-Transfer-Encoding for MIC calculation");
    }

    // No need to canonicalize here - see issue #12
    try (final DigestOutputStream aDigestOS = new DigestOutputStream (new NullOutputStream (), aMessageDigest);
         final OutputStream aEncodedOS = AS2IOHelper.getContentTransferEncodingAwareOutputStream (aDigestOS,
                                                                                                  sMICEncoding))
    {
      aPart.getDataHandler ().writeTo (aEncodedOS);
    }

    // Build result digest array
    final byte [] aMIC = aMessageDigest.digest ();

    // Perform Base64 encoding and append algorithm ID
    final MIC ret = new MIC (aMIC, eDigestAlgorithm);

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("  Calculated MIC = " + ret.getAsAS2String ());

    return ret;
  }

  private static void _dumpDecrypted (@NonNull final byte [] aPayload)
  {
    // Ensure a unique filename
    File aDestinationFile;
    int nIndex = 0;
    do
    {
      aDestinationFile = new File (DUMP_DECRYPTED_DIR_PATH,
                                   "as2-decrypted-" +
                                                            Long.toString (PDTFactory.getCurrentMillis ()) +
                                                            "-" +
                                                            nIndex +
                                                            ".part");
      nIndex++;
    } while (aDestinationFile.exists ());

    LOGGER.info ("Dumping decrypted MIME part to file " + aDestinationFile.getAbsolutePath ());
    try (final OutputStream aOS = FileHelper.getOutputStream (aDestinationFile))
    {
      // Add payload
      aOS.write (aPayload);
    }
    catch (final IOException ex)
    {
      LOGGER.error ("Failed to dump decrypted MIME part to file " + aDestinationFile.getAbsolutePath (), ex);
    }
  }

  @NonNull
  public MimeBodyPart decrypt (@NonNull final MimeBodyPart aPart,
                               @NonNull final X509Certificate aX509Cert,
                               @NonNull final PrivateKey aPrivateKey,
                               final boolean bForceDecrypt,
                               @NonNull final AS2ResourceHelper aResHelper) throws GeneralSecurityException,
                                                                            MessagingException,
                                                                            CMSException,
                                                                            SMIMEException,
                                                                            IOException
  {
    ValueEnforcer.notNull (aPart, "MimeBodyPart");
    ValueEnforcer.notNull (aX509Cert, "X509Cert");
    ValueEnforcer.notNull (aPrivateKey, "PrivateKey");
    ValueEnforcer.notNull (aResHelper, "ResHelper");

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("BCCryptoHelper.decrypt; X509 subject=" +
                    aX509Cert.getSubjectX500Principal ().getName () +
                    "; forceDecrypt=" +
                    bForceDecrypt);

    // Make sure the data is encrypted
    if (!bForceDecrypt && !isEncrypted (aPart))
      throw new GeneralSecurityException ("Content-Type '" +
                                          aPart.getContentType () +
                                          "' indicates data isn't encrypted");

    // Get the recipient object for decryption
    final RecipientId aRecipientID = new JceKeyTransRecipientId (aX509Cert);

    // Parse the MIME body into an SMIME envelope object
    RecipientInformation aRecipient = null;
    try
    {
      final SMIMEEnvelopedParser aEnvelope = new SMIMEEnvelopedParser (aPart);
      aRecipient = aEnvelope.getRecipientInfos ().get (aRecipientID);
    }
    catch (final Exception ex)
    {
      LOGGER.error ("Error retrieving RecipientInformation", ex);
    }

    if (aRecipient == null)
      throw new GeneralSecurityException ("Certificate does not match part signature");

    // try to decrypt the data
    // Custom file: see #103
    final FileBackedMimeBodyPart aDecryptedDataBodyPart = SMIMEUtil.toMimeBodyPart (aRecipient.getContentStream (new JceKeyTransEnvelopedRecipient (aPrivateKey).setProvider (m_sSecurityProviderName)),
                                                                                    aResHelper.createTempFile ());

    if (DUMP_DECRYPTED_DIR_PATH != null)
    {
      // dump decrypted
      try (final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream (aDecryptedDataBodyPart.getSize ()))
      {
        aDecryptedDataBodyPart.writeTo (aBAOS);
        _dumpDecrypted (aBAOS.toByteArray ());
      }
    }

    return aDecryptedDataBodyPart;
  }

  @NonNull
  public MimeBodyPart encrypt (@NonNull final MimeBodyPart aPart,
                               @NonNull final X509Certificate aX509Cert,
                               @NonNull final ECryptoAlgorithmCrypt eAlgorithm,
                               @NonNull final EContentTransferEncoding eCTE) throws GeneralSecurityException,
                                                                             SMIMEException,
                                                                             CMSException
  {
    ValueEnforcer.notNull (aPart, "MimeBodyPart");
    ValueEnforcer.notNull (aX509Cert, "X509Cert");
    ValueEnforcer.notNull (eAlgorithm, "Algorithm");
    ValueEnforcer.notNull (eCTE, "ContentTransferEncoding");

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("BCCryptoHelper.encrypt; X509 subject=" +
                    aX509Cert.getSubjectX500Principal ().getName () +
                    "; algorithm=" +
                    eAlgorithm +
                    " [" +
                    eAlgorithm.getOID ().getId () +
                    "], CTE=" +
                    eCTE);

    // Check if the certificate is expired or active.
    aX509Cert.checkValidity ();

    final ASN1ObjectIdentifier aEncAlg = eAlgorithm.getOID ();

    final SMIMEEnvelopedGenerator aGen = new SMIMEEnvelopedGenerator ();
    aGen.addRecipientInfoGenerator (new JceKeyTransRecipientInfoGenerator (aX509Cert).setProvider (m_sSecurityProviderName));
    aGen.setContentTransferEncoding (eCTE.getID ());

    final OutputEncryptor aEncryptor = new JceCMSContentEncryptorBuilder (aEncAlg).setProvider (m_sSecurityProviderName)
                                                                                  .build ();

    // Return the encrypted Mime Body Part
    return aGen.generate (aPart, aEncryptor);
  }

  @CodingStyleguideUnaware
  private static final Map <ASN1ObjectIdentifier, String> NEW_SIGN_ALGOS;

  static
  {
    @SuppressWarnings ("unchecked")
    // Start with existing RFC-5751 algorithms
    final ICommonsMap <ASN1ObjectIdentifier, String> stdMicAlgs = new CommonsLinkedHashMap <> (SMIMESignedGenerator.RFC5751_MICALGS);

    // add all remaining algorithms
    for (final ECryptoAlgorithmSign e : ECryptoAlgorithmSign.values ())
      if (!e.isRFC3851Algorithm ())
      {
        // It's not an old value
        stdMicAlgs.computeIfAbsent (e.getOID (), k -> e.getMICAlgorithmID ());
      }
    NEW_SIGN_ALGOS = Collections.unmodifiableMap (stdMicAlgs);
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("The current algorithms for RFC 5751 mode are " + NEW_SIGN_ALGOS);
  }

  @NonNull
  public MimeBodyPart sign (@NonNull final MimeBodyPart aPart,
                            @NonNull final X509Certificate aX509Cert,
                            @NonNull final PrivateKey aPrivateKey,
                            @NonNull final ECryptoAlgorithmSign eAlgorithm,
                            final boolean bIncludeCertificateInSignedContent,
                            final boolean bUseOldRFC3851MicAlgs,
                            final boolean bRemoveCmsAlgorithmProtect,
                            @NonNull final EContentTransferEncoding eCTE) throws GeneralSecurityException,
                                                                          SMIMEException,
                                                                          MessagingException,
                                                                          OperatorCreationException
  {
    ValueEnforcer.notNull (aPart, "MimeBodyPart");
    ValueEnforcer.notNull (aX509Cert, "X509Cert");
    ValueEnforcer.notNull (aPrivateKey, "PrivateKey");
    ValueEnforcer.notNull (eAlgorithm, "Algorithm");
    ValueEnforcer.notNull (eCTE, "ContentTransferEncoding");

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("BCCryptoHelper.sign; X509 subject=" +
                    aX509Cert.getSubjectX500Principal ().getName () +
                    "; algorithm=" +
                    eAlgorithm +
                    "; includeCertificateInSignedContent=" +
                    bIncludeCertificateInSignedContent +
                    "; CTE=" +
                    eCTE);

    // Check if the certificate is expired or active.
    aX509Cert.checkValidity ();

    // create a CertStore containing the certificates we want carried
    // in the signature
    final ICommonsList <X509Certificate> aCertList = new CommonsArrayList <> (aX509Cert);
    final JcaCertStore aCertStore = new JcaCertStore (aCertList);

    // create some smime capabilities in case someone wants to respond
    final ASN1EncodableVector aSignedAttrs = new ASN1EncodableVector ();
    final SMIMECapabilityVector aCapabilities = new SMIMECapabilityVector ();
    aCapabilities.addCapability (eAlgorithm.getOID ());
    aSignedAttrs.add (new SMIMECapabilitiesAttribute (aCapabilities));

    // add an encryption key preference for encrypted responses -
    // normally this would be different from the signing certificate...
    // final IssuerAndSerialNumber issAndSer = new IssuerAndSerialNumber (new
    // X500Name (signDN),
    // aX509Cert.getSerialNumber ());
    // aSignedAttrs.add (new SMIMEEncryptionKeyPreferenceAttribute (issAndSer));

    // create the generator for creating an smime/signed message
    final SMIMESignedGenerator aSGen = new SMIMESignedGenerator (bUseOldRFC3851MicAlgs ? SMIMESignedGenerator.RFC3851_MICALGS
                                                                                       : NEW_SIGN_ALGOS);

    // set the content-transfer-encoding for the CMS block (enveloped data,
    // signature, etc...) in the message.
    aSGen.setContentTransferEncoding (eCTE.getID ());

    // aSGen.addSigner (aPrivKey, aX509Cert, aSignDigest.getId ());

    // add a signer to the generator - this specifies we are using SHA1 and
    // adding the smime attributes above to the signed attributes that
    // will be generated as part of the signature. The encryption algorithm
    // used is taken from the key
    {
      SignerInfoGenerator aSigInfoGen = new JcaSimpleSignerInfoGeneratorBuilder ().setProvider (m_sSecurityProviderName)
                                                                                  .setSignedAttributeGenerator (new AttributeTable (aSignedAttrs))
                                                                                  .build (eAlgorithm.getSignAlgorithmName (),
                                                                                          aPrivateKey,
                                                                                          aX509Cert);
      if (bRemoveCmsAlgorithmProtect)
      {
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("Removing CMS AlgorithmProtect attribute, if it is present");

        final CMSAttributeTableGenerator aAttrGen = aSigInfoGen.getSignedAttributeTableGenerator ();
        aSigInfoGen = new SignerInfoGenerator (aSigInfoGen, new DefaultSignedAttributeTableGenerator ()
        {
          @Override
          public AttributeTable getAttributes (final Map parameters)
          {
            final AttributeTable ret = aAttrGen.getAttributes (parameters);
            return ret.remove (CMSAttributes.cmsAlgorithmProtect);
          }
        }, aSigInfoGen.getUnsignedAttributeTableGenerator ());
      }
      aSGen.addSignerInfoGenerator (aSigInfoGen);
    }

    if (bIncludeCertificateInSignedContent)
    {
      // add our pool of certs and crls (if any) to go with the signature
      aSGen.addCertificates (aCertStore);
    }

    // This does the main signing.
    // The next call also might modify the source part (!) by adding
    // "Content-Type" and "Content-Transfer-Encoding" headers if they would be
    // missing
    final MimeMultipart aSignedData = aSGen.generate (aPart);

    final MimeBodyPart aSignedPart = new MimeBodyPart ();
    aSignedPart.setContent (aSignedData);
    aSignedPart.setHeader (CHttpHeader.CONTENT_TYPE, aSignedData.getContentType ());
    return aSignedPart;
  }

  @NonNull
  private X509Certificate _verifyFindCertificate (@Nullable final X509Certificate aX509Cert,
                                                  final boolean bUseCertificateInBodyPart,
                                                  @NonNull final SMIMESignedParser aSignedParser) throws CMSException,
                                                                                                  GeneralSecurityException
  {
    X509Certificate aRealX509Cert = aX509Cert;
    if (bUseCertificateInBodyPart)
    {
      // get signing certificates contained in the body part
      SignerId aSignerID = null;
      final Collection <SignerInformation> aSigners = aSignedParser.getSignerInfos ().getSigners ();
      if (!aSigners.isEmpty ())
      {
        // Use the first signer
        if (aSigners.size () > 1)
          LOGGER.warn ("Signed part contains " + aSigners.size () + " signers - using the first one!");

        final SignerInformation aSigner = aSigners.iterator ().next ();
        aSignerID = aSigner.getSID ();
      }

      // Java 11 complains here, because getCertificates() has no generics
      // parameter
      // Null signer ID returns empty ArrayList
      final Collection <?> aContainedCerts = aSignedParser.getCertificates ().getMatches (aSignerID);
      if (!aContainedCerts.isEmpty ())
      {
        // E.g. for Peppol the certificate is passed in
        if (aContainedCerts.size () > 1)
          LOGGER.warn ("Signed part contains " + aContainedCerts.size () + " certificates - using the first one!");

        final X509CertificateHolder aCertHolder = (X509CertificateHolder) CollectionFind.getFirstElement (aContainedCerts);
        final X509Certificate aCert = new JcaX509CertificateConverter ().setProvider (m_sSecurityProviderName)
                                                                        .getCertificate (aCertHolder);
        if (aX509Cert != null && !aX509Cert.equals (aCert))
          LOGGER.warn ("Certificate mismatch! Provided certificate\n" +
                       aX509Cert +
                       "\ndiffers from certficate contained in message\n" +
                       aCert);

        // The certificate provided in the message takes precedence
        aRealX509Cert = aCert;
      }
    }
    if (aRealX509Cert == null)
      throw new GeneralSecurityException ("No certificate provided" +
                                          (bUseCertificateInBodyPart ? " and none found in the message" : "") +
                                          "!");
    return aRealX509Cert;
  }

  @NonNull
  public MimeBodyPart verify (@NonNull final MimeBodyPart aPart,
                              @Nullable final X509Certificate aX509Cert,
                              final boolean bUseCertificateInBodyPart,
                              final boolean bForceVerifySigned,
                              @Nullable final Consumer <X509Certificate> aEffectiveCertificateConsumer,
                              @NonNull final AS2ResourceHelper aResHelper) throws GeneralSecurityException,
                                                                           IOException,
                                                                           MessagingException,
                                                                           CMSException,
                                                                           OperatorCreationException
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("BCCryptoHelper.verify; X509 subject=" +
                    (aX509Cert == null ? "null" : aX509Cert.getSubjectX500Principal ().getName ()) +
                    "; useCertificateInBodyPart=" +
                    bUseCertificateInBodyPart +
                    "; forceVerify=" +
                    bForceVerifySigned);

    // Make sure the data is signed
    if (!bForceVerifySigned && !isSigned (aPart))
      throw new GeneralSecurityException ("Content-Type indicates data isn't signed: " + aPart.getContentType ());

    // Get only once and check
    // Throws "ParseException" if it is not a MIME message
    final Object aContent = aPart.getContent ();
    if (!(aContent instanceof final MimeMultipart aMainPart))
      throw new IllegalStateException ("Expected Part content to be MimeMultipart but it isn't. It is " +
                                       ClassHelper.getClassName (aContent));
    // SMIMESignedParser uses "7bit" as the default - AS2 wants "binary"
    final SMIMESignedParser aSignedParser = new SMIMESignedParser (new JcaDigestCalculatorProviderBuilder ().setProvider (m_sSecurityProviderName)
                                                                                                            .build (),
                                                                   aMainPart,
                                                                   EContentTransferEncoding.AS2_DEFAULT.getID (),
                                                                   aResHelper.createTempFile ());

    // If no certificate is found, an exception is thrown
    final X509Certificate aRealX509Cert = _verifyFindCertificate (aX509Cert, bUseCertificateInBodyPart, aSignedParser);

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug (EqualsHelper.identityEqual (aRealX509Cert, aX509Cert)
                                                                          ? "Verifying signature using the provided certificate (partnership)"
                                                                          : "Verifying signature using the certificate contained in the MIME body part");

    // Call before validity check to retrieve the information about the
    // details outside
    if (aEffectiveCertificateConsumer != null)
      aEffectiveCertificateConsumer.accept (aRealX509Cert);

    // Check if the certificate is expired or active.
    aRealX509Cert.checkValidity ();

    // Verify certificate
    final SignerInformationVerifier aSIV = new JcaSimpleSignerInfoVerifierBuilder ().setProvider (m_sSecurityProviderName)
                                                                                    .build (aRealX509Cert.getPublicKey ());

    for (final SignerInformation aSignerInfo : aSignedParser.getSignerInfos ().getSigners ())
    {
      if (!aSignerInfo.verify (aSIV))
        throw new SignatureException ("Verification failed for SignerInfo " + aSignerInfo);
    }

    return aSignedParser.getContent ();
  }
}
