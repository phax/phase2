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
package com.helger.phase2.servlet;

import org.jspecify.annotations.NonNull;

import com.helger.web.scope.mgr.WebScopeManager;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * A very simple listener, that setups the framework for running the AS2
 * servlet. Use this as a &lt;listener&gt; in your <code>web.xml</code>.
 *
 * @author Philip Helger
 */
public class AS2WebAppListener implements ServletContextListener
{
  /**
   * Do the global initialization when not using the
   * {@link ServletContextListener}.
   *
   * @param aSC
   *        The servlet context. May not be <code>null</code>.
   * @since 4.4.5
   */
  public static void staticInit (@NonNull final ServletContext aSC)
  {
    WebScopeManager.onGlobalBegin (aSC);
  }

  public void contextInitialized (@NonNull final ServletContextEvent aSCE)
  {
    final ServletContext aSC = aSCE.getServletContext ();
    staticInit (aSC);
  }

  /**
   * Do the global shutdown when not using the {@link ServletContextListener}.
   *
   * @since 4.4.5
   */
  public static void staticDestroy ()
  {
    WebScopeManager.onGlobalEnd ();
  }

  public void contextDestroyed (@NonNull final ServletContextEvent aSce)
  {
    staticDestroy ();
  }
}
