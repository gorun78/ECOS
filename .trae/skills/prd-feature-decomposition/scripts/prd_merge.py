#!/usr/bin/env python3
"""合并 PRD 模块 segment JSON，输出功能清单 JSON，支持增量 delta 合并。"""
import os, json, argparse, shutil
from pathlib import Path
from typing import Optional

HEADER_FIELDS = [
    "id", "name", "module", "priority", "description",
    "acceptance_criteria", "sprint", "status", "review_result",
    "review_comment", "source_docs", "dependencies",
    "user_name", "create_time", "update_time", "change_of"
]


def merge_segments(segments_dir: str, output_json: str, delta_file: Optional[str] = None,
                   keep_segments: bool = False) -> None:
    seg_path = Path(segments_dir)
    if not seg_path.exists():
        raise FileNotFoundError(f"Segments目录不存在: {segments_dir}")

    all_features = []
    segment_files = sorted(seg_path.glob("*.json"))
    print(f"[INFO] 发现 {len(segment_files)} 个segment文件")

    for seg_file in segment_files:
        try:
            with open(seg_file, 'r', encoding='utf-8') as f:
                features = json.load(f)
                all_features.extend(features)
                print(f"[INFO] 读取segment: {seg_file.name}, 功能数 {len(features)}")
        except Exception as e:
            print(f"[WARN] 读取失败 {seg_file.name}: {e}")

    if delta_file:
        delta_path = Path(delta_file)
        if delta_path.exists():
            with open(delta_path, 'r', encoding='utf-8') as f:
                delta = json.load(f)
            delta_data = delta.get("delta", delta)
            all_features = _apply_delta(all_features, delta_data)
            print(f"[INFO] 已应用delta: +{len(delta_data.get('new_features',[]))}新增, "
                  f"~{len(delta_data.get('modified_features',[]))}修改, "
                  f"-{len(delta_data.get('deleted_features',[]))}删除")
        else:
            print(f"[WARN] delta文件不存在: {delta_file}，跳过")

    if not all_features:
        raise ValueError("未找到任何功能数据")

    # 已按分段文件名的序号前缀排列，保持该顺序（与文档模块顺序一致），仅作稳定兜底

    id_mapping = {}
    for idx, feat in enumerate(all_features, start=1):
        old_id = feat.get("id", "") or feat.get("feature_id", "")
        new_id = f"FEAT-{str(idx).zfill(4)}"
        feat["id"] = new_id
        id_mapping[old_id] = new_id
        if not feat.get("status"):
            feat["status"] = "待审查"
        if not feat.get("sprint"):
            feat["sprint"] = "Sprint 1"
        if "change_of" not in feat:
            feat["change_of"] = ""

    def _translate_deps(deps_str: str) -> str:
        if not deps_str:
            return ""
        parts = [d.strip() for d in deps_str.split(",")]
        return ",".join([id_mapping.get(p, p) for p in parts if p])

    for feat in all_features:
        feat["dependencies"] = _translate_deps(feat.get("dependencies", ""))

    for feat in all_features:
        for field in HEADER_FIELDS:
            if field not in feat:
                feat[field] = ""

    output_path = Path(output_json)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = output_path.with_suffix(".tmp")
    with open(tmp_path, 'w', encoding='utf-8') as f:
        json.dump(all_features, f, ensure_ascii=False, indent=2)
    os.replace(str(tmp_path), output_json)

    print(f"[INFO] 合并完成，共 {len(all_features)} 个功能")
    print(f"[INFO] 已保存至: {output_json}")

    if not keep_segments:
        try:
            # 删除 _tmp 父目录（segments_dir 通常是 .../_tmp/segments/）
            cleanup_path = seg_path.parent
            shutil.rmtree(cleanup_path)
            print(f"[INFO] 已清理临时目录: {cleanup_path}")
        except Exception as e:
            print(f"[WARN] 清理临时目录失败: {e}")


def _apply_delta(features: list, delta: dict) -> list:
    index = {}
    for f in features:
        fid = f.get("id", "") or f.get("feature_id", "")
        if fid:
            index[fid] = f
    for deleted in delta.get("deleted_features", []):
        original_id = deleted.get("change_of", "")
        index.pop(original_id, None)
    for modified in delta.get("modified_features", []):
        original_id = modified.get("change_of", "")
        if original_id in index:
            modified["id"] = modified.get("id", "")
            index[original_id] = modified
    new_features = delta.get("new_features", [])
    return list(index.values()) + new_features


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="合并PRD模块segment为功能清单JSON")
    parser.add_argument("--segments", "-s", required=True, help="segment JSON目录路径")
    parser.add_argument("--output", "-o", required=True, help="输出功能清单JSON路径")
    parser.add_argument("--delta", "-d", default=None, help="增量变更delta JSON文件路径（可选）")
    parser.add_argument("--keep-segments", "-k", action="store_true",
                        help="保留分段临时目录（默认合并后自动清理）")
    args = parser.parse_args()
    try:
        merge_segments(args.segments, args.output, args.delta,
                       keep_segments=args.keep_segments)
    except Exception as e:
        print(f"[ERROR] 合并失败: {e}")
        exit(1)