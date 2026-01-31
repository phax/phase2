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
package com.helger.phase2.webapp.servlet;

import java.io.File;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.exception.InitializationException;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.servlet.util.AS2ServletXMLSession;
import com.helger.phase2.session.AS2ComponentNotFoundException;

/**
 * Wrapper around the global AS2 session.
 *
 * @author Philip Helger
 * @since 4.8.0
 */
@Immutable
public final class GlobalAS2Session
{
  public static final AS2ServletXMLSession AS2_SESSION;
  static
  {
    try
    {
      // Load the configuration from that file
      // In a real-world application you can make this path customizable e.g.
      // via a system property or an environment variable.
      AS2_SESSION = new AS2ServletXMLSession (new File ("config/config.xml"));
      // Start them once
      AS2_SESSION.getMessageProcessor ().startActiveModules ();
    }
    catch (final AS2Exception ex)
    {
      throw new InitializationException (ex);
    }
  }

  private GlobalAS2Session ()
  {}

  static void ensureClassIsLoaded ()
  {
    // Do nothing
  }

  /**
   * Call this method at the application end, to shutdown all active modules.
   */
  public static void shutDown ()
  {
    if (AS2_SESSION != null)
      try
      {
        AS2_SESSION.getMessageProcessor ().stopActiveModules ();
      }
      catch (final AS2ComponentNotFoundException ex)
      {
        // we don't care on shut down
      }
  }
}
