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
package com.helger.phase2.cert;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonempty;
import com.helger.annotation.WillClose;
import com.helger.annotation.concurrent.GuardedBy;
import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.OverrideOnDemand;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.exception.InitializationException;
import com.helger.base.io.stream.StreamHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.phase2.AbstractDynamicComponent;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.WrappedAS2Exception;
import com.helger.phase2.message.IBaseMessage;
import com.helger.phase2.partner.Partnership;
import com.helger.phase2.session.IAS2Session;
import com.helger.phase2.util.AS2Helper;
import com.helger.security.keystore.EKeyStoreType;
import com.helger.security.keystore.IKeyStoreType;
import com.helger.typeconvert.collection.IStringMap;

/**
 * Abstract base implementation of a read-only Certificate factory that operates on a
 * {@link KeyStore} object. The only method to be implemented is {@link #reinitKeyStore()} which is
 * responsible for setting the keystore. The protected method <code>setKeyStore(KeyStore)</code> may
 * be used to work around the default behaviour and provide an arbitrary implementation.
 *
 * @author Philip Helger
 * @since 4.6.4
 */
@ThreadSafe
public abstract class AbstractCertificateFactory extends AbstractDynamicComponent implements
                                                 IKeyStoreCertificateFactory,
                                                 IAliasedCertificateFactory
{
  public static final EKeyStoreType DEFAULT_KEY_STORE_TYPE = EKeyStoreType.PKCS12;

  /** Key store type; since 4.0.0 */
  public static final String ATTR_TYPE = "type";
  public static final String ATTR_PASSWORD = "password";

  private static final Logger LOGGER = LoggerFactory.getLogger (AbstractCertificateFactory.class);

  @GuardedBy ("m_aRWLock")
  private KeyStore m_aKeyStore;
  @GuardedBy ("m_aRWLock")
  private boolean m_bDebugLog = false;

  public AbstractCertificateFactory ()
  {}

  public final boolean isDebugLogEnabled ()
  {
    return m_aRWLock.readLockedBoolean ( () -> m_bDebugLog);
  }

  public final void setDebugLogEnaled (final boolean bDebugLog)
  {
    m_aRWLock.writeLockedBoolean ( () -> m_bDebugLog = bDebugLog);
  }

  protected final void debugLog (@NonNull final Supplier <String> aSupplier)
  {
    if (isDebugLogEnabled ())
      LOGGER.info (aSupplier.get ());
  }

  @Nullable
  public final String getKeyStoreType ()
  {
    debugLog ( () -> "getKeyStoreType ()");
    final String ret = m_aRWLock.readLockedGet ( () -> attrs ().getAsString (ATTR_TYPE));
    debugLog ( () -> "getKeyStoreType -> " + ret);
    return ret;
  }

  public final void setKeyStoreType (@Nullable final IKeyStoreType aKeyStoreType)
  {
    setKeyStoreType (aKeyStoreType == null ? null : aKeyStoreType.getID ());
  }

  public final void setKeyStoreType (@Nullable final String sKeyStoreType)
  {
    debugLog ( () -> "setKeyStoreType (" + sKeyStoreType + ")");

    m_aRWLock.writeLocked ( () -> {
      if (sKeyStoreType == null)
        attrs ().remove (ATTR_TYPE);
      else
        attrs ().putIn (ATTR_TYPE, sKeyStoreType);
    });
  }

  public void setPassword (@Nullable final String sPassword)
  {
    debugLog ( () -> "setPassword (***)");
    m_aRWLock.writeLockedGet ( () -> attrs ().putIn (ATTR_PASSWORD, sPassword));
  }

  @Nullable
  public char [] getPassword ()
  {
    debugLog ( () -> "getPassword ()");
    final char [] ret = m_aRWLock.readLockedGet ( () -> attrs ().getAsCharArray (ATTR_PASSWORD));
    debugLog ( () -> "getPassword -> ***");
    return ret;
  }

  @NonNull
  @Nonempty
  private static String _debug (@Nullable final X509Certificate aCert)
  {
    return aCert == null ? "null" : aCert.getSubjectX500Principal ().getName () +
                                    "/" +
                                    aCert.getSerialNumber ().toString ();
  }

  @NonNull
  @Nonempty
  private static String _debug (@NonNull final Exception ex)
  {
    return ex.getClass ().getName () + " - " + ex.getMessage ();
  }

  @NonNull
  @OverrideOnDemand
  protected KeyStore createNewKeyStore (@NonNull final EKeyStoreType eKeyStoreType) throws GeneralSecurityException
  {
    ValueEnforcer.notNull (eKeyStoreType, "KeystoreType");

    debugLog ( () -> "createNewKeyStore (" + eKeyStoreType + ")");

    return AS2Helper.getCryptoHelper ().createNewKeyStore (eKeyStoreType);
  }

  @Override
  public void initDynamicComponent (@NonNull final IAS2Session aSession, @Nullable final IStringMap aOptions)
                                                                                                              throws AS2Exception
  {
    debugLog ( () -> "initDynamicComponent (" + aSession + ", " + aOptions + ")");

    super.initDynamicComponent (aSession, aOptions);

    reinitKeyStore ();

    debugLog ( () -> "initDynamicComponent -> done");
  }

  @NonNull
  public KeyStore getKeyStore ()
  {
    final KeyStore ret = m_aRWLock.readLockedGet ( () -> m_aKeyStore);
    if (ret == null)
      throw new IllegalStateException ("No keystore present");
    return ret;
  }

  /**
   * Internal method to set the {@link KeyStore} used internally.
   *
   * @param aKeyStore
   *        The key store to use. May not be <code>null</code>.
   */
  protected final void setKeyStore (@NonNull final KeyStore aKeyStore)
  {
    ValueEnforcer.notNull (aKeyStore, "KeyStore");

    debugLog ( () -> "setKeyStore (" + aKeyStore + ")");
    m_aRWLock.writeLockedGet ( () -> m_aKeyStore = aKeyStore);
    debugLog ( () -> "setKeyStore -> done");
  }

  /**
   * This method is responsible to create a new empty keystore based on the configured type.
   *
   * @throws AS2Exception
   *         In case of error
   * @see #getKeyStoreType()
   * @see #createNewKeyStore(EKeyStoreType)
   * @see #setKeyStore(KeyStore)
   */
  protected void initEmptyKeyStore () throws AS2Exception
  {
    try
    {
      final String sKeyStoreType = getKeyStoreType ();
      final EKeyStoreType eKeyStoreType = EKeyStoreType.getFromIDCaseInsensitiveOrDefault (sKeyStoreType,
                                                                                           DEFAULT_KEY_STORE_TYPE);

      LOGGER.info ("Using internal keystore of type " + eKeyStoreType);

      final KeyStore aKeyStore = createNewKeyStore (eKeyStoreType);
      if (aKeyStore == null)
      {
        debugLog ( () -> "initDynamicComponent -> no keystore");
        throw new InitializationException ("Failed to create new keystore with type " + eKeyStoreType);
      }
      setKeyStore (aKeyStore);
    }
    catch (final GeneralSecurityException ex)
    {
      debugLog ( () -> "initDynamicComponent -> " + _debug (ex));
      throw WrappedAS2Exception.wrap (ex);
    }
  }

  /**
   * Overridable method to perform unifications on aliases, e.g. for lower casing when using Oracle
   * JDKs PKCS12 implementation.
   *
   * @param sAlias
   *        Source alias. May be <code>null</code>.
   * @return <code>null</code> if the source was <code>null</code>.
   * @since 4.0.2
   */
  @Nullable
  @OverrideOnDemand
  protected String getUnifiedAlias (@Nullable final String sAlias)
  {
    return sAlias;
  }

  @NonNull
  public String getAlias (@NonNull final Partnership aPartnership,
                          @NonNull final ECertificatePartnershipType ePartnershipType) throws AS2Exception
  {
    ValueEnforcer.notNull (aPartnership, "Partnership");
    ValueEnforcer.notNull (ePartnershipType, "PartnershipType");

    debugLog ( () -> "getAlias (" + aPartnership + ", " + ePartnershipType + ")");

    final String sAlias;
    switch (ePartnershipType)
    {
      case RECEIVER:
        sAlias = aPartnership.getReceiverX509Alias ();
        break;
      case SENDER:
        sAlias = aPartnership.getSenderX509Alias ();
        break;
      default:
        sAlias = null;
    }
    if (sAlias == null)
    {
      debugLog ( () -> "getAlias -> null");
      throw new AS2CertificateNotFoundException (ePartnershipType, aPartnership);
    }

    final String ret = getUnifiedAlias (sAlias);
    debugLog ( () -> "getAlias -> " + ret);
    return ret;
  }

  @NonNull
  protected X509Certificate internalGetCertificate (@Nullable final String sAlias,
                                                    @Nullable final ECertificatePartnershipType ePartnershipType) throws AS2Exception
  {
    debugLog ( () -> "internalGetCertificate (" + sAlias + ", " + ePartnershipType + ")");

    final String sRealAlias = getUnifiedAlias (sAlias);

    m_aRWLock.readLock ().lock ();
    try
    {
      final X509Certificate aCert = (X509Certificate) m_aKeyStore.getCertificate (sRealAlias);
      if (aCert == null)
        throw new AS2CertificateNotFoundException (ePartnershipType, sRealAlias);
      debugLog ( () -> "internalGetCertificate -> " + _debug (aCert));
      return aCert;
    }
    catch (final KeyStoreException ex)
    {
      debugLog ( () -> "internalGetCertificate -> " + _debug (ex));
      throw WrappedAS2Exception.wrap (ex);
    }
    finally
    {
      m_aRWLock.readLock ().unlock ();
    }
  }

  @NonNull
  public X509Certificate getCertificate (@Nullable final String sAlias) throws AS2Exception
  {
    debugLog ( () -> "getCertificate (" + sAlias + ")");
    final X509Certificate ret = internalGetCertificate (sAlias, null);
    debugLog ( () -> "getCertificate -> " + _debug (ret));
    return ret;
  }

  @NonNull
  public X509Certificate getCertificate (@NonNull final IBaseMessage aMsg,
                                         @NonNull final ECertificatePartnershipType ePartnershipType) throws AS2Exception
  {
    debugLog ( () -> "getCertificate (" + aMsg.getMessageID () + ", " + ePartnershipType + ")");

    final String sAlias = getAlias (aMsg.partnership (), ePartnershipType);
    final X509Certificate ret = internalGetCertificate (sAlias, ePartnershipType);
    debugLog ( () -> "getCertificate -> " + _debug (ret));
    return ret;
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsOrderedMap <String, X509Certificate> getCertificates () throws AS2Exception
  {
    debugLog ( () -> "getCertificates ()");

    final ICommonsOrderedMap <String, X509Certificate> ret = new CommonsLinkedHashMap <> ();
    m_aRWLock.readLock ().lock ();
    try
    {
      final Enumeration <String> aAliases = m_aKeyStore.aliases ();
      while (aAliases.hasMoreElements ())
      {
        final String sAlias = aAliases.nextElement ();
        ret.put (sAlias, (X509Certificate) m_aKeyStore.getCertificate (sAlias));
      }
    }
    catch (final GeneralSecurityException ex)
    {
      debugLog ( () -> "getCertificates -> " + _debug (ex));
      throw WrappedAS2Exception.wrap (ex);
    }
    finally
    {
      m_aRWLock.readLock ().unlock ();
    }
    debugLog ( () -> "getCertificates -> " +
                     new CommonsLinkedHashMap <> (ret, x -> x, AbstractCertificateFactory::_debug).toString ());
    return ret;
  }

  /**
   * Custom callback method that is invoked if something changes in the key store. By default the
   * changes are written back to disk.
   *
   * @throws AS2Exception
   *         In case saving fails.
   */
  @OverrideOnDemand
  protected void onChange () throws AS2Exception
  {}

  @NonNull
  private ICommonsList <String> _getAllAliases ()
  {
    debugLog ( () -> "_getAllAliases ()");

    // Get all aliases
    final ICommonsList <String> ret = new CommonsArrayList <> ();
    m_aRWLock.readLock ().lock ();
    try
    {
      ret.addAll (m_aKeyStore.aliases ());
    }
    catch (final KeyStoreException ex)
    {
      LOGGER.warn ("Failed to determine all aliases from keystore", ex);
    }
    finally
    {
      m_aRWLock.readLock ().unlock ();
    }
    debugLog ( () -> "_getAllAliases -> " + ret);
    return ret;
  }

  @NonNull
  public PrivateKey getPrivateKey (@Nullable final X509Certificate aCert) throws AS2Exception
  {
    debugLog ( () -> "getPrivateKey (" + _debug (aCert) + ")");

    if (aCert == null)
      throw new AS2CertificateNotFoundException (aCert);

    final ICommonsList <String> aAllAliases = _getAllAliases ();
    String sRealAlias = null;

    m_aRWLock.readLock ().lock ();
    try
    {
      // Scan all aliases, in case the same alias is used for Key AND
      // Certificate
      PrivateKey aKey = null;
      for (final String sCurAlias : aAllAliases)
      {
        // Does the certificate resolved from the current alias match the
        // requested one?
        if (m_aKeyStore.getCertificate (sCurAlias).equals (aCert))
        {
          sRealAlias = getUnifiedAlias (sCurAlias);

          // Check if a key entry is present as well
          aKey = (PrivateKey) m_aKeyStore.getKey (sRealAlias, getPassword ());
          if (aKey != null)
            break;
        }
      }

      if (aKey == null)
      {
        debugLog ( () -> "getPrivateKey -> null");
        throw new AS2KeyNotFoundException (aCert, sRealAlias, aAllAliases, null);
      }

      final PrivateKey aFinalKey = aKey;
      debugLog ( () -> "getPrivateKey -> " + aFinalKey);
      return aKey;
    }
    catch (final GeneralSecurityException ex)
    {
      debugLog ( () -> "getPrivateKey -> " + _debug (ex));
      throw new AS2KeyNotFoundException (aCert, sRealAlias, aAllAliases, ex);
    }
    finally
    {
      m_aRWLock.readLock ().unlock ();
    }
  }

  public void addCertificate (@NonNull @Nonempty final String sAlias,
                              @NonNull final X509Certificate aCert,
                              final boolean bOverwrite) throws AS2Exception
  {
    ValueEnforcer.notEmpty (sAlias, "Alias");
    ValueEnforcer.notNull (aCert, "Cert");

    debugLog ( () -> "addCertificate (" + sAlias + ", " + _debug (aCert) + ", " + bOverwrite + ")");

    final String sRealAlias = getUnifiedAlias (sAlias);

    m_aRWLock.writeLock ().lock ();
    try
    {
      if (m_aKeyStore.containsAlias (sRealAlias) && !bOverwrite)
        throw new AS2CertificateExistsException (sRealAlias);

      m_aKeyStore.setCertificateEntry (sRealAlias, aCert);
    }
    catch (final GeneralSecurityException ex)
    {
      debugLog ( () -> "addCertificate -> " + _debug (ex));
      throw WrappedAS2Exception.wrap (ex);
    }
    finally
    {
      m_aRWLock.writeLock ().unlock ();
    }

    onChange ();

    LOGGER.info ("Added certificate alias '" + sRealAlias + "' of certificate '" + _debug (aCert) + "'");
    debugLog ( () -> "addCertificate -> done");
  }

  public void addPrivateKey (@NonNull @Nonempty final String sAlias,
                             @NonNull final Key aKey,
                             @NonNull final String sPassword) throws AS2Exception
  {
    ValueEnforcer.notEmpty (sAlias, "Alias");
    ValueEnforcer.notNull (aKey, "Key");
    ValueEnforcer.notNull (sPassword, "Password");

    debugLog ( () -> "addPrivateKey (" + sAlias + ", " + aKey + ", ***)");

    final String sRealAlias = getUnifiedAlias (sAlias);

    m_aRWLock.writeLock ().lock ();
    try
    {
      if (!m_aKeyStore.containsAlias (sRealAlias))
        throw new AS2CertificateNotFoundException (null, sRealAlias);

      final Certificate [] aCertChain = m_aKeyStore.getCertificateChain (sRealAlias);
      m_aKeyStore.setKeyEntry (sRealAlias, aKey, sPassword.toCharArray (), aCertChain);
    }
    catch (final GeneralSecurityException ex)
    {
      debugLog ( () -> "addPrivateKey -> " + _debug (ex));
      throw WrappedAS2Exception.wrap (ex);
    }
    finally
    {
      m_aRWLock.writeLock ().unlock ();
    }

    onChange ();

    LOGGER.info ("Added private key alias '" + sRealAlias + "'");
    debugLog ( () -> "addPrivateKey -> done");
  }

  public void clearCertificates () throws AS2Exception
  {
    debugLog ( () -> "clearCertificates ()");

    int nDeleted = 0;

    m_aRWLock.writeLock ().lock ();
    try
    {
      // Make a copy to be sure
      for (final String sAlias : new CommonsArrayList <> (m_aKeyStore.aliases ()))
      {
        m_aKeyStore.deleteEntry (sAlias);
        nDeleted++;
      }
    }
    catch (final GeneralSecurityException ex)
    {
      debugLog ( () -> "clearCertificates -> " + _debug (ex));
      throw WrappedAS2Exception.wrap (ex);
    }
    finally
    {
      m_aRWLock.writeLock ().unlock ();
    }

    if (nDeleted > 0)
    {
      // Only if something changed
      onChange ();

      LOGGER.info ("Remove all aliases (" + nDeleted + ") in key store");
    }

    final int nFinalDeleted = nDeleted;
    debugLog ( () -> "clearCertificates -> removed " + nFinalDeleted);
  }

  public void removeCertificate (@NonNull final X509Certificate aCert) throws AS2Exception
  {
    ValueEnforcer.notNull (aCert, "Cert");

    debugLog ( () -> "removeCertificate (" + _debug (aCert) + ")");

    final String sAlias;

    m_aRWLock.readLock ().lock ();
    try
    {
      sAlias = m_aKeyStore.getCertificateAlias (aCert);
      if (sAlias == null)
        throw new AS2CertificateNotFoundException (aCert);
    }
    catch (final GeneralSecurityException ex)
    {
      debugLog ( () -> "removeCertificate -> " + _debug (ex));
      throw WrappedAS2Exception.wrap (ex);
    }
    finally
    {
      m_aRWLock.readLock ().unlock ();
    }

    removeCertificate (sAlias);
    debugLog ( () -> "removeCertificate -> done");
  }

  public void removeCertificate (@Nullable final String sAlias) throws AS2Exception
  {
    debugLog ( () -> "removeCertificate (" + sAlias + ")");

    final String sRealAlias = getUnifiedAlias (sAlias);

    final X509Certificate aCert;
    m_aRWLock.writeLock ().lock ();
    try
    {
      aCert = (X509Certificate) m_aKeyStore.getCertificate (sRealAlias);
      if (aCert == null)
        throw new AS2CertificateNotFoundException (null, sRealAlias);

      m_aKeyStore.deleteEntry (sRealAlias);
    }
    catch (final GeneralSecurityException ex)
    {
      debugLog ( () -> "removeCertificate -> " + _debug (ex));
      throw WrappedAS2Exception.wrap (ex);
    }
    finally
    {
      m_aRWLock.writeLock ().unlock ();
    }

    onChange ();

    LOGGER.info ("Removed certificate alias '" + sRealAlias + "' of certificate " + _debug (aCert));
    debugLog ( () -> "removeCertificate -> done");
  }

  public void load (@NonNull @WillClose final InputStream aIS, @NonNull final char [] aPassword) throws AS2Exception
  {
    debugLog ( () -> "load (" + aIS + ", ***)");

    m_aRWLock.writeLock ().lock ();
    try
    {
      try
      {
        m_aKeyStore.load (aIS, aPassword);
      }
      catch (final IOException | GeneralSecurityException ex)
      {
        debugLog ( () -> "load -> " + _debug (ex));
        throw WrappedAS2Exception.wrap (ex);
      }
      finally
      {
        StreamHelper.close (aIS);
      }
    }
    finally
    {
      m_aRWLock.writeLock ().unlock ();
    }

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Finished loading keystore from an InputStream");
    debugLog ( () -> "load -> done");
  }

  public void save (@NonNull @WillClose final OutputStream aOS, @NonNull final char [] aPassword) throws AS2Exception
  {
    debugLog ( () -> "save (" + aOS + ", ***)");

    m_aRWLock.writeLock ().lock ();
    try
    {
      try
      {
        m_aKeyStore.store (aOS, aPassword);
      }
      catch (final IOException | GeneralSecurityException ex)
      {
        debugLog ( () -> "save -> " + _debug (ex));
        throw WrappedAS2Exception.wrap (ex);
      }
      finally
      {
        StreamHelper.close (aOS);
      }
    }
    finally
    {
      m_aRWLock.writeLock ().unlock ();
    }

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Finished saving keystore to an OutputStream");
    debugLog ( () -> "save -> done");
  }

  @Override
  public boolean equals (final Object o)
  {
    // New members, but no change in implementation
    return super.equals (o);
  }

  @Override
  public int hashCode ()
  {
    // New members, but no change in implementation
    return super.hashCode ();
  }
}
