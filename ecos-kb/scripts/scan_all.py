#!/usr/bin/env python3
"""一键刷新所有ECOS知识库索引"""
import subprocess, sys
from pathlib import Path

SCRIPTS = Path(__file__).parent

scripts = [
    ("scan_apis.py", "API端点索引"),
    ("scan_schema.py", "数据模型"),
    ("scan_modules.py", "模块地图"),
]

for script, desc in scripts:
    print(f"🔍 {desc}...")
    result = subprocess.run([sys.executable, str(SCRIPTS / script)], capture_output=True, text=True)
    if result.returncode == 0:
        print(result.stdout)
    else:
        print(f"❌ {script} failed:\n{result.stderr}")
        sys.exit(1)

print("\n✅ ECOS知识库刷新完成")
print(f"   /home/guorongxiao/ecos-kb/api-index.json")
print(f"   /home/guorongxiao/ecos-kb/data-model.json")
print(f"   /home/guorongxiao/ecos-kb/module-map.md")
