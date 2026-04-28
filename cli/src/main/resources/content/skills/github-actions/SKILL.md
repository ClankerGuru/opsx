---
name: github-actions
description: Reference guide for GitHub Actions covering workflow syntax (on triggers, filters, jobs, matrix strategy, services, containers, permissions, concurrency), expression contexts, functions, reusable workflows, composite actions, secrets and variables, plus this project's two-workflow CI/CD pattern (build.yml + release.yml) with JetBrains JDK 17 and vanniktech Maven Central publishing. Activates when authoring workflows or publishing releases.
---

# GitHub Actions

> For related topics see:
> - `/gh-cli` — `gh run`, `gh workflow`, `gh release` from the terminal
> - `/git-workflow` — branch strategy and commit conventions feeding CI
> - `/gradle-build-conventions` — convention plugins used by `build.yml`

## When to reach for this skill

- Writing or modifying a workflow in `.github/workflows/`.
- Debugging a failing job: logs, reruns, matrix fan-out.
- Configuring CI/CD: triggers, concurrency, permissions.
- Factoring duplicated steps into reusable workflows or composite actions.
- Managing repository secrets and variables.

## Workflow file basics

```yaml
name: Build                                  # UI label
run-name: Build on ${{ github.ref_name }}    # per-run label

on: [push]                                   # single trigger, shorthand

jobs:
  hello:
    runs-on: ubuntu-latest
    steps:
      - run: echo "hi"
```

Files live in `.github/workflows/*.{yml,yaml}`. Every file is an
independent workflow; there's no multi-file composition by default
(use reusable workflows — see below).

## Triggers (`on`)

### Common events

```yaml
on:
  push:
    branches: [main, 'releases/**']
    tags: ['v*']
    paths: ['src/**', '**/*.kt']
    paths-ignore: ['docs/**']
  pull_request:
    branches: [main]
    types: [opened, synchronize, reopened, ready_for_review]
  schedule:
    - cron: '30 5 * * 1-5'       # Mon–Fri 05:30 UTC
  workflow_dispatch:             # manual, via UI or `gh workflow run`
    inputs:
      target:
        description: Deploy target
        type: choice
        options: [staging, production]
        required: true
      debug:
        type: boolean
        default: false
  release:
    types: [published]
  workflow_call:                 # reusable — see below
  workflow_run:
    workflows: [Build]
    types: [completed]
```

Filters:

- `branches` / `branches-ignore` / `tags` / `tags-ignore` — glob
  patterns (`releases/**`, `v*`). Cannot use both include + ignore for
  the same attribute.
- `paths` / `paths-ignore` — trigger only on matching file changes.
- `types` — activity types (`opened`, `closed`, `published`, ...). Each
  event has its own type list.

`push` + `pull_request` together can double-run on branches with PRs —
filter one out (common: keep `pull_request`, limit `push` to `main`).

### `pull_request` vs `pull_request_target`

- `pull_request` — runs in the PR's forked context; `GITHUB_TOKEN` is
  read-only for forks; no access to secrets for forks.
- `pull_request_target` — runs on the base repo's default-branch
  workflow with full secrets. **Dangerous** — never `checkout` the PR
  head in `pull_request_target` and run untrusted code.

## Jobs

```yaml
jobs:
  build:
    name: Build & test
    runs-on: ubuntu-latest
    needs: [lint]                          # job dependency (implicit DAG)
    if: github.event_name != 'push' || github.ref == 'refs/heads/main'
    timeout-minutes: 30
    continue-on-error: false
    permissions:
      contents: read
      pull-requests: write
    environment: production                # gated by environment rules
    outputs:
      artifact: ${{ steps.pack.outputs.name }}
    env:
      CI: 'true'
    defaults:
      run:
        shell: bash
        working-directory: ./app
    steps: [...]
```

- `runs-on` — `ubuntu-latest`, `ubuntu-24.04`, `macos-14`,
  `windows-2022`, self-hosted labels (`[self-hosted, linux, x64]`), or
  `group: my-runners`.
- `needs` — wait on listed jobs; creates the DAG. Failures in needed
  jobs skip this one unless `if: always()`.
- `environment` — ties the job to a GitHub Environment with its own
  secrets, variables, and approval gates.
