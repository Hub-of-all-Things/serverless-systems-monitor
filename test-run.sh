#!/bin/bash

sbt assembly
serverless deploy

curl -X POST https://rsz9g5hh8b.execute-api.eu-west-1.amazonaws.com/dev/ping --data '{ "inputMsg" : "ping" }'
curl https://rsz9g5hh8b.execute-api.eu-west-1.amazonaws.com/dev/todos