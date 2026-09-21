#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MySQL 8 转储 → PostgreSQL 16 转换器（ECOS 数据工作台 · ecos_demo 演示数据导入）。

用途
----
把 Navicat 导出的 MySQL 转储（单库多表：DDL + INSERT）转换为可直接在 PostgreSQL 上执行的
脚本：剥离 MySQL 专有语法、映射列类型、重写字符串与二进制字面量，并输出「逐表行数核对」
与「有损/降级」清单。全程流式处理，140MB 级转储不会整体载入内存。

用法
----
    python mysql_to_pg_dump.py --source touzi.sql --out-dir D:\\temp\\ecos-touzi
    # 可选：转换后直接经 docker exec 导入（宿主机无需安装 psql）
    python mysql_to_pg_dump.py --source touzi.sql --out-dir D:\\temp\\ecos-touzi \\
        --import-container ecos-postgres --database sys_man

产物（全部写入 --out-dir，不写入仓库）
    ecos_demo_import.sql     合并后的最终 PG 脚本（DDL 在前、数据在后，可直接 -f 导入）
    ecos_demo_ddl.sql        仅 DDL（DROP/CREATE/ALTER ADD CONSTRAINT FK）
    ecos_demo_data.sql       仅数据 INSERT
    rowcounts_source.csv     源 dump 逐表 INSERT 行数（供与 PG count(*) 逐表核对）
    conversion_report.json   转换统计与「有损/降级」清单
    import.log               使用 --import-container 时的 psql 输出
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import shutil
import subprocess
import sys
from collections import OrderedDict

DEFAULT_SCHEMA = "ecos_demo"
DEFAULT_DB_USER = "postgres"

# ---------------------------------------------------------------------------
# MySQL 字面量转义表（MySQL 8 手册 "String Literals"）
#   \% 与 \_ 在 MySQL 中保留反斜杠（供 LIKE 使用），此处保持原样以保真
# ---------------------------------------------------------------------------
MYSQL_ESCAPES = {
    "0": "\0",
    "b": "\b",
    "n": "\n",
    "r": "\r",
    "t": "\t",
    "Z": "\x1a",
    "\\": "\\",
    "'": "'",
    '"': '"',
    "%": "\\%",
    "_": "\\_",
}

# 字符串字面量（含转义）匹配：用于 DEFAULT 取值解析
QUOTED_STRING_RE = r"'(?:[^'\\]|\\.|'')*'"

# 值字面量匹配（按优先级排列）；命名分组便于定位
VALUE_RE = re.compile(
    r"(?P<str>'(?:[^'\\]|\\.|'')*')"
    r"|(?P<hex>0[xX][0-9A-Fa-f]+)"
    rf"|(?P<hexstr>[xX]{QUOTED_STRING_RE})"
    r"|(?P<bit>[bB]'[01]+')"
    r"|(?P<num>[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?)"
    r"|(?P<null>NULL)"
)

# DDL 语句与注释
CREATE_TABLE_RE = re.compile(r"^\s*CREATE\s+TABLE\s+`(?P<name>[^`]+)`", re.I)
DROP_OBJECT_RE = re.compile(r"^\s*DROP\s+(?P<kind>TABLE|VIEW)\s+IF\s+EXISTS\s+`(?P<name>[^`]+)`", re.I)
CREATE_VIEW_RE = re.compile(r"^\s*CREATE\s+.*?\bVIEW\s+`(?P<name>[^`]+)`", re.I)
INSERT_RE = re.compile(r"^\s*INSERT\s+INTO\s+`(?P<name>[^`]+)`\s*VALUES\s*", re.I)
COLUMN_RE = re.compile(r"^`(?P<name>[^`]+)`\s+(?P<rest>.+)$")
TYPE_RE = re.compile(r"^(?P<base>[A-Za-z]+)\s*(?:\((?P<args>[^)]*)\))?$")
ENUM_MEMBER_RE = re.compile(QUOTED_STRING_RE)

# 需要原样丢弃的 MySQL 会话级语句
SKIP_STMT_RE = re.compile(
    r"^\s*(SET\s+NAMES|SET\s+FOREIGN_KEY_CHECKS|LOCK\s+TABLES|UNLOCK\s+TABLES"
    r"|START\s+TRANSACTION|COMMIT|BEGIN|ALTER\s+TABLE\s+.*\bDISABLE\s+KEYS)\b",
    re.I,
)


class ConvertError(Exception):
    """转换期不可恢复错误（附带语句起始行号）。"""


