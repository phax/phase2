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
package com.helger.phase2.client;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.annotation.WillClose;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.stream.StreamHelper;
import com.helger.base.string.StringHelper;
import com.helger.http.CHttpHeader;
import com.helger.io.file.FileHelper;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.mime.CMimeType;

import jakarta.activation.DataHandler;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;

/**
 * This class represents the content of an AS2 client request.
 *
 * @author oleo Date: May 12, 2010 Time: 5:48:26 PM
 * @author Philip Helger
 */
public class AS2ClientRequest
{
  public static final String DEFAULT_CONTENT_TYPE = CMimeType.APPLICATION_XML.getAsString ();

  // Content type
  private String m_sContentType = DEFAULT_CONTENT_TYPE;
  private final String m_sSubject;
  // Set either text or filename or stream
  // Precedence: byte[] before text
  private byte [] m_aDataByteArray;
  private String m_sDataText;
  private Charset m_aDataCharset;
  private DataHandler m_aDataHandler;
  private String m_sFilename;
  private EContentTransferEncoding m_eCTE;
  private String m_sContentDescription;

  /**
   * @param sSubject
   *        The subject to use. May neither be <code>null</code> nor empty. Has no impact on the
   *        MIME part creation. Just declarative.
   */
  public AS2ClientRequest (@NonNull @Nonempty final String sSubject)
  {
    m_sSubject = ValueEnforcer.notEmpty (sSubject, "Subject");
  }

  /**
   * @return The subject as provided in the constructor. May neither be <code>null</code> nor empty.
   */
  @NonNull
  @Nonempty
  public String getSubject ()
  {
    return m_sSubject;
  }

  /**
   * Set the content type to be used. Use this AFTER <code>setData</code> was called, as this may
   * select a default MIME type.
   *
   * @param sContentType
   *        The content type. May neither be <code>null</code> nor empty.
   * @return this
   */
  @NonNull
  public AS2ClientRequest setContentType (@NonNull @Nonempty final String sContentType)
  {
    m_sContentType = ValueEnforcer.notEmpty (sContentType, "ContentType");
    return this;
  }

  /**
   * @return The content type to be used. Defaults to {@link #DEFAULT_CONTENT_TYPE}. Is overridden
   *         in the <code>setData</code> methods.
   */
  @NonNull
  @Nonempty
  public String getContentType ()
  {
    return m_sContentType;
  }

  /**
   * Set the content of the {@link File} as a payload. If no charset is applied ( <code>null</code>)
   * no content type starting with "text/" may be used. The name of the file is used as the payload
   * file name.
   *
   * @param aFile
   *        {@link File} to read the content from. Never <code>null</code>.
   * @param aCharset
   *        Charset to use. If it is <code>null</code> the content is set as a byte array, if not
   *        <code>null</code> the content is set as a String.
   * @return this
   */
  @NonNull
  public AS2ClientRequest setData (@NonNull final File aFile, @Nullable final Charset aCharset)
  {
    ValueEnforcer.notNull (aFile, "File");
    setData (FileHelper.getInputStream (aFile), aCharset);

    // Set filename by default
    setFilename (aFile.getName ());
    return this;
  }

  /**
   * Set the content of the {@link InputStream} as a payload. No charset is applied and therefore no
   * content type starting with "text/" may be used.
   *
   * @param aIS
   *        {@link InputStream} to read the content from. Never <code>null</code>.
   * @return this
   */
  @NonNull
  public AS2ClientRequest setData (@NonNull @WillClose final InputStream aIS)
  {
    return setData (aIS, (Charset) null);
  }

  /**
   * Set the content of the {@link InputStream} as a payload. No charset is applied and therefore no
   * content type starting with "text/" may be used.
   *
   * @param aIS
   *        {@link InputStream} to read the content from. Never <code>null</code>.
   * @param aCharset
   *        Charset to use. If it is <code>null</code> the content is set as a byte array, if not
   *        <code>null</code> the content is set as a String.
   * @return this
   */
  @NonNull
  public AS2ClientRequest setData (@NonNull @WillClose final InputStream aIS, @Nullable final Charset aCharset)
  {
    ValueEnforcer.notNull (aIS, "InputStream");
    final byte [] aBytes = StreamHelper.getAllBytes (aIS);
    if (aCharset == null)
    {
      // Set pure byte array
      return setData (aBytes);
    }

    // Convert to String and remember charset
    return setData (new String (aBytes, aCharset), aCharset);
  }

  /**
   * Set the provided byte array as data. The "Content-Type" is set to "application/octet-stream".
   *
   * @param aData
   *        The data to be used. May not be <code>null</code>.
   * @return this for chaining.
   */
  @NonNull
  public AS2ClientRequest setData (@NonNull final byte [] aData)
  {
    ValueEnforcer.notNull (aData, "Data");

    m_aDataByteArray = aData;
    m_sDataText = null;
    m_aDataCharset = null;
    m_aDataHandler = null;
    m_sContentType = CMimeType.APPLICATION_OCTET_STREAM.getAsStringWithoutParameters ();
    return this;
  }

