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

import org.jspecify.annotations.NonNull;

import com.helger.phase2.processor.receiver.net.AS2MDNReceiverHandler;
import com.helger.phase2.servlet.util.AS2ServletMDNReceiverModule;

/**
 * Configurable version of {@link AS2ServletMDNReceiverModule}.
 *
 * @author Philip Helger
 */
public class ConfigurableAS2MDNServletReceiverModule extends AS2ServletMDNReceiverModule
{
  @Override
  @NonNull
  public AS2MDNReceiverHandler createHandler ()
  {
    final AS2MDNReceiverHandler ret = super.createHandler ();
    // Customize receive handler if you like
    return ret;
  }
}