# ---------------------------------------------------------------------------
# 字符串编解码
# ---------------------------------------------------------------------------
def decode_mysql_string(raw: str) -> tuple[str, int]:
    """把 MySQL 单引号字面量（含两端引号）解码为真实文本。

    返回 (文本, NUL 个数)。NUL 无法存入 PG text，调用方负责剔除并计数。
    """
    body = raw[1:-1]
    nuls = 0
    if "\\" not in body:
        text = body.replace("''", "'")
    else:
        out = []
        i = 0
        n = len(body)
        while i < n:
            ch = body[i]
            if ch == "\\":
                if i + 1 >= n:  # 理论上不会出现：转义符位于末尾
                    out.append("\\")
                    i += 1
                    continue
                nxt = body[i + 1]
                out.append(MYSQL_ESCAPES.get(nxt, nxt))
                i += 2
                continue
            if ch == "'" and i + 1 < n and body[i + 1] == "'":
                out.append("'")
                i += 2
                continue
            out.append(ch)
            i += 1
        # 注意：循环内已按字符逐个消解 '' → '（见上方 `ch == "'"` 分支），
        # 此处不得再对结果整体 replace("''", "'")——那会把相邻两个转义单引号
        # （如 'a\'\'b' → a''b）错误压缩为一个。
        text = "".join(out)
    if "\0" in text:
        nuls = text.count("\0")
        text = text.replace("\0", "")
    return text, nuls


def encode_pg_string(text: str) -> str:
    """按 PG 规则重发（standard_conforming_strings=on）：单引号双写，反斜杠按字面保留。"""
    return "'" + text.replace("'", "''") + "'"


# ---------------------------------------------------------------------------
# 统计容器
# ---------------------------------------------------------------------------
class Stats:
    """转换统计 + 有损点登记。"""

    def __init__(self) -> None:
        self.tables: "OrderedDict[str, dict]" = OrderedDict()
        self.counters: dict[str, int] = {}
        self.notes: dict[str, list] = {}

    def bump(self, key: str, delta: int = 1) -> None:
        self.counters[key] = self.counters.get(key, 0) + delta

    def note(self, key: str, value) -> None:
        self.notes.setdefault(key, []).append(value)

    def table(self, name: str) -> dict:
        return self.tables.setdefault(
            name,
            {
                "table": name,
                "columns": 0,
                "source_insert_statements": 0,
                "source_rows": 0,
                "hex_literals": 0,
                "nuls_replaced": 0,
                "bytea_columns": [],
                "boolean_columns": [],
                "identity_columns": [],
                "foreign_keys": 0,
            },
        )


# ---------------------------------------------------------------------------
# 语句切分（流式）
# ---------------------------------------------------------------------------
def _scan_quote_state(line: str, in_str: bool) -> bool:
    """扫描一行，返回扫描结束时是否仍处于单引号字符串内部。"""
    i = 0
    n = len(line)
    while i < n:
        ch = line[i]
        if in_str:
            if ch == "\\":
                i += 2
                continue
            if ch == "'":
                if i + 1 < n and line[i + 1] == "'":
                    i += 2
                    continue
                in_str = False
            i += 1
        else:
            if ch == "'":
                in_str = True
            i += 1
    return in_str


def iter_statements(path: str, stats: Stats):
    """流式产出 (语句文本, 起始行号)；跳过注释与空行。"""
    buf: list[str] = []
    in_str = False
    in_block_comment = False
    start_line = 0
    with io.open(path, "r", encoding="utf-8", errors="replace", newline="") as fh:
        for lineno, line in enumerate(fh, 1):
            if not in_str and not in_block_comment:
                stripped = line.lstrip()
                if stripped.startswith("--"):
                    continue
                if stripped.startswith("/*"):
                    if "*/" not in stripped:
                        in_block_comment = True
                    continue
                if not stripped.strip():
                    continue
            if in_block_comment:
                if "*/" in line:
                    in_block_comment = False
                continue

            if not buf:
                start_line = lineno
            buf.append(line)
            in_str = _scan_quote_state(line, in_str)
            if not in_str and line.rstrip().endswith(";"):
                stmt = "".join(buf).strip()
                buf = []
                if stmt:
                    yield stmt, start_line
    if buf:
        stats.note("warnings", f"文件末尾存在未以分号结束的残留内容（起始行 {start_line}），已忽略")


# ---------------------------------------------------------------------------
# DDL 转换
# ---------------------------------------------------------------------------
def _cut_comment(rest: str, stats: Stats, table: str, column: str) -> str:
    """删除列级 COMMENT '...' 子句（登记说明：PG 无列注释等价物，未转 COMMENT ON）。"""
    pattern = re.compile(r"\s+COMMENT\s+" + QUOTED_STRING_RE, re.I)
    while True:
        m = pattern.search(rest)
        if not m:
            return rest
        stats.bump("comment_clause_removed")
        rest = rest[: m.start()] + rest[m.end():]


