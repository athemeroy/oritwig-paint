#!/usr/bin/env python3
"""Offline integrity checks for the reviewed source-module snapshot; no Android build."""
from pathlib import Path
import hashlib
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
LOCK_SHA256 = 'cedfd27eff91326c4fbb3aea684d78b5b68f95db64b246514a815123d0a9da72'
IGNORED_DIRS = {'.git', '.gradle', 'build', 'evidence', '__pycache__', '.idea'}
BANNED_SUFFIXES = {'.apk', '.aab', '.aar', '.so', '.jks', '.keystore', '.hprof', '.pem', '.p12', '.pfx', '.ttf', '.otf', '.dex', '.class'}
LEDGER_PATH = 'provenance/authored-ledger.json'


def require(ok, message):
    if not ok:
        raise ValueError(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checked_path(name):
    path = Path(name)
    require(not path.is_absolute() and '..' not in path.parts, 'Unsafe manifest path: ' + name)
    result = ROOT / path
    require(result.is_file() and not result.is_symlink(), 'Missing or linked file: ' + name)
    return result


def source_files(directory):
    return {p.relative_to(directory).as_posix() for p in directory.rglob('*')
            if p.is_file() and not (set(p.relative_to(directory).parts) & IGNORED_DIRS)}


def main():
    lock_file = checked_path('provenance/module-source-lock.json')
    require(digest(lock_file) == LOCK_SHA256, 'Reviewed module lock changed')
    engine_lock = json.loads(lock_file.read_text())
    require(len(engine_lock) == 37, 'Unexpected reviewed engine input count')
    require(source_files(ROOT / 'engine') == {n.removeprefix('engine/') for n in engine_lock}, 'Root engine file closure changed')
    for name, sha in engine_lock.items():
        require(digest(checked_path(name)) == sha, 'Root engine mismatch: ' + name)
        require(digest(checked_path('standalone-consumer/' + name)) == sha, 'Independent engine mismatch: ' + name)
    independent_lock = json.loads(checked_path('standalone-consumer/PINNED-MODULE-SOURCE.json').read_text())
    require(independent_lock == {n.removeprefix('engine/'): sha for n, sha in engine_lock.items()}, 'Independent engine pin differs')
    require(source_files(ROOT / 'standalone-consumer/engine') == set(independent_lock), 'Independent engine closure changed')

    upstream_ledger = json.loads(checked_path('provenance/source-ledger.json').read_text())
    require(upstream_ledger['telegram_commit'] == 'f2908b14133bbffbf7ab04f641ecb5bfaf533242', 'Upstream pin changed')
    for entry in upstream_ledger['files']:
        require(entry['sha256'] == digest(checked_path(entry['module_path'])), 'Per-file source ledger mismatch: ' + entry['module_path'])

    authored = json.loads(checked_path(LEDGER_PATH).read_text())
    public_files = source_files(ROOT)
    expected = set(authored['files']) | set(engine_lock) | {'standalone-consumer/' + n for n in engine_lock} | {LEDGER_PATH}
    require(public_files == expected, 'Public file closure changed: ' + str(sorted(public_files ^ expected)))
    for name, entry in authored['files'].items():
        path = checked_path(name)
        require(digest(path) == entry['sha256'], 'Authored/tooling/media hash mismatch: ' + name)
        require(path.stat().st_size == entry['bytes'], 'File size mismatch: ' + name)
    for name in public_files:
        p = checked_path(name)
        require(p.suffix.lower() not in BANNED_SUFFIXES, 'Forbidden source-repository payload: ' + name)
        require(not (set(p.relative_to(ROOT).parts) & {'upstream-private', 'delivery', 'artifacts'}), 'Private/artifact directory: ' + name)
        require(p.name not in {'local.properties', '.env'} and not p.name.startswith('.env.'), 'Local configuration: ' + name)
        if p.suffix == '.jar':
            require(name in {'gradle/wrapper/gradle-wrapper.jar', 'standalone-consumer/gradle/wrapper/gradle-wrapper.jar'}, 'Unexpected binary: ' + name)

    for name in ('engine/src/main/AndroidManifest.xml', 'smoke-host/src/main/AndroidManifest.xml', 'standalone-consumer/app/src/main/AndroidManifest.xml'):
        require('uses-permission' not in checked_path(name).read_text(), 'Permission added: ' + name)
    for name in ('smoke-host/src/main/java/dev/oritwig/markup/proof/AnnotationEditor.java',
                 'smoke-host/src/main/java/dev/oritwig/markup/proof/MainActivity.java',
                 'standalone-consumer/app/src/main/java/dev/oritwig/markup/consumer/AnnotationEditor.java',
                 'standalone-consumer/app/src/main/java/dev/oritwig/markup/consumer/MainActivity.java'):
        source = checked_path(name).read_text()
        require('import dev.oritwig.markup.core.' not in source, 'Ordinary host imports core: ' + name)

    for name in ('gradle/wrapper/gradle-wrapper.properties', 'standalone-consumer/gradle/wrapper/gradle-wrapper.properties'):
        require('distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6' in checked_path(name).read_text(), 'Gradle distribution checksum changed')
    for name in public_files:
        if not name.endswith('.md'):
            continue
        path = checked_path(name)
        for link in re.findall(r'!?\[[^\]]*\]\(([^)]+)\)', path.read_text()):
            if re.match(r'^[a-zA-Z][a-zA-Z0-9+.-]*:', link) or link.startswith('#'):
                continue
            target = (path.parent / link.split('#', 1)[0]).resolve()
            require(target.is_relative_to(ROOT) and target.exists(), 'Broken local link in ' + name + ': ' + link)

    artifacts = json.loads(checked_path('provenance/tested-artifacts.json').read_text())
    require(artifacts['source_lock_sha256'] == LOCK_SHA256, 'Recorded artifact source lock mismatch')
    require(digest(checked_path('smoke-host/src/main/java/dev/oritwig/markup/proof/WorkflowFixtureActivity.java')) == artifacts['fixture_source_sha256'], 'Recorded fixture differs')
    require(artifacts['fixture'] == {'passed': 272, 'failed': 0, 'unsupported': 2, 'pixel_tolerance': 0}, 'Recorded fixture scope differs')
    print('PASS 37 locked engine inputs, exact independent copy, authored/tooling/media hashes, source boundary and local document links')
    print('Source integrity only: this does not build Android or rerun recorded runtime checks.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, json.JSONDecodeError) as error:
        print('FAIL:', error, file=sys.stderr)
        sys.exit(1)
