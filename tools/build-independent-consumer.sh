#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Only engine/build infrastructure is transferred. The attachment app is independently authored.
# A source-only mode prepares/verifies closure without starting Gradle.
mode=${1:-build}
if [[ "$mode" != source-only && -f evidence/HOLD_BUILDS ]]; then echo 'Build slot not granted'; exit 75; fi
p=standalone-consumer
cp build.gradle gradle.properties gradlew gradlew.bat LICENSE NOTICE "$p/"
cp -r gradle "$p/"
mkdir -p "$p/engine/src" "$p/provenance"
cp engine/build.gradle "$p/engine/"
cp -r engine/src/main "$p/engine/src/"
cp provenance/LICENSE-telegram-GPL2.txt "$p/provenance/"
python3 - <<'PY'
from pathlib import Path
import hashlib,json
r=Path('.');src=r/'engine';dst=r/'standalone-consumer/engine'
files={str(p.relative_to(src)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(src.rglob('*')) if p.is_file() and '/build/' not in str(p)}
for n,h in files.items():assert hashlib.sha256((dst/n).read_bytes()).hexdigest()==h,n
actual={str(p.relative_to(dst)) for p in dst.rglob('*') if p.is_file() and '/build/' not in str(p)}
assert actual==set(files),(actual-set(files),set(files)-actual)
(r/'standalone-consumer/PINNED-MODULE-SOURCE.json').write_text(json.dumps(files,indent=2)+'\n')
print('PASS independent consumer engine source matches final module; host sources preserved')
PY
if [[ "$mode" == source-only ]]; then exit 0; fi
free=$(df -PB1 . | awk 'NR==2{print $4}')
[[ "$free" -ge 2147483648 ]] || { echo 'Need at least 2GiB free'; exit 75; }
(cd "$p" && ./gradlew --offline --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1g -Dfile.encoding=UTF-8' :app:assembleDebug) > evidence/build-independent-consumer.log 2>&1
tail -18 evidence/build-independent-consumer.log