- `outputs` — surface step outputs to downstream jobs via
  `needs.<job>.outputs.<name>`.

### Strategy / matrix

```yaml
strategy:
  fail-fast: false           # default true — first failure cancels rest
  max-parallel: 4
  matrix:
    jdk: [17, 21]
    os: [ubuntu-latest, macos-14]
    include:
      - jdk: 24
        os: ubuntu-latest
        experimental: true
    exclude:
      - jdk: 17
        os: macos-14
```

- `include` adds extra combos or extra fields to existing ones.
- `exclude` removes combinations.
- Access via `${{ matrix.jdk }}`.
- `fail-fast: false` keeps all cells running so you see every failure.

### Services (sidecar containers)

```yaml
services:
  postgres:
    image: postgres:16
    env:
      POSTGRES_PASSWORD: test
    ports: [5432:5432]
    options: >-
      --health-cmd "pg_isready -U postgres"
      --health-interval 10s
      --health-timeout 5s
      --health-retries 5
```

Services run on the host runner and are resolvable as hostnames
(`postgres:5432`) when the job uses a container, or `localhost:5432` on
a runner.

### Job container

```yaml
container:
  image: eclipse-temurin:21
  env: { FOO: bar }
  options: --cpus 2
  volumes: [/data:/data]
```

Runs all `run:` steps inside the container. Skip this unless you truly
need a specific image — the default runner is faster.

## Steps

```yaml
steps:
  # Third-party action
  - uses: actions/checkout@v4
    with:
      fetch-depth: 0            # full history (for version tools)

  # Shell command
  - name: Test
    id: test
    run: ./gradlew test
    shell: bash                 # bash | pwsh | python | sh | cmd | powershell
    working-directory: ./app
    env: { CI: 'true' }
    if: matrix.os == 'ubuntu-latest'
    timeout-minutes: 10
    continue-on-error: true

  # Conditional on prior step
  - if: steps.test.outcome == 'failure'
    run: ./scripts/upload-diagnostics.sh

  # Step outputs
  - id: version
    run: echo "tag=v1.2.3" >> "$GITHUB_OUTPUT"
```

Inter-step communication files (always in `$GITHUB_*`):

- `$GITHUB_OUTPUT` — `key=value` → `steps.<id>.outputs.<key>`.
- `$GITHUB_ENV` — `key=value` → available to later steps via `$KEY`.
- `$GITHUB_PATH` — append paths (one per line) to `PATH`.
- `$GITHUB_STEP_SUMMARY` — Markdown rendered on the run page.
- Multi-line outputs use the heredoc delimiter:

```bash
{
  echo "payload<<EOF"
  cat big.json
  echo "EOF"
} >> "$GITHUB_OUTPUT"
```

## Contexts and expressions

Expressions are wrapped in `${{ }}`. Inside `if:` the wrapping is
optional and idiomatic to omit:

```yaml
if: github.event_name == 'push' && github.ref == 'refs/heads/main'
```

### Contexts

| Context | Contents |
|---|---|
| `github` | Event metadata (`event`, `ref`, `sha`, `actor`, `repository`, `event_name`, `ref_name`, `run_id`) |
| `env` | Env vars set via `env:` |
| `vars` | Repo/environment/org Variables |
| `secrets` | Repo/environment/org Secrets (masked in logs) |
| `inputs` | `workflow_dispatch` / `workflow_call` inputs |
| `matrix` | Current matrix cell |
| `strategy` | `job-index`, `job-total`, `fail-fast`, `max-parallel` |
| `needs` | Outputs of upstream jobs |
| `steps` | `outcome`, `conclusion`, `outputs.<id>` of prior steps |
| `job` | Current job — `status`, `container`, `services` |
| `runner` | `os`, `arch`, `name`, `temp`, `tool_cache` |

### Built-in functions

- `contains(search, item)` — `contains('main,next', github.ref_name)`.
- `startsWith(str, prefix)`, `endsWith(str, suffix)`.
- `format('Hello {0}', github.actor)`.
- `join(array, ', ')`.
- `toJSON(value)`, `fromJSON(jsonStr)` — critical for dynamic matrices.
- `hashFiles('**/*.gradle*', 'gradle/**/*.versions.toml')` — cache key.
- Status checks: `success()`, `failure()`, `cancelled()`, `always()`.
  `always()` makes a step run even if the job is cancelled; rarely
  appropriate.

