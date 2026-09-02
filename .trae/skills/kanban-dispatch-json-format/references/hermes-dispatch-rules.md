# Hermes dispatch rules

## Output

- Resolve the selected or active profile with `hermes profile show <profile>`.
- Read `TERMINAL_CWD` from `<profile Path>/.env` with `scripts/resolve_terminal_cwd.py`.
- Create `<TERMINAL_CWD>/.hermes` when needed. Read its existing `agents.json` and create only `kanban.json`; never modify or delete `agents.json`.
- Keep `dispatch-values.json` in a unique system temporary directory and delete it after success.
- Remove only the two legacy outputs `dispatch-values.json` and `kanban-ui-task-list.json` from the output directory. Do not remove unrelated `.hermes` contents.
- Never fall back to the agent session CWD or Skill directory.

## CLI contract

Verify help at runtime because Hermes may change. Create the swarm with this command shape, using the exact `boards` value from `agents.json`:

```text
hermes kanban --board <boards> swarm --worker ... --verifier ... --synthesizer ... --created-by ... --json <goal>
```

Run Hermes commands directly in the terminal. Snapshot `hermes kanban --board <boards> list --json` before and after dispatch, prefer IDs returned by swarm JSON, and use the same-board before/after difference only as a fallback.

## Agent assignment

- Resolve the current profile first; its `.env` determines `TERMINAL_CWD`.
- Load `<TERMINAL_CWD>/.hermes/agents.json` as `{project, boards, agents}`.
- Require non-empty `project` and `boards` strings and a non-empty `agents` array.
- Require unique, non-empty names and codes.
- Use `project` for final `kanban.json.project` and `boards` for every board-scoped Hermes command.
- Match requested work to `name` semantically, but use the exact corresponding `code` everywhere an assignment identity is required.
- Set final `tasks[].assignee` and `ui_task_list.rows[].assignee.id` to `code`; set UI `display_name` to `name`.
- Stop if the file is missing or invalid, an assignee code is unavailable, or no listed agent has a required capability.

## Canonical enum mapping

| Hermes priority | Final value |
|---|---|
| P0 | 紧急 |
| P1 | 高 |
| P2 | 中 |
| P3 | 低 |

| Hermes status | Final value |
|---|---|
| todo, ready, scheduled, triage | 待办 |
| running, review | 进行中 |
| done, archived | 已完成 |
| blocked | 暂停 |

The four priority values and four status values above are the only allowed values in final `tasks`, UI rows, and statistics.

## Profiles and IDs

- Verify profiles with `hermes profile list`; select only existing profiles.
- Read relevant `SOUL.md` files when available.
- Make verifier and synthesizer explicit.
- In `actual` mode, record a swarm command and use only real `t_XXXXXXXX` IDs.
- In `dry-run` mode, do not execute swarm and use `t_pre_...` IDs.
