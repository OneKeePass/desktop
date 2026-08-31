#!/usr/bin/env python3
# Generates a flatpak-builder sources file for the Maven artifacts that
# shadow-cljs needs, so the ClojureScript build can run with no network.
#
# There is no upstream flatpak-builder-tools generator for Maven, and
# src-cljs/.shadow-cljs/classpath.edn is not a substitute: it lists only the
# jars that end up on the classpath, while offline resolution also needs every
# parent/BOM pom Aether walks to rebuild the dependency graph.
#
# The list is produced by actually resolving into an empty local repository,
# so it is complete by construction rather than by inspection. The real
# src-cljs tree is never touched: shadow-cljs.edn is copied into a scratch
# project directory and resolved there with HOME pointed at scratch too.

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

# Repository ids as shadow-cljs defines them (shadow.cljs.npm.deps merges these
# defaults with :maven {:repositories ...} from shadow-cljs.edn). The ids are
# what Aether records in the _remote.repositories tracking files.
REPOSITORY_URLS = {
    "central": "https://repo1.maven.org/maven2",
    "maven-central": "https://repo1.maven.org/maven2",
    "clojars": "https://repo.clojars.org",
}

# Aether bookkeeping that must not be vendored: these record failed or
# time-based resolution state and would make the build non-reproducible.
SKIPPED_SUFFIXES = (".lastUpdated", ".part", ".sha1", ".md5")
SKIPPED_NAMES = ("resolver-status.properties", "_remote.repositories")


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def resolve_into_scratch(src_cljs, shadow_cljs_bin, scratch):
    # A pristine project dir means no classpath.edn cache to short-circuit
    # resolution, and an explicit :local-repo means an empty repository to
    # resolve into. HOME cannot be used for this: the JVM takes user.home from
    # the OS rather than the environment on some platforms, so setting HOME
    # would silently resolve against the developer's own ~/.m2 and produce a
    # list that only looks complete. The Flatpak build sets :local-repo the
    # same way, for the same reason.
    local_repo = scratch / ".m2" / "repository"
    project = scratch / "project"
    project.mkdir(parents=True)

    config = (src_cljs / "shadow-cljs.edn").read_text()
    if not config.lstrip().startswith("{"):
        sys.exit("shadow-cljs.edn does not start with a map")
    config = config.replace("{", '{:maven {:local-repo "%s"}' % local_repo, 1)
    (project / "shadow-cljs.edn").write_text(config)

    env = dict(os.environ)

    print(f"resolving into {local_repo} ...", file=sys.stderr)
    result = subprocess.run(
        [str(shadow_cljs_bin), "classpath"],
        cwd=project,
        env=env,
        stdout=subprocess.PIPE,
        stderr=None,
        text=True,
    )
    if result.returncode != 0:
        sys.exit(f"shadow-cljs classpath failed with exit code {result.returncode}")

    if not local_repo.is_dir():
        sys.exit(f"nothing was resolved into {local_repo}")
    return local_repo


def repository_id_for(artifact, tracking):
    # _remote.repositories maps each file in the directory to the repository it
    # came from, e.g. "clojure-1.12.1.jar>central=". Reading it is what lets us
    # rebuild the download URL without guessing which repo served the artifact.
    if not tracking.exists():
        return None
    pattern = re.compile(r"^" + re.escape(artifact.name) + r">([^=]*)=", re.MULTILINE)
    match = pattern.search(tracking.read_text())
    return match.group(1) if match else None


def tracking_contents(tracking):
    # Aether writes a timestamp comment into every tracking file; dropping the
    # comments keeps the generated sources stable across regenerations.
    lines = [
        line
        for line in tracking.read_text().splitlines()
        if line and not line.startswith("#")
    ]
    return "\n".join(sorted(lines)) + "\n"


