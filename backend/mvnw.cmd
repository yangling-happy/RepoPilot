@ECHO OFF
where mvn >NUL 2>NUL
IF %ERRORLEVEL% NEQ 0 (
  ECHO Apache Maven not found in PATH.
  ECHO Install Maven first, then run: mvn -N wrapper
  ECHO That command will generate the official Maven Wrapper files.
  EXIT /B 1
)
mvn %*
