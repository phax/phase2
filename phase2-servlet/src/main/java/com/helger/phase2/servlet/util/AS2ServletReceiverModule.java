/*
 * Copyright (C) 2015-2026 Philip Helger (www.helger.com)
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

import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.processor.receiver.AS2ReceiverModule;

/**
 * A specialized {@link AS2ReceiverModule} implementation that disables the
 * active parts.
 *
 * @author Philip Helger
 */
public class AS2ServletReceiverModule extends AS2ReceiverModule
{
  @Override
  public void doStart () throws AS2Exception
  {
    // Would start a thread - so don't do it in the servlet environment
  }

  @Override
  public void doStop () throws AS2Exception
  {
    // Would stop a thread - so don't do it in the servlet environment
  }
}
