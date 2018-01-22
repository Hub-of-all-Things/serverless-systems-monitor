#!/bin/bash

source env.sh

echo "Create artifacts directory"
mkdir artifacts

set -e

# Build the scala package
echo "Build Scala package"
cd status-monitor
sbt assembly
cd -
cp status-monitor/target/scala-2.12/status-monitor.jar artifacts/status-monitor.jar

# Build the Node.js package
echo "Build Node.js package"
cd newman
npm install
zip -qur ../artifacts/newman.zip handler.js package.json node_modules/
cd -

serverless deploy