### Dynamic matrix

```yaml
jobs:
  plan:
    runs-on: ubuntu-latest
    outputs:
      matrix: ${{ steps.set.outputs.matrix }}
    steps:
      - id: set
        run: |
          echo "matrix=$(./scripts/discover-modules.sh | jq -c)" >> "$GITHUB_OUTPUT"

  build:
    needs: plan
    strategy:
      matrix:
        module: ${{ fromJSON(needs.plan.outputs.matrix) }}
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew :${{ matrix.module }}:build
```

## Permissions

`GITHUB_TOKEN` auto-provisioned per job; its scopes default to the
repo's "Workflow permissions" setting. Narrow per workflow or per job:

```yaml
permissions:
  contents: read              # most jobs
  pull-requests: write        # commenting on PRs
  id-token: write             # OIDC to cloud providers
  packages: write             # push to GHCR
  # actions, attestations, checks, deployments, issues, models, pages,
  # security-events, statuses — all support read|write|none
```

Shortcuts: `permissions: read-all`, `permissions: write-all`,
`permissions: {}` (zero).

Set permissions as **tight** as the job needs. `contents: write` lets a
compromised action push to the repo.

## Concurrency

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

One run per `group` at a time. With `cancel-in-progress: true`, new
runs cancel older in-progress ones — standard for PR CI to kill obsolete
runs after a new push.

For deploys, use `cancel-in-progress: false` and pin the group to the
environment:

```yaml
concurrency:
  group: deploy-${{ github.event.inputs.target || 'production' }}
  cancel-in-progress: false
```

## Secrets and variables

- **Secrets** — encrypted; masked in logs; accessed via
  `${{ secrets.NAME }}`. Pass to reusable workflows explicitly.
- **Variables** — plaintext; visible in logs; `${{ vars.NAME }}`.

Scopes (precedence: environment > repository > organization):

```bash
gh secret set FOO --body "value"                    # repo
gh secret set FOO --env production                  # environment
gh secret set FOO --org my-org --visibility all     # org
gh variable set BAR --body "value"
```

Organization secrets can be restricted to specific repos. Environment
secrets can require approvals before exposure.

## Reusable workflows

Call another workflow from a job:

```yaml
# .github/workflows/deploy.yml (caller)
jobs:
  deploy:
    uses: ./.github/workflows/shared-deploy.yml
    with:
      target: production
    secrets:
      token: ${{ secrets.DEPLOY_TOKEN }}
```

```yaml
# .github/workflows/shared-deploy.yml
on:
  workflow_call:
    inputs:
      target:
        type: string
        required: true
    secrets:
      token:
        required: true
    outputs:
      url:
        value: ${{ jobs.run.outputs.url }}

jobs:
  run:
    runs-on: ubuntu-latest
    outputs:
      url: ${{ steps.deploy.outputs.url }}
    steps:
      - id: deploy
        run: ./deploy.sh ${{ inputs.target }}
        env: { TOKEN: ${{ secrets.token }} }
```

Cross-repo: `uses: org/shared/.github/workflows/foo.yml@v1`. Prefer a
tag over `main`.

Reusable workflows run in their own context; no shared filesystem or
step outputs with the caller.

## Composite actions

For step-level reuse, create `.github/actions/<name>/action.yml`:

```yaml
# .github/actions/setup-project/action.yml
name: 'Set up Kotlin project'
inputs:
  java-version:
    default: '17'
runs:
  using: composite
  steps:
    - uses: actions/setup-java@v4
      with:
        distribution: jetbrains
        java-version: ${{ inputs.java-version }}
    - uses: gradle/actions/setup-gradle@v4
    - shell: bash
      run: git config --global user.name "CI"
```

Consumer:

```yaml
- uses: ./.github/actions/setup-project
  with: { java-version: '21' }
```

Composite actions share the caller's filesystem. Each `run:` needs an
explicit `shell:`.

## Caching

```yaml
- uses: actions/cache@v4
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: gradle-${{ runner.os }}-${{ hashFiles('**/*.gradle*', 'gradle/libs.versions.toml') }}
    restore-keys: |
      gradle-${{ runner.os }}-
```

