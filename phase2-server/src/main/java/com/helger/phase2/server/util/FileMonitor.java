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
package com.helger.phase2.server.util;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Timer;
import java.util.TimerTask;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.CGlobal;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.datetime.helper.PDTFactory;

public class FileMonitor
{
  private ICommonsList <IFileMonitorListener> m_aListeners;
  private LocalDateTime m_aLastModified;
  private File m_aFile;
  private Timer m_aTimer;
  private boolean m_bBusy;
  private int m_nIntervalSecs;

  public FileMonitor (final File file, final int nIntervalSecs)
  {
    super ();
    m_aFile = file;
    m_nIntervalSecs = nIntervalSecs;
    start ();
  }

  public void setBusy (final boolean busy)
  {
    m_bBusy = busy;
  }

  public boolean isBusy ()
  {
    return m_bBusy;
  }

  public void setFile (final File file)
  {
    m_aFile = file;
  }

  public File getFile ()
  {
    return m_aFile;
  }

  public String getFilename ()
  {
    if (getFile () != null)
    {
      return getFile ().getAbsolutePath ();
    }

    return null;
  }

  public void setInterval (final int nIntervalSecs)
  {
    m_nIntervalSecs = nIntervalSecs;
    restart ();
  }

  public int getInterval ()
  {
    return m_nIntervalSecs;
  }

  public void setLastModified (final LocalDateTime aLastModified)
  {
    m_aLastModified = aLastModified;
  }

  public LocalDateTime getLastModified ()
  {
    return m_aLastModified;
  }

  public void setListeners (@Nullable final ICommonsList <IFileMonitorListener> aListeners)
  {
    m_aListeners = aListeners;
  }

  @NonNull
  public ICommonsList <IFileMonitorListener> getListeners ()
  {
    if (m_aListeners == null)
      m_aListeners = new CommonsArrayList <> ();
    return m_aListeners;
  }

  public void addListener (final IFileMonitorListener listener)
  {
    getListeners ().add (listener);
  }

  public void restart ()
  {
    stop ();
    start ();
  }

  public final void start ()
  {
    m_aTimer = getTimer ();
    m_aTimer.scheduleAtFixedRate (new TimerTick (), 0, getInterval () * CGlobal.MILLISECONDS_PER_SECOND);
  }

  public void stop ()
  {
    if (m_aTimer != null)
      m_aTimer.cancel ();
  }

  protected boolean isModified ()
  {
    final LocalDateTime lastModified = getLastModified ();
    if (lastModified != null)
    {
      final LocalDateTime currentModified = PDTFactory.createLocalDateTime (getFile ().lastModified ());

      return !currentModified.equals (getLastModified ());
    }
    updateModified ();
    return false;
  }

  @NonNull
  protected Timer getTimer ()
  {
    if (m_aTimer == null)
      m_aTimer = new Timer (true);
    return m_aTimer;
  }

  protected void updateListeners ()
  {
    if (isModified ())
    {
      updateModified ();
      updateListeners (EFileMonitorEvent.EVENT_MODIFIED);
    }
  }

  protected void updateListeners (@NonNull final EFileMonitorEvent eEvent)
  {
    final ICommonsList <IFileMonitorListener> aListeners = getListeners ();
    for (final IFileMonitorListener aListener : aListeners)
      aListener.onFileMonitorEvent (this, getFile (), eEvent);
  }

  protected void updateModified ()
  {
    setLastModified (PDTFactory.createLocalDateTime (getFile ().lastModified ()));
  }

  private class TimerTick extends TimerTask
  {
    @Override
    public void run ()
    {
      if (!isBusy ())
      {
        setBusy (true);
        updateListeners ();
        setBusy (false);
      }
      else
      {
        updateListeners (EFileMonitorEvent.EVENT_MISSED_TICK);
      }
    }
  }
}
