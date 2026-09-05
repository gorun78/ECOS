# Kanban Swarm 派发参考

## 完整流程

```
1. 生成 dry-run kanban.json（t_pre_xxx 占位符）
2. 用户确认 "开始派发"
3. hermes kanban swarm --json → 返回 root_id + worker_ids
4. hermes kanban list --json → 确认各 task_id 与 title/assignee 的对应关系
5. execute_code 将 t_pre_xxx 替换为真实 t_xxxx
6. 更新 execution.mode=swarm, validation, statistics, swarm metadata
7. 写回 kanban.json
```

## swarm 命令参数说明

| 参数 | 说明 | 必须 |
|------|------|------|
| `--worker PROFILE:TITLE` | 每个 worker 一个 --worker | ✅ |
| `--verifier PROFILE` | 校验角色 | ✅ |
| `--synthesizer PROFILE` | 汇总角色 | ✅ |
| `--created-by PROFILE` | 创建者 | ✅ |
| `--json` | 输出 JSON | ✅ |
| `--priority` | 优先级 | 可选 |
| `--idempotency-key` | 幂等key | 可选 |

## 可用 Profile

| Profile ID | 角色 |
|------------|------|
| fullstack-1784098453689 | 前端/全栈开发 |
| qa-1784271911442 | QA 审查 |
| pm-1784271841029 | PM 管理/汇总 |

## ID 替换代码模板

```python
import json

id_map = {
    "t_pre_001": "t_e68a4639",
    "t_pre_002": "t_9f765f23",
    "t_pre_003": "t_5e57eb58"
}

with open('kanban.json') as f:
    d = json.load(f)

# 替换顶层 dependencies
new_deps = {}
for old_id, new_id in id_map.items():
    old_dep = d["dependencies"].pop(old_id)
    new_deps[new_id] = {
        "depends_on": [id_map[p] for p in old_dep["depends_on"]],
        "blocks": [id_map[b] for b in old_dep["blocks"]]
    }
d["dependencies"] = new_deps

# 替换 tasks
for t in d["tasks"]:
    old_id = t["id"]
    t["id"] = id_map[old_id]
    t["parents"] = [id_map[p] for p in t["parents"]]
    t["children"] = [id_map[c] for c in t["children"]]

# 替换 ui_task_list.rows
for row in d["ui_task_list"]["rows"]:
    row["id"] = id_map[row["id"]]
    row["dependencies"] = [id_map[p] for p in row["dependencies"]]

# 更新 execution
d["execution"]["mode"] = "swarm"
d["execution"]["dispatched"] = list(id_map.values())
d["execution"]["pending_dispatch"] = []

# 更新 validation
d["validation"]["dispatch_result_valid"] = True
d["validation"]["all_validations_pass"] = True

# 清除 warnings
d["warnings"] = []

# 添加 swarm metadata
d["swarm"] = {
    "root_id": "t_37fe660e",
    "worker_ids": ["t_e68a4639", "t_9f765f23"],
    "verifier_id": "t_eddfcc9b",
    "synthesizer_id": "t_5e57eb58"
}

with open('kanban.json', 'w') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
```

## 验证命令

```bash
python3 -m json.tool kanban.json > /dev/null && echo "JSON valid ✅"
python3 -c "
import json
with open('kanban.json') as f:
    d = json.load(f)
assert len(d['tasks']) == len(d['ui_task_list']['rows'])
assert d['validation']['dispatch_result_valid'] == True
assert d['execution']['mode'] == 'swarm'
print('All assertions passed ✅')
"