For Gradle specifically, `gradle/actions/setup-gradle@v4` manages its
own cache; don't stack a manual `actions/cache` on top.

Toolchain-specific cache:

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '21'
    cache: gradle          # built-in Gradle cache
```

## Artifacts

```yaml
- uses: actions/upload-artifact@v4
  with:
    name: test-results
    path: |
      build/reports/tests/
      build/reports/kover/
    retention-days: 14
    if-no-files-found: warn    # error | warn | ignore

- uses: actions/download-artifact@v4
  with:
    name: test-results
    path: ./downloaded
```

Artifacts are per-run. Use `actions/cache` for inter-run sharing.

## OIDC to clouds

Exchange a GitHub-issued JWT for cloud credentials — no static secrets:

```yaml
permissions:
  id-token: write
  contents: read
steps:
  - uses: aws-actions/configure-aws-credentials@v4
    with:
      role-to-assume: arn:aws:iam::123456789012:role/ci
      aws-region: us-east-1
```

Configure the trust relationship in AWS / GCP / Azure to accept the
repo's OIDC issuer (`token.actions.githubusercontent.com`).

## Debugging

- Enable debug logs in the UI: repo Settings → Secrets and variables →
  Actions → add `ACTIONS_STEP_DEBUG=true` and `ACTIONS_RUNNER_DEBUG=true`.
- `gh run view <id> --log-failed` — only failed step logs.
- `gh run rerun <id> --debug` — re-run with debug logging.
- Local runs: `act` (https://github.com/nektos/act). Close to prod but
  not identical.

## This project's workflows

Two workflows: `build.yml` (CI) + `release.yml` (CD).

### `build.yml` — CI

Triggered on push to `main` and PRs targeting `main`.

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Configure git for tests
        run: |
          git config --global user.name "CI"
          git config --global user.email "ci@users.noreply.github.com"
          git config --global init.defaultBranch main

      - uses: actions/setup-java@v4
        with:
          distribution: jetbrains
          java-version: 17

      - uses: gradle/actions/setup-gradle@v4

      - name: Build
        run: ./gradlew build

      - name: Coverage report
        if: always()
        run: ./gradlew koverLog

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: |
            build/reports/tests/
            build/reports/kover/
          retention-days: 14
```

### `release.yml` — CD

Triggered when a GitHub Release is published (not raw tag pushes).

```yaml
on:
  release:
    types: [published]

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Configure git for tests
        run: |
          git config --global user.name "CI"
          git config --global user.email "ci@users.noreply.github.com"
          git config --global init.defaultBranch main

      - name: Extract version from tag
        id: version
        run: |
          VERSION="${{ github.event.release.tag_name }}"
          VERSION="${VERSION#v}"
          if ! echo "$VERSION" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?(\+[a-zA-Z0-9.]+)?$'; then
            echo "Invalid version: $VERSION"
            exit 1
          fi
          echo "version=$VERSION" >> "$GITHUB_OUTPUT"

      - uses: actions/setup-java@v4
        with:
          distribution: jetbrains
          java-version: 17

      - uses: gradle/actions/setup-gradle@v4

      - name: Build and test
        run: ./gradlew build -PVERSION_NAME=${{ steps.version.outputs.version }}

      - name: Publish to Maven Central
        run: ./gradlew publishAndReleaseToMavenCentral -PVERSION_NAME=${{ steps.version.outputs.version }}
        env:
          ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.SONATYPE_USERNAME }}
          ORG_GRADLE_PROJECT_mavenCentralPassword: ${{ secrets.SONATYPE_PASSWORD }}
          ORG_GRADLE_PROJECT_signingInMemoryKey: ${{ secrets.GPG_SIGNING_KEY }}
          ORG_GRADLE_PROJECT_signingInMemoryKeyId: ${{ secrets.GPG_KEY_ID }}
          ORG_GRADLE_PROJECT_signingInMemoryKeyPassword: ${{ secrets.GPG_PASSPHRASE }}
          ORG_GRADLE_PROJECT_RELEASE_SIGNING_ENABLED: true
```

### Version flow

