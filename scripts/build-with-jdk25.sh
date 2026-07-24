#!/usr/bin/env bash
set -euo pipefail
MC_JDK="/mnt/c/Users/${USER}/AppData/Roaming/.minecraft/runtime/java-runtime-epsilon"
if [[ ! -f "$MC_JDK/bin/java.exe" ]]; then
  MC_JDK="/mnt/c/Users/Chalcanthite/AppData/Roaming/.minecraft/runtime/java-runtime-epsilon"
fi
WRAP="$HOME/.local/jdk25-wrap"
mkdir -p "$WRAP/bin"
for cmd in java javac jar javadoc; do
  if [[ -f "$MC_JDK/bin/${cmd}.exe" ]]; then
    printf '%s\n' "#!/bin/bash" "exec \"$MC_JDK/bin/${cmd}.exe\" \"\$@\"" > "$WRAP/bin/$cmd"
    chmod +x "$WRAP/bin/$cmd"
  fi
done
export JAVA_HOME="$WRAP"
export PATH="$WRAP/bin:$PATH"
MVN="${MVN:-$HOME/.local/apache-maven-3.9.9/bin/mvn}"
cd "$(dirname "$0")/.."
java -version
"$MVN" "$@"
