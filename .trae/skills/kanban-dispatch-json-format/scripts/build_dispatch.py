#!/usr/bin/env python3
"""Build one canonical kanban.json from Hermes dispatch values."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict, deque
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from generate_json import load_schema, resolve_local_refs, save_json, validate_instance
from resolve_terminal_cwd import resolve_terminal_cwd


PRIORITY_MAP = {"P0": "紧急", "P1": "高", "P2": "中", "P3": "低"}
PRIORITIES = {"低", "中", "高", "紧急"}
STATUS_MAP = {
    "todo": "待办",
    "ready": "待办",
    "scheduled": "待办",
    "triage": "待办",
    "running": "进行中",
    "review": "进行中",
    "done": "已完成",
    "archived": "已完成",
    "blocked": "暂停",
}
STATUSES = {"待办", "进行中", "已完成", "暂停"}

PRIORITY_STYLE = {
    "低": "background:#43a047;color:#fff",
    "中": "background:#1e88e5;color:#fff",
    "高": "background:#fb8c00;color:#fff",
    "紧急": "background:#e53935;color:#fff",
}
STATUS_STYLE = {
    "待办": "background:#1e88e5;color:#fff",
    "进行中": "background:#fb8c00;color:#fff",
    "已完成": "background:#43a047;color:#fff",
    "暂停": "background:#757575;color:#fff",
}

COLUMNS = [
    {"key": "id", "label": "ID", "sortable": True, "filterable": True, "visible": True, "width": 120},
    {"key": "title", "label": "任务", "sortable": True, "filterable": True, "visible": True, "width": 280},
    {"key": "assignee", "label": "执行人", "sortable": True, "filterable": True, "visible": True, "width": 160},
    {"key": "status", "label": "状态", "sortable": True, "filterable": True, "visible": True, "width": 100},
    {"key": "priority", "label": "优先级", "sortable": True, "filterable": True, "visible": True, "width": 80},
    {"key": "dependencies", "label": "依赖", "sortable": False, "filterable": False, "visible": True, "width": 200},
    {"key": "actions", "label": "操作", "sortable": False, "filterable": False, "visible": True, "width": 120},
]


def read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except FileNotFoundError as exc:
        raise RuntimeError(f"Values file not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Invalid values JSON at {path}:{exc.lineno}:{exc.colno}") from exc


def normalize_priority(value: Any) -> str:
    if value in PRIORITIES:
        return str(value)
    if value in PRIORITY_MAP:
        return PRIORITY_MAP[str(value)]
    raise RuntimeError(f"Unsupported priority {value!r}; use P0-P3 or 低/中/高/紧急")


def normalize_status(value: Any) -> str:
    if value in STATUSES:
        return str(value)
    if value in STATUS_MAP:
        return STATUS_MAP[str(value)]
    raise RuntimeError(f"Unsupported status {value!r}; use a Hermes status or 待办/进行中/已完成/暂停")


def unix_time(value: Any) -> int:
    if isinstance(value, int) and not isinstance(value, bool) and value >= 0:
        return value
    if not isinstance(value, str) or not value.strip():
        raise RuntimeError(f"Invalid task timestamp: {value!r}")
    try:
        parsed = datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    except ValueError as exc:
        raise RuntimeError(f"Invalid ISO 8601 task timestamp: {value}") from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return int(parsed.timestamp())


def iso_time(value: Any) -> str:
    if isinstance(value, str) and value.strip():
        parsed = datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    elif isinstance(value, int) and not isinstance(value, bool):
        parsed = datetime.fromtimestamp(value, tz=timezone.utc)
    else:
        parsed = datetime.now(timezone.utc)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def read_agent_config(path: Path) -> tuple[str, str, dict[str, str]]:
    raw = read_json(path)
    if not isinstance(raw, dict):
        raise RuntimeError(f"agents.json must be an object: {path}")
    project, boards, raw_agents = raw.get("project"), raw.get("boards"), raw.get("agents")
    if not isinstance(project, str) or not project.strip():
        raise RuntimeError("agents.json.project must be a non-empty string")
    if not isinstance(boards, str) or not boards.strip():
        raise RuntimeError("agents.json.boards must be a non-empty string")
    if not isinstance(raw_agents, list) or not raw_agents:
        raise RuntimeError("agents.json.agents must be a non-empty array")
    agents: dict[str, str] = {}
    names: set[str] = set()
    for index, item in enumerate(raw_agents):
        if not isinstance(item, dict):
            raise RuntimeError(f"agents.json.agents[{index}] must be an object")
        name, code = item.get("name"), item.get("code")
        if not isinstance(name, str) or not name.strip():
            raise RuntimeError(f"agents.json.agents[{index}].name must be a non-empty string")
        if not isinstance(code, str) or not code.strip():
            raise RuntimeError(f"agents.json.agents[{index}].code must be a non-empty string")
        name, code = name.strip(), code.strip()
        if code in agents:
            raise RuntimeError(f"Duplicate agent code in agents.json: {code}")
        if name in names:
            raise RuntimeError(f"Duplicate agent name in agents.json: {name}")
        agents[code] = name
        names.add(name)
    return project.strip(), boards.strip(), agents


def dependency_graph(task_ids: list[str], edges: list[dict[str, Any]]) -> tuple[dict[str, list[str]], dict[str, list[str]]]:
    known = set(task_ids)
    parents: dict[str, list[str]] = defaultdict(list)
    children: dict[str, list[str]] = defaultdict(list)
    seen: set[tuple[str, str]] = set()
    for index, edge in enumerate(edges):
        if not isinstance(edge, dict):
            raise RuntimeError(f"dependencies[{index}] must be an object")
        parent, child = edge.get("parent"), edge.get("child")
        if parent not in known or child not in known:
            raise RuntimeError(f"dependencies[{index}] references an unknown task")
        if parent == child:
            raise RuntimeError(f"dependencies[{index}] cannot be a self-dependency")
        pair = (parent, child)
        if pair in seen:
            raise RuntimeError(f"Duplicate dependency: {parent} -> {child}")
        seen.add(pair)
        parents[child].append(parent)
        children[parent].append(child)

    indegree = {task_id: len(parents[task_id]) for task_id in task_ids}
    queue = deque(task_id for task_id in task_ids if indegree[task_id] == 0)
    visited = 0
    while queue:
        current = queue.popleft()
        visited += 1
        for child in children[current]:
            indegree[child] -= 1
            if indegree[child] == 0:
                queue.append(child)
    if visited != len(task_ids):
        raise RuntimeError("Task dependencies contain a cycle")
    return parents, children


def select_tagged_id(tasks: list[dict[str, Any]], tag: str) -> str | None:
    for task in tasks:
        if tag in task.get("tags", []):
            return task["id"]
    return None


def build_document(
    values: dict[str, Any],
    project: str,
    boards: str,
    agents: dict[str, str],
) -> dict[str, Any]:
    tasks_input = values.get("tasks")
    if not isinstance(tasks_input, list) or not tasks_input:
        raise RuntimeError("tasks must be a non-empty array")
    if not all(isinstance(task, dict) for task in tasks_input):
        raise RuntimeError("Every task must be an object")

    task_ids = [task.get("id") for task in tasks_input]
    if any(not isinstance(task_id, str) or not task_id for task_id in task_ids):
        raise RuntimeError("Every task must have a non-empty string id")
    if len(set(task_ids)) != len(task_ids):
        raise RuntimeError("Task ids must be unique")

    edges = values.get("dependencies", [])
    if not isinstance(edges, list):
        raise RuntimeError("dependencies must be an array")
    parents, children = dependency_graph(task_ids, edges)

    execution_input = values.get("execution", {})
    if not isinstance(execution_input, dict):
        raise RuntimeError("execution must be an object")
    mode = execution_input.get("mode")
    if mode not in {"actual", "dry-run"}:
        raise RuntimeError("execution.mode must be actual or dry-run")
    created_by = execution_input.get("dispatcher_profile", values.get("created_by", "pm"))
    if not isinstance(created_by, str) or not created_by:
        raise RuntimeError("dispatcher_profile must be a non-empty string")

    real_id = re.compile(r"^t_[0-9a-f]{8}$")
    ids_are_real = all(real_id.fullmatch(task_id) for task_id in task_ids)
    if mode == "actual" and not ids_are_real:
        raise RuntimeError("actual mode requires real Hermes t_XXXXXXXX task ids")
    commands = execution_input.get("commands_executed", [])
    swarm_commands = [
        c for c in commands
        if isinstance(c, dict) and c.get("type") == "swarm"
    ] if isinstance(commands, list) else []
    if mode == "actual" and not swarm_commands:
        raise RuntimeError("actual mode requires a recorded swarm command")
    if mode == "actual" and not any(
        "hermes kanban --board" in str(c.get("command_summary", ""))
        and boards in str(c.get("command_summary", ""))
        and " swarm" in str(c.get("command_summary", ""))
        for c in swarm_commands
    ):
        raise RuntimeError(
            f"actual swarm command must use agents.json boards value: "
            f"hermes kanban --board {boards} swarm"
        )
    tasks: list[dict[str, Any]] = []
    rows: list[dict[str, Any]] = []
    for source_task in tasks_input:
        task_id = source_task["id"]
        assignee = source_task.get("assignee")
        if assignee not in agents:
            available = ", ".join(sorted(agents))
            raise RuntimeError(
                f"Task {task_id} assignee {assignee!r} is not an agent code from agents.json; "
                f"available codes: {available}"
            )
        status = normalize_status(source_task.get("status"))
        priority = normalize_priority(source_task.get("priority"))
        created_at = unix_time(source_task.get("created_at", source_task.get("created_time")))
        started_at = source_task.get("started_at")
        completed_at = source_task.get("completed_at")
        task = {
            "id": task_id,
            "title": source_task.get("title"),
            "body": source_task.get("body", source_task.get("description")),
            "assignee": assignee,
            "status": status,
            "priority": priority,
            "created_by": source_task.get("created_by", created_by),
            "created_at": created_at,
            "started_at": unix_time(started_at) if started_at is not None else None,
            "completed_at": unix_time(completed_at) if completed_at is not None else None,
            "result": source_task.get("result"),
            "skills": list(source_task.get("skills", source_task.get("tags", []))),
            "max_retries": source_task.get("max_retries", 1),
            "session_id": source_task.get("session_id"),
            "workflow_template_id": source_task.get("workflow_template_id"),
            "current_step_key": source_task.get("current_step_key"),
            "workspace_path": source_task.get("workspace_path"),
            "branch_name": source_task.get("branch_name"),
            "project_id": source_task.get("project_id"),
            "tenant": source_task.get("tenant"),
            "workspace_kind": source_task.get("workspace_kind", "shared"),
            "parents": parents[task_id],
            "children": children[task_id],
        }
        for field in ("title", "body", "assignee"):
            if not isinstance(task[field], str) or not task[field]:
                raise RuntimeError(f"Task {task_id} requires non-empty {field}")
        tasks.append(task)
        rows.append({
            "id": task_id,
            "title": task["title"],
            "assignee": {"id": task["assignee"], "display_name": agents[task["assignee"]]},
            "status": {"value": status, "label": status, "style": STATUS_STYLE[status]},
            "priority": {"value": priority, "label": priority, "style": PRIORITY_STYLE[priority]},
            "dependencies": task["parents"],
            "created_at": created_at,
            "updated_at": unix_time(source_task.get("updated_at", created_at)),
            "actions": [{"label": "查看", "type": "view"}, {"label": "追踪", "type": "track"}],
        })

    dependencies = {
        task_id: {"depends_on": parents[task_id], "blocks": children[task_id]}
        for task_id in task_ids
    }
    by_status = dict(sorted(Counter(task["status"] for task in tasks).items()))
    by_priority = dict(sorted(Counter(task["priority"] for task in tasks).items()))
    by_assignee = dict(sorted(Counter(task["assignee"] for task in tasks).items()))
    in_progress = [task["id"] for task in tasks if task["status"] == "进行中"]
    completed = [task["id"] for task in tasks if task["status"] == "已完成"]
    blocked = [task["id"] for task in tasks if task["status"] == "暂停"]
    ready = [task["id"] for task in tasks if task["status"] == "待办"]

    source = values.get("source", {})
    if not isinstance(source, dict):
        source = {}
    generated_at = iso_time(source.get("generated_at", values.get("created_at")))

    warnings = list(values.get("warnings", []))
    if mode == "dry-run" and not any("占位" in str(item) for item in warnings):
        warnings.append("dry-run 使用占位任务 ID；实际派发后必须替换为 Hermes task ID")

    root_id = select_tagged_id(tasks_input, "swarm-root") or execution_input.get("swarm_root") or task_ids[0]
    verifier_id = select_tagged_id(tasks_input, "verifier")
    synthesizer_id = execution_input.get("synthesize_task_id") or select_tagged_id(tasks_input, "synthesizer")
    excluded = {root_id, verifier_id, synthesizer_id, None}
    worker_ids = [task_id for task_id in task_ids if task_id not in excluded]

    validation = {
        "dry_run_valid": mode != "dry-run" or all(task_id.startswith("t_pre_") for task_id in task_ids),
        "dispatch_result_valid": mode != "actual" or ids_are_real,
        "json_valid": True,
        "tasks_length_match": len(tasks) == len(tasks_input),
        "ui_task_list_length_match": len(rows) == len(tasks),
        "dependencies_direction_valid": True,
        "no_circular_dependency": True,
        "all_required_fields_present": True,
        "assignee_valid": all(task["assignee"] in agents for task in tasks),
        "all_validations_pass": True,
    }
    validation["all_validations_pass"] = all(validation.values())

    return {
        "project": str(project),
        "workflow_mode": "swarm",
        "created_by": created_by,
        "created_at": generated_at,
        "dispatch_mode": mode,
        "dependencies": dependencies,
        "tasks": tasks,
        "ui_task_list": {"columns": COLUMNS, "rows": rows},
        "execution": {
            "mode": "swarm" if mode == "actual" else "dry-run",
            "planned_dispatch_method": "swarm",
            "dispatched": task_ids if mode == "actual" else [],
            "in_progress": in_progress,
            "completed": completed,
            "blocked": blocked,
            "ready": ready,
            "pending_dispatch": task_ids if mode == "dry-run" else [],
        },
        "statistics": {"total": len(tasks), "by_status": by_status, "by_priority": by_priority, "by_assignee": by_assignee},
        "validation": validation,
        "warnings": warnings,
        "next_steps": list(values.get("next_steps", [])),
        "swarm": {"root_id": root_id, "worker_ids": worker_ids, "verifier_id": verifier_id, "synthesizer_id": synthesizer_id},
    }


def validate_semantics(document: dict[str, Any]) -> None:
    task_ids = [task["id"] for task in document["tasks"]]
    row_ids = [row["id"] for row in document["ui_task_list"]["rows"]]
    if task_ids != row_ids:
        raise RuntimeError("tasks and ui_task_list.rows must have identical ordered ids")
    for task, row in zip(document["tasks"], document["ui_task_list"]["rows"]):
        if row["status"]["value"] != task["status"] or row["priority"]["value"] != task["priority"]:
            raise RuntimeError(f"Task/UI enum mismatch for {task['id']}")
        if row["dependencies"] != task["parents"]:
            raise RuntimeError(f"Task/UI dependency mismatch for {task['id']}")
    if not document["validation"]["all_validations_pass"]:
        raise RuntimeError("Derived validation did not pass")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build canonical Hermes kanban.json")
    parser.add_argument("--template", type=Path, required=True)
    parser.add_argument("--values", type=Path, required=True)
    parser.add_argument("--profile-env", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        values = read_json(args.values)
        if not isinstance(values, dict):
            raise RuntimeError("Values JSON root must be an object")
        terminal_cwd = resolve_terminal_cwd(args.profile_env)
        output_dir = terminal_cwd / ".hermes"
        output_dir.mkdir(exist_ok=True)
        agents_path = output_dir / "agents.json"
        project, boards, agents = read_agent_config(agents_path)
        output_path = output_dir / "kanban.json"
        document = build_document(values, project, boards, agents)
        schema = resolve_local_refs(load_schema(args.template))
        errors = validate_instance(document, schema)
        if errors:
            raise RuntimeError("Generated JSON does not match canonical schema:\n" + "\n".join(f"  - {e}" for e in errors[:30]))
        validate_semantics(document)
        save_json(output_path, document, compact=False)
        for legacy_name in ("dispatch-values.json", "kanban-ui-task-list.json"):
            legacy_path = output_dir / legacy_name
            if legacy_path.exists():
                legacy_path.unlink()
    except (RuntimeError, ValueError, TypeError, OSError) as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1
    print(f"Hermes kanban JSON validated: {output_path}")
    print(f"Task count: {len(document['tasks'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
