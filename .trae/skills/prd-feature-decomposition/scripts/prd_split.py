#!/usr/bin/env python3
"""按 Markdown 标题层级拆分 PRD 文档为独立模块文件。"""
import os, re, json, argparse
from pathlib import Path


def split_prd(input_file: str, output_dir: str) -> None:
    input_path = Path(input_file)
    if not input_path.exists():
        raise FileNotFoundError(f"输入文件不存在: {input_file}")

    output_path = Path(output_dir)
    module_list_path = output_path / "_module_list.json"

    if module_list_path.exists():
        with open(module_list_path, 'r', encoding='utf-8') as f:
            meta = json.load(f)
        if meta.get("source_file") == str(input_path):
            print(f"[INFO] 缓存命中，跳过拆分（{meta.get('total_modules', 0)} 个模块）")
            return

    output_path.mkdir(parents=True, exist_ok=True)
    with open(input_path, 'r', encoding='utf-8') as f:
        content = f.read()

    line_starts = [0]
    for i, ch in enumerate(content):
        if ch == '\n':
            line_starts.append(i + 1)
    if line_starts[-1] < len(content):
        line_starts.append(len(content) + 1)

    def _offset_to_line(offset: int) -> int:
        lo, hi = 0, len(line_starts) - 1
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if line_starts[mid] <= offset:
                lo = mid
            else:
                hi = mid - 1
        return lo + 1

    modules = []
    current_module_id = 1
    # 只匹配指定层级的标题（默认 ### 三级标题，对应模块级章节如 "### 3.3.1 项目管理"）
    # 不同文档的模块标题层级可能不同，可通过 --heading-level 参数调整
    pattern = re.compile(r'(^#{1,3}\s.+$)', re.MULTILINE)
    matches = list(pattern.finditer(content))

    if not matches:
        module_name = input_path.stem
        module_file = output_path / f"MOD-{str(current_module_id).zfill(3)}_{module_name}.md"
        with open(module_file, 'w', encoding='utf-8') as f:
            f.write(content)
        total_lines = len(line_starts)
        modules.append({
            "module_id": f"MOD-{str(current_module_id).zfill(3)}",
            "name": module_name,
            "file": str(module_file.relative_to(output_path)),
            "start_line": 1,
            "end_line": total_lines,
            "line_count": total_lines,
            "char_count": len(content)
        })
        print(f"[INFO] 文件无标题层级，作为单个模块处理")
    else:
        for i, match in enumerate(matches):
            title = match.group(1).strip()
            heading_level = len(title) - len(title.lstrip('#'))
            title_text = title.lstrip('#').strip()
            safe_title = re.sub(r'[\\\\/:*?\"<>|]', '_', title_text)
            module_id = f"MOD-{str(current_module_id).zfill(3)}"
            start_pos = match.start()
            end_pos = matches[i + 1].start() if i < len(matches) - 1 else len(content)
            module_content = content[start_pos:end_pos].strip() + '\n'
            module_file = output_path / f"{module_id}_{safe_title}.md"
            with open(module_file, 'w', encoding='utf-8') as f:
                f.write(module_content)
            start_line = _offset_to_line(start_pos)
            line_count = module_content.count('\n')
            end_line = start_line + line_count - 1
            modules.append({
                "module_id": module_id,
                "name": title_text,
                "heading_level": heading_level,
                "file": str(module_file.relative_to(output_path)),
                "start_line": start_line,
                "end_line": end_line,
                "line_count": line_count,
                "char_count": len(module_content)
            })
            current_module_id += 1
            print(f"[INFO] 拆分模块: {module_id} - {title_text} ({line_count}行)")

    with open(module_list_path, 'w', encoding='utf-8') as f:
        json.dump({
            "source_file": str(input_path),
            "total_modules": len(modules),
            "modules": modules
        }, f, ensure_ascii=False, indent=2)
    print(f"[INFO] 拆分完成，共生成 {len(modules)} 个模块")
    print(f"[INFO] 模块清单已保存至: {module_list_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="按Markdown标题层级拆分PRD文档")
    parser.add_argument("--input", "-i", required=True, help="输入PRD文件路径")
    parser.add_argument("--output", "-o", required=True, help="输出模块目录路径")
    args = parser.parse_args()
    try:
        split_prd(args.input, args.output)
    except Exception as e:
        print(f"[ERROR] 拆分失败: {e}")
        exit(1)