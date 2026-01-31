/*
 * The FreeBSD Copyright
 * Copyright 1994-2008 The FreeBSD Project. All rights reserved.
 * Copyright (C) 2013-2026 Philip Helger philip[at]helger[dot]com
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
package com.helger.phase2.server.cmdprocessor;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.style.OverrideOnDemand;
import com.helger.base.CGlobal;
import com.helger.base.io.nonblocking.NonBlockingBufferedReader;
import com.helger.base.io.nonblocking.NonBlockingBufferedWriter;
import com.helger.base.string.StringHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashSet;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedSet;
import com.helger.phase2.CPhase2Info;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.WrappedAS2Exception;
import com.helger.phase2.server.cmd.CommandResult;
import com.helger.phase2.server.cmd.ICommand;
import com.helger.phase2.server.util.CommandTokenizer;
import com.helger.phase2.session.IAS2Session;
import com.helger.typeconvert.collection.IStringMap;
import com.helger.typeconvert.collection.StringMap;

/**
 * actual socket command processor takes commands from socket/port and passes them to the
 * OpenAS2Server message format &lt;command userid="abc" pasword="xyz"&gt;the actual
 * command&lt;/command&gt; when inited the valid userid and password is passed, then as each command
 * is processed the processCommand method verifies the two fields correctness
 *
 * @author joseph mcverry
 */
public class SocketCommandProcessor extends AbstractCommandProcessor
{
  public static final String ATTR_PORTID = "portid";
  public static final String ATTR_USERID = "userid";
  public static final String ATTR_PASSWORD = "password";

  private static final Logger LOGGER = LoggerFactory.getLogger (SocketCommandProcessor.class);

  private NonBlockingBufferedReader m_aReader;
  private NonBlockingBufferedWriter m_aWriter;
  private SSLServerSocket m_aSSLServerSocket;

  private String m_sUserID;
  private String m_sPassword;
  private SocketCommandParser m_aParser;

  public SocketCommandProcessor ()
  {}

  @OverrideOnDemand
  @Nullable
  protected String [] getEnabledAnonymousCipherSuites (@NonNull final String [] aEnabled,
                                                       @NonNull final String [] aSupported)
  {
    final ICommonsOrderedSet <String> ret = new CommonsLinkedHashSet <> ();
    for (final String sSupported : aSupported)
    {
      // No SSL - usually TLS
      // Only anonymous
      // No DES
      // No MD5
      if (!sSupported.startsWith ("SSL") &&
          sSupported.indexOf ("_anon_") >= 0 &&
          (sSupported.indexOf ("_AES_") >= 0 || sSupported.indexOf ("_3DES_") >= 0) &&
          sSupported.indexOf ("_SHA") >= 0)
      {
        ret.add (sSupported);
      }
    }
    ret.addAll (aEnabled);
    return ret.toArray (CGlobal.EMPTY_STRING_ARRAY);
  }

  @Override
  public void initDynamicComponent (@NonNull final IAS2Session aSession, @Nullable final IStringMap aParams)
                                                                                                             throws AS2Exception
  {
    final StringMap aParameters = aParams == null ? new StringMap () : new StringMap (aParams);
    final String sPort = aParameters.getAsString (ATTR_PORTID);
    try
    {
      final int nPort = Integer.parseInt (sPort);

      final SSLServerSocketFactory aSSLServerSocketFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault ();
      m_aSSLServerSocket = (SSLServerSocket) aSSLServerSocketFactory.createServerSocket (nPort);
      final String [] aEnabledCipherSuites = getEnabledAnonymousCipherSuites (m_aSSLServerSocket.getEnabledCipherSuites (),
                                                                              m_aSSLServerSocket.getSupportedCipherSuites ());
      m_aSSLServerSocket.setEnabledCipherSuites (aEnabledCipherSuites);
    }
    catch (final IOException e)
    {
      throw new AS2Exception (e);
    }
    catch (final NumberFormatException e)
    {
      throw new AS2Exception ("Error converting portid parameter '" + sPort + "': " + e);
    }

    m_sUserID = aParameters.getAsString (ATTR_USERID);
    if (StringHelper.isEmpty (m_sUserID))
      throw new AS2Exception ("missing 'userid' parameter");

    m_sPassword = aParameters.getAsString (ATTR_PASSWORD);
    if (StringHelper.isEmpty (m_sPassword))
      throw new AS2Exception ("missing 'password' parameter");

    try
    {
      m_aParser = new SocketCommandParser ();
    }
    catch (final Exception e)
    {
      throw new AS2Exception (e);
    }
  }

  @Override
  public void processCommand () throws AS2Exception
  {
    try (final SSLSocket socket = (SSLSocket) m_aSSLServerSocket.accept ())
    {
      socket.setSoTimeout (2000);
      m_aReader = new NonBlockingBufferedReader (new InputStreamReader (socket.getInputStream ()));
      m_aWriter = new NonBlockingBufferedWriter (new OutputStreamWriter (socket.getOutputStream ()));

      final String line = m_aReader.readLine ();

      m_aParser.parse (line);

      if (!m_aParser.getUserid ().equals (m_sUserID))
      {
        m_aWriter.write ("Bad userid/password");
        throw new AS2Exception ("Bad userid");
      }

      if (!m_aParser.getPassword ().equals (m_sPassword))
      {
        m_aWriter.write ("Bad userid/password");
        throw new AS2Exception ("Bad password");
      }

      final String str = m_aParser.getCommandText ();
      if (str != null && str.length () > 0)
      {
        final CommandTokenizer cmdTkn = new CommandTokenizer (str);

        if (cmdTkn.hasMoreTokens ())
        {
          final String sCommandName = cmdTkn.nextToken ().toLowerCase ();

          if (sCommandName.equals (StreamCommandProcessor.EXIT_COMMAND))
          {
            terminate ();
          }
          else
          {
            final ICommonsList <String> params = new CommonsArrayList <> ();

            while (cmdTkn.hasMoreTokens ())
            {
              params.add (cmdTkn.nextToken ());
            }

            final ICommand aCommand = getCommand (sCommandName);
            if (aCommand != null)
            {
              final CommandResult aResult = aCommand.execute (params.toArray ());

              if (aResult.getType ().isSuccess ())
              {
                m_aWriter.write (aResult.getAsXMLString ());
              }
              else
              {
                m_aWriter.write ("\r\n" + StreamCommandProcessor.COMMAND_ERROR + "\r\n");
                m_aWriter.write (aResult.getResultAsString ());
              }
            }
            else
            {
              m_aWriter.write (StreamCommandProcessor.COMMAND_NOT_FOUND + "> " + sCommandName + "\r\n");
              m_aWriter.write ("List of commands:" + "\r\n");
              for (final String sCurCmd : getAllCommands ().keySet ())
                m_aWriter.write (sCurCmd + "\r\n");
            }
          }
        }

      }
      m_aWriter.flush ();
    }
    catch (final IOException ex)
    {
      throw WrappedAS2Exception.wrap (ex);
    }
  }

  @Override
  public void run ()
  {
    try
    {
      while (true)
        processCommand ();
    }
    catch (final AS2Exception ex)
    {
      LOGGER.error ("Error running command processor " + CPhase2Info.NAME_VERSION, ex);
    }
  }
}