def map_type(spec: str, stats: Stats, table: str, column: str) -> tuple[str, dict]:
    """MySQL 列类型 → PG 列类型；返回 (PG 类型, 列属性)。"""
    info = {"bytea": False, "boolean": False, "identity": False}
    s = spec.strip()
    unsigned = bool(re.search(r"\bunsigned\b", s, re.I))
    s = re.sub(r"\b(unsigned|zerofill)\b", "", s, flags=re.I).strip()
    m = TYPE_RE.match(s)
    if not m:
        raise ConvertError(f"{table}.{column}: 无法识别的列类型 {spec!r}")
    base = m.group("base").lower()
    args = (m.group("args") or "").strip()
    arg_parts = [p.strip() for p in args.split(",")] if args else []
    where = f"{table}.{column}"

    def widened(pg_plain: str, pg_unsigned: str) -> str:
        if unsigned:
            stats.note("unsigned_widened", f"{where}: {spec} -> {pg_unsigned}")
            return pg_unsigned
        return pg_plain

    if base == "tinyint":
        if args == "1" and not unsigned:
            info["boolean"] = True
            stats.note("tinyint1_to_boolean", where)
            return "boolean", info
        return widened("smallint", "smallint"), info
    if base == "smallint":
        return widened("smallint", "integer"), info
    if base == "mediumint":
        return widened("integer", "bigint"), info
    if base in ("int", "integer"):
        return widened("integer", "bigint"), info
    if base == "bigint":
        if unsigned:
            stats.note("unsigned_widened", f"{where}: {spec} -> numeric(20)")
            return "numeric(20)", info
        return "bigint", info
    if base in ("decimal", "numeric"):
        return (f"numeric({args})" if args else "numeric"), info
    if base == "double":
        stats.note("float_mapping", f"{where}: {spec} -> double precision")
        return "double precision", info
    if base == "float":
        stats.note("float_mapping", f"{where}: {spec} -> real（精度语义收窄）")
        return "real", info
    if base == "real":
        return "real", info
    if base == "bit":
        if args == "1":
            info["boolean"] = True
            stats.note("bit1_to_boolean", where)
            return "boolean", info
        return (f"bit({args})" if args else "bit(1)"), info
    if base in ("char", "character"):
        return f"char({args})" if args else "char(1)", info
    if base in ("varchar", "nvarchar"):
        return f"varchar({args})" if args else "varchar", info
    if base in ("longtext", "mediumtext", "tinytext", "text", "longvarchar", "clob"):
        return "text", info
    if base in ("longblob", "mediumblob", "tinyblob", "blob", "binary", "varbinary", "bytea"):
        info["bytea"] = True
        stats.note("blob_to_bytea", f"{where}: {spec} -> bytea")
        return "bytea", info
    if base == "json":
        stats.note("json_to_jsonb", where)
        return "jsonb", info
    if base == "enum":
        members = [decode_mysql_string(x)[0] for x in ENUM_MEMBER_RE.findall(args)]
        maxlen = max((len(x) for x in members), default=1)
        stats.note("enum_to_varchar", f"{where}: {spec} -> varchar({maxlen})，取值={members}")
        return f"varchar({maxlen})", info
    if base == "set":
        members = [decode_mysql_string(x)[0] for x in ENUM_MEMBER_RE.findall(args)]
        stats.note("set_to_text", f"{where}: {spec} -> text，取值={members}")
        return "text", info
    if base == "date":
        return "date", info
    if base in ("datetime", "timestamp"):
        return f"timestamp({args})" if args else "timestamp", info
    if base == "time":
        return f"time({args})" if args else "time", info
    if base == "year":
        stats.note("year_to_smallint", where)
        return "smallint", info
    if base in ("boolean", "bool"):
        info["boolean"] = True
        return "boolean", info
    raise ConvertError(f"{where}: 未覆盖的列类型 {base!r}（原文 {spec!r}）")


