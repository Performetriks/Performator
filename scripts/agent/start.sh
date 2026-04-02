#!/bin/bash

#####################################################
# SETTINGS
#####################################################
PORT=9876
AGENTBORNEPORT=9877
LOGLEVEL=INFO
DATAFOLDER=data

#####################################################
# Create Run Directory
#####################################################
mkdir -p "$DATAFOLDER"
cd "$DATAFOLDER" || exit 1

#####################################################
# Find Agent JAR
#####################################################
JAR=$(ls ../performator-agent-*.jar 2>/dev/null | head -n 1)

if [ -z "$JAR" ]; then
    echo "No performator-agent JAR found."
    read -p "Press enter to exit..."
    exit 1
fi

#####################################################
# Execute Agent
#####################################################
java \
  -Dpfr_mode=agent \
  -Dpfr_port=$PORT \
  -Dpfr_agentbornePort=$AGENTBORNEPORT \
  -Dpfr_loglevel=$LOGLEVEL \
  -jar "$JAR"

read -p "Press enter to exit..."