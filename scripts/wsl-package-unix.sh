#!/usr/bin/env bash
set -euo pipefail
MC_JDK="/mnt/c/Users/Chalcanthite/AppData/Roaming/.minecraft/runtime/java-runtime-epsilon"
WRAP="$HOME/.local/jdk25-wrap"
mkdir -p "$WRAP/bin"
for cmd in java javac jar; do
  printf '%s\n' "#!/bin/bash" "exec \"$MC_JDK/bin/${cmd}.exe\" \"\$@\"" > "$WRAP/bin/$cmd"
  chmod +x "$WRAP/bin/$cmd"
done
export PATH="$WRAP/bin:$HOME/.local/apache-maven-3.9.9/bin:$PATH"
export JAVA_HOME="$WRAP"
export MAVEN_HOME="$HOME/.local/apache-maven-3.9.9"
MVN="$HOME/.local/apache-maven-3.9.9/bin/mvn"
IC="/mnt/e/Intellij_Idea/pluginTest/MCZJUItemCreator"
PLUGIN="/mnt/e/Intellij_Idea/plugins/MCPlugin"
java -version
JAR="$HOME/.m2/repository/io/mczju/MCZJUItemCreator/1.1.0/MCZJUItemCreator-1.1.0.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Installing MCZJUItemCreator 1.1.0..."
  (cd "$IC" && "$MVN" install -DskipTests -q)
fi
(cd "$PLUGIN" && "$MVN" package -q)
ls -la "$PLUGIN/target/Maggoteers-0.1.0-SNAPSHOT.jar"
