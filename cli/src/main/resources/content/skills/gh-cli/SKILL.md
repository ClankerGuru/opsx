---
name: gh-cli
description: Reference guide for the GitHub CLI (gh) covering auth, repos, PRs (create/list/view/checks/review/merge), issues, releases, Actions (run), gh api for REST+GraphQL with -f/-F/-H and pagination, workflows, secrets, gists, extensions, JSON output with --jq, plus this project's CodeRabbit + auto-merge conventions. Activates when operating GitHub from the shell.
---

# GitHub CLI (gh)

> For related topics see:
> - `/git-workflow` — branch strategy, commit conventions
> - `/github-actions` — workflow file authoring and CI
> - `/opsx-status` — PR integration with the change lifecycle

## When to reach for this skill

- Creating, reviewing, merging PRs without leaving the terminal.
- Checking CI status, tailing logs, rerunning failed runs.
- Cutting releases and uploading assets.
- Querying arbitrary GitHub state via `gh api` (REST) or GraphQL.
- Scripting repo / workflow / secret management in shell.
- Wiring `gh` calls into local tooling, hooks, and CI jobs.

## Rules

- **Always `gh`, never raw `curl`** — `gh api` handles auth, base URL,
  hostname, rate-limit retries, and pagination.
- **Never force-merge** — pass `--auto` so the PR merges when required
  checks pass. If checks fail, fix them; do not bypass.
- **Wait for CodeRabbit** — let the bot review and address every comment
  before merge.
- **`--delete-branch` on merge** — keeps the remote clean.
- **Repo slug** — under orgs use `<org>/<repo>` (e.g. `my-org/my-repo`);
  pass to any command via `-R <slug>` / `--repo <slug>`.
- **Don't poll with `sleep`** — use `gh run watch`, `gh pr checks
  --watch`, `gh pr merge --auto`.

## Installation and auth

```bash
brew install gh                              # macOS
# or see https://cli.github.com/ for Linux / Windows packages

gh auth login                                # interactive browser flow
gh auth login --with-token < token.txt       # non-interactive (CI)
gh auth status                               # who am I?
gh auth refresh -s repo,read:org             # add scopes
gh auth token                                # print current token
gh auth setup-git                            # teach git to use gh for HTTPS
```

For GitHub Enterprise:

```bash
gh auth login --hostname github.my-corp.com
gh api --hostname github.my-corp.com repos/org/repo
```

`GH_TOKEN` and `GITHUB_TOKEN` environment variables override saved creds.

## Top-level command map

| Group | Purpose |
|---|---|
| `gh auth` | Login, tokens, scopes, SSH keys |
| `gh repo` | Create/clone/fork/view/edit/archive/delete repos |
| `gh pr` | Pull requests — create, list, view, checks, review, merge |
| `gh issue` | Issues — create, list, view, comment, transfer, pin |
| `gh release` | Releases + asset upload/download |
| `gh run` | Workflow runs — list, view, watch, rerun, cancel |
| `gh workflow` | Workflow files — list, view, enable, disable, run |
| `gh api` | Arbitrary REST or GraphQL |
| `gh gist` | Gists |
| `gh browse` | Open the right repo page in a browser |
| `gh secret` | Repository / environment / org secrets |
| `gh variable` | Repository / environment / org variables |
| `gh label` | Manage labels |
| `gh project` | Projects V2 |
| `gh codespace` | Codespaces |
| `gh search` | Search code, issues, PRs, commits |
| `gh extension` | Install/update/remove `gh`-extensions |
| `gh attestation` | Sigstore artifact attestations |

## Pull requests

### Create

```bash
gh pr create --title "..." --body "..."
gh pr create --fill                # title + body from commits
gh pr create --draft               # draft PR
gh pr create --base main --head feature-x
gh pr create --reviewer alice,bob --assignee alice --label bug
gh pr create --template .github/pull_request_template.md
```

Heredoc body for formatted Markdown:

```bash
gh pr create --title "Add retry logic" --body "$(cat <<'EOF'
## Summary
- Exponential backoff on sync failures
- Tests for retry behavior

## Test plan
- [ ] ./gradlew build
- [ ] Verify retries on network error
EOF
)"
```

### List and view

```bash
gh pr list                                 # open PRs in current repo
gh pr list --state all --author @me
gh pr list --label bug --search "in:title retry"
gh pr list --limit 100

gh pr view 42                              # summary
gh pr view 42 --web                        # open in browser
gh pr view 42 --comments                   # include comment thread
gh pr view 42 --json state,author,reviewDecision,checks
```

`--json <fields>` returns machine-readable JSON. Combine with `--jq`:

```bash
gh pr view 42 --json reviews \
  --jq '.reviews[] | select(.author.login == "coderabbitai")'

