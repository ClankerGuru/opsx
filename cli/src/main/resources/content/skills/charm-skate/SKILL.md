---
name: charm-skate
description: >-
  Use when a shell script needs a tiny local key-value store — storing
  release tags, last-used branches, cached answers, or shared config
  across script runs without a real database. Covers every skate
  subcommand, the `KEY[@DB]` namespace syntax, reading values from stdin,
  binary values, and list output shapes.
---

## When to use this skill

- Persisting a bit of state between runs of a shell script or git hook.
- Scratchpad during a release flow: `skate set last_tag@release v1.2.3`.
- Sharing config across scripts without inventing a dotfile format.
- Caching the answer to a slow command for the lifetime of a shell.

## When NOT to use this skill

- Structured data: nested objects, schemas, joins — reach for SQLite.
- Secrets. Skate is not encrypted at rest.
- Data another machine needs to read. Databases are local.
- High-throughput or concurrent writers. Single-writer semantics only.

## Install

```bash
brew install charmbracelet/tap/skate     # macOS / Linux
go install github.com/charmbracelet/skate@latest
```

Verify: `skate --version`.

## Core concept: `KEY[@DB]`

Every key lives in a database. Omit `@DB` for the default database.
Databases are created on first write; list with `skate list-dbs`.

```bash
skate set token 'abc123'                 # default DB
skate set token@prod 'xyz789'            # separate namespace
skate get token@prod
```

Choose one DB per project or concern (e.g. `@release`, `@cache`). Don't
pack unrelated data into the default DB.

## Subcommands

| Command      | Usage                            | Purpose                                       |
|--------------|----------------------------------|-----------------------------------------------|
| `set`        | `set KEY[@DB] [VALUE]`           | Write a value. Reads stdin if VALUE omitted.  |
| `get`        | `get KEY[@DB]`                   | Print the value. Exits non-zero if missing.   |
| `delete`     | `delete KEY[@DB]`                | Remove a key.                                 |
| `list`       | `list [@DB]`                     | List pairs in the named or default DB.        |
| `list-dbs`   | `list-dbs`                       | List all databases.                           |
| `delete-db`  | `delete-db [@DB]`                | Drop a whole database.                        |

## Flags

| Flag                | Where              | Effect                                        |
|---------------------|--------------------|-----------------------------------------------|
| `-b, --show-binary` | `get`, `list`      | Print binary values as-is (otherwise hidden). |
| `-d, --delimiter`   | `list`             | Separator between key and value. Default `\t`.|
| `-k, --keys-only`   | `list`             | Print only keys (no value fetch — faster).    |
| `-v, --values-only` | `list`             | Print only values.                            |
| `-r, --reverse`     | `list`             | Reverse lexicographic order.                  |

## Recipes

### Write from stdin (multi-line or binary-safe)

```bash
skate set config@app <./config.yaml
curl -s https://example.com/banner | skate set banner@ui
```

`set KEY <FILE` and `set KEY@DB <FILE` both work — stdin is read
whenever VALUE is omitted.

### Read-or-default in a script

```bash
tag=$(skate get last_release@ship 2>/dev/null) || tag="v0.0.0"
```

Missing keys exit non-zero — always guard with `|| fallback`.

### Iterate with `list`

```bash
# Tab-separated (default) — safe for awk/cut with -F$'\t'
skate list @cache | while IFS=$'\t' read -r key value; do
  echo "$key has $value"
done

# Keys only, reversed, newest-first prefix
skate list @release -k -r
```

Prefer `--keys-only` when you're filtering — skips the value fetch on
every row.

### Pick a key interactively (with gum)

```bash
target=$(skate list @branches -k | gum filter --header "checkout")
[ -n "$target" ] && git checkout "$(skate get "$target@branches")"
```

### Export / import a DB

```bash
skate list @prod -d '|' > prod.backup
while IFS='|' read -r k v; do
  skate set "$k@prod" "$v"
done < prod.backup
```

Pick a delimiter your values can't contain — `\t` is safer than space;
`\0` works for truly arbitrary payloads.

### Sweep a namespace

```bash
skate list @stale -k | while read -r k; do
  skate delete "$k@stale"
done
# Or, nuclear:
skate delete-db @stale
```

## Binary values

Binary-safe on write (stdin) and read (`-b`). Without `-b`, `get` and
`list` hide non-UTF-8 values to keep terminals sane.

```bash
skate set logo@ui <./logo.png
skate get logo@ui -b > logo.png
```

## Pitfalls

| Symptom                                 | Cause / fix                                                |
|-----------------------------------------|------------------------------------------------------------|
| `get` returns nothing and exits 0       | Key exists but value is empty. Use `list -k` to confirm.   |
| Binary value prints as `�` or garbled   | Pass `-b` to `get` / `list`.                               |
| `list` output splits on unexpected char | Change `--delimiter` to something absent from values.      |
| Wrong DB, right key                     | Double-check `@DB`. Default DB is separate from `@...`.    |
| Can't tell DBs apart                    | `skate list-dbs`.                                          |
| Lost data after `delete-db`             | No undo. Back up with `skate list @db` before destructive ops. |

## Reference points

- https://github.com/charmbracelet/skate — repo and docs
