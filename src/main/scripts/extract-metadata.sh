#!/bin/bash
ProgDir=`dirname "$0"`
. "${ProgDir}/env.sh"

MAIN_CLASS=dk.netarkivet.research.ExtractMetadata

if [ "${NAS_SETTINGS}" ]; then
  NAS_SETTINGS_OPTS=-Ddk.netarkivet.settings.file=$NAS_SETTINGS
elif [ "${NAS_SCRIPT}" ]; then
  NAS_SETTINGS_OPTS=-Ddk.netarkivet.research.script=$NAS_SCRIPT
fi


# echo "NAS_SETTINGS_OPTS: " $NAS_SETTINGS_OPTS
# echo "MAIN_CLASS: " $MAIN_CLASS
# echo "JAVA: " $JAVA
# echo "CP: " $CP
# echo "JAVA_OPTS: " $JAVA_OPTS

cd ${assembly.home.env.name.ref}

"${JAVA}" ${JAVA_OPTS} ${NAS_SETTINGS_OPTS} -D${assembly.home.env.name}="${assembly.home.env.name.ref}" -cp "$CP" $MAIN_CLASS "$@"