def convert_column(coldef: str, stats: Stats, table: str) -> tuple[str, dict]:
    """把一行 MySQL 列定义转成 PG 列定义；返回 (SQL 片段, 列信息)。"""
    m = COLUMN_RE.match(coldef)
    if not m:
        raise ConvertError(f"{table}: 无法解析的列定义 {coldef!r}")
    column = m.group("name")
    rest = m.group("rest").rstrip().rstrip(",")

    rest = _cut_comment(rest, stats, table, column)

    # 字符集/排序规则（PG 由数据库级 collation 决定，列级声明删除）
    if re.search(r"\bCHARACTER\s+SET\b|\bCOLLATE\b", rest, re.I):
        stats.bump("charset_collate_removed")
        rest = re.sub(r"\s+CHARACTER\s+SET\s+\w+", "", rest, flags=re.I)
        rest = re.sub(r"\s+COLLATE\s+\w+", "", rest, flags=re.I)

    # AUTO_INCREMENT -> GENERATED BY DEFAULT AS IDENTITY
    identity = bool(re.search(r"\bAUTO_INCREMENT\b", rest, re.I))
    if identity:
        stats.bump("auto_increment_to_identity")
        rest = re.sub(r"\s*\bAUTO_INCREMENT\b", "", rest, flags=re.I)

    # ON UPDATE CURRENT_TIMESTAMP 子句（PG 不支持，删除并登记）
    if re.search(r"\bON\s+UPDATE\s+CURRENT_TIMESTAMP", rest, re.I):
        stats.note("on_update_current_timestamp", f"{table}.{column}")
        rest = re.sub(r"\s*\bON\s+UPDATE\s+CURRENT_TIMESTAMP(?:\(\d+\))?", "", rest, flags=re.I)

    # DEFAULT 子句
    default_sql = ""
    pending_numeric: str | None = None  # 数值型 DEFAULT（boolean 列需改写为 true/false）
    pending_bool_text: str | None = None  # 字符串 '0'/'1' 型 DEFAULT
    mdef = re.search(rf"\bDEFAULT\s+({QUOTED_STRING_RE}|b'[01]'|[^\s,]+)", rest, re.I)
    if mdef:
        raw_default = mdef.group(1)
        rest = rest[: mdef.start()] + rest[mdef.end():]
        low = raw_default.lower()
        if low == "null":
            stats.bump("default_null_removed")
        elif low in ("b'0'", "b'1'"):
            stats.bump("default_bit_to_boolean")
            default_sql = " DEFAULT false" if low == "b'0'" else " DEFAULT true"
        elif raw_default.startswith("'"):
            text, _ = decode_mysql_string(raw_default)
            stats.bump("default_string_reescaped")
            default_sql = " DEFAULT " + encode_pg_string(text)
            if text in ("0", "1"):
                pending_bool_text = text
        elif re.fullmatch(r"[-+]?\d+", raw_default):
            pending_numeric = raw_default
            default_sql = " DEFAULT " + raw_default
        elif re.match(r"^current_timestamp", low):
            default_sql = " DEFAULT " + raw_default.upper()
        else:
            default_sql = " DEFAULT " + raw_default

    # 空值约束
    notnull = False
    mnull = re.search(r"\b(NOT\s+NULL|NULL)\b", rest, re.I)
    if mnull:
        notnull = mnull.group(1).upper().startswith("NOT")
        rest = rest[: mnull.start()] + rest[mnull.end():]

    pg_type, info = map_type(rest, stats, table, column)
    info["identity"] = identity

    # tinyint(1)/bit(1) -> boolean 时，0/1 默认值必须改写为 false/true
    if info["boolean"] and (pending_numeric is not None or pending_bool_text is not None):
        flag = pending_numeric if pending_numeric is not None else pending_bool_text
        is_true = flag.lstrip("+-") != "0"
        default_sql = " DEFAULT true" if is_true else " DEFAULT false"
        stats.note("boolean_default_rewritten", f"{table}.{column}: DEFAULT {flag} -> {is_true}")

    pieces = ['"' + column + '"', pg_type]
    if default_sql:
        pieces.append(default_sql.strip())
    if notnull:
        pieces.append("NOT NULL")
    if identity:
        pieces.append("GENERATED BY DEFAULT AS IDENTITY")
    return "  " + " ".join(pieces), info


def normalize_column_list(raw: str) -> str:
    """`a` ASC, `b` DESC -> "a", "b"（去反引号/排序方向/USING BTREE）。"""
    raw = re.sub(r"\s+USING\s+\w+", "", raw, flags=re.I)
    cols = [c.strip() for c in raw.split(",")]
    out = []
    for c in cols:
        c = re.sub(r"\s+(ASC|DESC)\s*$", "", c, flags=re.I).strip()
        c = c.strip("`")
        out.append('"' + c + '"')
    return ", ".join(out)


def take_paren_group(text: str, start: int) -> tuple[str, int]:
    """取 text 中 start 处 '(' 起的配对括号内容（跳过字符串字面量）；返回 (内容, 右括号下标)。"""
    depth = 0
    inner_start = start + 1
    i = start
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "'":
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "'":
                    break
                i += 1
        elif ch == "(":
            depth += 1
            if depth == 1:
                inner_start = i + 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return text[inner_start:i], i
        i += 1
    raise ConvertError(f"括号不配对：{text[:80]!r}")


