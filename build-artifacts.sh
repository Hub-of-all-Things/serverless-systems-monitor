#!/bin/bash

echo "Create artifacts directory"
mkdir artifacts

# Build the scala package
echo "Build Scala package"
cd status-monitor
sbt assembly
cd -
cp status-monitor/target/scala-2.12/todos.jar artifacts/todos.jar

# Build the Node.js package
echo "Build Node.js package"
cd newman
npm install
cd -
zip -u artifacts/newman.zip newman/**