```
Tag v0.41.0
  → shell: VERSION="0.41.0" (strip v)
  → SemVer regex validation
  → Gradle: -PVERSION_NAME=0.41.0
  → gradle.properties VERSION_NAME (overridden)
  → build.gradle.kts project.version
  → Maven Central artifact version
```

### Required secrets

| Secret | Purpose |
|---|---|
| `SONATYPE_USERNAME` | Maven Central (Sonatype) username |
| `SONATYPE_PASSWORD` | Maven Central password |
| `GPG_SIGNING_KEY` | ASCII-armored GPG private key |
| `GPG_KEY_ID` | GPG key ID (short form) |
| `GPG_PASSPHRASE` | GPG key passphrase |

### SemVer regex

```bash
# Accepts: 0.41.0, 1.0.0-beta.1, 2.3.4+build.5
# Rejects: 0.41, v0.41.0, 0.41.0.1
^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?(\+[a-zA-Z0-9.]+)?$
```

### Triggering a release

```bash
# Creates tag + GitHub Release in one step, triggers release.yml
gh release create v0.41.0 --title "v0.41.0" --generate-notes

# Watch it
gh run list --workflow=release.yml --limit 1
gh run watch <run-id>
gh run view <run-id> --log-failed
```

## Anti-patterns

```yaml
# WRONG: checkout PR head in pull_request_target
on: pull_request_target
jobs:
  test:
    steps:
      - uses: actions/checkout@v4
        with: { ref: ${{ github.event.pull_request.head.sha }} }  # runs untrusted code with secrets
# Correct: use pull_request, or do not checkout the head in _target
```

```yaml
# WRONG: hardcoded secret
env:
  TOKEN: "ghp_xxxxxxxxxxxxxxxxxxxx"
# Correct
env:
  TOKEN: ${{ secrets.GH_TOKEN }}
```

```yaml
# WRONG: permissions: write-all on every workflow
permissions: write-all
# Correct: narrow to what the job needs
permissions:
  contents: read
  pull-requests: write
```

```yaml
# WRONG: unversioned or `@main` action reference
- uses: some-org/some-action@main
# Correct: pin to a tag or SHA
- uses: some-org/some-action@v1.2.3
- uses: some-org/some-action@2a1b4c...   # SHA (most locked-down)
```

```yaml
# WRONG: pushing a tag without creating a release
# The release workflow triggers on `release: published`, NOT on tag push.
# A bare `git push origin v0.41.0` does nothing.
# Correct: gh release create v0.41.0 --generate-notes
```

```yaml
# WRONG: hardcoded version in source
# version = "0.41.0" in build.gradle.kts
# Correct: VERSION_NAME flows in via -PVERSION_NAME
```

```yaml
# WRONG: RELEASE_SIGNING_ENABLED on local builds
# Signing is CI-only. Never sign locally.
```

## Common pitfalls

- **Double-runs on PR branches** — a PR from a same-repo branch fires
  both `push` and `pull_request`. Filter one out (usually restrict
  `push` to `main`).
- **`actions/checkout` default depth is 1** — tools that need history
  (GitVersion, git describe) need `fetch-depth: 0`.
- **Matrix combos explode silently** — two lists of three cells = six
  jobs per OS; watch your runner minutes.
- **`if:` false skips the step but succeeds** — downstream `if:
  success()` still runs. Use `if: steps.x.outcome == 'success'` for
  precision.
- **Secrets don't mask if they're encoded** — JSON-stringifying a secret
  in a step breaks masking. Don't `toJSON(secrets.X)`.
- **`needs.*` only sees jobs that actually ran** — a skipped needed job
  skips this one too unless you gate with `if: always() && ...`.
- **Workflow changes live in the default branch for scheduled events**
  — editing a cron schedule on a branch doesn't change when it fires
  until merged to `main`.

## References

- Workflow syntax — https://docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions
- Events — https://docs.github.com/en/actions/writing-workflows/choosing-when-your-workflow-runs/events-that-trigger-workflows
- Contexts — https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/accessing-contextual-information-about-workflow-runs
- Expressions — https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/expressions
- Reusable workflows — https://docs.github.com/en/actions/sharing-automations/reusing-workflows
- Composite actions — https://docs.github.com/en/actions/sharing-automations/creating-actions/creating-a-composite-action
- OIDC — https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect
- Runner images — https://github.com/actions/runner-images
