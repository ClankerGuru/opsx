## Activity setup (run first)

Before any real work, export the active change name so
`opsx log` can find the log:

```bash
export OPSX_ACTIVE_CHANGE=<name>
```

If no watcher is already running for this change, launch one in the
background so the user can watch progress live. The watcher is
always started through the `opsx status --change` wrapper so every
CLI hits the same host-aware rendering pipeline — never call
`opsx status --follow` directly from skill docs:

```bash
if [ ! -f .opsx/watch.pid ] || ! kill -0 "$(cat .opsx/watch.pid 2>/dev/null)" 2>/dev/null; then
    if [ "${OPSX_NO_WATCH:-}" != "1" ]; then
        mkdir -p .opsx
        (opsx status --follow --change "$OPSX_ACTIVE_CHANGE" \
            >/dev/null 2>&1 & echo $! > .opsx/watch.pid)
    fi
fi
```

Set `OPSX_NO_WATCH=1` in the environment to suppress the watcher for
one-off runs.

**Every step that does real work must be bracketed with
`opsx log`** — no CLI-specific hook is involved, so
logging is the caller's responsibility. This applies whether the
step delegates to a subagent or runs inline in the main loop.

Pattern for an inline step:

```bash
opsx log <agent> start <task-id|-> <one-line>
# ... do the work ...
opsx log <agent> done  <task-id|-> <one-line>
```

Pattern for a delegated step: call `start` just before invoking the
`Agent` tool, and `done` (or `failed`) immediately after the
subagent returns.

`opsx log` is shared across every AI CLI
(claude / copilot / codex / opencode) — one script, one path, same
behavior everywhere.