def convert_key_line(line: str, table: str, stats: Stats) -> tuple[str | None, str | None]:
    """转换表内键/约束行；返回 (表内 SQL 片段, 表级 ALTER 语句)。"""
    t = line.strip().rstrip(",")
    # 键级 COMMENT（可能含括号）先摘除，避免干扰括号配对
    t = re.sub(r"\s+COMMENT\s+" + QUOTED_STRING_RE + r"\s*$", "", t, flags=re.I)
    upper = t.upper()

    if upper.startswith("PRIMARY KEY"):
        stats.bump("primary_key_kept")
        cols_raw, _ = take_paren_group(t, t.index("("))
        return f"  PRIMARY KEY ({normalize_column_list(cols_raw)})", None

    if upper.startswith("UNIQUE KEY") or upper.startswith("UNIQUE INDEX"):
        cols_raw, _ = take_paren_group(t, t.index("("))
        if "(" in cols_raw:  # 前缀索引（MySQL 专有）→ 无法等价表达
            stats.bump("unique_prefix_index_dropped")
            stats.note("unique_prefix_index_dropped", f"{table}: {t}")
            return None, None
        stats.bump("unique_key_kept")
        return f"  UNIQUE ({normalize_column_list(cols_raw)})", None

    if re.match(r"^(KEY|INDEX|FULLTEXT\s+KEY|SPATIAL\s+KEY)\b", upper):
        stats.bump("plain_index_dropped")
        stats.note("dropped_indexes", f"{table}: {t}")
        return None, None

    if upper.startswith("CONSTRAINT"):
        mname = re.match(r"CONSTRAINT\s+`([^`]+)`\s+(.*)$", t, re.I | re.S)
        if not mname:
            raise ConvertError(f"{table}: 无法解析约束行 {t!r}")
        cname, body = mname.group(1), mname.group(2)
        if re.search(r"FOREIGN\s+KEY", body, re.I):
            mfk = re.match(
                r"FOREIGN\s+KEY\s*\(([^)]*)\)\s*REFERENCES\s*`([^`]+)`\s*\(([^)]*)\)(.*)$",
                body,
                re.I | re.S,
            )
            if not mfk:
                raise ConvertError(f"{table}: 无法解析外键 {t!r}")
            cols = normalize_column_list(mfk.group(1))
            ref_table = mfk.group(2)
            ref_cols = normalize_column_list(mfk.group(3))
            tail = " ".join(mfk.group(4).split())
            if not re.match(r"^ON\s+DELETE\s+(RESTRICT|CASCADE|SET\s+NULL|NO\s+ACTION)"
                            r"(\s+ON\s+UPDATE\s+(RESTRICT|CASCADE|SET\s+NULL|NO\s+ACTION))?$", tail, re.I):
                if tail:
                    raise ConvertError(f"{table}: 未覆盖的外键尾部子句 {tail!r}")
            stats.bump("foreign_key_kept")
            alter = (
                f'ALTER TABLE "{table}" ADD CONSTRAINT "{cname}" FOREIGN KEY ({cols}) '
                f'REFERENCES "{ref_table}" ({ref_cols}){(" " + tail) if tail else ""};'
            )
            return None, alter
        if re.search(r"\bCHECK\b", body, re.I):
            stats.note("check_kept", f"{table}: {cname}")
            expr = body[body.upper().index("CHECK"):]
            expr = expr.replace("`", '"')
            return f"  CONSTRAINT \"{cname}\" {expr}", None
        raise ConvertError(f"{table}: 未覆盖的约束类型 {t!r}")

    if upper.startswith("FULLTEXT") or upper.startswith("SPATIAL"):
        stats.bump("plain_index_dropped")
        stats.note("dropped_indexes", f"{table}: {t}")
        return None, None

    raise ConvertError(f"{table}: 未覆盖的表内行 {t!r}")


def convert_create_table(stmt: str, stats: Stats, lineno: int) -> tuple[str, list[str], dict]:
    """把 CREATE TABLE 语句转成 PG 版本；返回 (DDL 文本, FK ALTER 列表, 表定义)。"""
    m = CREATE_TABLE_RE.match(stmt)
    if not m:
        raise ConvertError(f"第 {lineno} 行: 无法解析 CREATE TABLE 语句")
    table = m.group("name")
    tinfo = stats.table(table)

    lines = stmt.splitlines()
    body_cols: list[str] = []
    alter_fks: list[str] = []
    columns: list[dict] = []
    tail_started = False
    for raw in lines[1:]:
        line = raw.strip()
        if not line:
            continue
        if line.startswith(")"):
            tail_started = True
            break
        if line.startswith("`"):
            coldef = line.rstrip().rstrip(",")
            sql, info = convert_column(coldef, stats, table)
            columns.append({"name": COLUMN_RE.match(coldef).group("name"), **info})
            body_cols.append(sql)
            continue
        inner, alter = convert_key_line(line, table, stats)
        if inner:
            body_cols.append(inner)
        if alter:
            alter_fks.append(alter)
    if not tail_started:
        raise ConvertError(f"{table}: CREATE TABLE 缺少收尾括号（第 {lineno} 行起）")

    tinfo["columns"] = len(columns)
    tinfo["bytea_columns"] = [c["name"] for c in columns if c["bytea"]]
    tinfo["boolean_columns"] = [c["name"] for c in columns if c["boolean"]]
    tinfo["identity_columns"] = [c["name"] for c in columns if c["identity"]]
    tinfo["foreign_keys"] = len(alter_fks)
    for c in columns:
        if c["identity"]:
            stats.note("identity_columns", f"{table}.{c['name']}")

    ddl = (
        f'DROP TABLE IF EXISTS "{table}" CASCADE;\n'
        f'CREATE TABLE "{table}" (\n' + ",\n".join(body_cols) + "\n);"
    )
    return ddl, alter_fks, {"table": table, "columns": columns}


