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

import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.http.EHttpMethod;
import com.helger.phase2.servlet.mdn.AS2MDNReceiveXServletHandlerConstantSession;
import com.helger.xservlet.AbstractXServlet;

import jakarta.servlet.ServletException;

/**
 * This is the main servlet that takes async AS2 MDN messages and processes
 * them. This servlet is configured to accept only POST requests. The logic for
 * receiving is contained in
 * {@link AS2MDNReceiveXServletHandlerConstantSession}.
 *
 * @author Philip Helger
 * @since 4.8.0
 */
public class AS2MDNReceiveServlet extends AbstractXServlet
{
  public AS2MDNReceiveServlet ()
  {}

  @Override
  @OverridingMethodsMustInvokeSuper
  public void init () throws ServletException
  {
    // Multipart is handled specifically inside
    settings ().setMultipartEnabled (false);
    handlerRegistry ().registerHandler (EHttpMethod.POST,
                                        new AS2MDNReceiveXServletHandlerConstantSession (GlobalAS2Session.AS2_SESSION),
                                        false);
  }
}
