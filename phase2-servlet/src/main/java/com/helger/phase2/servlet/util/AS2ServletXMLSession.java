/*
 * Copyright (C) 2015-2025 Philip Helger (www.helger.com)
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
package com.helger.phase2.servlet.util;

import java.io.File;
import java.io.InputStream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.WillClose;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.io.file.FileHelper;
import com.helger.phase2.cert.ICertificateFactory;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.partner.IPartnershipFactory;
import com.helger.phase2.processor.IMessageProcessor;
import com.helger.phase2.processor.module.IProcessorModule;
import com.helger.phase2.session.AS2Session;
import com.helger.phase2.util.AS2XMLHelper;
import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.IMicroElement;
import com.helger.xml.microdom.serialize.MicroReader;

/**
 * A special {@link AS2Session} that loads its configuration from a file.
 *
 * @author Philip Helger
 */
public class AS2ServletXMLSession extends AS2Session
{
  public static final String EL_CERTIFICATES = "certificates";
  public static final String EL_PROCESSOR = "processor";
  public static final String EL_PARTNERSHIPS = "partnerships";

  private static final Logger LOGGER = LoggerFactory.getLogger (AS2ServletXMLSession.class);

  private final File m_aConfigFile;
  private final String m_sBaseDirectory;

  public AS2ServletXMLSession (@NonNull final File aFile) throws AS2Exception
  {
    ValueEnforcer.notNull (aFile, "File");
    if (!aFile.exists ())
      throw new AS2Exception ("AS2ServletXMLSession configuration file '" +
                              aFile.getAbsolutePath () +
                              "' does not exist!");
    m_aConfigFile = aFile;
    m_sBaseDirectory = aFile.getParentFile ().getAbsolutePath ();
    LOGGER.info ("Loading AS2 configuration file '" + aFile.getAbsolutePath ());
    _load (FileHelper.getInputStream (aFile));
  }

  @NonNull
  public final File getConfigFile ()
  {
    return m_aConfigFile;
  }

  @NonNull
  public final String getBaseDirectory ()
  {
    return m_sBaseDirectory;
  }

  private void _loadCertificateFactory (@NonNull final IMicroElement aElement) throws AS2Exception
  {
    LOGGER.info ("Loading certificates");
    final ICertificateFactory aFactory = AS2XMLHelper.createComponent (aElement,
                                                                       ICertificateFactory.class,
                                                                       this,
                                                                       m_sBaseDirectory);
    setCertificateFactory (aFactory);
  }

  private void _loadPartnershipFactory (final IMicroElement eRootNode) throws AS2Exception
  {
    LOGGER.info ("Loading partnerships");
    final IPartnershipFactory aFactory = AS2XMLHelper.createComponent (eRootNode,
                                                                       IPartnershipFactory.class,
                                                                       this,
                                                                       m_sBaseDirectory);
    setPartnershipFactory (aFactory);
  }

  private void _loadProcessorModule (@NonNull final IMessageProcessor aMsgProcessor,
                                     @NonNull final IMicroElement eModule) throws AS2Exception
  {
    final IProcessorModule aProcessorModule = AS2XMLHelper.createComponent (eModule,
                                                                            IProcessorModule.class,
                                                                            this,
                                                                            m_sBaseDirectory);
    aMsgProcessor.addModule (aProcessorModule);
    LOGGER.info ("  Loaded processor module " + aProcessorModule.getName ());
  }

  private void _loadMessageProcessor (final IMicroElement eRootNode) throws AS2Exception
  {
    LOGGER.info ("Loading message processor");
    final IMessageProcessor aMsgProcessor = AS2XMLHelper.createComponent (eRootNode,
                                                                          IMessageProcessor.class,
                                                                          this,
                                                                          m_sBaseDirectory);
    setMessageProcessor (aMsgProcessor);

    for (final IMicroElement eModule : eRootNode.getAllChildElements ("module"))
      _loadProcessorModule (aMsgProcessor, eModule);
  }

  private void _load (@Nullable @WillClose final InputStream aIS) throws AS2Exception
  {
    if (aIS != null)
    {
      final IMicroDocument aDoc = MicroReader.readMicroXML (aIS);
      final IMicroElement eRoot = aDoc.getDocumentElement ();

      for (final IMicroElement eRootChild : eRoot.getAllChildElements ())
      {
        final String sNodeName = eRootChild.getTagName ();

        if (sNodeName.equals (EL_CERTIFICATES))
          _loadCertificateFactory (eRootChild);
        else
          if (sNodeName.equals (EL_PROCESSOR))
            _loadMessageProcessor (eRootChild);
          else
            if (sNodeName.equals (EL_PARTNERSHIPS))
              _loadPartnershipFactory (eRootChild);
            else
              throw new AS2Exception ("Undefined tag: " + sNodeName);
      }
    }
  }

  public void reloadConfiguration () throws AS2Exception
  {
    LOGGER.info ("Loading AS2 configuration file '" + m_aConfigFile.getAbsolutePath ());

    // Clear old stuff
    resetToDefault ();

    _load (FileHelper.getInputStream (m_aConfigFile));
  }
}