# ---------------------------------------------------------------------------
# DML 转换
# ---------------------------------------------------------------------------
def convert_value(match: re.Match, col: dict, table: str, stats: Stats) -> str:
    """单个值字面量 → PG 字面量。col 为该列的类型信息（按位置对齐）。"""
    raw = match.group(0)
    group = match.lastgroup
    if group == "str":
        text, nuls = decode_mysql_string(raw)
        if nuls:
            stats.table(table)["nuls_replaced"] += nuls
            stats.bump("nul_replaced", nuls)
        return encode_pg_string(text)
    if group == "null":
        return "NULL"
    if group == "num":
        if col["boolean"]:  # tinyint(1) -> boolean：0/1 必须转为 false/true
            is_true = raw.lstrip("+-") != "0"
            stats.bump("boolean_value_rewritten")
            return "true" if is_true else "false"
        return raw
    if group == "hex":
        hexdigits = raw[2:]
    elif group == "hexstr":
        hexdigits = raw[2:-1].replace(" ", "")
    elif group == "bit":
        bits = raw[2:-1]
        value = int(bits, 2) if bits else 0
        if col["boolean"]:
            return "true" if value else "false"
        return str(value)
    else:
        raise ConvertError(f"{table}: 未覆盖的值字面量 {raw[:40]!r}")
    stats.table(table)["hex_literals"] += 1
    stats.bump("hex_literal")
    raw_bytes = bytes.fromhex(hexdigits)
    if col["bytea"]:
        return "'\\x" + raw_bytes.hex() + "'::bytea"
    if col["boolean"]:
        return "true" if int.from_bytes(raw_bytes, "big") else "false"
    if col["pg_type"] in ("integer", "bigint", "smallint", "numeric", "numeric(20)", "real", "double precision"):
        return str(int.from_bytes(raw_bytes, "big"))
    text = raw_bytes.decode("utf-8", errors="replace")
    stats.note("hex_decoded_to_text", f"{table}.{col['name']}: 目标列非 bytea，十六进制按 UTF-8 解码")
    return encode_pg_string(text)


def convert_insert(
    stmt: str,
    tables: dict[str, dict],
    stats: Stats,
    lineno: int,
) -> str:
    """把 INSERT 语句逐行转换为 PG INSERT（保持原始分组与行数）。"""
    m = INSERT_RE.match(stmt)
    if not m:
        raise ConvertError(f"第 {lineno} 行: 无法解析 INSERT 语句（仅支持 INSERT INTO `t` VALUES）")
    table = m.group("name")
    if table not in tables:
        raise ConvertError(f"第 {lineno} 行: 表 {table} 的 CREATE TABLE 尚未解析，无法确定列类型")
    columns = tables[table]["columns"]
    ncols = len(columns)

    s = stmt
    end = s.rindex(";")
    pos = m.end()
    tuples: list[str] = []
    rows = 0
    while pos < end:
        while pos < end and s[pos] in " \t\r\n":
            pos += 1
        if pos >= end:
            break
        if s[pos] != "(":
            raise ConvertError(f"第 {lineno} 行: 第 {rows + 1} 个元组不是以 '(' 开始（表 {table}）")
        pos += 1
        values: list[str] = []
        while True:
            while pos < end and s[pos] in " \t\r\n":
                pos += 1
            vm = VALUE_RE.match(s, pos, end)
            if not vm:
                raise ConvertError(
                    f"第 {lineno} 行: 表 {table} 第 {rows + 1} 行第 {len(values) + 1} 列取值无法解析 —— "
                    f"{s[pos:pos + 60]!r}"
                )
            idx = len(values)
            if idx >= ncols:
                raise ConvertError(f"第 {lineno} 行: 表 {table} 第 {rows + 1} 行取值个数超过列数（{ncols}）")
            values.append(convert_value(vm, columns[idx], table, stats))
            pos = vm.end()
            while pos < end and s[pos] in " \t\r\n":
                pos += 1
            ch = s[pos] if pos < end else ""
            if ch == ")":
                pos += 1
                break
            if ch == ",":
                pos += 1
                continue
            raise ConvertError(f"第 {lineno} 行: 表 {table} 取值分隔符异常 {ch!r}")
        if len(values) != ncols:
            raise ConvertError(
                f"第 {lineno} 行: 表 {table} 第 {rows + 1} 行取值个数 {len(values)} != 列数 {ncols}"
            )
        tuples.append("(" + ", ".join(values) + ")")
        rows += 1
        while pos < end and s[pos] in " \t\r\n":
            pos += 1
        if pos < end and s[pos] == ",":
            pos += 1
            continue
        if pos >= end:
            break
        raise ConvertError(f"第 {lineno} 行: 表 {table} 元组分隔符异常 {s[pos]!r}")

    tinfo = stats.table(table)
    tinfo["source_insert_statements"] += 1
    tinfo["source_rows"] += rows
    return f'INSERT INTO "{table}" VALUES ' + ", ".join(tuples) + ";"


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
BANNER_TEMPLATE = """-- ============================================================================
-- MySQL -> PostgreSQL 演示数据导入脚本（由 scripts/mysql_to_pg_dump.py 生成）
-- 源：{source_name}
-- 目标：库 {database} · schema {schema}
-- 生成时间：{generated_at}
-- 说明：本脚本由 Navicat MySQL 转储机械转换而来，仅用于演示/测试环境。
--       执行顺序：SESSION 设置 -> DROP/CREATE TABLE -> ALTER ADD FK -> INSERT -> 收尾
-- ============================================================================

"""

