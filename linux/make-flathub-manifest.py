#!/usr/bin/env python3
"""Derive the Flathub manifest from the in-repo one.

The two differ in exactly one way. Here, each module reaches into the working tree
with `type: dir` / `type: file` sources; on Flathub the same trees arrive as one
`type: git` source pinned to a tag. Everything else -- modules, build commands,
finish-args, the four generated source lists -- is identical, so deriving one from
the other is what keeps them from drifting.

The transform is textual on purpose: the manifest's comments carry most of what was
learned building it, and a YAML round-trip would throw them away. Comment lines
starting with `##` are repo-only -- development history, dates, `just` recipes, source
line references -- and are dropped here. Plain `#` comments justify a permission or a
non-obvious build step, which is what a Flathub reviewer reads, so they stay.

    python3 linux/make-flathub-manifest.py --tag v0.25.1 --out-dir /tmp/flathub-pr

Writes the manifest plus flathub.json and the generated source lists into --out-dir,
laid out as the Flathub repo expects.

Run it with the tag checked out. The manifest and source lists come from the working
tree while the output pins the tag, so the script refuses unless HEAD is at the tag and
those files have no uncommitted changes.
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

REPO_URL = "https://github.com/OneKeePass/desktop.git"

# Read relative to the manifest's own repo, so they are copied into the Flathub repo
# rather than arriving with the git source.
GENERATED_LISTS = [
    "cargo-sources.json",
    "proxy-cargo-sources.json",
    "node-sources.json",
    "maven-sources.json",
]

HEADER = """\
# OneKeePass Flatpak manifest.
#
# Generated from com.onekeepass.OneKeePass.yml in the app repo by
# linux/make-flathub-manifest.py. Edit it there, not here.
#
# The application, proxy and ClojureScript frontend are built from source offline.
#
# The four linux/*-sources.json lists live in THIS repo, not in the app repo, because
# a source list is read relative to the manifest's own repo. Regenerate them there
# whenever a lockfile changes and copy them across.

