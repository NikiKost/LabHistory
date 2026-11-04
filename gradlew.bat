@rem Gradle wrapper for Windows
@echo off
setlocal

set APP_HOME=%~dp0
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo Java не найдена. Установите JDK 17 и задайте JAVA_HOME. 1>&2
exit /b 1

:findJavaFromJavaHome
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto execute

echo JAVA_HOME указывает на несуществующую папку: %JAVA_HOME% 1>&2
exit /b 1

:execute
"%JAVA_EXE%" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