# 会话设置：DDL 段与数据段各自携带一份，保证任一文件都能独立导入
SESSION_SETUP = """SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET search_path TO {schema};
SET DateStyle = 'ISO, MDY';
-- 批量导入加速：仅影响本会话；崩溃时可能丢失最近若干提交（测试库可接受）
SET synchronous_commit = off;
-- 导入期间关闭外键/触发器校验（导完 RESET），否则 MySQL 侧的历史脏数据会中断导入
SET session_replication_role = replica;

"""

DATA_BANNER = """-- ============================================================================
-- 数据段（自带 search_path 等会话设置，可独立导入）
-- ============================================================================

"""

FOOTER_TEMPLATE = """
-- ----------------------------------------------------------------------------
-- 收尾：恢复触发器校验 + 同步 IDENTITY 序列（对齐 MySQL AUTO_INCREMENT 语义）
-- ----------------------------------------------------------------------------
RESET session_replication_role;
"""


def build_footer(identity_pairs: list[tuple[str, str]]) -> str:
    """生成收尾段：恢复触发器校验 + 把 IDENTITY 序列推到当前最大值。

    注意 pg_get_serial_sequence 的两个入参按「裸名称」解析（不做 PG 标识符去引号），
    故此处传原始表名/列名，不做双引号包装。
    """
    lines = [FOOTER_TEMPLATE]
    for table, column in identity_pairs:
        lines.append(
            f"SELECT setval(pg_get_serial_sequence('{table}', '{column}'), "
            f"GREATEST(COALESCE(MAX(\"{column}\"), 1), 1)) FROM \"{table}\";"
        )
    return "\n".join(lines) + "\n"


def convert(source: str, out_dir: str, schema: str, database: str, generated_at: str) -> dict:
    os.makedirs(out_dir, exist_ok=True)
    ddl_path = os.path.join(out_dir, "ecos_demo_ddl.sql")
    data_path = os.path.join(out_dir, "ecos_demo_data.sql")
    combined_path = os.path.join(out_dir, "ecos_demo_import.sql")

    stats = Stats()
    tables: dict[str, dict] = {}
    table_order: list[str] = []
    alter_fks: list[str] = []
    identity_pairs: list[tuple[str, str]] = []

    with io.open(ddl_path, "w", encoding="utf-8", newline="\n") as fddl, \
            io.open(data_path, "w", encoding="utf-8", newline="\n") as fdata:
        fddl.write(
            BANNER_TEMPLATE.format(
                source_name=os.path.basename(source),
                database=database,
                schema=schema,
                generated_at=generated_at,
            )
        )
        fddl.write(SESSION_SETUP.format(schema=schema))
        fdata.write(DATA_BANNER)
        fdata.write(SESSION_SETUP.format(schema=schema))
        buffered = 0
        for stmt, lineno in iter_statements(source, stats):
            if CREATE_TABLE_RE.match(stmt):
                ddl, fks, tdef = convert_create_table(stmt, stats, lineno)
                tables[tdef["table"]] = tdef
                table_order.append(tdef["table"])
                alter_fks.extend(fks)
                for col in tdef["columns"]:
                    if col["identity"]:
                        identity_pairs.append((tdef["table"], col["name"]))
                fddl.write(ddl + "\n\n")
                continue
            if INSERT_RE.match(stmt):
                fdata.write(convert_insert(stmt, tables, stats, lineno) + "\n")
                buffered += 1
                if buffered % 5000 == 0:
                    fdata.flush()
                continue
            md = DROP_OBJECT_RE.match(stmt)
            if md:
                if md.group("kind").upper() == "VIEW":
                    stats.note("views_dropped", md.group("name"))
                # TABLE 的 DROP 由 CREATE TABLE 转换时统一输出
                continue
            if CREATE_VIEW_RE.match(stmt):
                stats.note("views_skipped", CREATE_VIEW_RE.match(stmt).group("name"))
                continue
            if SKIP_STMT_RE.match(stmt):
                stats.bump("mysql_preamble_skipped")
                stats.note("preamble_skipped", " ".join(stmt.split())[:80])
                continue
            raise ConvertError(f"第 {lineno} 行: 未覆盖的语句类型 —— {' '.join(stmt.split())[:100]!r}")

        # FK 语句集中输出在 DDL 末尾：必须先建齐所有表，且空表校验天然通过
        for alter in alter_fks:
            fddl.write(alter + "\n")
        fddl.write("\n")
        fdata.write(build_footer(identity_pairs))

    # 合并为单一可导入脚本
    with io.open(combined_path, "wb") as fout:
        for part in (ddl_path, data_path):
            with io.open(part, "rb") as fin:
                shutil.copyfileobj(fin, fout, 1024 * 1024)

    # 逐表行数核对表
    csv_path = os.path.join(out_dir, "rowcounts_source.csv")
    with io.open(csv_path, "w", encoding="utf-8", newline="\n") as fcsv:
        fcsv.write("table,source_insert_statements,source_rows,hex_literals,nuls_replaced,columns\n")
        for name in table_order:
            t = stats.tables[name]
            fcsv.write(
                f"{name},{t['source_insert_statements']},{t['source_rows']},"
                f"{t['hex_literals']},{t['nuls_replaced']},{t['columns']}\n"
            )

    total_rows = sum(stats.tables[n]["source_rows"] for n in table_order)
    report = {
        "source": os.path.abspath(source),
        "source_size_bytes": os.path.getsize(source),
        "out_dir": os.path.abspath(out_dir),
        "schema": schema,
        "database": database,
        "generated_at": generated_at,
        "tables_total": len(table_order),
        "source_rows_total": total_rows,
        "source_insert_statements_total": sum(
            stats.tables[n]["source_insert_statements"] for n in table_order
        ),
        "views_skipped": stats.notes.get("views_skipped", []),
        "tables": [stats.tables[n] for n in table_order],
        "counters": stats.counters,
        "lossy": {
            k: v for k, v in stats.notes.items() if k not in ("views_dropped", "preamble_skipped")
        },
        "preamble_skipped": stats.notes.get("preamble_skipped", []),
        "artifacts": {},
    }
    for path in (ddl_path, data_path, combined_path, csv_path):
        report["artifacts"][os.path.basename(path)] = os.path.getsize(path)

    report_path = os.path.join(out_dir, "conversion_report.json")
    with io.open(report_path, "w", encoding="utf-8") as fjson:
        json.dump(report, fjson, ensure_ascii=False, indent=2)
    report["report_path"] = report_path
    return report