gh pr view 42 --json reviewDecision --jq .reviewDecision
gh pr view 42 --json statusCheckRollup \
  --jq '.statusCheckRollup[] | select(.conclusion != "SUCCESS")'
```

Discover available fields with `--json` alone:

```bash
gh pr view --json      # prints the full list
```

### Checks

```bash
gh pr checks 42                # snapshot of all checks
gh pr checks 42 --watch        # poll until done; exits non-zero on fail
gh pr checks 42 --required     # only required checks
```

### Checkout, diff, review

```bash
gh pr checkout 42              # fetch and check out locally
gh pr diff 42                  # unified diff
gh pr diff 42 --name-only

gh pr review 42 --approve
gh pr review 42 --request-changes --body "Fix the failing test"
gh pr review 42 --comment --body "Nit: rename this var"
gh pr edit 42 --add-reviewer alice --remove-reviewer bob
```

### Merge

```bash
gh pr merge 42 --squash --delete-branch
gh pr merge 42 --merge --delete-branch         # merge commit
gh pr merge 42 --rebase --delete-branch

gh pr merge 42 --squash --delete-branch --auto # merge when checks pass
gh pr merge 42 --admin                          # bypass protection (do not)
```

### Lifecycle

```bash
gh pr ready 42                  # mark a draft ready for review
gh pr close 42
gh pr reopen 42
gh pr comment 42 --body "Rebased on main"
```

### CodeRabbit workflow

```bash
# Has CodeRabbit posted a review yet?
gh pr view 42 --json reviews \
  --jq '[.reviews[] | select(.author.login=="coderabbitai")] | length'

# Dump CodeRabbit comment bodies
gh api "repos/{owner}/{repo}/pulls/42/comments" \
  --jq '.[] | select(.user.login=="coderabbitai") | .body'

# Overall review decision (APPROVED, CHANGES_REQUESTED, REVIEW_REQUIRED)
gh pr view 42 --json reviewDecision --jq .reviewDecision
```

Wait for all checks and auto-merge once CodeRabbit approves:

```bash
gh pr checks 42 --watch \
  && gh pr merge 42 --squash --delete-branch
```

## Issues

```bash
gh issue create --title "..." --body "..." --label bug --assignee alice
gh issue list --state open --label bug
gh issue list --search "in:title flaky test"
gh issue view 17
gh issue comment 17 --body "Repro on macOS 14"
gh issue close 17 --reason "not planned"
gh issue reopen 17
gh issue edit 17 --add-label regression --remove-label triage
gh issue transfer 17 org/other-repo
gh issue pin 17
gh issue develop 17 --checkout     # branch from an issue
```

## Releases

```bash
gh release create v0.41.0 --title "v0.41.0" --generate-notes
gh release create v0.41.0 --notes-file CHANGELOG.md
gh release create v0.41.0 --draft --prerelease

