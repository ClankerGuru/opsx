---
name: git-workflow
description: Reference guide for the project's Git workflow — branch strategy, commit style, PR lifecycle, rebase vs merge, history rewriting rules, git hooks, stash/worktree/bisect/reflog/cherry-pick, plus the enforced never-push-to-main + CodeRabbit + pre-commit/pre-push hook policy. Activates when creating branches, writing commits, or opening PRs.
---

# Git Workflow

> For related topics see:
> - `/gh-cli` — `gh pr create`, `gh pr merge --auto`, CodeRabbit queries
> - `/github-actions` — CI triggers that gate every PR
> - `/opsx-propose` — change-lifecycle integration with branches

## Project rules (non-negotiable)

1. **Never push directly to `main`.** Always branch + PR.
2. **Never include AI / Claude / Anthropic branding** in commit
   messages, PR titles, PR descriptions, comments, or any content
   pushed to the repo. No "Generated with Claude Code", no
   `Co-Authored-By: Claude`, nothing.
3. **Always branch + PR**, even for a one-line fix.
4. **Wait for CodeRabbit** and address every comment before merge.
5. **Prove it works** — run the feature end-to-end before pushing and
   include evidence (logs, output) in the PR body.
6. **Don't skip hooks with `--no-verify`.** The pre-commit hook runs
   the full build (`./gradlew build`): compile, test, detekt, ktlint,
   coverage. If the hook fails, fix the cause.
7. **Don't force-push to `main`.** Force-push feature branches only
   when the PR still belongs to you and hasn't been reviewed.

## Branch strategy

Trunk-based: one long-lived branch (`main`). All work lives on
short-lived feature branches that merge back via PR and delete on
merge.

```bash
# Feature work
git checkout -b feature/add-retry-logic

# Bug fix
git checkout -b fix/null-pointer-in-sync

# opsx change (lifecycle-tracked)
git checkout -b change/catalog-polish
```

Naming prefixes — project convention:

| Prefix | Use for |
|---|---|
| `feature/` | New functionality |
| `fix/` | Bug fixes |
| `change/` | opsx-tracked changes |
| `chore/` | Dep bumps, tooling |
| `docs/` | Documentation only |
| `refactor/` | Non-behavioural restructuring |

Keep branches short-lived (hours to days). A branch open for two weeks
is a merge-conflict factory — rebase frequently.

## Commit messages

First line under 72 characters, imperative mood, no trailing period.
Body wraps at 72 columns. Explain **why**, not just what.

```text
Add exponential backoff to sync task

Sync was failing silently on transient network errors. Added retry
with backoff (100ms, 200ms, 400ms) and clear error messages when all
retries are exhausted.

Fixes #142
```

Short fixes can be one-liners:

```text
Fix null branch name in checkout task
```

Bad (rejected in review):

```text
Fix stuff                         # vague
WIP                               # not a commit
Update code                       # says nothing
Co-Authored-By: Claude <...>      # forbidden AI attribution
Generated with Claude Code        # forbidden
```

## The PR lifecycle

```bash
# 1. Branch from an up-to-date main
git checkout main
git pull --ff-only origin main
git checkout -b feature/add-retry-logic

# 2. Work, commit. The pre-commit hook runs ./gradlew build.
git add -p                        # stage hunks, not whole files
git commit -m "Add exponential backoff to sync task"

# 3. Push (pre-push hook blocks pushes to main)
git push -u origin feature/add-retry-logic

# 4. PR
gh pr create --title "Add retry logic to sync task" --body "..."

# 5. Let CodeRabbit and CI do their thing
gh pr checks <n> --watch

# 6. Address review feedback; push fixes
git commit --fixup <hash>         # tag follow-up as fixup
git push

# 7. Squash-merge with branch cleanup
gh pr merge <n> --squash --delete-branch
# or wait for checks and auto-merge
gh pr merge <n> --squash --delete-branch --auto
```

`--squash` is the default strategy here — it flattens review-round
commits into a single on-`main` commit that matches the PR title and
body.

## Staging and committing

```bash
git status                           # what's changed
git diff                             # unstaged changes
git diff --staged                    # staged changes
git diff main...HEAD                 # what this branch adds vs main

git add path/file.kt                 # stage a file
git add -p                           # interactive hunk staging
git add -A                           # everything (use carefully)
git restore --staged path/file.kt    # unstage
git restore path/file.kt             # discard unstaged changes (destructive)

git commit -m "..."
git commit --amend                   # replace last commit (only before push)
git commit --fixup <sha>             # fixup pointing at an earlier commit
git commit --no-verify               # SKIP HOOKS — don't
```

## Keeping a branch fresh — rebase, not merge

```bash
# Rebase your branch onto latest main
git fetch origin
git rebase origin/main

# Conflicts: resolve, then
git add <resolved-files>
git rebase --continue
# ...or abort
git rebase --abort
```