def run_import(container: str, database: str, user: str, out_dir: str, log_path: str) -> int:
    """经 docker exec 把 DDL、数据依次灌入容器（宿主机无需 psql）。"""
    rc = 0
    with io.open(log_path, "w", encoding="utf-8", newline="\n") as log:
        for name, stop in (("ecos_demo_ddl.sql", "1"), ("ecos_demo_data.sql", "0")):
            path = os.path.join(out_dir, name)
            cmd = [
                "docker", "exec", "-i", container,
                "psql", "-U", user, "-d", database, "-v", f"ON_ERROR_STOP={stop}", "-f", "-",
            ]
            log.write(f"\n===== psql < {name} (ON_ERROR_STOP={stop}) =====\n")
            log.flush()
            with io.open(path, "rb") as fin:
                proc = subprocess.run(cmd, stdin=fin, stdout=log, stderr=subprocess.STDOUT)
            if proc.returncode != 0:
                log.write(f"[ERROR] {name} 导入退出码 {proc.returncode}\n")
                rc = proc.returncode
                break
    return rc


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="MySQL 转储 → PostgreSQL 转换器（流式）")
    parser.add_argument("--source", required=True, help="MySQL 转储 .sql 路径（只读）")
    parser.add_argument("--out-dir", required=True, help="产物目录（建议 $env:TEMP 下的独立目录）")
    parser.add_argument("--schema", default=DEFAULT_SCHEMA, help=f"目标 schema（默认 {DEFAULT_SCHEMA}）")
    parser.add_argument("--database", default="sys_man", help="目标库名（默认 sys_man）")
    parser.add_argument("--generated-at", default="", help="脚本头生成时间戳（可复现构建用）")
    parser.add_argument("--import-container", default="", help="转换后经 docker exec 导入的容器名（可选）")
    parser.add_argument("--db-user", default=DEFAULT_DB_USER, help=f"psql 用户（默认 {DEFAULT_DB_USER}）")
    args = parser.parse_args(argv)

    if not os.path.isfile(args.source):
        print(f"[FATAL] 源文件不存在：{args.source}", file=sys.stderr)
        return 2

    try:
        report = convert(
            source=args.source,
            out_dir=args.out_dir,
            schema=args.schema,
            database=args.database,
            generated_at=args.generated_at or "1970-01-01T00:00:00Z",
        )
    except ConvertError as exc:
        print(f"[FATAL] 转换失败：{exc}", file=sys.stderr)
        return 1

    print(f"[OK] 表 {report['tables_total']} 张 · 源 INSERT 语句 "
          f"{report['source_insert_statements_total']} 条 · 源行数 {report['source_rows_total']}")
    print(f"[OK] 产物目录：{report['out_dir']}")
    for name, size in report["artifacts"].items():
        print(f"     {name}  {size / 1048576:.2f} MB")
    if report["counters"].get("hex_literal"):
        print(f"[INFO] 二进制字面量 {report['counters']['hex_literal']} 处已转 bytea")
    if report["counters"].get("nul_replaced"):
        print(f"[WARN] NUL 字符替换 {report['counters']['nul_replaced']} 处")
    if report["views_skipped"]:
        print(f"[INFO] 跳过视图 {len(report['views_skipped'])} 个：{report['views_skipped']}")

    if args.import_container:
        log_path = os.path.join(args.out_dir, "import.log")
        rc = run_import(args.import_container, args.database, args.db_user, args.out_dir, log_path)
        print(f"[{'OK' if rc == 0 else 'ERROR'}] 导入日志：{log_path}")
        return rc
    return 0


if __name__ == "__main__":
    sys.exit(main())
