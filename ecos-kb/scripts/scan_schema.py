#!/usr/bin/env python3
"""扫描所有Flyway SQL文件 → data-model.json"""

import json, re
from pathlib import Path

SRC_ROOT = Path("/home/guorongxiao/databridge-v2")
OUTPUT = Path("/home/guorongxiao/ecos-kb/data-model.json")

def scan_flyway():
    tables = []

    # 找所有Flyway迁移文件 (glob V*__.sql 不支持双下划线匹配，改用 *.sql + 过滤)
    sql_files = list(SRC_ROOT.rglob("*.sql"))
    sql_files = [f for f in sql_files 
                 if "target/" not in str(f) and f.name.startswith("V") and "__" in f.name]

    for fpath in sorted(sql_files):
        content = fpath.read_text(encoding="utf-8", errors="ignore")

        # 提取CREATE TABLE — 用括号计数找匹配的)
        for match in re.finditer(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\(', content, re.IGNORECASE):
            table_name = match.group(1)
            start_pos = match.end()  # 右在 '(' 之后

            # 括号计数找匹配的 )
            depth = 1
            pos = start_pos
            while pos < len(content) and depth > 0:
                if content[pos] == '(':
                    depth += 1
                elif content[pos] == ')':
                    depth -= 1
                pos += 1

            body = content[start_pos:pos-1]  # pos在)之后，减1去掉)

            columns = []
            # 逐行解析列定义
            col_buf = ""
            for line in body.split('\n'):
                stripped = line.strip()
                if not stripped or stripped.startswith('--'):
                    continue
                # 跳过约束行
                if re.match(r'(CONSTRAINT|PRIMARY|UNIQUE|FOREIGN|INDEX|KEY|CHECK)\s', stripped, re.IGNORECASE):
                    continue
                col_buf += " " + stripped
                if stripped.endswith(','):
                    col_def = col_buf.strip().rstrip(',').strip()
                    col_match = re.match(r'(\w+)\s+(\S+(?:\(\d+(?:,\d+)?\))?)\s*(.*)', col_def, re.IGNORECASE)
                    if col_match:
                        columns.append({
                            "name": col_match.group(1),
                            "type": col_match.group(2).upper(),
                            "extra": col_match.group(3).strip() if col_match.group(3) else ""
                        })
                    col_buf = ""

            # 处理最后一行（无逗号结尾）
            if col_buf.strip():
                col_def = col_buf.strip()
                col_match = re.match(r'(\w+)\s+(\S+(?:\(\d+(?:,\d+)?\))?)\s*(.*)', col_def, re.IGNORECASE)
                if col_match:
                    columns.append({
                        "name": col_match.group(1),
                        "type": col_match.group(2).upper(),
                        "extra": col_match.group(3).strip() if col_match.group(3) else ""
                    })

            tables.append({
                "table": table_name,
                "source": str(fpath.name),
                "columns": columns,
                "columnCount": len(columns)
            })

    # 提取外键关系
    relationships = []
    for fpath in sql_files:
        content = fpath.read_text(encoding="utf-8", errors="ignore")
        
        # ALTER TABLE 外键
        alter_fk = re.finditer(
            r'ALTER\s+TABLE\s+(\w+)\s+ADD\s+CONSTRAINT\s+\w+\s+FOREIGN\s+KEY\s*\((\w+)\)\s*REFERENCES\s+(\w+)\s*\((\w+)\)',
            content, re.IGNORECASE)
        for match in alter_fk:
            relationships.append({
                "fromTable": match.group(1),
                "fromColumn": match.group(2),
                "toTable": match.group(3),
                "toColumn": match.group(4),
                "source": str(fpath.name)
            })
        
        # CREATE TABLE 内联外键
        inline_fk = re.finditer(
            r'CONSTRAINT\s+\w+\s+FOREIGN\s+KEY\s*\((\w+)\)\s*REFERENCES\s+(\w+)\s*\((\w+)\)',
            content, re.IGNORECASE)
        for match in inline_fk:
            from_col = match.group(1)
            to_table = match.group(2)
            to_col = match.group(3)
            # 需要找到这个FK属于哪个表 — 向上查找最近的CREATE TABLE
            before = content[:match.start()]
            table_match = re.findall(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)', before, re.IGNORECASE)
            from_table = table_match[-1] if table_match else "unknown"
            relationships.append({
                "fromTable": from_table,
                "fromColumn": from_col,
                "toTable": to_table,
                "toColumn": to_col,
                "source": str(fpath.name)
            })

    total_columns = sum(t["columnCount"] for t in tables)

    return {
        "totalTables": len(tables),
        "totalColumns": total_columns,
        "totalRelationships": len(relationships),
        "tables": tables,
        "relationships": relationships
    }

if __name__ == "__main__":
    data = scan_flyway()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=2))
    print(f"✅ data-model.json: {data['totalTables']} tables, {data['totalColumns']} columns, {data['totalRelationships']} relationships")
