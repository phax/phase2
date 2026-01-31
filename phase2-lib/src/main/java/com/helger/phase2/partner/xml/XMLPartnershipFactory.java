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
package com.helger.phase2.partner.xml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.OverridingMethodsMustInvokeSuper;
import com.helger.annotation.WillClose;
import com.helger.base.string.StringHelper;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.io.file.FileHelper;
import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.exception.WrappedAS2Exception;
import com.helger.phase2.params.AS2InvalidParameterException;
import com.helger.phase2.partner.IRefreshablePartnershipFactory;
import com.helger.phase2.partner.Partnership;
import com.helger.phase2.partner.PartnershipMap;
import com.helger.phase2.session.IAS2Session;
import com.helger.phase2.util.AS2IOHelper;
import com.helger.phase2.util.AS2XMLHelper;
import com.helger.typeconvert.collection.IStringMap;
import com.helger.typeconvert.collection.StringMap;
import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.IMicroElement;
import com.helger.xml.microdom.MicroDocument;
import com.helger.xml.microdom.serialize.MicroReader;
import com.helger.xml.microdom.serialize.MicroWriter;

/**
 * original author unknown this release added logic to store partnerships and provide methods for
 * partner/partnership command line processor
 *
 * @author joseph mcverry
 */
public class XMLPartnershipFactory extends AbstractPartnershipFactoryWithPartners implements
                                   IRefreshablePartnershipFactory
{
  public static final String ATTR_FILENAME = "filename";
  public static final String ATTR_DISABLE_BACKUP = "disablebackup";

  private static final String ATTR_PARTNER_NAME = Partner.PARTNER_NAME;
  private static final String ATTR_PARTNERSHIP_NAME = Partner.PARTNER_NAME;
  private static final Logger LOGGER = LoggerFactory.getLogger (XMLPartnershipFactory.class);

  @NonNull
  public String getFilename () throws AS2InvalidParameterException
  {
    return getAttributeAsStringRequired (ATTR_FILENAME);
  }

  public void setFilename (final String filename)
  {
    attrs ().putIn (ATTR_FILENAME, filename);
  }

  public boolean isDisableBackup ()
  {
    return attrs ().containsKey (ATTR_DISABLE_BACKUP);
  }

  public void setDisableBackup (final boolean bDisableBackup)
  {
    if (bDisableBackup)
      attrs ().putIn (ATTR_DISABLE_BACKUP, true);
    else
      attrs ().remove (ATTR_DISABLE_BACKUP);
  }

  @Override
  public void initDynamicComponent (@NonNull final IAS2Session session, @Nullable final IStringMap parameters)
                                                                                                               throws AS2Exception
  {
    super.initDynamicComponent (session, parameters);

    refreshPartnershipFactory ();
  }

  @OverridingMethodsMustInvokeSuper
  public void refreshPartnershipFactory () throws AS2Exception
  {
    try
    {
      final File aFile = new File (getFilename ());
      load (FileHelper.getInputStream (aFile));
    }
    catch (final Exception ex)
    {
      throw WrappedAS2Exception.wrap (ex);
    }
  }

  protected void load (@Nullable @WillClose final InputStream aIS) throws AS2Exception
  {
    final PartnerMap aNewPartners = new PartnerMap ();
    final PartnershipMap aNewPartnerships = new PartnershipMap ();

    if (aIS != null)
    {
      final IMicroDocument aDocument = MicroReader.readMicroXML (aIS);
      if (aDocument == null)
        throw new AS2Exception ("Failed to read the XML partnership information");

      final IMicroElement aRoot = aDocument.getDocumentElement ();
      for (final IMicroElement eRootNode : aRoot.getAllChildElements ())
      {
        final String sNodeName = eRootNode.getTagName ();

        if (sNodeName.equals ("partner"))
        {
          final Partner aNewPartner = loadPartner (eRootNode);
          aNewPartners.addPartner (aNewPartner);
        }
        else
          if (sNodeName.equals ("partnership"))
          {
            final Partnership aNewPartnership = loadPartnership (eRootNode, aNewPartners);
            if (aNewPartnerships.getPartnershipByName (aNewPartnership.getName ()) != null)
              throw new AS2Exception ("Partnership with name '" +
                                      aNewPartnership.getName () +
                                      "' is defined more than once");
            aNewPartnerships.addPartnership (aNewPartnership);
          }
          else
          {
            LOGGER.warn ("Invalid element '" + sNodeName + "' in XML partnership file");
          }
      }
    }

    setPartners (aNewPartners);
    setPartnerships (aNewPartnerships);
  }

  protected void loadPartnershipAttributes (@NonNull final IMicroElement aNode, @NonNull final Partnership aPartnership)
                                                                                                                         throws AS2Exception
  {
    final String sNodeName = "attribute";
    final String sNodeKeyName = "name";
    final String sNodeValueName = "value";
    final ICommonsOrderedMap <String, String> aAttributes = AS2XMLHelper.mapAttributeNodes (aNode,
                                                                                            sNodeName,
                                                                                            sNodeKeyName,
                                                                                            sNodeValueName);
    aPartnership.addAllAttributes (aAttributes);
  }

  @NonNull
  public Partner loadPartner (@NonNull final IMicroElement ePartner) throws AS2Exception
  {
    // Name is required
    final StringMap aAttrs = AS2XMLHelper.getAllAttrsWithLowercaseNameWithRequired (ePartner, ATTR_PARTNER_NAME);
    return new Partner (aAttrs);
  }

  protected void loadPartnerIDs (@NonNull final IMicroElement ePartnership,
                                 @NonNull final IPartnerMap aAllPartners,
                                 @NonNull final Partnership aPartnership,
                                 final boolean bIsSender) throws AS2Exception
  {
    final String sPartnerType = bIsSender ? "sender" : "receiver";
    final IMicroElement ePartner = ePartnership.getFirstChildElement (sPartnerType);
    if (ePartner == null)
      throw new AS2Exception ("Partnership '" +
                              aPartnership.getName () +
                              "' is missing '" +
                              sPartnerType +
                              "' child element");

    final IStringMap aPartnerAttrs = AS2XMLHelper.getAllAttrsWithLowercaseName (ePartner);

    // check for a partner name, and look up in partners list if one is found
    final String sPartnerName = aPartnerAttrs.getAsString (ATTR_PARTNER_NAME);
    if (sPartnerName != null)
    {
      // Resolve name from existing partners
      final IPartner aPartner = aAllPartners.getPartnerOfName (sPartnerName);
      if (aPartner == null)
      {
        throw new AS2Exception ("Partnership '" +
                                aPartnership.getName () +
                                "' has a non-existing " +
                                sPartnerType +
                                " partner: '" +
                                sPartnerName +
                                "'");
      }

      // Set all attributes from the stored partner
      if (bIsSender)
        aPartnership.addSenderIDs (aPartner.getAllAttributes ());
      else
        aPartnership.addReceiverIDs (aPartner.getAllAttributes ());
    }

    // copy all other (existing) attributes to the partner id map - overwrite
    // the ones present in the partner element
    if (bIsSender)
      aPartnership.addSenderIDs (aPartnerAttrs);
    else
      aPartnership.addReceiverIDs (aPartnerAttrs);
  }

  @NonNull
  public Partnership loadPartnership (@NonNull final IMicroElement ePartnership,
                                      @NonNull final IPartnerMap aAllPartners) throws AS2Exception
  {
    // Name attribute is required
    final IStringMap aPartnershipAttrs = AS2XMLHelper.getAllAttrsWithLowercaseNameWithRequired (ePartnership,
                                                                                                ATTR_PARTNERSHIP_NAME);

    final Partnership aPartnership = new Partnership (aPartnershipAttrs.getAsString (ATTR_PARTNERSHIP_NAME));

    // load the sender and receiver information
    loadPartnerIDs (ePartnership, aAllPartners, aPartnership, true);
    loadPartnerIDs (ePartnership, aAllPartners, aPartnership, false);

    // read in the partnership attributes
    loadPartnershipAttributes (ePartnership, aPartnership);

    return aPartnership;
  }

  @NonNull
  private static File _getUniqueBackupFile (@NonNull final String sFilename)
  {
    long nIndex = 0;
    File aBackupFile;
    do
    {
      aBackupFile = new File (sFilename + '.' + StringHelper.getLeadingZero (nIndex, 7));
      nIndex++;
    } while (aBackupFile.exists ());
    return aBackupFile;
  }

  /**
   * Store the current status of the partnerships to a file.
   *
   * @throws AS2Exception
   *         In case of an error
   */
  public void storePartnership () throws AS2Exception
  {
    final String sFilename = getFilename ();

    if (!isDisableBackup ())
    {
      final File aBackupFile = _getUniqueBackupFile (sFilename);
      LOGGER.info ("backing up " + sFilename + " to " + aBackupFile.getName ());

      final File aSourceFile = new File (sFilename);
      try
      {
        AS2IOHelper.moveFile (aSourceFile, aBackupFile, true, true);
      }
      catch (final IOException ex)
      {
        new AS2Exception ("Failed to move file " + aSourceFile + " to " + aBackupFile, ex).terminate ();
      }
    }

    final IMicroDocument aDoc = new MicroDocument ();
    final IMicroElement eRoot = aDoc.addElement ("partnerships");
    for (final IPartner aPartner : getAllPartners ())
    {
      final IMicroElement ePartner = eRoot.addElement ("partner");
      for (final Map.Entry <String, String> aAttr : aPartner)
        ePartner.setAttribute (aAttr.getKey (), aAttr.getValue ());
    }

    for (final Partnership aPartnership : getAllPartnerships ())
    {
      final IMicroElement ePartnership = eRoot.addElement ("partnership");
      ePartnership.setAttribute (ATTR_PARTNERSHIP_NAME, aPartnership.getName ());

      final IMicroElement eSender = ePartnership.addElement ("sender");
      for (final Map.Entry <String, String> aAttr : aPartnership.getAllSenderIDs ().entrySet ())
        eSender.setAttribute (aAttr.getKey (), aAttr.getValue ());

      final IMicroElement eReceiver = ePartnership.addElement ("receiver");
      for (final Map.Entry <String, String> aAttr : aPartnership.getAllReceiverIDs ().entrySet ())
        eReceiver.setAttribute (aAttr.getKey (), aAttr.getValue ());

      for (final Map.Entry <String, String> aAttr : aPartnership.getAllAttributes ().entrySet ())
        ePartnership.addElement ("attribute")
                    .setAttribute ("name", aAttr.getKey ())
                    .setAttribute ("value", aAttr.getValue ());
    }
    if (MicroWriter.writeToFile (aDoc, new File (sFilename)).isFailure ())
      throw new AS2Exception ("Failed to write to file " + sFilename);
  }
}