Force-push your branch after a rebase (only yours, not shared):

```bash
git push --force-with-lease          # safer: fails if remote moved
# never: git push --force             # overwrites others' work
```

**Don't `git pull` on a feature branch.** It creates a merge commit
from `origin/<branch>` into your local copy. Prefer `git pull --rebase`
or explicit `git fetch` + `git rebase`.

### Interactive rebase — clean up before review

```bash
git rebase -i origin/main
# In the editor:
#   pick abc  feat: first try
#   fixup def fix
#   reword ghi wording
#   drop jkl  accidental log line
```

Commands: `pick`, `reword`, `edit`, `squash` (merge + edit message),
`fixup` (merge silently), `drop`, `exec`.

Fixup autosquash workflow:

```bash
git commit --fixup <old-sha>
git commit --fixup <old-sha>
git rebase -i --autosquash origin/main
```

Interactive rebase is safe only on unshared history. Once pushed and
reviewed, prefer adding new commits.

## Merge vs rebase — when to use which

- **Rebase** your PR branch onto `main` before review and before merge.
  Clean history, linear graph.
- **Merge** happens automatically via `gh pr merge --squash`. The
  squash merge keeps `main` linear.
- **Never rebase a shared/public branch** (anything other contributors
  or CI have based work on).
- **Never merge `main` into your branch** repeatedly — it bloats the
  history with noise merges. Rebase instead.

History-rewriting rules:

- Rewrite your own un-pushed / un-reviewed commits freely.
- Once a PR is open and reviewed, prefer new commits (`--fixup`) over
  force-push; reviewers can see each round.
- Once merged to `main`, history is immutable. Never force-push
  `main`.

## Tag-based releases

```bash
# Option A: tag locally, then create a release
git tag v0.41.0
git push origin v0.41.0
gh release create v0.41.0 --title "v0.41.0" --generate-notes

# Option B: one step (creates tag + release; triggers release.yml)
gh release create v0.41.0 --title "v0.41.0" --generate-notes
```

Tag naming: `v<major>.<minor>.<patch>` (SemVer). The release workflow
strips the `v` and validates SemVer — `v0.41` or `release-41` are
rejected.

Pushing a bare tag does **not** trigger `release.yml` — it triggers on
`release: published`. Always create the release.

## Branch cleanup

```bash
git checkout main
git pull --ff-only origin main

# Local branch merged via --delete-branch gets orphaned locally
git branch -d feature/add-retry-logic         # safe delete (refuses if unmerged)
git branch -D feature/abandoned               # force delete (lost work is gone)

# Prune stale remote refs
git fetch --prune

# List merged branches
git branch --merged main

# Bulk-delete merged locals
git branch --merged main | grep -vE '(^\*|main)' | xargs -r git branch -d
```

## Git hooks (project)

The repo ships hooks under `config/hooks/`. Point git at them:

```bash
git config core.hooksPath config/hooks
```

What they do:

- **pre-commit** — runs `./gradlew build` (compile, test, detekt,
  ktlint, Kover verify). Blocks the commit on any failure.
- **pre-push** — blocks direct pushes to `main`. Forces the PR flow.

Don't disable or bypass. If a hook is slow, fix the build, don't skip
the check.

## Stash — short-lived detours

```bash
git stash push -m "halfway through retry logic"
git stash push --keep-index           # stash only unstaged; tests the index
git stash push -u                     # include untracked files

git stash list
git stash show -p stash@{0}
git stash apply stash@{0}             # apply, keep stash
git stash pop                         # apply + drop
git stash drop stash@{0}
git stash clear                       # nuke all stashes
```

Stash is for quick context switches. For longer parking, make a commit
on a branch and come back to it.

## Worktrees — multiple checkouts of one repo

```bash
# Make a separate directory checked out at a different branch
git worktree add ../hotfix main
git worktree add ../review-42 --detach $(git rev-parse origin/pr/42)

git worktree list
git worktree remove ../hotfix
git worktree prune
```

Worktrees share the same `.git` directory — objects and refs are
shared. Great for reviewing a PR without blowing away your current
state.

## Inspecting history

```bash
git log --oneline --graph --decorate --all
git log --author="slop"
git log --since="2 weeks ago"
git log --grep "retry"                     # match commit message
git log -S "runCatching" -- src/           # "pickaxe": content search
git log --follow path/File.kt              # through renames
git log main..feature/x                    # commits on feature not on main
git log feature/x...main                   # symmetric diff

git show <sha>
git show <sha>:path/file.kt                # file at that revision
git blame path/file.kt -L 100,150
git blame -w -C -C -C path/file.kt         # ignore whitespace, detect moves

git shortlog -sn --all                     # commit counts per author
```

## git bisect — hunt a regression

