@REM
@REM The FreeBSD Copyright
@REM Copyright 1994-2008 The FreeBSD Project. All rights reserved.
@REM Copyright (C) 2013-2026 Philip Helger philip[at]helger[dot]com
@REM
@REM Redistribution and use in source and binary forms, with or without
@REM modification, are permitted provided that the following conditions are
@REM met:
@REM
@REM    1. Redistributions of source code must retain the above copyright
@REM    notice, this list of conditions and the following disclaimer.
@REM
@REM    2. Redistributions in binary form must reproduce the above copyright
@REM    notice, this list of conditions and the following disclaimer in the
@REM    documentation and/or other materials provided with the distribution.
@REM
@REM THIS SOFTWARE IS PROVIDED BY THE FREEBSD PROJECT ``AS IS'' AND ANY
@REM EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
@REM IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
@REM PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE FREEBSD PROJECT OR
@REM CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
@REM EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
@REM PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
@REM PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
@REM LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
@REM NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
@REM SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
@REM
@REM The views and conclusions contained in the software and documentation
@REM are those of the authors and should not be interpreted as representing
@REM official policies, either expressed or implied, of the FreeBSD Project.
@REM

@echo off
"%JAVA_HOME%\bin\java" -cp "standalone/*" com.helger.phase2.server.app.MainPhase2Server standalone/config/config.xml