  /**
   * Set the provided String as data. The "Content-Type" is set to "text/plain".
   *
   * @param sText
   *        The data to be used. May not be <code>null</code>.
   * @param aCharset
   *        The charset to be used. May be <code>null</code>.
   * @return this for chaining.
   */
  @NonNull
  public AS2ClientRequest setData (@NonNull final String sText, @Nullable final Charset aCharset)
  {
    ValueEnforcer.notNull (sText, "Text");

    m_aDataByteArray = null;
    m_sDataText = sText;
    m_aDataCharset = aCharset;
    m_aDataHandler = null;
    m_sContentType = CMimeType.TEXT_PLAIN.getAsStringWithoutParameters ();
    return this;
  }

  /**
   * Set the provided {@link DataHandler} as data. The "Content-Type" is directly taken from the
   * provided handler.
   *
   * @param aDataHandler
   *        The data handler to be used. May not be <code>null</code>.
   * @return this for chaining.
   */
  @NonNull
  public AS2ClientRequest setData (@NonNull final DataHandler aDataHandler)
  {
    ValueEnforcer.notNull (aDataHandler, "DataHandler");

    m_aDataByteArray = null;
    m_sDataText = null;
    m_aDataCharset = null;
    m_aDataHandler = aDataHandler;
    m_sContentType = aDataHandler.getContentType ();
    return this;
  }

  /**
   * Set the filename to be used to name the content. This will add a
   * <code>Content-Disposition: attachment; filename=...</code> header to the MIME part
   *
   * @param sFilename
   *        Filename to use. May be <code>null</code> to indicate none (also the default)
   * @return this
   */
  @NonNull
  public AS2ClientRequest setFilename (@Nullable final String sFilename)
  {
    m_sFilename = sFilename;
    return this;
  }

  /**
   * Set the optional Content-Transfer-Encoding to be used. By default it is determined by the data
   * type that defines the body.
   *
   * @param eCTE
   *        CTE to be used. May be <code>null</code> in which case the default CTE is used.
   * @return this for chaining
   * @since 3.0.4
   */
  @NonNull
  public AS2ClientRequest setContentTransferEncoding (@Nullable final EContentTransferEncoding eCTE)
  {
    m_eCTE = eCTE;
    return this;
  }

  /**
   * @return The Content-Transfer-Encoding provided. May be <code>null</code>.
   * @since 4.1.1
   */
  @Nullable
  public EContentTransferEncoding getContentTransferEncoding ()
  {
    return m_eCTE;
  }

  /**
   * Set the optional Content-Description header to be used. By default non is present.
   *
   * @param sDescription
   *        Content description to be used. May be <code>null</code>.
   * @return this for chaining
   * @since 3.0.4
   */
  @NonNull
  public AS2ClientRequest setContentDescription (@Nullable final String sDescription)
  {
    m_sContentDescription = sDescription;
    return this;
  }

  public void applyDataOntoMimeBodyPart (@NonNull final MimeBodyPart aPart) throws MessagingException
  {
    if (m_aDataByteArray != null)
    {
      // Set content with a specific MIME type
      // Removes the "Content-Type" and the "Content-Transfer-Encoding"
      // headers
      aPart.setDataHandler (new DataHandler (m_aDataByteArray, m_sContentType));
    }
    else
      if (m_sDataText != null)
      {
        // Set text with an optional charset
        // Sets the "text/plain" content-type internally!
        // This basically calls "setDataHandler (new DataHandler (text,
        // "text/plain; charset="+charset))
        // And that removes the "Content-Type" and the
        // "Content-Transfer-Encoding" headers
        aPart.setText (m_sDataText, m_aDataCharset == null ? null : m_aDataCharset.name ());
      }
      else
        if (m_aDataHandler != null)
        {
          // Use the provided data handler
          // Removes the "Content-Type" and the "Content-Transfer-Encoding"
          // headers
          aPart.setDataHandler (m_aDataHandler);
        }
        else
          throw new IllegalStateException ("No data specified in AS2 client request! A call to setData is missing.");

    // Set as filename as well
    if (m_sFilename != null)
      aPart.setFileName (m_sFilename);

    // aPart.getContentType() is always non-null!
    if (aPart.getHeader (CHttpHeader.CONTENT_TYPE) == null)
    {
      // Ensure Content-Type is present - required for MIC calculation etc.
      aPart.setHeader (CHttpHeader.CONTENT_TYPE,
                       m_sContentType != null ? m_sContentType : CMimeType.APPLICATION_OCTET_STREAM
                                                                                                   .getAsStringWithoutParameters ());
    }

    // Set Content-Transfer-Encoding of the uncompressed, unsigned, unencrypted
    // source message
    // Because all sources uses DataHandler, setting the header here should be
    // fine
    if (m_eCTE != null)
      aPart.setHeader (CHttpHeader.CONTENT_TRANSFER_ENCODING, m_eCTE.getID ());

    if (StringHelper.isNotEmpty (m_sContentDescription))
      aPart.setHeader (CHttpHeader.CONTENT_DESCRIPTION, m_sContentDescription);
  }
}