# Upload assets at create time
gh release create v0.41.0 ./dist/*.tar.gz ./dist/*.sha256

# Upload later
gh release upload v0.41.0 ./dist/extra.zip --clobber

gh release list
gh release view v0.41.0
gh release view v0.41.0 --json assets --jq '.assets[].name'
gh release download v0.41.0 --pattern '*.tar.gz'
gh release delete v0.41.0 --yes --cleanup-tag
```

Creating a release triggers any workflow listening for
`release: { types: [published] }`.

## GitHub Actions

### Runs

```bash
gh run list                               # recent runs
gh run list --workflow=build.yml --branch main --limit 20
gh run list --status failure --event pull_request
gh run list --json databaseId,name,status,conclusion,headBranch

gh run view 12345678                      # summary
gh run view 12345678 --log                # all logs
gh run view 12345678 --log-failed         # only failed step logs
gh run view 12345678 --job 98765          # a single job

gh run watch 12345678                     # tail until completion
gh run watch 12345678 --exit-status       # non-zero if the run failed

gh run cancel 12345678
gh run rerun 12345678                     # re-run all jobs
gh run rerun 12345678 --failed            # only failed jobs
gh run rerun 12345678 --job 98765

gh run download 12345678                  # artifacts
gh run download 12345678 --name coverage  # single artifact

gh run delete 12345678
```

### Workflow files

```bash
gh workflow list
gh workflow view build.yml
gh workflow view build.yml --yaml
gh workflow run build.yml                         # workflow_dispatch
gh workflow run build.yml -f version=1.2.3 --ref main
gh workflow enable build.yml
gh workflow disable build.yml
```

## gh api — REST and GraphQL

### REST

Default method is `GET`; sending fields flips it to `POST`.

```bash
gh api repos/{owner}/{repo}                         # repo info
gh api repos/{owner}/{repo}/branches/main/protection
gh api repos/{owner}/{repo}/pulls/42/comments --jq '.[].body'
gh api user
gh api orgs/{org}/members --paginate                # follow Link headers
gh api -X DELETE repos/{owner}/{repo}/issues/comments/98765
```

Parameter flags:

- `-f key=value` — raw string (always a string, even if it looks like an int).
- `-F key=value` — typed: `true`/`false` → bool, digits → int, `null` → null,
  `@file` → file contents.
- `-F key[]=a -F key[]=b` — arrays.
- `-H 'Accept: ...'` — custom headers.

```bash
gh api -X POST repos/{owner}/{repo}/issues \
  -f title="Bug: ..." \
  -f body="Repro steps" \
  -F labels[]=bug -F labels[]=triage

gh api repos/{owner}/{repo}/contents/README.md \
  -H 'Accept: application/vnd.github.raw'

gh api repos/{owner}/{repo}/actions/runs --paginate --slurp
```

`--paginate` walks all pages automatically (uses Link headers on REST,
`pageInfo.endCursor` on GraphQL). `--slurp` wraps paged JSON arrays into
a single top-level array.

`--template '{{range .}}...{{end}}'` formats output with Go templates;
`--jq '<expr>'` runs a jq query.

### GraphQL

Pass `graphql` as the endpoint. Every field besides `query` and
`operationName` becomes a GraphQL variable.

```bash
gh api graphql -f query='
  query($owner: String!, $repo: String!) {
    repository(owner: $owner, name: $repo) {
      pullRequests(first: 20, states: OPEN) {
        nodes { number title author { login } }
      }
    }
  }
' -f owner=my-org -f repo=my-repo --jq '.data.repository.pullRequests.nodes'
```

For paginated GraphQL queries, include `pageInfo { hasNextPage
endCursor }` and accept `$endCursor: String`; `--paginate` handles the
rest.

## Repositories

```bash
gh repo create my-org/new-repo --private --clone --add-readme
gh repo create --template my-org/template-repo my-org/from-template
gh repo clone my-org/my-repo
gh repo fork my-org/my-repo --clone
gh repo view my-org/my-repo
gh repo view my-org/my-repo --json defaultBranchRef,visibility,isArchived
gh repo edit my-org/my-repo --description "..." --homepage "https://..."
gh repo edit my-org/my-repo --enable-issues=false
gh repo archive my-org/my-repo --yes
gh repo delete my-org/my-repo --yes
gh repo set-default my-org/my-repo
gh repo sync                                    # pull upstream into fork
```

## Secrets and variables

```bash
gh secret list
gh secret set MY_KEY --body "value"
gh secret set MY_KEY < key.pem                  # from file / stdin
gh secret set MY_KEY --env production           # environment-scoped
gh secret set MY_KEY --org my-org --visibility all
gh secret delete MY_KEY

gh variable list
gh variable set MY_VAR --body "value"
gh variable delete MY_VAR
```

Secrets are encrypted; `gh secret list` shows names only.

## Gists

```bash
gh gist create notes.md --public --desc "..."
gh gist create --filename output.log < some.log
gh gist list
gh gist view <id>
gh gist edit <id>
gh gist clone <id>
gh gist delete <id>
```

## Browse and search

```bash
gh browse                             # current repo in browser
gh browse 42                          # PR/issue 42
gh browse --commit HEAD
gh browse src/main/kotlin/Foo.kt:120

gh search code "runCatching" --language kotlin --owner my-org
gh search prs --author @me --state open
gh search issues "is:open label:bug repo:my-org/my-repo"
gh search commits "hotfix" --author @me
```

## Extensions

```bash
gh extension list
gh extension install github/gh-copilot
gh extension install https://github.com/user/gh-awesome
gh extension upgrade gh-copilot
gh extension remove gh-copilot

# Create a new extension scaffolding
gh extension create my-tool
```

Extensions expose themselves as `gh <name>` — e.g. `gh copilot suggest`.

## JSON output + jq / templates

Most read commands accept `--json <fields>` for structured output. The
naked `--json` with no fields prints the available field list, which is
how you discover the shape.

```bash
gh pr list --json number,title,author,headRefName
gh pr view 42 --json state,isDraft,mergeable --jq '{state, mergeable}'

gh run list --limit 5 \
  --json databaseId,workflowName,status,conclusion,headBranch \
  --jq '.[] | select(.conclusion=="failure")'

# Go template example
gh pr list --json number,title \
  --template '{{range .}}#{{.number}} {{.title}}{{"\n"}}{{end}}'
```

## Aliases

```bash
gh alias list
gh alias set prs 'pr list --author @me'
gh alias set co 'pr checkout'
gh alias set --shell bugs 'pr list --label bug --json number,title --jq ".[] | \"\(.number) \(.title)\""'
gh alias delete prs
```

Shell-mode (`--shell`) aliases expand through `/bin/sh`; regular aliases
just prepend args.

## Configuration

```bash
gh config get editor
gh config set editor "code --wait"
gh config set git_protocol ssh
gh config set prompt disabled            # non-interactive
gh config set pager "less -R"
```

Useful env vars:

- `GH_TOKEN`, `GITHUB_TOKEN` — override saved auth.
- `GH_HOST` — default hostname for enterprise.
- `GH_REPO` — default repo for commands that take `-R`.
- `GH_PAGER`, `NO_PROMPT=1`, `GH_FORCE_TTY=1`.

## Scripting patterns

```bash
# 1. Wait for CI, merge when green
gh pr checks 42 --watch && gh pr merge 42 --squash --delete-branch

# 2. List failed jobs and re-run them
failed=$(gh run view 12345678 --json jobs \
  --jq '.jobs[] | select(.conclusion=="failure") | .databaseId')
for id in $failed; do gh run rerun 12345678 --job "$id"; done

# 3. Guard a script — exit if not on main and clean
[ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] || exit 1
gh release create "v$VERSION" --generate-notes ./dist/*

# 4. Harvest all open PR numbers for a repo
gh pr list --state open --json number --jq '.[].number'

# 5. Check author-owned open PRs across an org
gh search prs --author @me --state open --owner my-org \
  --json number,title,repository --jq '.[] | "\(.repository.nameWithOwner)#\(.number) \(.title)"'
```

## Anti-patterns

```bash
# WRONG: raw curl with GitHub API
curl -H "Authorization: token $TOKEN" https://api.github.com/repos/o/r

# Correct: gh api (handles auth, base URL, retries)
gh api repos/o/r
```

```bash
# WRONG: polling CI with sleep
while [ "$(gh pr checks 42 --json state --jq .state)" != "SUCCESS" ]; do
  sleep 10
done

# Correct:
gh pr checks 42 --watch
```

```bash
# WRONG: merging without CodeRabbit review
gh pr merge 42 --squash

# Correct: wait for review, then auto-merge when checks pass
gh pr merge 42 --squash --delete-branch --auto
```

```bash
# WRONG: --admin to bypass required checks
gh pr merge 42 --admin
# Fix the failing check instead.
```

```bash
# WRONG: --force anywhere near main
gh release delete v1.0.0 --yes                    # already destructive
gh repo delete my-org/prod --yes                  # confirm with a human
```

```bash
# WRONG: skipping --delete-branch
gh pr merge 42 --squash
# Leaves the remote branch dangling. Use --delete-branch.
```

## Common pitfalls

- **Rate limits on unauthenticated GraphQL** — ensure `gh auth status`
  shows you're logged in; GraphQL has a 5000 points/hr window per user.
- **`--json` field typos** — if the field doesn't exist, you get an
  empty object. Run `gh <cmd> --json` alone to list valid fields.
- **`gh pr create` from a detached HEAD** — push a branch first, or use
  `-H <branch>`.
- **Missing scopes** — some endpoints need `admin:org`, `workflow`, or
  `repo:status`. Add with `gh auth refresh -s <scope>`.
- **Pagination defaults** — list commands default to 30 items. Use
  `--limit N` (up to 1000 on many commands) or `--paginate` on `gh api`.
- **`--jq` quoting** — shell eats `$`, so use single quotes:
  `--jq '.foo'`. Double quotes in zsh with `setopt nomatch` can bite.
- **Enterprise hostname** — `GH_HOST` or `--hostname` is needed;
  otherwise requests go to github.com.

## References

- Manual — https://cli.github.com/manual/
- `gh pr` — https://cli.github.com/manual/gh_pr
- `gh api` — https://cli.github.com/manual/gh_api
- `gh run` — https://cli.github.com/manual/gh_run
- `gh release` — https://cli.github.com/manual/gh_release
- GitHub REST — https://docs.github.com/en/rest
- GitHub GraphQL — https://docs.github.com/en/graphql
- Releases (GitHub) — https://github.com/cli/cli/releases
