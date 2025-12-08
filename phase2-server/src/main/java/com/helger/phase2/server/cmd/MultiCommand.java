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
package com.helger.phase2.server.cmd;

import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.array.ArrayHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.session.IAS2Session;
import com.helger.typeconvert.collection.IStringMap;

public class MultiCommand extends AbstractCommand
{
  private ICommonsList <ICommand> m_aCmds;

  @Override
  public void initDynamicComponent (@NonNull final IAS2Session session, @Nullable final IStringMap parameters) throws AS2Exception
  {
    super.initDynamicComponent (session, parameters);
    getAttributeAsStringRequired (ATTR_NAME);
    getAttributeAsStringRequired (ATTR_DESCRIPTION);
    if (getUsage () == null)
      setUsage (getName () + " <command> <parameters>");
  }

  @Nullable
  public ICommand getCommand (@NonNull final String sName)
  {
    final String sLCName = sName.toLowerCase (Locale.US);
    for (final ICommand cmd : getCommands ())
      if (cmd.getName ().equals (sLCName))
        return cmd;
    return null;
  }

  @NonNull
  public ICommonsList <ICommand> getCommands ()
  {
    if (m_aCmds == null)
      m_aCmds = new CommonsArrayList <> ();
    return m_aCmds;
  }

  public String getDescription (final String name)
  {
    final ICommand cmd = getCommand (name);
    return cmd == null ? null : cmd.getDescription ();
  }

  public String getUsage (final String name)
  {
    final ICommand cmd = getCommand (name);
    return cmd == null ? null : cmd.getUsage ();
  }

  @Override
  public CommandResult execute (final Object [] params)
  {
    if (params.length > 0)
    {
      final String subName = params[0].toString ();
      final ICommand subCmd = getCommand (subName);
      if (subCmd != null)
      {
        // All params expcept first
        final Object [] aSubParams = ArrayHelper.getCopy (params, 1, params.length - 1);
        return subCmd.execute (aSubParams);
      }
    }

    final CommandResult listCmds = new CommandResult (ECommandResultType.TYPE_ERROR, "List of valid subcommands:");
    for (final ICommand currentCmd : getCommands ())
      listCmds.addResult (currentCmd.getName ());
    return listCmds;
  }

  public CommandResult execute (final String name, final Object [] params) throws AS2Exception
  {
    final ICommand cmd = getCommand (name);

    if (cmd != null)
      return cmd.execute (params);
    throw new AS2ServerCommandException ("Command doesn't exist: " + name);
  }

  public boolean supports (final String name)
  {
    return getCommand (name) != null;
  }

  @Override
  public String getDefaultName ()
  {
    return null;
  }

  @Override
  public String getDefaultDescription ()
  {
    return null;
  }

  @Override
  public String getDefaultUsage ()
  {
    return null;
  }
}
