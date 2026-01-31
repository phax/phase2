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
package com.helger.phase2.processor.resender;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.concurrent.GuardedBy;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.processor.sender.IProcessorSenderModule;

/**
 * An asynchronous, in-memory, polling based resender module. Upon
 * {@link #handle(String, IMessage, Map)} the document is added to a queue there is a background
 * poller task that checks for resending (see {@link #resend()} ). If resending fails an exception
 * is thrown.
 *
 * @author Philip Helger
 * @since 2.2.0
 */
public class InMemoryResenderModule extends AbstractActiveResenderModule
{
  private static final Logger LOGGER = LoggerFactory.getLogger (InMemoryResenderModule.class);

  @GuardedBy ("m_aRWLock")
  private final ICommonsList <ResendItem> m_aItems = new CommonsArrayList <> ();

  @Override
  public boolean canHandle (@NonNull final String sAction,
                            @NonNull final IMessage aMsg,
                            @Nullable final Map <String, Object> aOptions)
  {
    return sAction.equals (IProcessorResenderModule.DO_RESEND);
  }

  @Override
  public void handle (@NonNull final String sAction,
                      @NonNull final IMessage aMsg,
                      @Nullable final Map <String, Object> aOptions) throws AS2Exception
  {
    // Get the action to be used
    String sResendAction = aOptions == null ? null : (String) aOptions.get (
                                                                            IProcessorResenderModule.OPTION_RESEND_ACTION);
    if (sResendAction == null)
    {
      LOGGER.warn ("The resending action is missing - default to message sending!");
      sResendAction = IProcessorSenderModule.DO_SEND;
    }

    // Get the number of retries
    final String sRetries = aOptions == null ? null : (String) aOptions.get (IProcessorResenderModule.OPTION_RETRIES);
    int nRetries;
    if (sRetries != null)
      nRetries = Integer.parseInt (sRetries);
    else
    {
      nRetries = IProcessorResenderModule.DEFAULT_RETRIES;
      LOGGER.warn ("The resending retry count is missing - default to " + nRetries + "!");
    }

    // Build the item and add it to the vector
    final ResendItem aItem = new ResendItem (sResendAction, nRetries, aMsg, getResendDelayMS ());
    m_aRWLock.writeLocked ( () -> m_aItems.add (aItem));

    LOGGER.info ("Message put in resend queue" + aMsg.getLoggingText ());
  }

  protected void resendItem (@NonNull final ResendItem aItem) throws AS2Exception
  {
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Resending item");

    IMessage aMsg = null;
    try
    {
      final String sResendAction = aItem.getResendAction ();
      final String sRemainingRetries = Integer.toString (aItem.getRetries () - 1);
      aMsg = aItem.getMessage ();

      // Transmit the message
      LOGGER.info ("Loaded message for resend" + aMsg.getLoggingText ());

      final ICommonsMap <String, Object> aOptions = new CommonsHashMap <> ();
      aOptions.put (IProcessorResenderModule.OPTION_RETRIES, sRemainingRetries);
      getSession ().getMessageProcessor ().handle (sResendAction, aMsg, aOptions);

      // Finally remove from list
      m_aRWLock.writeLocked ( () -> m_aItems.remove (aItem));
    }
    catch (final AS2Exception ex)
    {
      throw ex.setSourceMsg (aMsg);
    }
  }

  @Override
  public void resend ()
  {
    try
    {
      // Determine all items to be re-send
      final ICommonsList <ResendItem> aResendItems = new CommonsArrayList <> ();
      m_aRWLock.readLocked ( () -> m_aItems.findAll (ResendItem::isTimeToSend, aResendItems::add));

      // Resend all selected items
      for (final ResendItem aResendItem : aResendItems)
        resendItem (aResendItem);
    }
    catch (final AS2Exception ex)
    {
      ex.terminate ();
      forceStop (ex);
    }
  }

  @Nonnegative
  public int getResendItemCount ()
  {
    return m_aRWLock.readLockedInt (m_aItems::size);
  }

  /**
   * Remove all resend items.
   */
  public void removeAllResendItems ()
  {
    final int nItems = getResendItemCount ();
    if (nItems > 0)
    {
      m_aRWLock.writeLocked (m_aItems::clear);
      LOGGER.info ("Removed " + nItems + " items from InMemoryResenderModule");
    }
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <ResendItem> getAllResendItems ()
  {
    return m_aRWLock.readLockedGet (m_aItems::getClone);
  }

  @Override
  public void doStop () throws AS2Exception
  {
    final int nRemainingItems = getResendItemCount ();
    if (nRemainingItems > 0)
    {
      LOGGER.error ("InMemoryResenderModule is stopped but " +
                    nRemainingItems +
                    " items are still contained. They are discarded and will be lost!");
    }

    super.doStop ();
  }
}
