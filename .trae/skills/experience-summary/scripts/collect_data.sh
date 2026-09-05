#!/bin/bash
#
# experience-summary skill — 数据采集脚本
# 采集 Git 提交历史、看板任务记录、各 Profile 会话历史
# 输出三段数据，供 Agent 拼接为 Prompt 生成经验总结
#
# 用法: bash collect_data.sh [PROJECT_ROOT]
#   PROJECT_ROOT 默认为当前工作目录
#

set -o pipefail

PROJECT_ROOT="${1:-$(pwd)}"

# 1. Git 提交历史
echo "=== GIT LOG ==="
git -C "$PROJECT_ROOT" log --oneline -20 --format="%h %s (%an, %ar)" 2>/dev/null || echo "(无Git提交记录)"

# 2. 看板任务
echo ""
echo "=== KANBAN TASKS ==="
BOARD=$(python3 -c "import json; print(json.load(open('$PROJECT_ROOT/.hermes/agents.json'))['boards'])" 2>/dev/null)
if [ -n "$BOARD" ]; then
  hermes kanban --board "$BOARD" list --json 2>/dev/null | head -c 10000
  echo ""
else
  echo "(无看板任务记录)"
fi

# 3. 各 Profile 会话
# ⚠️ hermes sessions list 不支持 --profile 参数，必须直接读取 state.db
echo ""
echo "=== PROFILE SESSIONS ==="
AGENTS_JSON="$PROJECT_ROOT/.hermes/agents.json"
if [ -f "$AGENTS_JSON" ]; then
  python3 -c "
import json, sqlite3, os

with open('$AGENTS_JSON') as f:
    agents = json.load(f).get('agents', [])

for agent in agents:
    code = agent.get('code', '')
    name = agent.get('name', '')
    if not code:
        continue
    db_path = f'/home/hermes/.hermes/profiles/{code}/state.db'
    if not os.path.exists(db_path):
        print(f'--- Profile: {name} ({code}) ---')
        print('(无会话数据库)')
        print()
        continue
    try:
        conn = sqlite3.connect(db_path)
        cur = conn.cursor()
        cur.execute('SELECT id FROM sessions ORDER BY rowid DESC LIMIT 5')
        session_ids = [r[0] for r in cur.fetchall()]
        print(f'--- Profile: {name} ({code}) ---')
        for sid in session_ids:
            cur.execute(
                'SELECT role, content FROM messages WHERE session_id=? AND role IN (\"user\",\"assistant\") ORDER BY rowid DESC LIMIT 10',
                (sid,)
            )
            msgs = cur.fetchall()
            print(f'Session {sid}:')
            for role, content in msgs:
                text = (content or '')[:300]
                if text.strip():
                    print(f'  [{role}] {text}')
            print()
        conn.close()
    except Exception as e:
        print(f'--- Profile: {name} ({code}) ---')
        print(f'(读取失败: {e})')
        print()
" 2>/dev/null
else
  echo "(无 agents.json)"
fi
