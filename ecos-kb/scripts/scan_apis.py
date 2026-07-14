#!/usr/bin/env python3
"""扫描所有Controller → api-index.json
策略: 先剥离Javadoc注释 → 提取类级别@RequestMapping → 逐方法匹配
"""

import json, re
from pathlib import Path

SRC_ROOT = Path("/home/guorongxiao/databridge-v2")
OUTPUT = Path("/home/guorongxiao/ecos-kb/api-index.json")

def scan_java_files():
    endpoints = []
    java_files = [f for f in SRC_ROOT.rglob("*.java") if "target/" not in str(f)]

    for fpath in java_files:
        content = fpath.read_text(encoding="utf-8", errors="ignore")
        if not re.search(r"@(Rest|)Controller", content):
            continue

        # 1. 剥离Javadoc和块注释
        clean = re.sub(r'/\*.*?\*/', ' ', content, flags=re.DOTALL)
        # 剥离行注释
        clean = re.sub(r'//[^\n]*', '', clean)

        # 2. 提取类级别@RequestMapping的所有路径
        class_prefixes = []
        class_req = re.search(r'@RequestMapping\s*\((\{[^}]+\}|"[^"]+")', clean)
        if class_req:
            prefix_str = class_req.group(1)
            class_prefixes = re.findall(r'"([^"]+)"', prefix_str)
        if not class_prefixes:
            class_prefixes = [""]

        # 3. 模块名
        module = _extract_module(str(fpath))

        # 4. 逐方法匹配 —— 要求注解与public方法之间不超过3行
        method_pattern = re.compile(r"""
            @(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)
            \s*\(\s*(?:value\s*=\s*)?\{?\s*"([^"]+)"\s*\}?
            [^;]*?                                           # 注解剩余部分
            \n\s*                                            # 换行
            (?:@\w+(?:\s*\([^)]*\))?\s*\n\s*)*             # 可选的参数注解(0到多个)
            public\s+
            ((?:[\w\[\]<>,\s])+?)\s+                        # 返回类型(支持泛型)
            (\w+)\s*\(                                       # 方法名
        """, re.VERBOSE)

        for match in method_pattern.finditer(clean):
            ann_type = match.group(1)
            path = match.group(2)
            return_type = match.group(3).strip()
            method_name = match.group(4)

            http_method = ann_type.replace("Mapping", "").upper()

            # 提取参数列表
            params = []
            # 从方法名后找参数
            after_method = clean[match.end():]
            paren_depth = 0
            param_str = ""
            started = False
            for ch in after_method:
                if ch == '(':
                    paren_depth += 1
                    started = True
                elif ch == ')':
                    paren_depth -= 1
                    if paren_depth == 0 and started:
                        break
                elif started:
                    param_str += ch

            # 解析参数: @Annotation Type name
            for pm in re.finditer(r'(@\w+(?:\s*\([^)]*\))?\s+)?(\S+)\s+(\w+)\s*,?', param_str):
                annotation = pm.group(1).strip() if pm.group(1) else None
                ptype = pm.group(2)
                pname = pm.group(3)
                params.append({"type": ptype, "name": pname, "annotation": annotation})

            # 构建完整路径
            full_paths = []
            for prefix in class_prefixes:
                fp = prefix.rstrip("/") + "/" + path.lstrip("/")
                full_paths.append(fp.replace("//", "/"))

            endpoints.append({
                "module": module,
                "class": fpath.stem,
                "method": method_name,
                "httpMethod": http_method,
                "paths": full_paths,
                "returnType": return_type,
                "params": params
            })

    # 按模块统计
    by_module = {}
    for ep in endpoints:
        mod = ep["module"]
        by_module.setdefault(mod, []).append(ep)

    return {
        "totalEndpoints": len(endpoints),
        "totalControllers": len(set(ep["class"] for ep in endpoints)),
        "modules": {m: len(eps) for m, eps in sorted(by_module.items())},
        "endpoints": endpoints
    }

def _extract_module(filepath: str) -> str:
    m = re.search(r'databridge-v2/(databridge-\w+)/', filepath)
    return m.group(1) if m else "unknown"

if __name__ == "__main__":
    data = scan_java_files()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=2))
    print(f"✅ api-index.json: {data['totalEndpoints']} endpoints in {data['totalControllers']} controllers")
    for mod, count in data['modules'].items():
        print(f"   {mod}: {count} endpoints")
