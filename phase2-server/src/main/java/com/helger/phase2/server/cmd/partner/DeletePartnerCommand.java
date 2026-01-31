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
package com.helger.phase2.server.cmd.partner;

import org.jspecify.annotations.NonNull;

import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.partner.Partnership;
import com.helger.phase2.partner.xml.IPartnershipFactoryWithPartners;
import com.helger.phase2.server.cmd.CommandResult;
import com.helger.phase2.server.cmd.ECommandResultType;

/**
 * removes a partner entry in partnership store
 *
 * @author joseph mcverry
 */
public class DeletePartnerCommand extends AbstractAliasedPartnershipsCommand
{
  @Override
  public String getDefaultDescription ()
  {
    return "Delete the partnership associated with an name.";
  }

  @Override
  public String getDefaultName ()
  {
    return "delete";
  }

  @Override
  public String getDefaultUsage ()
  {
    return "delete <name>";
  }

  @Override
  public CommandResult execute (@NonNull final IPartnershipFactoryWithPartners partFx, final Object [] aParams) throws AS2Exception
  {
    if (aParams.length < 1)
      return new CommandResult (ECommandResultType.TYPE_INVALID_PARAM_COUNT, getUsage ());

    final String sName = aParams[0].toString ();

    if (!partFx.getAllPartnerNames ().contains (sName))
      return new CommandResult (ECommandResultType.TYPE_ERROR, "Unknown partner name '" + sName + "'");

    for (final Partnership aPartnership : partFx.getAllPartnerships ())
      if (aPartnership.containsReceiverID (sName) || aPartnership.containsSenderID (sName))
      {
        return new CommandResult (ECommandResultType.TYPE_ERROR, "Can not delete partner '" + sName + "'; it is tied to some partnerships");
      }

    partFx.removePartner (sName);
    return new CommandResult (ECommandResultType.TYPE_OK);
  }
}
