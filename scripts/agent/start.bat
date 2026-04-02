@echo off

rem #####################################################
rem SETTINGS
rem #####################################################
set PORT=9876
set AGENTBORNEPORT=9877
set LOGLEVEL=INFO
set DATAFOLDER=data

rem #####################################################
rem Create Run Directory
rem #####################################################
mkdir %DATAFOLDER%
cd %DATAFOLDER%

rem #####################################################
rem Find Agent JAR
rem #####################################################
set JAR=

for %%F in (..\performator-agent-*.jar) do (
    set JAR=%%F
)

if "%JAR%"=="" (
    echo No performator-agent JAR found.
    pause
    exit /b
)

rem #####################################################
rem Execute Agent
rem #####################################################
java -Dpfr_mode=agent -Dpfr_port=%PORT% -Dpfr_agentbornePort=%AGENTBORNEPORT% -Dpfr_loglevel=%LOGLEVEL% -jar %JAR%

pause