```bash
git bisect start
git bisect bad HEAD
git bisect good v0.40.0
# git checks out midpoint; run your repro
./repro-script.sh
git bisect bad        # or good
# repeat until git prints the first bad commit
git bisect reset

# Fully automated
git bisect start HEAD v0.40.0
git bisect run ./scripts/repro.sh          # exit 0 good, non-zero bad
```

## git reflog — the recovery tool

```bash
git reflog                                 # every HEAD move
git reflog show feature/x                  # specific branch
git reset --hard HEAD@{3}                  # go back three moves
git checkout HEAD@{1}                      # where I was before last op

# Resurrect a deleted branch
git reflog | grep feature/lost
git branch feature/lost <sha>
```

Almost nothing in git is irrecoverable until `git gc` runs. When in
doubt, check reflog first.

## Cherry-pick — specific commits between branches

```bash
git cherry-pick <sha>
git cherry-pick <sha1>..<sha2>             # range (exclusive..inclusive)
git cherry-pick -x <sha>                   # append "(cherry picked from ...)"
git cherry-pick --continue / --abort / --skip
```

Use for backporting a hotfix to a release branch. Don't abuse for
general branch sync — rebase is cleaner.

## Reset, revert, restore

```bash
# Move HEAD and working tree
git reset --soft <sha>          # HEAD moves; index + working tree keep changes
git reset --mixed <sha>         # default; index reset, working tree kept
git reset --hard <sha>          # nuke everything — destructive

# Undo a published commit by creating a new inverse commit
git revert <sha>

# Discard working-tree changes (post-Git 2.23)
git restore path/file.kt
git restore --staged path/file.kt
git restore --source=<sha> path/file.kt
```

Never `git reset --hard` a branch that's pushed; use `git revert`
instead so collaborators don't lose commits.

## Remote management

```bash
git remote -v
git remote add upstream https://github.com/org/upstream-repo
git remote set-url origin git@github.com:org/repo.git

git fetch --all --prune
git fetch upstream main:upstream-main       # fetch into a local ref
```

For forks: `origin` = your fork, `upstream` = source of truth. Rebase
onto `upstream/main` before opening PRs.

## Config essentials

```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
git config --global init.defaultBranch main
git config --global pull.rebase true           # pull = fetch + rebase
git config --global rebase.autoStash true      # auto-stash on rebase
git config --global rerere.enabled true        # remember conflict resolutions
git config --global diff.colorMoved zebra
git config --global fetch.prune true
git config --global push.default simple
git config --global push.autoSetupRemote true
```

Local (per repo) overrides drop the `--global`.

## Anti-patterns

```bash
# WRONG: pushing to main
git push origin main:main

# WRONG: --no-verify to skip the pre-commit build
git commit --no-verify -m "ship it"

# WRONG: force-push main
git push --force origin main

# WRONG: amend a commit that's already been pushed and reviewed
git commit --amend --no-edit
git push --force-with-lease
# Correct: new commit (git commit --fixup <sha>), push, let reviewers see

# WRONG: merging main into feature branch repeatedly
git merge main                       # noise merges
# Correct: rebase
git rebase origin/main

# WRONG: vague commit messages
git commit -m "stuff"
git commit -m "wip"
git commit -m "fix bug"

# WRONG: AI / Claude attribution anywhere
git commit -m "fix(something)
Co-Authored-By: Claude <...>"

# WRONG: merging before CodeRabbit has reviewed
gh pr merge 42 --squash
# Correct: wait, address comments, then --auto

# WRONG: pushing without running the feature
# PR body: "works on my machine"
# Correct: run end-to-end, attach output to the PR
```

## Common pitfalls

- **Detached HEAD after `git checkout <sha>`** — make a branch before
  committing, or the commits are reachable only via reflog.
- **`git pull` creates a merge commit** — set `pull.rebase true` or
  use `git pull --rebase`.
- **`git push --force` vs `--force-with-lease`** — always prefer the
  latter. It refuses the push if the remote moved, preventing
  silent overwrites of others' commits.
- **Line-ending churn on Windows/macOS** — set `core.autocrlf = input`
  on Windows and commit a `.gitattributes` for text files.
- **Large files sneaking in** — once pushed, they stay in history.
  Audit `git log --stat` before merging; use `git lfs` for anything
  >10 MB.
- **Merge conflict markers committed** — grep for `<<<<<<<` pre-commit
  (the project's hook runs a full build which compiles Kotlin and
  catches this, but YAML/Markdown slip through).
- **Rebased a shared branch, broke co-workers** — never rebase a branch
  someone else has checked out. Communicate before rebasing review
  feedback if others are collaborating on the branch.

## References

- Pro Git book — https://git-scm.com/book/en/v2
- Reference — https://git-scm.com/docs
- Workflows — https://git-scm.com/docs/gitworkflows
- Conventional commits (optional reading) — https://www.conventionalcommits.org/
- GitHub flow — https://docs.github.com/en/get-started/using-github/github-flow
