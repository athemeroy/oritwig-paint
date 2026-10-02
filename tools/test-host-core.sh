#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p evidence/host-classes
javac -d evidence/host-classes $(find tests/host -name '*.java') engine/src/main/java/dev/oritwig/markup/core/{Point,RenderState,Slice,UndoStore}.java
java -cp evidence/host-classes dev.oritwig.markup.core.CoreHostTest | tee evidence/host-tests.log
