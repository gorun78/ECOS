#!/usr/bin/env python3
"""扫描所有POM文件 → module-map.md"""

import re, xml.etree.ElementTree as ET
from pathlib import Path

SRC_ROOT = Path("/home/guorongxiao/databridge-v2")
OUTPUT = Path("/home/guorongxiao/ecos-kb/module-map.md")

def scan_modules():
    """扫描Maven模块结构"""
    root_pom = SRC_ROOT / "pom.xml"
    if not root_pom.exists():
        return {"modules": [], "root": {"artifactId": "unknown"}}

    # 解析根POM
    root = _parse_pom(root_pom)
    modules = []

    # 遍历所有子模块
    for pom_path in sorted(SRC_ROOT.rglob("pom.xml")):
        if "target/" in str(pom_path) or pom_path == root_pom:
            continue
        # 排除 test/resources/target 等非源码目录
        if any(skip in str(pom_path) for skip in ["/test/", "/resources/", "/target/"]):
            continue

        mod = _parse_pom(pom_path)

        # 统计Java文件数
        mod_dir = pom_path.parent
        java_count = len(list(mod_dir.rglob("*.java")))

        # 统计SQL文件数
        sql_count = len(list(mod_dir.rglob("*.sql")))

        modules.append({
            "name": mod["artifactId"],
            "path": str(pom_path.parent.relative_to(SRC_ROOT)),
            "groupId": mod["groupId"],
            "version": mod["version"],
            "packaging": mod["packaging"],
            "dependencies": mod["dependencies"],
            "javaFiles": java_count,
            "sqlFiles": sql_count
        })

    return {
        "root": {"artifactId": root["artifactId"], "version": root["version"]},
        "modules": modules
    }

def _parse_pom(pom_path):
    """解析POM XML"""
    try:
        tree = ET.parse(str(pom_path))
        root = tree.getroot()

        # namespace
        ns = ""
        if root.tag.startswith("{"):
            ns = root.tag.split("}")[0] + "}"

        artifactId = _el(root, ns, "artifactId")
        groupId = _el(root, ns, "groupId")
        if not groupId:
            # 查parent
            parent = root.find(f"{ns}parent")
            if parent:
                groupId = _el(parent, ns, "groupId")
        version = _el(root, ns, "version")
        if not version:
            parent = root.find(f"{ns}parent")
            if parent:
                version = _el(parent, ns, "version")
        packaging = _el(root, ns, "packaging") or "jar"

        # 提取依赖
        deps = []
        deps_el = root.find(f"{ns}dependencies")
        if deps_el:
            for dep in deps_el.findall(f"{ns}dependency"):
                dep_gid = _el(dep, ns, "groupId")
                dep_aid = _el(dep, ns, "artifactId")
                dep_scope = _el(dep, ns, "scope") or "compile"
                if dep_gid and dep_aid:
                    deps.append(f"{dep_gid}:{dep_aid}" + (f":{dep_scope}" if dep_scope != "compile" else ""))

        return {
            "artifactId": artifactId,
            "groupId": groupId,
            "version": version,
            "packaging": packaging,
            "dependencies": deps
        }
    except Exception as e:
        return {
            "artifactId": f"parse_error_{pom_path.parent.name}",
            "groupId": "", "version": "", "packaging": "",
            "dependencies": [f"⚠️ parse error: {e}"]
        }

def _el(root, ns, tag):
    """获取元素文本"""
    el = root.find(f"{ns}{tag}")
    return el.text.strip() if el is not None and el.text else None

if __name__ == "__main__":
    data = scan_modules()

    lines = []
    lines.append("# ECOS Maven 模块地图\n")
    lines.append(f"> 根: `{data['root']['artifactId']}` v{data['root']['version']}\n")
    lines.append(f"## 模块清单（{len(data['modules'])} 个）\n")
    lines.append("| 模块 | 路径 | 打包 | Java文件 | SQL文件 | 关键依赖 |")
    lines.append("|------|------|:----:|:--------:|:-------:|---------|")

    for mod in data["modules"]:
        deps_short = ", ".join(d for d in mod["dependencies"] if "databridge-" in d)[:80]
        lines.append(
            f"| `{mod['name']}` | `{mod['path']}` | {mod['packaging']} | "
            f"{mod['javaFiles']} | {mod['sqlFiles']} | {deps_short} |"
        )

    lines.append("")
    lines.append("## 依赖关系图\n")
    lines.append("```")
    for mod in data["modules"]:
        internal_deps = [d.split(":")[1] for d in mod["dependencies"] if "databridge-" in d and ":test" not in d]
        if internal_deps:
            for dep in internal_deps:
                lines.append(f"  [{mod['name']}] ──→ [{dep}]")
        else:
            lines.append(f"  [{mod['name']}] (无内部依赖)")
    lines.append("```")

    output = "\n".join(lines)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(output)
    print(f"✅ module-map.md: {len(data['modules'])} modules")
