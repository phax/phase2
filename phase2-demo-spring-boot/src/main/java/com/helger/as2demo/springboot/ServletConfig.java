/*
 * Copyright (C) 2018-2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 * Idea by: Sergey Yaskov
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
package com.helger.as2demo.springboot;

import java.io.File;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.base.exception.InitializationException;
import com.helger.http.EHttpMethod;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.servlet.AS2ReceiveXServletHandlerConstantSession;
import com.helger.phase2.servlet.AS2WebAppListener;
import com.helger.phase2.servlet.mdn.AS2MDNReceiveXServletHandlerConstantSession;
import com.helger.phase2.servlet.util.AS2ServletXMLSession;
import com.helger.phase2.session.AS2ComponentNotFoundException;
import com.helger.web.scope.mgr.WebScopeManager;
import com.helger.xservlet.AbstractXServlet;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

@Configuration
public class ServletConfig
{
  private static final AS2ServletXMLSession AS2_SESSION;
  static
  {
    try
    {
      AS2_SESSION = new AS2ServletXMLSession (new File ("config/config.xml"));
      // Start them once
      AS2_SESSION.getMessageProcessor ().startActiveModules ();
    }
    catch (final AS2Exception ex)
    {
      throw new InitializationException (ex);
    }
  }

  /**
   * Special AS2 receive servlet, that use the global AS2 Session.
   *
   * @author Philip Helger
   */
  public static class MyAS2ReceiveServlet extends AbstractXServlet
  {
    @Override
    @OverridingMethodsMustInvokeSuper
    public void init () throws ServletException
    {
      // Multipart is handled specifically inside
      settings ().setMultipartEnabled (false);
      handlerRegistry ().registerHandler (EHttpMethod.POST,
                                          new AS2ReceiveXServletHandlerConstantSession (AS2_SESSION),
                                          false);
    }
  }

  /**
   * Special AS2 MDN receive servlet, that use the global AS2 Session.
   *
   * @author Philip Helger
   */
  public static class MyAS2MDNReceiveServlet extends AbstractXServlet
  {
    @Override
    @OverridingMethodsMustInvokeSuper
    public void init () throws ServletException
    {
      // Multipart is handled specifically inside
      settings ().setMultipartEnabled (false);
      handlerRegistry ().registerHandler (EHttpMethod.POST,
                                          new AS2MDNReceiveXServletHandlerConstantSession (AS2_SESSION),
                                          false);
    }
  }

  /** The ServletContext to be used */
  @Autowired
  private ServletContext m_aSC;

  private void _initScope ()
  {
    // Required to be called before the servlet is initialized
    if (!WebScopeManager.isGlobalScopePresent ())
    {
      AS2WebAppListener.staticInit (m_aSC);
    }
  }

  @Bean
  public ServletRegistrationBean <MyAS2ReceiveServlet> servletRegistrationBeanAS2 ()
  {
    _initScope ();

    return new ServletRegistrationBean <> (new MyAS2ReceiveServlet (), "/as2");
  }

  @Bean
  public ServletRegistrationBean <MyAS2MDNReceiveServlet> servletRegistrationBeanMDN ()
  {
    _initScope ();

    return new ServletRegistrationBean <> (new MyAS2MDNReceiveServlet (), "/as2mdn");
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

  @Component
  public static class As2Cleanup
  {
    @PreDestroy
    public void onShutdown ()
    {
      ServletConfig.shutDown ();
      AS2WebAppListener.staticDestroy ();
    }
  }

  @Bean
  public As2Cleanup cleanUpBean ()
  {
    return new As2Cleanup ();
  }
}
