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
package com.helger.phase2.processor.module;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.phase2.IDynamicComponent;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.processor.IMessageProcessor;

/**
 * A processor module is a module that is to be used within an {@link IMessageProcessor} instance.
 *
 * @author OpenAS2
 */
public interface IProcessorModule extends IDynamicComponent
{
  /**
   * Check if this processor module can handle a certain action on the provided message
   *
   * @param sAction
   *        The action to be executed. Never <code>null</code>.
   * @param aMsg
   *        The message in question. May be an AS2 message or an MDN message. Never
   *        <code>null</code>.
   * @param aOptions
   *        The options to be considered. May be <code>null</code>.
   * @return <code>true</code> of this module can handle the respective message, <code>false</code>
   *         if not.
   */
  boolean canHandle (@NonNull String sAction, @NonNull IMessage aMsg, @Nullable Map <String, Object> aOptions);

  /**
   * Main handling of the message. Only called, if {@link #canHandle(String, IMessage, Map)}
   * returned <code>true</code>.
   *
   * @param sAction
   *        The action to be executed. Never <code>null</code>.
   * @param aMsg
   *        The message in question. May be an AS2 message or an MDN message. Never
   *        <code>null</code>.
   * @param aOptions
   *        The options to be considered. May be <code>null</code>.
   * @throws AS2Exception
   *         in case something goes wrong
   */
  void handle (@NonNull String sAction, @NonNull IMessage aMsg, @Nullable Map <String, Object> aOptions)
                                                                                                         throws AS2Exception;
}
