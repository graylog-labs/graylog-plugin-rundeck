#!/bin/bash -e
PROJECT_NAME='graylog'
JOB_ID='804f107a-cafe-babe-0000-deadbeef0000'
DIR=$(dirname "$0")

mkdir /token
cd /token
rd tokens create -u admin | grep -v '^#' > /token/token.txt

nohup python -m SimpleHTTPServer 12345 > /dev/null &

rd projects create -p "${PROJECT_NAME}"
for JOB_YAML in ${DIR}/jobs/*.yml
do
  rd jobs load -p "${PROJECT_NAME}" -f "${JOB_YAML}" -F yaml
done

