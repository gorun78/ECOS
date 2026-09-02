# Core dispatch values

Write these non-derived values to a temporary `dispatch-values.json`. Delete it after a successful build. The final persistent output is only `kanban.json`.

```json
{
  "execution": {
    "mode": "actual",
    "dispatcher_profile": "pm",
    "verifier": "pm",
    "synthesizer": "pm",
    "commands_executed": [
      {
        "type": "swarm",
        "command_summary": "hermes kanban --board app-boards swarm --worker ... --json <goal>",
        "timestamp": "2026-07-21T06:00:00Z"
      }
    ],
    "synthesize_task_id": "t_1234abcd"
  },
  "source": {
    "feature_name": "Feature name",
    "requirement_file": null,
    "generated_at": "2026-07-21T06:00:00Z"
  },
  "tasks": [
    {
      "id": "t_1234abcd",
      "title": "Implement feature",
      "assignee": "pm-1784859477297",
      "role": "pm",
      "priority": "P1",
      "status": "ready",
      "created_time": "2026-07-21T06:00:00Z",
      "description": "Concrete scope and acceptance criteria",
      "tags": ["feature"],
      "estimate": null
    }
  ],
  "dependencies": [],
  "warnings": [],
  "next_steps": ["Wait for implementation"]
}
```

## Rules

- Read `project`, `boards`, and available agents from the resolved `<TERMINAL_CWD>/.hermes/agents.json`:

```json
{
  "project": "app",
  "boards": "app-boards",
  "agents": [
    {"name": "项目-1", "code": "pm-1784859477297"},
    {"name": "架构-1", "code": "arch-1784859543339"}
  ]
}
```

- Do not copy `project` or `boards` into temporary values; the builder rereads the authoritative file.
- In actual mode, record the exact board-scoped command beginning `hermes kanban --board <boards> swarm`.
- `execution.mode`: `actual` or `dry-run`.
- Task count is dynamic; never force three tasks.
- Set every `tasks[].assignee` to an exact `code` from `agents.json.agents`. Never use a role alias, display name, fabricated code, or unavailable agent.
- The builder rejects missing/empty/invalid `agents.json`, duplicate names or codes, and task assignees not present in the file.
- Map `ui_task_list.rows[].assignee.id` from agent `code` and `display_name` from agent `name`.
- Input priority may be `P0/P1/P2/P3` or `紧急/高/中/低`.
- Input status may be a Hermes status or `待办/进行中/已完成/暂停`.
- Final task and UI priority values are only `低`, `中`, `高`, `紧急`.
- Final task and UI status values are only `待办`, `进行中`, `已完成`, `暂停`.
- Dependencies are directed edges `{parent, child, reason}` where `parent` is the prerequisite.
- Dependency endpoints must exist; duplicates, self-dependencies, and cycles are invalid.
- Actual IDs must come from Hermes and match `t_` plus eight lowercase hexadecimal characters.
- Dry-run IDs begin with `t_pre_`.
- Use ISO 8601 UTC timestamps in temporary values; final task timestamps are Unix seconds as required by the canonical example.
- Optional canonical task fields such as `session_id`, `workspace_path`, and `branch_name` may be supplied on a task; omitted values become `null` without changing the output field set.
