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
package com.helger.phase2.servlet.mdn;

import java.io.File;
import java.io.IOException;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.style.OverrideOnDemand;
import com.helger.base.string.StringHelper;
import com.helger.collection.commons.ICommonsMap;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.servlet.util.AS2ServletXMLSession;
import com.helger.phase2.session.AS2Session;

import jakarta.servlet.ServletException;

/**
 * A special {@link AbstractAS2MDNReceiveXServletHandler} with a file based
 * configuration.
 *
 * @author Philip Helger
 * @since 4.6.4
 */
public class AS2MDNReceiveXServletHandlerFileBasedConfig extends AbstractAS2MDNReceiveXServletHandler
{
  /**
   * Get the AS2 configuration file to be used. By default this method reads it
   * from the Servlet init-param called
   * {@link #SERVLET_INIT_PARAM_AS2_SERVLET_CONFIG_FILENAME}. You may override
   * this method to use another way of retrieving the configuration file. <br>
   * Note: it must be a {@link File} because the configuration file allows for
   * "%home%" parameter substitution which uses the directory of the
   * configuration file as the base directory.
   *
   * @param aInitParams
   *        Servlet init parameters
   * @return The configuration file to be used. MUST not be <code>null</code>.
   * @throws ServletException
   *         If no or an invalid configuration file was provided.
   */
  @OverrideOnDemand
  @NonNull
  protected File getConfigurationFile (@NonNull final ICommonsMap <String, String> aInitParams) throws ServletException
  {
    final String sConfigurationFilename = aInitParams.get (SERVLET_INIT_PARAM_AS2_SERVLET_CONFIG_FILENAME);
    if (StringHelper.isEmpty (sConfigurationFilename))
      throw new ServletException ("Servlet Init-Parameter '" + SERVLET_INIT_PARAM_AS2_SERVLET_CONFIG_FILENAME + "' is missing or empty!");

    try
    {
      return new File (sConfigurationFilename).getCanonicalFile ();
    }
    catch (final IOException ex)
    {
      throw new ServletException ("Failed to get the canonical file from '" + sConfigurationFilename + "'", ex);
    }
  }

  @Override
  @NonNull
  protected AS2Session createAS2Session (@NonNull final ICommonsMap <String, String> aInitParams) throws AS2Exception, ServletException
  {
    // Get configuration file
    final File aConfigurationFile = getConfigurationFile (aInitParams);
    if (aConfigurationFile == null)
      throw new ServletException ("No configuration file provided!");

    return new AS2ServletXMLSession (aConfigurationFile);
  }
}