def collect_sources(local_repo):
    sources = []
    jars = []
    for path in sorted(local_repo.rglob("*")):
        if not path.is_file():
            continue
        if path.name in SKIPPED_NAMES or path.name.endswith(SKIPPED_SUFFIXES):
            continue

        relative = path.relative_to(local_repo)
        tracking = path.parent / "_remote.repositories"
        repository_id = repository_id_for(path, tracking)
        if repository_id is None:
            sys.exit(f"no repository recorded for {relative} in {tracking}")
        if repository_id not in REPOSITORY_URLS:
            sys.exit(f"unknown repository id {repository_id!r} for {relative}")

        sources.append(
            {
                "type": "file",
                "url": f"{REPOSITORY_URLS[repository_id]}/{relative.as_posix()}",
                "sha256": sha256_of(path),
                "dest": f".m2/repository/{relative.parent.as_posix()}",
                "dest-filename": path.name,
            }
        )
        if path.suffix == ".jar":
            jars.append(relative.as_posix())

    # The tracking files go in too. Aether's enhanced local repository manager
    # consults them, and an artifact it cannot confirm came from a configured
    # repository is re-fetched - which is exactly what must not happen offline.
    for tracking in sorted(local_repo.rglob("_remote.repositories")):
        relative = tracking.relative_to(local_repo)
        sources.append(
            {
                "type": "inline",
                "contents": tracking_contents(tracking),
                "dest": f".m2/repository/{relative.parent.as_posix()}",
                "dest-filename": "_remote.repositories",
            }
        )

    return sources, jars


def cross_check_classpath(src_cljs, jars):
    # classpath.edn is the list shadow-cljs actually put on the classpath last
    # time it ran on this machine. Every one of those jars must appear here, or
    # the vendored repository is short of what the build will ask for.
    classpath_edn = src_cljs / ".shadow-cljs" / "classpath.edn"
    if not classpath_edn.exists():
        print("no classpath.edn to cross-check against", file=sys.stderr)
        return

    expected = set()
    for jar in re.findall(r'"([^"]*\.jar)"', classpath_edn.read_text()):
        marker = "/.m2/repository/"
        if marker in jar:
            expected.add(jar.split(marker, 1)[1])

    missing = expected - set(jars)
    print(
        f"cross-check: {len(expected)} jars on the recorded classpath, "
        f"{len(jars)} jars vendored, {len(missing)} missing",
        file=sys.stderr,
    )
    for jar in sorted(missing):
        print(f"  MISSING {jar}", file=sys.stderr)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--src-cljs",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "src-cljs",
        help="the shadow-cljs project directory",
    )
    parser.add_argument(
        "--shadow-cljs",
        type=Path,
        default=Path(__file__).resolve().parent.parent
        / "node_modules"
        / ".bin"
        / "shadow-cljs",
        help="the shadow-cljs launcher to resolve with",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=Path(__file__).resolve().parent / "maven-sources.json",
    )
    parser.add_argument(
        "--keep-scratch",
        action="store_true",
        help="leave the scratch resolve directory in place for inspection",
    )
    args = parser.parse_args()

    if not args.shadow_cljs.exists():
        sys.exit(f"{args.shadow_cljs} not found - run yarn install first")

    scratch = Path(tempfile.mkdtemp(prefix="flatpak-maven-"))
    try:
        local_repo = resolve_into_scratch(args.src_cljs, args.shadow_cljs, scratch)
        sources, jars = collect_sources(local_repo)
        cross_check_classpath(args.src_cljs, jars)

        args.output.write_text(json.dumps(sources, indent=4) + "\n")
        files = sum(1 for s in sources if s["type"] == "file")
        print(
            f"wrote {args.output} - {files} artifacts "
            f"({len(jars)} jars), {len(sources) - files} tracking files",
            file=sys.stderr,
        )
    finally:
        if args.keep_scratch:
            print(f"scratch kept at {scratch}", file=sys.stderr)
        else:
            shutil.rmtree(scratch, ignore_errors=True)


if __name__ == "__main__":
    main()
