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
package com.helger.phase2.processor;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonnegative;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.state.EChange;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phase2.AbstractDynamicComponent;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.processor.module.IProcessorActiveModule;
import com.helger.phase2.processor.module.IProcessorModule;

/**
 * Abstract empty implementation of {@link IMessageProcessor}. It provides all methods except
 * {@link #handle(String, com.helger.phase2.message.IMessage, java.util.Map)}.
 *
 * @author Philip Helger
 */
public abstract class AbstractMessageProcessor extends AbstractDynamicComponent implements IMessageProcessor
{
  private static final Logger LOGGER = LoggerFactory.getLogger (AbstractMessageProcessor.class);

  private final ICommonsList <IProcessorModule> m_aModules = new CommonsArrayList <> ();

  protected AbstractMessageProcessor ()
  {}

  public void addModule (@NonNull final IProcessorModule aModule)
  {
    ValueEnforcer.notNull (aModule, "Module");
    m_aModules.add (aModule);
  }

  @NonNull
  public EChange removeModule (@Nullable final IProcessorModule aModule)
  {
    if (aModule == null)
      return EChange.UNCHANGED;
    return m_aModules.removeObject (aModule);
  }

  @Nonnegative
  public int getModuleCount ()
  {
    return m_aModules.size ();
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IProcessorModule> getAllModules ()
  {
    return m_aModules.getClone ();
  }

  @Nullable
  public <T extends IProcessorModule> T getModuleOfClass (@NonNull final Class <T> aClass)
  {
    ValueEnforcer.notNull (aClass, "Class");
    return m_aModules.findFirstMapped (x -> aClass.isAssignableFrom (x.getClass ()), aClass::cast);
  }

  @NonNull
  @ReturnsMutableCopy
  public <T extends IProcessorModule> ICommonsList <T> getAllModulesOfClass (@NonNull final Class <T> aClass)
  {
    ValueEnforcer.notNull (aClass, "Class");
    return m_aModules.getAllInstanceOf (aClass);
  }

  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <IProcessorActiveModule> getAllActiveModules ()
  {
    return m_aModules.getAllInstanceOf (IProcessorActiveModule.class);
  }

  public void startActiveModules ()
  {
    for (final IProcessorActiveModule aModule : getAllActiveModules ())
      try
      {
        aModule.start ();
      }
      catch (final AS2Exception ex)
      {
        ex.terminate ();
      }
  }

  public void stopActiveModules ()
  {
    for (final IProcessorActiveModule aModule : getAllActiveModules ())
      try
      {
        aModule.stop ();
      }
      catch (final AS2Exception ex)
      {
        ex.terminate ();
      }
  }

  /**
   * Execution the provided action with the registered modules.
   *
   * @param sAction
   *        Action to execute. Never <code>null</code>.
   * @param aMsg
   *        Message it is about. Never <code>null</code>.
   * @param aOptions
   *        Optional options map to be used. May be <code>null</code>.
   * @throws AS2Exception
   *         In case of error
   */
  protected final void executeAction (@NonNull final String sAction,
                                      @NonNull final IMessage aMsg,
                                      @Nullable final Map <String, Object> aOptions) throws AS2Exception
  {
    final ICommonsList <AS2Exception> aCauses = new CommonsArrayList <> ();
    final ICommonsList <IProcessorModule> aModulesFound = new CommonsArrayList <> ();

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("  handling action '" +
                    sAction +
                    "' on message '" +
                    aMsg.getMessageID () +
                    "' with options " +
                    aOptions);

    final ICommonsList <IProcessorModule> aAllModules = getAllModules ();
    for (final IProcessorModule aModule : aAllModules)
      if (aModule.canHandle (sAction, aMsg, aOptions))
      {
        if (LOGGER.isDebugEnabled ())
          LOGGER.debug ("  handling action '" + sAction + "' with module " + aModule);

        try
        {
          aModulesFound.add (aModule);
          aModule.handle (sAction, aMsg, aOptions);
        }
        catch (final AS2Exception ex)
        {
          aCauses.add (ex);
        }
      }
      else
      {
        if (LOGGER.isTraceEnabled ())
          LOGGER.trace ("  Not handling action '" + sAction + "' with module " + aModule);
      }

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("  action '" + sAction + "' was handled by modules: " + aModulesFound);

    if (aCauses.isNotEmpty ())
    {
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("  action '" + sAction + "' was handled but failed: " + aCauses);
      throw new AS2ProcessorException (this, aCauses);
    }

    if (aModulesFound.isEmpty ())
    {
      if (LOGGER.isDebugEnabled ())
        LOGGER.debug ("  no modules found for '" + sAction + "'; modules are: " + aAllModules);
      throw new AS2NoModuleException (sAction, aMsg, aOptions);
    }
  }

  @Override
  public boolean equals (final Object o)
  {
    // New member, no change
    return super.equals (o);
  }

  @Override
  public int hashCode ()
  {
    // New member, no change
    return super.hashCode ();
  }
}
