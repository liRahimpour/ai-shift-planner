@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM Required ENV vars: JAVA_HOME
@REM ----------------------------------------------------------------------------

@echo off
@setlocal

set ERROR_CODE=0

set MAVEN_PROJECTBASEDIR=%~dp0
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar

if exist %WRAPPER_JAR% goto runm2

echo Downloading Maven Wrapper %WRAPPER_URL% ...
powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile %WRAPPER_JAR%"

:runm2
if not "%JAVA_HOME%"=="" goto valid_java_home

set JAVACMD=java.exe
if "%ERRORLEVEL%" == "9009" goto error
goto init

:valid_java_home
set JAVACMD=%JAVA_HOME%\bin\java.exe
if exist "%JAVACMD%" goto init

echo Error: JAVA_HOME [%JAVA_HOME%] is not a valid directory
goto error

:init
set MAVEN_CMD_LINE_ARGS=%MAVEN_CONFIG% %*

%JAVACMD% %MAVEN_OPTS% -classpath %WRAPPER_JAR% "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %MAVEN_CONFIG% %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%
exit /B %ERROR_CODE%
