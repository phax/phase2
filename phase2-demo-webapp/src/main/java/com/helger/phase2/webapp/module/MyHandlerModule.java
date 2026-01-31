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
package com.helger.phase2.webapp.module;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.message.IMessage;
import com.helger.phase2.processor.module.AbstractProcessorModule;
import com.helger.phase2.processor.storage.IProcessorStorageModule;

public class MyHandlerModule extends AbstractProcessorModule implements IProcessorStorageModule
{
  private static final Logger LOGGER = LoggerFactory.getLogger (MyHandlerModule.class);

  public boolean canHandle (final String sAction, final IMessage aMsg, final Map <String, Object> aOptions)
  {
    return sAction.equals (DO_STORE);
  }

  public void handle (final String sAction, final IMessage aMsg, final Map <String, Object> aOptions) throws AS2Exception
  {
    // TODO e.g. save to DB
    LOGGER.info ("Received AS2 message");
  }
}
