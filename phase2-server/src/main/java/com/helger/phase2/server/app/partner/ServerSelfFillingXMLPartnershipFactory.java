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
package com.helger.phase2.server.app.partner;

import java.io.File;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.string.StringHelper;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.WrappedAS2Exception;
import com.helger.phase2.params.AS2InvalidParameterException;
import com.helger.phase2.partner.xml.SelfFillingXMLPartnershipFactory;
import com.helger.phase2.server.util.EFileMonitorEvent;
import com.helger.phase2.server.util.FileMonitor;
import com.helger.phase2.server.util.IFileMonitorListener;

/**
 * Same as {@link ServerXMLPartnershipFactory} but based on the
 * {@link SelfFillingXMLPartnershipFactory}
 *
 * @author Philip Helger
 * @since 4.3.0
 */
public class ServerSelfFillingXMLPartnershipFactory extends SelfFillingXMLPartnershipFactory implements
                                                    IFileMonitorListener
{
  public static final String ATTR_INTERVAL = "interval";
  private static final Logger LOGGER = LoggerFactory.getLogger (ServerSelfFillingXMLPartnershipFactory.class);

  private FileMonitor m_aFileMonitor;

  public void setFileMonitor (@Nullable final FileMonitor aFileMonitor)
  {
    m_aFileMonitor = aFileMonitor;
  }

  @NonNull
  public FileMonitor getFileMonitor () throws AS2InvalidParameterException
  {
    boolean bCreateMonitor = m_aFileMonitor == null && attrs ().containsKey (ATTR_INTERVAL);

    if (!bCreateMonitor && m_aFileMonitor != null)
    {
      final String sFilename = m_aFileMonitor.getFilename ();
      bCreateMonitor = StringHelper.isNotEmpty (sFilename) && !sFilename.equals (getFilename ());
    }

    if (bCreateMonitor)
    {
      if (m_aFileMonitor != null)
        m_aFileMonitor.stop ();

      final int nInterval = getAttributeAsIntRequired (ATTR_INTERVAL);
      final File aFile = new File (getFilename ());
      m_aFileMonitor = new FileMonitor (aFile, nInterval);
      m_aFileMonitor.addListener (this);
    }

    return m_aFileMonitor;
  }

  public void onFileMonitorEvent (final FileMonitor monitor, final File file, @NonNull final EFileMonitorEvent eEvent)
  {
    switch (eEvent)
    {
      case EVENT_MODIFIED:
        try
        {
          refreshPartnershipFactory ();
          LOGGER.info ("- Partnerships Reloaded -");
        }
        catch (final AS2Exception oae)
        {
          oae.terminate ();
        }

        break;
    }
  }

  @Override
  public void refreshPartnershipFactory () throws AS2Exception
  {
    super.refreshPartnershipFactory ();
    try
    {
      getFileMonitor ();
    }
    catch (final Exception ex)
    {
      throw WrappedAS2Exception.wrap (ex);
    }
  }
}