"""


def git_source(tag: str, commit: str) -> str:
    return (
        "      - type: git\n"
        f"        url: {REPO_URL}\n"
        f"        tag: {tag}\n"
        # Pinned alongside the tag so a moved tag cannot silently change the build.
        f"        commit: {commit}\n"
    )


def split_entries(block: str):
    """Split a sources: block into chunks of (leading comments + one entry).

    A comment block sits above the entry it describes, so it belongs to the entry that
    FOLLOWS it -- otherwise dropping a source takes the next source's comment with it.
    """
    chunks, current, pending = [], [], []
    for line in block.splitlines(keepends=True):
        if re.match(r"^      - ", line):
            if current:
                chunks.append("".join(current))
            current, pending = pending + [line], []
        elif not current or not line.strip() or line.lstrip().startswith("#"):
            pending.append(line)
        else:
            current.extend(pending)
            pending = []
            current.append(line)
    if current:
        chunks.append("".join(current))
    if pending:
        chunks.append("".join(pending))
    return chunks


def is_worktree_source(chunk: str) -> bool:
    """True for a source that reaches into the working tree -- what the git source replaces."""
    entry = "".join(ln for ln in chunk.splitlines(keepends=True) if not ln.lstrip().startswith("#"))
    return bool(re.search(r"^      - type: (dir|file)$", entry, re.MULTILINE))


def strip_repo_only_comments(text: str) -> str:
    """Drop '##' lines, and any blank line a dropped block leaves stranded."""
    out = []
    for line in text.splitlines(keepends=True):
        if line.lstrip().startswith("##"):
            continue
        # A block that was entirely '##' can leave two blank lines where there was one.
        if not line.strip() and out and not out[-1].strip():
            continue
        out.append(line)
    return "".join(out)


def transform(text: str, tag: str, commit: str) -> str:
    # Replace the header: its build/run instructions and its note about why the
    # manifest sits at the repo root are about the in-repo copy only.
    body = re.sub(r"\A(#[^\n]*\n|\n)+", "", text)
    out, pos = [HEADER], 0

    # Each module's sources: block runs until that module's build-commands:.
    for m in re.finditer(r"^    sources:\n(.*?)(?=^    build-commands:)", body, re.MULTILINE | re.DOTALL):
        out.append(body[pos:m.start()])
        kept = [c for c in split_entries(m.group(1)) if not is_worktree_source(c)]
        out.append("    sources:\n" + git_source(tag, commit) + "".join(kept))
        pos = m.end()
    out.append(body[pos:])
    return strip_repo_only_comments("".join(out))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--manifest", default="com.onekeepass.OneKeePass.yml")
    args = ap.parse_args()

    repo = Path(args.manifest).resolve().parent

    def git(*a):
        return subprocess.run(["git", "-C", str(repo), *a], capture_output=True, text=True)

    # An annotated tag names a tag object, not a commit. flatpak-builder's commit: must
    # be the commit, so peel it; the ref SHA is what identifies the tag on the remote.
    ref = git("rev-parse", "--verify", "--quiet", args.tag).stdout.strip()
    commit = git("rev-parse", "--verify", "--quiet", args.tag + "^{commit}").stdout.strip()
    if not ref or not commit:
        print(f"error: tag {args.tag} not found. Tag and push before generating.", file=sys.stderr)
        return 1

    # A tag that is not on the remote yet is a failed Flathub build, not a local error,
    # so it is worth catching here rather than in review.
    remote = dict(
        reversed(ln.split("\t"))
        for ln in git("ls-remote", "origin", f"refs/tags/{args.tag}*").stdout.splitlines() if ln
    )
    if remote.get(f"refs/tags/{args.tag}") != ref:
        print(f"error: {args.tag} is not pushed to origin (or points elsewhere there).", file=sys.stderr)
        return 1

    # The manifest and the source lists are read from the working tree, but the git
    # source pins the tag. When the two disagree -- a checkout of a newer release, or
    # uncommitted edits -- the lists no longer describe the source Flathub fetches, and
    # its offline build fails on dependencies the VM build never saw.
    head = git("rev-parse", "--verify", "--quiet", "HEAD").stdout.strip()
    if head != commit:
        print(
            f"error: HEAD ({head[:12]}) is not at {args.tag} ({commit[:12]}).\n"
            f"  Check out the tag:        git switch --detach {args.tag}\n"
            f"  or use a worktree:        git worktree add ../okp-{args.tag} {args.tag}\n"
            f"                            and pass --manifest ../okp-{args.tag}/{Path(args.manifest).name}",
            file=sys.stderr,
        )
        return 1

    dirty = git(
        "status", "--porcelain", "--", Path(args.manifest).name, *(f"linux/{n}" for n in GENERATED_LISTS)
    ).stdout
    if dirty:
        print(
            f"error: uncommitted changes to files this script reads:\n{dirty}"
            f"  Commit them and cut a new tag, or discard them.",
            file=sys.stderr,
        )
        return 1

    out_dir = Path(args.out_dir)
    (out_dir / "linux").mkdir(parents=True, exist_ok=True)

    manifest = transform(Path(args.manifest).read_text(), args.tag, commit)
    (out_dir / Path(args.manifest).name).write_text(manifest)

    # x86_64 only: the botan build and the vendored crate set are generated for it,
    # and we have no way to test aarch64.
    (out_dir / "flathub.json").write_text(json.dumps({"only-arches": ["x86_64"]}, indent=4) + "\n")

    for name in GENERATED_LISTS:
        # The upstream cargo and node generators write no trailing newline. Add one so
        # git does not report "\ No newline at end of file" on every regeneration.
        text = (repo / "linux" / name).read_text()
        (out_dir / "linux" / name).write_text(text if text.endswith("\n") else text + "\n")

    print(f"{args.tag} -> {commit}")
    for p in sorted(out_dir.rglob("*")):
        if p.is_file():
            print(f"  {p.relative_to(out_dir)}  ({p.stat().st_size:,} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
