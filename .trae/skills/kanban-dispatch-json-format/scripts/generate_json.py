#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
根据 JSON Schema 生成 JSON 实例数据。

支持：
- object / array / string / integer / number / boolean / null
- enum / const / oneOf / anyOf
- prefixItems（按位置生成元组/固定数组）
- required
- minimum / maximum / exclusiveMinimum / exclusiveMaximum / multipleOf
- minLength / maxLength
- minItems / maxItems / uniqueItems
- 常见 format：email、date、date-time、uri、uuid、ipv4、hostname
- 批量生成根对象数组
- 根对象 id 字段自动递增
- 从已有 JSON 实例反推同结构、不同值的 JSON Schema
"""

import argparse
import json
import math
import random
import string
import sys
import uuid
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any


class JsonInstanceGenerator:
    """根据 JSON Schema 模板生成 JSON 实例数据。"""

    def __init__(
            self,
            max_depth: int = 20,
            optional_probability: float = 0.7,
            start_id: int = 1,
    ) -> None:
        if max_depth < 0:
            raise ValueError("max_depth 不能小于 0")

        if not 0 <= optional_probability <= 1:
            raise ValueError("optional_probability 必须在 0 到 1 之间")

        self.max_depth = max_depth
        self.optional_probability = optional_probability
        self.next_root_id = start_id

    def generate(
            self,
            schema: dict[str, Any],
            current_depth: int = 0,
            *,
            property_name: str | None = None,
            is_root: bool = False,
    ) -> Any:
        """根据 Schema 生成单个值。"""

        if current_depth > self.max_depth:
            return None

        if not isinstance(schema, dict):
            raise TypeError("Schema 节点必须是 JSON 对象")

        if "const" in schema:
            return schema["const"]

        if "enum" in schema:
            enum_values = schema["enum"]
            if not isinstance(enum_values, list) or not enum_values:
                raise ValueError("enum 必须是非空数组")
            return random.choice(enum_values)

        if "oneOf" in schema:
            candidates = schema["oneOf"]
            if not isinstance(candidates, list) or not candidates:
                raise ValueError("oneOf 必须是非空数组")
            return self.generate(
                random.choice(candidates),
                current_depth,
                property_name=property_name,
                is_root=is_root,
            )

        if "anyOf" in schema:
            candidates = schema["anyOf"]
            if not isinstance(candidates, list) or not candidates:
                raise ValueError("anyOf 必须是非空数组")
            return self.generate(
                random.choice(candidates),
                current_depth,
                property_name=property_name,
                is_root=is_root,
            )

        schema_type = schema.get("type")

        if isinstance(schema_type, list):
            allowed_types = [item for item in schema_type if item != "null"]
            if not allowed_types:
                return None

            if "null" in schema_type and random.random() < 0.15:
                return None

            selected_schema = dict(schema)
            selected_schema["type"] = random.choice(allowed_types)
            return self.generate(
                selected_schema,
                current_depth,
                property_name=property_name,
                is_root=is_root,
            )

        if schema_type is None:
            schema_type = self._infer_type(schema)

        if schema_type == "object":
            return self._generate_object(schema, current_depth, is_root=is_root)

        if schema_type == "array":
            return self._generate_array(schema, current_depth)

        if schema_type == "string":
            return self._generate_string(schema)

        if schema_type == "integer":
            if is_root and property_name == "id":
                value = self.next_root_id
                self.next_root_id += 1
                return value
            return self._generate_integer(schema)

        if schema_type == "number":
            return self._generate_number(schema)

        if schema_type == "boolean":
            return random.choice([True, False])

        if schema_type == "null":
            return None

        return None

    def generate_records(
            self,
            schema: dict[str, Any],
            count: int,
            *,
            always_array: bool = True,
    ) -> Any:
        """批量生成根实例。"""

        if count < 1:
            raise ValueError("count 必须大于等于 1")

        records = [
            self.generate(schema, current_depth=0, is_root=True)
            for _ in range(count)
        ]

        if always_array or count > 1:
            return records

        return records[0]

    def _generate_object(
            self,
            schema: dict[str, Any],
            current_depth: int,
            *,
            is_root: bool,
    ) -> dict[str, Any]:
        obj: dict[str, Any] = {}

        properties = schema.get("properties", {})
        required_fields = set(schema.get("required", []))

        if not isinstance(properties, dict):
            raise ValueError("properties 必须是 JSON 对象")

        for key, prop_schema in properties.items():
            if not isinstance(prop_schema, dict):
                continue

            should_generate = (
                    key in required_fields
                    or random.random() < self.optional_probability
            )

            if not should_generate:
                continue

            obj[key] = self.generate(
                prop_schema,
                current_depth + 1,
                property_name=key,
                # 只有根对象的直接 id 属性参与自动递增，避免把嵌套
                # 对象中的 id 误判为根记录 id。
                is_root=is_root and key == "id",
                )

        return obj

    def _generate_array(
            self,
            schema: dict[str, Any],
            current_depth: int,
    ) -> list[Any]:
        prefix_items = schema.get("prefixItems")
        if prefix_items is not None:
            if not isinstance(prefix_items, list):
                raise ValueError("prefixItems 必须是 JSON 数组")

            result = []
            for item_schema in prefix_items:
                if not isinstance(item_schema, dict):
                    raise ValueError("prefixItems 中的每一项都必须是 JSON 对象")
                result.append(self.generate(item_schema, current_depth + 1))

            return result

        items_schema = schema.get("items")

        if not isinstance(items_schema, dict):
            return []

        min_items = self._as_non_negative_int(schema.get("minItems", 1), "minItems")
        max_items = self._as_non_negative_int(
            schema.get("maxItems", max(min_items, 3)),
            "maxItems",
        )

        if max_items < min_items:
            raise ValueError(
                f"数组范围无效：minItems={min_items}, maxItems={max_items}"
            )

        length = random.randint(min_items, max_items)
        unique_items = bool(schema.get("uniqueItems", False))

        if not unique_items:
            return [
                self.generate(items_schema, current_depth + 1)
                for _ in range(length)
            ]

        result: list[Any] = []
        seen: set[str] = set()
        max_attempts = max(100, length * 20)

        for _ in range(max_attempts):
            if len(result) >= length:
                break

            value = self.generate(items_schema, current_depth + 1)
            marker = json.dumps(value, ensure_ascii=False, sort_keys=True)

            if marker not in seen:
                seen.add(marker)
                result.append(value)

        return result

    def _generate_string(self, schema: dict[str, Any]) -> str:
        string_format = schema.get("format")

        if string_format == "email":
            return f"user{random.randint(1, 999999)}@example.com"

        if string_format == "date":
            return date.today().isoformat()

        if string_format == "date-time":
            return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace(
                "+00:00", "Z"
            )

        if string_format in {"uri", "url"}:
            return f"https://example.com/{self._random_text(8)}"

        if string_format == "uuid":
            return str(uuid.uuid4())

        if string_format == "ipv4":
            return ".".join(str(random.randint(1, 254)) for _ in range(4))

        if string_format == "hostname":
            return f"{self._random_text(8).lower()}.example.com"

        min_length = self._as_non_negative_int(
            schema.get("minLength", 1),
            "minLength",
        )
        max_length = self._as_non_negative_int(
            schema.get("maxLength", max(min_length, 8)),
            "maxLength",
        )

        if max_length < min_length:
            raise ValueError(
                f"字符串长度范围无效：minLength={min_length}, maxLength={max_length}"
            )

        length = random.randint(min_length, max_length)
        return self._random_text(length)

    def _generate_integer(self, schema: dict[str, Any]) -> int:
        minimum = int(math.ceil(schema.get("minimum", 1)))
        maximum = int(math.floor(schema.get("maximum", 100)))

        if "exclusiveMinimum" in schema:
            minimum = int(math.floor(schema["exclusiveMinimum"])) + 1

        if "exclusiveMaximum" in schema:
            maximum = int(math.ceil(schema["exclusiveMaximum"])) - 1

        if minimum > maximum:
            raise ValueError(
                f"整数范围无效：minimum={minimum}, maximum={maximum}"
            )

        multiple_of = schema.get("multipleOf")

        if multiple_of is None:
            return random.randint(minimum, maximum)

        multiple_of = int(multiple_of)
        if multiple_of <= 0:
            raise ValueError("integer 的 multipleOf 必须大于 0")

        first = math.ceil(minimum / multiple_of)
        last = math.floor(maximum / multiple_of)

        if first > last:
            raise ValueError("给定范围内不存在满足 multipleOf 的整数")

        return random.randint(first, last) * multiple_of

    def _generate_number(self, schema: dict[str, Any]) -> float:
        minimum = float(schema.get("minimum", 1.0))
        maximum = float(schema.get("maximum", 100.0))

        if "exclusiveMinimum" in schema:
            minimum = math.nextafter(
                float(schema["exclusiveMinimum"]),
                math.inf,
            )

        if "exclusiveMaximum" in schema:
            maximum = math.nextafter(
                float(schema["exclusiveMaximum"]),
                -math.inf,
            )

        if minimum > maximum:
            raise ValueError(
                f"数字范围无效：minimum={minimum}, maximum={maximum}"
            )

        multiple_of = schema.get("multipleOf")

        if multiple_of is None:
            return round(random.uniform(minimum, maximum), 2)

        multiple_of = float(multiple_of)
        if multiple_of <= 0:
            raise ValueError("number 的 multipleOf 必须大于 0")

        first = math.ceil(minimum / multiple_of)
        last = math.floor(maximum / multiple_of)

        if first > last:
            raise ValueError("给定范围内不存在满足 multipleOf 的数字")

        return round(random.randint(first, last) * multiple_of, 10)

    @staticmethod
    def _infer_type(schema: dict[str, Any]) -> str | None:
        if "properties" in schema:
            return "object"
        if "items" in schema:
            return "array"
        return None

    @staticmethod
    def _random_text(length: int) -> str:
        alphabet = string.ascii_letters + string.digits
        return "".join(random.choices(alphabet, k=length))

    @staticmethod
    def _as_non_negative_int(value: Any, field_name: str) -> int:
        try:
            result = int(value)
        except (TypeError, ValueError) as exc:
            raise ValueError(f"{field_name} 必须是整数") from exc

        if result < 0:
            raise ValueError(f"{field_name} 不能小于 0")

        return result


def load_schema(path: Path) -> dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as file:
            schema = json.load(file)
    except FileNotFoundError as exc:
        raise RuntimeError(f"找不到模板文件：{path}") from exc
    except json.JSONDecodeError as exc:
        raise RuntimeError(
            f"模板不是有效 JSON：{path}，第 {exc.lineno} 行，第 {exc.colno} 列"
        ) from exc
    except OSError as exc:
        raise RuntimeError(f"读取模板失败：{path}，原因：{exc}") from exc

    if not isinstance(schema, dict):
        raise RuntimeError("JSON Schema 根节点必须是对象")

    return schema


def resolve_local_refs(schema: dict[str, Any]) -> dict[str, Any]:
    """展开 JSON Schema 中的本地 $ref，供内置生成器和校验器使用。"""
    root = schema

    def lookup(ref: str) -> Any:
        if not ref.startswith("#/"):
            raise ValueError(f"仅支持本地 JSON Pointer $ref：{ref}")
        current: Any = root
        for raw_part in ref[2:].split("/"):
            part = raw_part.replace("~1", "/").replace("~0", "~")
            if not isinstance(current, dict) or part not in current:
                raise ValueError(f"无法解析 $ref：{ref}")
            current = current[part]
        return current

    def expand(node: Any, resolving: tuple[str, ...] = ()) -> Any:
        if isinstance(node, list):
            return [expand(item, resolving) for item in node]
        if not isinstance(node, dict):
            return node
        if "$ref" in node:
            ref = node["$ref"]
            if not isinstance(ref, str):
                raise ValueError("$ref 必须是字符串")
            if ref in resolving:
                raise ValueError(f"检测到循环 $ref：{ref}")
            target = expand(lookup(ref), resolving + (ref,))
            if not isinstance(target, dict):
                raise ValueError(f"$ref 目标必须是对象：{ref}")
            siblings = {
                key: expand(value, resolving)
                for key, value in node.items()
                if key != "$ref"
            }
            return {**target, **siblings}
        return {
            key: expand(value, resolving)
            for key, value in node.items()
        }

    resolved = expand(schema)
    if not isinstance(resolved, dict):
        raise ValueError("展开后的 Schema 根节点必须是对象")
    return resolved


def load_json(path: Path) -> Any:
    """读取任意 JSON 值，用于从实例反推模板。"""
    try:
        with path.open("r", encoding="utf-8") as file:
            return json.load(file)
    except FileNotFoundError as exc:
        raise RuntimeError(f"找不到示例文件：{path}") from exc
    except json.JSONDecodeError as exc:
        raise RuntimeError(
            f"示例不是有效 JSON：{path}，第 {exc.lineno} 行，第 {exc.colno} 列"
        ) from exc
    except OSError as exc:
        raise RuntimeError(f"读取示例失败：{path}，原因：{exc}") from exc


def infer_schema(value: Any, *, exact_values: bool = False) -> dict[str, Any]:
    """从 JSON 值反推 Schema；默认只保留结构和类型。"""
    if isinstance(value, dict):
        properties = {
            key: infer_schema(item, exact_values=exact_values)
            for key, item in value.items()
        }
        return {
            "type": "object",
            "properties": properties,
            "required": list(value.keys()),
            "additionalProperties": False,
        }

    if isinstance(value, list):
        schema: dict[str, Any] = {
            "type": "array",
            "items": False,
            "minItems": len(value),
            "maxItems": len(value),
        }
        # Draft 2020-12 要求 prefixItems 至少包含一项；空数组只需用
        # items:false 和 maxItems:0 表达。
        if value:
            schema["prefixItems"] = [
                infer_schema(item, exact_values=exact_values)
                for item in value
            ]
        return schema

    if value is None:
        return {"type": "null"}

    # bool 必须放在 int 前面判断，因为 Python 中 bool 是 int 的子类。
    if isinstance(value, bool):
        schema = {"type": "boolean"}
        if exact_values:
            schema["const"] = value
        return schema

    if isinstance(value, int):
        schema = {"type": "integer"}
        if exact_values:
            schema["const"] = value
        elif value == 0:
            schema.update({"minimum": 0, "maximum": 100})
        elif value > 0:
            digits = len(str(value))
            schema.update({
                "minimum": 1 if digits == 1 else 10 ** (digits - 1),
                "maximum": 10 ** digits - 1,
            })
        else:
            digits = len(str(abs(value)))
            schema.update({
                "minimum": -(10 ** digits - 1),
                "maximum": -(1 if digits == 1 else 10 ** (digits - 1)),
            })
        return schema

    if isinstance(value, float):
        schema = {"type": "number"}
        if exact_values:
            schema["const"] = value
        return schema

    if isinstance(value, str):
        schema = {"type": "string"}
        if exact_values:
            schema["const"] = value
        elif not value:
            # 空字符串只有一个可能值，因此仍会生成空字符串。
            schema.update({"minLength": 0, "maxLength": 0})
        else:
            # 保持与示例相近的字符串长度，同时允许生成不同内容。
            length = len(value)
            schema.update({
                "minLength": max(1, length // 2),
                "maxLength": max(1, length * 2),
            })
        return schema

    raise TypeError(f"不支持的 JSON 值类型：{type(value).__name__}")


def validate_instance(value: Any, schema: dict[str, Any], path: str = "$") -> list[str]:
    """校验外部提供的实例值，返回便于自然语言代理修正的错误列表。"""
    errors: list[str] = []

    if "const" in schema and value != schema["const"]:
        return [f"{path}: 必须等于 {schema['const']!r}"]

    if "enum" in schema and value not in schema["enum"]:
        return [f"{path}: 必须是 enum 中的一个值"]

    schema_type = schema.get("type")
    if isinstance(schema_type, list):
        if value is None and "null" in schema_type:
            return []
        candidates = [item for item in schema_type if item != "null"]
        for candidate in candidates:
            candidate_schema = dict(schema)
            candidate_schema["type"] = candidate
            if not validate_instance(value, candidate_schema, path):
                return []
        return [f"{path}: 类型不属于 {schema_type}"]

    if schema_type == "null":
        return [] if value is None else [f"{path}: 必须是 null"]

    if schema_type == "boolean":
        return [] if isinstance(value, bool) else [f"{path}: 必须是 boolean"]

    if schema_type == "integer":
        if not isinstance(value, int) or isinstance(value, bool):
            return [f"{path}: 必须是 integer"]
        if "minimum" in schema and value < schema["minimum"]:
            errors.append(f"{path}: 不能小于 {schema['minimum']}")
        if "maximum" in schema and value > schema["maximum"]:
            errors.append(f"{path}: 不能大于 {schema['maximum']}")
        return errors

    if schema_type == "number":
        if not isinstance(value, (int, float)) or isinstance(value, bool):
            return [f"{path}: 必须是 number"]
        return errors

    if schema_type == "string":
        if not isinstance(value, str):
            return [f"{path}: 必须是 string"]
        if "minLength" in schema and len(value) < schema["minLength"]:
            errors.append(f"{path}: 长度不能小于 {schema['minLength']}")
        if "maxLength" in schema and len(value) > schema["maxLength"]:
            errors.append(f"{path}: 长度不能大于 {schema['maxLength']}")
        return errors

    if schema_type == "array":
        if not isinstance(value, list):
            return [f"{path}: 必须是 array"]
        if "minItems" in schema and len(value) < schema["minItems"]:
            errors.append(f"{path}: 元素数量不能小于 {schema['minItems']}")
        if "maxItems" in schema and len(value) > schema["maxItems"]:
            errors.append(f"{path}: 元素数量不能大于 {schema['maxItems']}")
        prefix_items = schema.get("prefixItems", [])
        for index, item_schema in enumerate(prefix_items):
            if index < len(value):
                errors.extend(validate_instance(value[index], item_schema, f"{path}[{index}]"))
        items_schema = schema.get("items")
        if isinstance(items_schema, dict):
            for index in range(len(prefix_items), len(value)):
                errors.extend(validate_instance(value[index], items_schema, f"{path}[{index}]"))
        elif items_schema is False and len(value) > len(prefix_items):
            errors.append(f"{path}: 不允许超过 {len(prefix_items)} 个元素")
        return errors

    if schema_type == "object":
        if not isinstance(value, dict):
            return [f"{path}: 必须是 object"]
        properties = schema.get("properties", {})
        if "minProperties" in schema and len(value) < schema["minProperties"]:
            errors.append(f"{path}: 字段数量不能小于 {schema['minProperties']}")
        if "maxProperties" in schema and len(value) > schema["maxProperties"]:
            errors.append(f"{path}: 字段数量不能大于 {schema['maxProperties']}")
        for key in schema.get("required", []):
            if key not in value:
                errors.append(f"{path}.{key}: 缺少必填字段")
        additional_schema = schema.get("additionalProperties")
        for key in value:
            if key not in properties:
                if additional_schema is False:
                    errors.append(f"{path}.{key}: 不允许的字段")
                elif isinstance(additional_schema, dict):
                    errors.extend(
                        validate_instance(value[key], additional_schema, f"{path}.{key}")
                    )
        for key, child_schema in properties.items():
            if key in value:
                errors.extend(validate_instance(value[key], child_schema, f"{path}.{key}"))
        return errors

    return errors


def save_json(path: Path, data: Any, compact: bool) -> None:
    try:
        path.parent.mkdir(parents=True, exist_ok=True)

        temp_path = path.with_name(path.name + ".tmp")

        with temp_path.open("w", encoding="utf-8") as file:
            json.dump(
                data,
                file,
                ensure_ascii=False,
                indent=None if compact else 2,
                separators=(",", ":") if compact else None,
            )

        temp_path.replace(path)
    except OSError as exc:
        raise RuntimeError(f"写入输出文件失败：{path}，原因：{exc}") from exc


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="根据 JSON Schema 模板生成 JSON 实例数据"
    )

    parser.add_argument(
        "template",
        type=Path,
        nargs="?",
        help="输入的 JSON Schema 模板文件路径",
    )

    parser.add_argument(
        "--infer-template",
        type=Path,
        metavar="EXAMPLE_JSON",
        help="从示例 JSON 反推同结构、不同值的模板，并通过 -o 写出",
    )

    parser.add_argument(
        "--exact-values",
        action="store_true",
        help="反推模板时使用 const 固定所有值（默认不固定）",
    )

    parser.add_argument(
        "--values",
        type=Path,
        metavar="VALUES_JSON",
        help="使用已构建的完整参数值生成实例；写出前按模板校验",
    )

    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=Path("kanban.json"),
        help="输出 JSON 文件路径，默认：kanban.json",
    )

    parser.add_argument(
        "-n",
        "--count",
        type=int,
        default=1,
        help="生成根实例数量，默认：1",
    )

    parser.add_argument(
        "--start-id",
        type=int,
        default=1,
        help="根对象整数 id 字段起始值，默认：1",
    )

    parser.add_argument(
        "--max-depth",
        type=int,
        default=20,
        help="最大递归深度，默认：20",
    )

    parser.add_argument(
        "--optional-probability",
        type=float,
        default=0.7,
        help="可选字段生成概率，范围 0~1，默认：0.7",
    )

    parser.add_argument(
        "--single-object",
        action="store_true",
        help=argparse.SUPPRESS,
    )

    parser.add_argument(
        "--always-array",
        action="store_true",
        help="即使 count=1，也将根实例放入数组中",
    )

    parser.add_argument(
        "--compact",
        action="store_true",
        help="输出紧凑 JSON，不进行缩进格式化",
    )

    parser.add_argument(
        "--seed",
        type=int,
        default=None,
        help="随机种子；指定后可重复生成相同结果",
    )

    return parser.parse_args()


def main() -> int:
    args = parse_args()

    if args.infer_template is not None and args.template is not None:
        print("错误：template 与 --infer-template 不能同时使用", file=sys.stderr)
        return 2

    if args.infer_template is not None and args.values is not None:
        print("错误：--infer-template 与 --values 不能同时使用", file=sys.stderr)
        return 2

    if args.values is not None and args.count != 1:
        print("错误：--values 模式仅支持 count=1", file=sys.stderr)
        return 2

    if args.infer_template is None and args.template is None:
        print("错误：必须提供 template 或 --infer-template", file=sys.stderr)
        return 2

    if args.count < 1:
        print("错误：count 必须大于等于 1", file=sys.stderr)
        return 2

    if args.start_id < 0:
        print("错误：start-id 不能小于 0", file=sys.stderr)
        return 2

    if args.max_depth < 0:
        print("错误：max-depth 不能小于 0", file=sys.stderr)
        return 2

    if not 0 <= args.optional_probability <= 1:
        print(
            "错误：optional-probability 必须在 0 到 1 之间",
            file=sys.stderr,
        )
        return 2

    if args.seed is not None:
        random.seed(args.seed)

    try:
        if args.infer_template is not None:
            example_data = load_json(args.infer_template)
            schema = infer_schema(
                example_data,
                exact_values=args.exact_values,
            )
            schema = {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                **schema,
            }
            save_json(args.output, schema, args.compact)
            print(f"成功生成模板文件：{args.output}")
            return 0

        schema = resolve_local_refs(load_schema(args.template))

        if args.values is not None:
            instance_data = load_json(args.values)
            validation_errors = validate_instance(instance_data, schema)
            if validation_errors:
                details = "\n".join(
                    f"  - {message}"
                    for message in validation_errors[:20]
                )
                if len(validation_errors) > 20:
                    details += f"\n  - 另有 {len(validation_errors) - 20} 个错误"
                raise RuntimeError(f"参数值不符合模板：\n{details}")
            save_json(args.output, instance_data, args.compact)
            print(f"成功根据参数值生成实例文件：{args.output}")
            return 0

        generator = JsonInstanceGenerator(
            max_depth=args.max_depth,
            optional_probability=args.optional_probability,
            start_id=args.start_id,
        )

        instance_data = generator.generate_records(
            schema,
            args.count,
            # 单条记录默认保持 Schema 的根类型；--single-object 是为兼容
            # 旧命令行保留的无操作参数。
            always_array=args.always_array and not args.single_object,
        )

        save_json(args.output, instance_data, args.compact)

    except (RuntimeError, ValueError, TypeError) as exc:
        print(f"错误：{exc}", file=sys.stderr)
        return 1

    print(f"成功生成实例文件：{args.output}")
    print(f"生成数量：{args.count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
