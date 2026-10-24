#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if [ "${1:-}" = "test" ]; then
    mvn -q test-compile
    java -cp target/classes:target/test-classes edu.example.visualizer.SmokeTest
    java -cp target/classes:target/test-classes edu.example.visualizer.ParentResolutionTest
else
    mvn -q -DskipTests package
    java -jar target/dependency-visualizer-1.0.0.jar config.yaml
fi
