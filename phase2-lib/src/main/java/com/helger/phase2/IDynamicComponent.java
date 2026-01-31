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
package com.helger.phase2;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.params.AS2InvalidParameterException;
import com.helger.phase2.session.IAS2Session;
import com.helger.typeconvert.collection.IStringMap;

/**
 * The Component interface provides a standard way to dynamically create and initialize an object.
 * Component-based objects also have access to a Session, which allow each component to access all
 * components registered to it's Session. Parameters for a component are defined as static
 * strings<br>
 * Note: Any object that implements this interface must have a constructor with no parameters, as
 * these parameters should be passed to the init method.
 *
 * @author Aaron Silinskas
 * @see AbstractDynamicComponent
 * @see IAS2Session
 */
public interface IDynamicComponent
{
  /**
   * @return The attributes of this component.
   */
  @NonNull
  IStringMap attrs ();

  /**
   * Returns a name for the component. These names are not guaranteed to be unique, and are intended
   * for display and logging. Generally this is the class name of the Component object, without
   * package information.
   *
   * @return name of the component
   */
  @Nullable
  String getName ();

  /**
   * Returns the Session used to initialize this Component. The returned session is also used to
   * locate other components if needed.
   *
   * @return this component's session. Never <code>null</code>.
   */
  @NonNull
  IAS2Session getSession ();

  /**
   * After creating a Component object, this method should be called to set any parameters used by
   * the component. Component implementations typically have required parameter checking and code to
   * start timers and threads within this method.
   *
   * @param aSession
   *        the component uses this object to access other components
   * @param aParameters
   *        configuration values for the component. All parameters will be stored as attributes of
   *        this component. All attributes existing before this method is called will be removed!
   * @throws AS2Exception
   *         If an error occurs while initializing the component
   * @throws AS2InvalidParameterException
   *         If a required parameter is null in the parameters Map
   * @see IAS2Session
   */
  void initDynamicComponent (@NonNull IAS2Session aSession, @Nullable IStringMap aParameters) throws AS2Exception;
}
