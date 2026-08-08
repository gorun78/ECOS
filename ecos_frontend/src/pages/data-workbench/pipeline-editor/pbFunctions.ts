/**
 * PB Function Definitions — 120+ Palantir Foundry 表达式函数全集
 * @license Apache-2.0
 */

export type PBFunctionCategory =
  | 'string'
  | 'numeric'
  | 'date_time'
  | 'conditional'
  | 'array'
  | 'window'
  | 'casting'
  | 'hash';

export interface PBFunctionParam {
  name: string;
  type: string;
  required: boolean;
  defaultValue?: string;
}

export interface PBFunctionDef {
  name: string;
  category: PBFunctionCategory;
  signature: string;
  params: PBFunctionParam[];
  returnType: string;
  description: string;
  example: string;
  isAggregate?: boolean;
}

// ─── Category display names ──────────────────────────
export const CATEGORY_LABELS: Record<PBFunctionCategory, string> = {
  string: 'databench.pbFunctions.category.string',
  numeric: 'databench.pbFunctions.category.numeric',
  date_time: 'databench.pbFunctions.category.date_time',
  conditional: 'databench.pbFunctions.category.conditional',
  array: 'databench.pbFunctions.category.array',
  window: 'databench.pbFunctions.category.window',
  casting: 'databench.pbFunctions.category.casting',
  hash: 'databench.pbFunctions.category.hash',
};

export const CATEGORY_ICONS: Record<PBFunctionCategory, string> = {
  string: 'abc',
  numeric: 'hash',
  date_time: 'calendar',
  conditional: 'git-branch',
  array: 'list',
  window: 'layout',
  casting: 'refresh-cw',
  hash: 'fingerprint',
};

// ─── All 120+ PB functions ───────────────────────────

export const PB_FUNCTIONS: PBFunctionDef[] = [
  // ═══ String (25) ═══
  { name: 'lower', category: 'string', signature: 'lower(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.lower', example: "lower('HELLO') → 'hello'" },
  { name: 'upper', category: 'string', signature: 'upper(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.upper', example: "upper('hello') → 'HELLO'" },
  { name: 'trim', category: 'string', signature: 'trim(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.trim', example: "trim('  abc  ') → 'abc'" },
  { name: 'ltrim', category: 'string', signature: 'ltrim(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.ltrim', example: "ltrim('  abc') → 'abc'" },
  { name: 'rtrim', category: 'string', signature: 'rtrim(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.rtrim', example: "rtrim('abc  ') → 'abc'" },
  { name: 'concat', category: 'string', signature: 'concat(a, b, ...)', params: [{ name: '...strings', type: 'string...', required: true }], returnType: 'string', description: 'databench.pbFunctions.concat', example: "concat('Hello', ' ', 'World') → 'Hello World'" },
  { name: 'substring', category: 'string', signature: 'substring(str, start, len)', params: [{ name: 'str', type: 'string', required: true }, { name: 'start', type: 'int', required: true }, { name: 'len', type: 'int', required: false }], returnType: 'string', description: 'databench.pbFunctions.substring', example: "substring('Hello', 1, 2) → 'He'" },
  { name: 'left', category: 'string', signature: 'left(str, n)', params: [{ name: 'str', type: 'string', required: true }, { name: 'n', type: 'int', required: true }], returnType: 'string', description: 'databench.pbFunctions.left', example: "left('Hello', 2) → 'He'" },
  { name: 'right', category: 'string', signature: 'right(str, n)', params: [{ name: 'str', type: 'string', required: true }, { name: 'n', type: 'int', required: true }], returnType: 'string', description: 'databench.pbFunctions.right', example: "right('Hello', 2) → 'lo'" },
  { name: 'length', category: 'string', signature: 'length(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'int', description: 'databench.pbFunctions.length', example: "length('Hello') → 5" },
  { name: 'replace', category: 'string', signature: 'replace(str, from, to)', params: [{ name: 'str', type: 'string', required: true }, { name: 'from', type: 'string', required: true }, { name: 'to', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.replace', example: "replace('Hello', 'l', 'x') → 'Hexxo'" },
  { name: 'split', category: 'string', signature: 'split(str, delimiter)', params: [{ name: 'str', type: 'string', required: true }, { name: 'delimiter', type: 'string', required: true }], returnType: 'array<string>', description: 'databench.pbFunctions.split', example: "split('a,b,c', ',') → ['a','b','c']" },
  { name: 'regex_extract', category: 'string', signature: 'regex_extract(str, regex, group)', params: [{ name: 'str', type: 'string', required: true }, { name: 'regex', type: 'string', required: true }, { name: 'group', type: 'int', required: false, defaultValue: '0' }], returnType: 'string', description: 'databench.pbFunctions.regex_extract', example: "regex_extract('CA1928', '([A-Z]+)', 1) → 'CA'" },
  { name: 'regex_replace', category: 'string', signature: 'regex_replace(str, regex, replacement)', params: [{ name: 'str', type: 'string', required: true }, { name: 'regex', type: 'string', required: true }, { name: 'replacement', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.regex_replace', example: "regex_replace('13812345678', '(\\d{3})\\d{4}', '$1****') → '138****5678'" },
  { name: 'starts_with', category: 'string', signature: 'starts_with(str, prefix)', params: [{ name: 'str', type: 'string', required: true }, { name: 'prefix', type: 'string', required: true }], returnType: 'boolean', description: 'databench.pbFunctions.starts_with', example: "starts_with('Hello', 'He') → true" },
  { name: 'ends_with', category: 'string', signature: 'ends_with(str, suffix)', params: [{ name: 'str', type: 'string', required: true }, { name: 'suffix', type: 'string', required: true }], returnType: 'boolean', description: 'databench.pbFunctions.ends_with', example: "ends_with('Hello', 'lo') → true" },
  { name: 'contains', category: 'string', signature: 'contains(str, substring)', params: [{ name: 'str', type: 'string', required: true }, { name: 'substring', type: 'string', required: true }], returnType: 'boolean', description: 'databench.pbFunctions.contains', example: "contains('Hello', 'ell') → true" },
  { name: 'initcap', category: 'string', signature: 'initcap(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.initcap', example: "initcap('hello world') → 'Hello World'" },
  { name: 'reverse', category: 'string', signature: 'reverse(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.reverse', example: "reverse('abc') → 'cba'" },
  { name: 'lpad', category: 'string', signature: 'lpad(str, len, pad)', params: [{ name: 'str', type: 'string', required: true }, { name: 'len', type: 'int', required: true }, { name: 'pad', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.lpad', example: "lpad('42', 5, '0') → '00042'" },
  { name: 'rpad', category: 'string', signature: 'rpad(str, len, pad)', params: [{ name: 'str', type: 'string', required: true }, { name: 'len', type: 'int', required: true }, { name: 'pad', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.rpad', example: "rpad('42', 5, '0') → '42000'" },
  { name: 'repeat', category: 'string', signature: 'repeat(str, n)', params: [{ name: 'str', type: 'string', required: true }, { name: 'n', type: 'int', required: true }], returnType: 'string', description: 'databench.pbFunctions.repeat', example: "repeat('ab', 3) → 'ababab'" },
  { name: 'translate', category: 'string', signature: 'translate(str, from, to)', params: [{ name: 'str', type: 'string', required: true }, { name: 'from', type: 'string', required: true }, { name: 'to', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.translate', example: "translate('abc', 'ac', 'xy') → 'xby'" },
  { name: 'instr', category: 'string', signature: 'instr(str, substr)', params: [{ name: 'str', type: 'string', required: true }, { name: 'substr', type: 'string', required: true }], returnType: 'int', description: 'databench.pbFunctions.instr', example: "instr('Hello', 'l') → 3" },
  { name: 'locate', category: 'string', signature: 'locate(substr, str, pos)', params: [{ name: 'substr', type: 'string', required: true }, { name: 'str', type: 'string', required: true }, { name: 'pos', type: 'int', required: false, defaultValue: '1' }], returnType: 'int', description: 'databench.pbFunctions.locate', example: "locate('l', 'Hello', 4) → 4" },

  // ═══ Numeric (25) ═══
  { name: 'abs', category: 'numeric', signature: 'abs(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.abs', example: 'abs(-5) → 5' },
  { name: 'ceil', category: 'numeric', signature: 'ceil(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'int', description: 'databench.pbFunctions.ceil', example: 'ceil(4.1) → 5' },
  { name: 'floor', category: 'numeric', signature: 'floor(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'int', description: 'databench.pbFunctions.floor', example: 'floor(4.9) → 4' },
  { name: 'round', category: 'numeric', signature: 'round(x, d)', params: [{ name: 'x', type: 'number', required: true }, { name: 'd', type: 'int', required: false, defaultValue: '0' }], returnType: 'number', description: 'databench.pbFunctions.round', example: 'round(3.14159, 2) → 3.14' },
  { name: 'power', category: 'numeric', signature: 'power(base, exp)', params: [{ name: 'base', type: 'number', required: true }, { name: 'exp', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.power', example: 'power(2, 3) → 8' },
  { name: 'sqrt', category: 'numeric', signature: 'sqrt(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.sqrt', example: 'sqrt(16) → 4' },
  { name: 'mod', category: 'numeric', signature: 'mod(a, b)', params: [{ name: 'a', type: 'number', required: true }, { name: 'b', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.mod', example: 'mod(10, 3) → 1' },
  { name: 'exp', category: 'numeric', signature: 'exp(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.exp', example: 'exp(1) → 2.71828' },
  { name: 'ln', category: 'numeric', signature: 'ln(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.ln', example: 'ln(2.718) → 1' },
  { name: 'log', category: 'numeric', signature: 'log(base, x)', params: [{ name: 'base', type: 'number', required: true }, { name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.log', example: 'log(10, 100) → 2' },
  { name: 'log10', category: 'numeric', signature: 'log10(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.log10', example: 'log10(100) → 2' },
  { name: 'sign', category: 'numeric', signature: 'sign(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'int', description: 'databench.pbFunctions.sign', example: 'sign(-5) → -1' },
  { name: 'greatest', category: 'numeric', signature: 'greatest(a, b, ...)', params: [{ name: '...values', type: 'number...', required: true }], returnType: 'number', description: 'databench.pbFunctions.greatest', example: 'greatest(3, 7, 2) → 7' },
  { name: 'least', category: 'numeric', signature: 'least(a, b, ...)', params: [{ name: '...values', type: 'number...', required: true }], returnType: 'number', description: 'databench.pbFunctions.least', example: 'least(3, 7, 2) → 2' },
  { name: 'rand', category: 'numeric', signature: 'rand()', params: [], returnType: 'double', description: 'databench.pbFunctions.rand', example: 'rand() → 0.7243' },
  { name: 'radians', category: 'numeric', signature: 'radians(deg)', params: [{ name: 'deg', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.radians', example: 'radians(180) → 3.14159' },
  { name: 'degrees', category: 'numeric', signature: 'degrees(rad)', params: [{ name: 'rad', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.degrees', example: 'degrees(3.14159) → 180' },
  { name: 'sin', category: 'numeric', signature: 'sin(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.sin', example: 'sin(0) → 0' },
  { name: 'cos', category: 'numeric', signature: 'cos(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.cos', example: 'cos(0) → 1' },
  { name: 'tan', category: 'numeric', signature: 'tan(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.tan', example: 'tan(0) → 0' },
  { name: 'asin', category: 'numeric', signature: 'asin(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.asin', example: 'asin(0) → 0' },
  { name: 'acos', category: 'numeric', signature: 'acos(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.acos', example: 'acos(1) → 0' },
  { name: 'atan', category: 'numeric', signature: 'atan(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.atan', example: 'atan(0) → 0' },
  { name: 'atan2', category: 'numeric', signature: 'atan2(y, x)', params: [{ name: 'y', type: 'number', required: true }, { name: 'x', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.atan2', example: 'atan2(1, 1) → 0.785' },
  { name: 'crc32', category: 'numeric', signature: 'crc32(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'int', description: 'databench.pbFunctions.crc32', example: "crc32('hello') → 907060870" },

  // ═══ Date/Time (25) ═══
  { name: 'year', category: 'date_time', signature: 'year(date)', params: [{ name: 'date', type: 'date/timestamp', required: true }], returnType: 'int', description: 'databench.pbFunctions.year', example: "year('2026-07-11') → 2026" },
  { name: 'month', category: 'date_time', signature: 'month(date)', params: [{ name: 'date', type: 'date/timestamp', required: true }], returnType: 'int', description: 'databench.pbFunctions.month', example: "month('2026-07-11') → 7" },
  { name: 'day', category: 'date_time', signature: 'day(date)', params: [{ name: 'date', type: 'date/timestamp', required: true }], returnType: 'int', description: 'databench.pbFunctions.day', example: "day('2026-07-11') → 11" },
  { name: 'hour', category: 'date_time', signature: 'hour(ts)', params: [{ name: 'ts', type: 'timestamp', required: true }], returnType: 'int', description: 'databench.pbFunctions.hour', example: "hour('2026-07-11 14:30:00') → 14" },
  { name: 'minute', category: 'date_time', signature: 'minute(ts)', params: [{ name: 'ts', type: 'timestamp', required: true }], returnType: 'int', description: 'databench.pbFunctions.minute', example: "minute('2026-07-11 14:30:00') → 30" },
  { name: 'second', category: 'date_time', signature: 'second(ts)', params: [{ name: 'ts', type: 'timestamp', required: true }], returnType: 'int', description: 'databench.pbFunctions.second', example: "second('2026-07-11 14:30:45') → 45" },
  { name: 'dayofweek', category: 'date_time', signature: 'dayofweek(date)', params: [{ name: 'date', type: 'date', required: true }], returnType: 'int', description: 'databench.pbFunctions.dayofweek', example: "dayofweek('2026-07-12') → 1" },
  { name: 'dayofyear', category: 'date_time', signature: 'dayofyear(date)', params: [{ name: 'date', type: 'date', required: true }], returnType: 'int', description: 'databench.pbFunctions.dayofyear', example: "dayofyear('2026-07-11') → 192" },
  { name: 'weekofyear', category: 'date_time', signature: 'weekofyear(date)', params: [{ name: 'date', type: 'date', required: true }], returnType: 'int', description: 'databench.pbFunctions.weekofyear', example: "weekofyear('2026-07-11') → 28" },
  { name: 'quarter', category: 'date_time', signature: 'quarter(date)', params: [{ name: 'date', type: 'date', required: true }], returnType: 'int', description: 'databench.pbFunctions.quarter', example: "quarter('2026-07-11') → 3" },
  { name: 'date_add', category: 'date_time', signature: 'date_add(date, days)', params: [{ name: 'date', type: 'date', required: true }, { name: 'days', type: 'int', required: true }], returnType: 'date', description: 'databench.pbFunctions.date_add', example: "date_add('2026-07-11', 3) → '2026-07-14'" },
  { name: 'date_sub', category: 'date_time', signature: 'date_sub(date, days)', params: [{ name: 'date', type: 'date', required: true }, { name: 'days', type: 'int', required: true }], returnType: 'date', description: 'databench.pbFunctions.date_sub', example: "date_sub('2026-07-11', 1) → '2026-07-10'" },
  { name: 'datediff', category: 'date_time', signature: 'datediff(end, start)', params: [{ name: 'end', type: 'date', required: true }, { name: 'start', type: 'date', required: true }], returnType: 'int', description: 'databench.pbFunctions.datediff', example: "datediff('2026-07-14', '2026-07-11') → 3" },
  { name: 'date_trunc', category: 'date_time', signature: 'date_trunc(date, unit)', params: [{ name: 'date', type: 'date', required: true }, { name: 'unit', type: 'string', required: true }], returnType: 'date', description: 'databench.pbFunctions.date_trunc', example: "date_trunc('2026-07-11', 'month') → '2026-07-01'" },
  { name: 'current_date', category: 'date_time', signature: 'current_date()', params: [], returnType: 'date', description: 'databench.pbFunctions.current_date', example: "current_date() → '2026-07-11'" },
  { name: 'current_timestamp', category: 'date_time', signature: 'current_timestamp()', params: [], returnType: 'timestamp', description: 'databench.pbFunctions.current_timestamp', example: "current_timestamp() → '2026-07-11 14:30:00'" },
  { name: 'to_date', category: 'date_time', signature: 'to_date(str, fmt)', params: [{ name: 'str', type: 'string', required: true }, { name: 'fmt', type: 'string', required: false, defaultValue: "'yyyy-MM-dd'" }], returnType: 'date', description: 'databench.pbFunctions.to_date', example: "to_date('2026/07/11', 'yyyy/MM/dd') → '2026-07-11'" },
  { name: 'to_timestamp', category: 'date_time', signature: 'to_timestamp(str, fmt)', params: [{ name: 'str', type: 'string', required: true }, { name: 'fmt', type: 'string', required: false }], returnType: 'timestamp', description: 'databench.pbFunctions.to_timestamp', example: "to_timestamp('2026-07-11 14:30:00', 'yyyy-MM-dd HH:mm:ss')" },
  { name: 'date_format', category: 'date_time', signature: 'date_format(date, fmt)', params: [{ name: 'date', type: 'date', required: true }, { name: 'fmt', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.date_format', example: "date_format('2026-07-11', 'yyyyMMdd') → '20260711'" },
  { name: 'unix_timestamp', category: 'date_time', signature: 'unix_timestamp(ts)', params: [{ name: 'ts', type: 'timestamp', required: false }], returnType: 'long', description: 'databench.pbFunctions.unix_timestamp', example: "unix_timestamp('2026-07-11 00:00:00') → 1753001600" },
  { name: 'from_unixtime', category: 'date_time', signature: 'from_unixtime(unix, fmt)', params: [{ name: 'unix', type: 'long', required: true }, { name: 'fmt', type: 'string', required: false }], returnType: 'string', description: 'databench.pbFunctions.from_unixtime', example: "from_unixtime(1753001600) → '2026-07-11 00:00:00'" },
  { name: 'add_months', category: 'date_time', signature: 'add_months(date, n)', params: [{ name: 'date', type: 'date', required: true }, { name: 'n', type: 'int', required: true }], returnType: 'date', description: 'databench.pbFunctions.add_months', example: "add_months('2026-07-11', 2) → '2026-09-11'" },
  { name: 'months_between', category: 'date_time', signature: 'months_between(end, start)', params: [{ name: 'end', type: 'date', required: true }, { name: 'start', type: 'date', required: true }], returnType: 'double', description: 'databench.pbFunctions.months_between', example: "months_between('2026-09-11', '2026-07-11') → 2.0" },
  { name: 'last_day', category: 'date_time', signature: 'last_day(date)', params: [{ name: 'date', type: 'date', required: true }], returnType: 'date', description: 'databench.pbFunctions.last_day', example: "last_day('2026-07-11') → '2026-07-31'" },
  { name: 'next_day', category: 'date_time', signature: 'next_day(date, weekday)', params: [{ name: 'date', type: 'date', required: true }, { name: 'weekday', type: 'string', required: true }], returnType: 'date', description: 'databench.pbFunctions.next_day', example: "next_day('2026-07-11', 'Mon') → '2026-07-13'" },

  // ═══ Conditional (10) ═══
  { name: 'if', category: 'conditional', signature: 'if(condition, true_val, false_val)', params: [{ name: 'condition', type: 'boolean', required: true }, { name: 'true_val', type: 'any', required: true }, { name: 'false_val', type: 'any', required: true }], returnType: 'any', description: 'databench.pbFunctions.if', example: "if(amount > 100, 'high', 'low') → 'high'" },
  { name: 'case', category: 'conditional', signature: 'case(cond1, val1, cond2, val2, ..., default)', params: [{ name: '...pairs', type: '(boolean, any)...', required: true }], returnType: 'any', description: 'databench.pbFunctions.case', example: "case(score > 90, 'A', score > 80, 'B', 'C')" },
  { name: 'coalesce', category: 'conditional', signature: 'coalesce(a, b, ...)', params: [{ name: '...values', type: 'any...', required: true }], returnType: 'any', description: 'databench.pbFunctions.coalesce', example: 'coalesce(null, null, 42) → 42' },
  { name: 'nullif', category: 'conditional', signature: 'nullif(a, b)', params: [{ name: 'a', type: 'any', required: true }, { name: 'b', type: 'any', required: true }], returnType: 'any', description: 'databench.pbFunctions.nullif', example: "nullif('N/A', 'N/A') → NULL" },
  { name: 'ifnull', category: 'conditional', signature: 'ifnull(a, b)', params: [{ name: 'a', type: 'any', required: true }, { name: 'b', type: 'any', required: true }], returnType: 'any', description: 'databench.pbFunctions.ifnull', example: "ifnull(null, 'default') → 'default'" },
  { name: 'nvl', category: 'conditional', signature: 'nvl(a, b)', params: [{ name: 'a', type: 'any', required: true }, { name: 'b', type: 'any', required: true }], returnType: 'any', description: 'databench.pbFunctions.nvl', example: "nvl(null, 0) → 0" },
  { name: 'nvl2', category: 'conditional', signature: 'nvl2(a, b, c)', params: [{ name: 'a', type: 'any', required: true }, { name: 'b', type: 'any', required: true }, { name: 'c', type: 'any', required: true }], returnType: 'any', description: 'databench.pbFunctions.nvl2', example: "nvl2(name, 'known', 'unknown') → 'known'" },
  { name: 'isnull', category: 'conditional', signature: 'isnull(a)', params: [{ name: 'a', type: 'any', required: true }], returnType: 'boolean', description: 'databench.pbFunctions.isnull', example: 'isnull(null) → true' },
  { name: 'isnotnull', category: 'conditional', signature: 'isnotnull(a)', params: [{ name: 'a', type: 'any', required: true }], returnType: 'boolean', description: 'databench.pbFunctions.isnotnull', example: 'isnotnull(42) → true' },
  { name: 'decode', category: 'conditional', signature: 'decode(expr, key1, val1, ..., default)', params: [{ name: 'expr', type: 'any', required: true }, { name: '...pairs', type: '(any, any)...', required: true }], returnType: 'any', description: 'databench.pbFunctions.decode', example: "decode(status, 1, 'active', 2, 'inactive', 'unknown')" },

  // ═══ Array/Struct (15) ═══
  { name: 'array', category: 'array', signature: 'array(e1, e2, ...)', params: [{ name: '...elements', type: 'any...', required: true }], returnType: 'array', description: 'databench.pbFunctions.array', example: 'array(1, 2, 3) → [1, 2, 3]' },
  { name: 'array_contains', category: 'array', signature: 'array_contains(arr, elem)', params: [{ name: 'arr', type: 'array', required: true }, { name: 'elem', type: 'any', required: true }], returnType: 'boolean', description: 'databench.pbFunctions.array_contains', example: "array_contains([1,2,3], 2) → true" },
  { name: 'array_join', category: 'array', signature: 'array_join(arr, delimiter)', params: [{ name: 'arr', type: 'array', required: true }, { name: 'delimiter', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.array_join', example: "array_join(['a','b','c'], ',') → 'a,b,c'" },
  { name: 'array_append', category: 'array', signature: 'array_append(arr, elem)', params: [{ name: 'arr', type: 'array', required: true }, { name: 'elem', type: 'any', required: true }], returnType: 'array', description: 'databench.pbFunctions.array_append', example: "array_append([1,2], 3) → [1, 2, 3]" },
  { name: 'array_prepend', category: 'array', signature: 'array_prepend(arr, elem)', params: [{ name: 'arr', type: 'array', required: true }, { name: 'elem', type: 'any', required: true }], returnType: 'array', description: 'databench.pbFunctions.array_prepend', example: "array_prepend([2,3], 1) → [1, 2, 3]" },
  { name: 'explode', category: 'array', signature: 'explode(arr)', params: [{ name: 'arr', type: 'array', required: true }], returnType: 'rowset', description: 'databench.pbFunctions.explode', example: 'explode([1,2,3]) → 三行' },
  { name: 'size', category: 'array', signature: 'size(arr)', params: [{ name: 'arr', type: 'array', required: true }], returnType: 'int', description: 'databench.pbFunctions.size', example: 'size([1,2,3]) → 3' },
  { name: 'cardinality', category: 'array', signature: 'cardinality(arr)', params: [{ name: 'arr', type: 'array', required: true }], returnType: 'int', description: 'databench.pbFunctions.cardinality', example: 'cardinality([1,2,3]) → 3' },
  { name: 'element_at', category: 'array', signature: 'element_at(arr, idx)', params: [{ name: 'arr', type: 'array', required: true }, { name: 'idx', type: 'int', required: true }], returnType: 'any', description: 'databench.pbFunctions.element_at', example: 'element_at([a,b,c], 2) → b' },
  { name: 'sort_array', category: 'array', signature: 'sort_array(arr)', params: [{ name: 'arr', type: 'array', required: true }], returnType: 'array', description: 'databench.pbFunctions.sort_array', example: 'sort_array([3,1,2]) → [1,2,3]' },
  { name: 'slice', category: 'array', signature: 'slice(arr, start, len)', params: [{ name: 'arr', type: 'array', required: true }, { name: 'start', type: 'int', required: true }, { name: 'len', type: 'int', required: true }], returnType: 'array', description: 'databench.pbFunctions.slice', example: 'slice([1,2,3,4], 2, 2) → [2,3]' },
  { name: 'map', category: 'array', signature: 'map(k1, v1, k2, v2, ...)', params: [{ name: '...pairs', type: '(any, any)...', required: true }], returnType: 'map', description: 'databench.pbFunctions.map', example: "map('a', 1, 'b', 2) → {a:1, b:2}" },
  { name: 'map_keys', category: 'array', signature: 'map_keys(m)', params: [{ name: 'm', type: 'map', required: true }], returnType: 'array', description: 'databench.pbFunctions.map_keys', example: "map_keys({a:1,b:2}) → ['a','b']" },
  { name: 'map_values', category: 'array', signature: 'map_values(m)', params: [{ name: 'm', type: 'map', required: true }], returnType: 'array', description: 'databench.pbFunctions.map_values', example: "map_values({a:1,b:2}) → [1,2]" },
  { name: 'struct', category: 'array', signature: 'struct(f1, f2, ...)', params: [{ name: '...fields', type: 'any...', required: true }], returnType: 'struct', description: 'databench.pbFunctions.struct', example: 'struct(name, age) → {name: "John", age: 30}' },

  // ═══ Window (12) ═══
  { name: 'row_number', category: 'window', signature: 'row_number()', params: [], returnType: 'int', description: 'databench.pbFunctions.row_number', example: 'row_number() → 1, 2, 3...' },
  { name: 'rank', category: 'window', signature: 'rank()', params: [], returnType: 'int', description: 'databench.pbFunctions.rank', example: 'rank() → 1, 2, 2, 4' },
  { name: 'dense_rank', category: 'window', signature: 'dense_rank()', params: [], returnType: 'int', description: 'databench.pbFunctions.dense_rank', example: 'dense_rank() → 1, 2, 2, 3' },
  { name: 'lead', category: 'window', signature: 'lead(col, offset, default)', params: [{ name: 'col', type: 'any', required: true }, { name: 'offset', type: 'int', required: false, defaultValue: '1' }, { name: 'default', type: 'any', required: false }], returnType: 'any', description: 'databench.pbFunctions.lead', example: 'lead(amount, 1, 0) → 下一行的 amount' },
  { name: 'lag', category: 'window', signature: 'lag(col, offset, default)', params: [{ name: 'col', type: 'any', required: true }, { name: 'offset', type: 'int', required: false, defaultValue: '1' }, { name: 'default', type: 'any', required: false }], returnType: 'any', description: 'databench.pbFunctions.lag', example: 'lag(amount, 1, 0) → 上一行的 amount' },
  { name: 'first_value', category: 'window', signature: 'first_value(col)', params: [{ name: 'col', type: 'any', required: true }], returnType: 'any', description: 'databench.pbFunctions.first_value', example: 'first_value(amount) → 窗口首行 amount' },
  { name: 'last_value', category: 'window', signature: 'last_value(col)', params: [{ name: 'col', type: 'any', required: true }], returnType: 'any', description: 'databench.pbFunctions.last_value', example: 'last_value(amount) → 窗口末行 amount' },
  { name: 'nth_value', category: 'window', signature: 'nth_value(col, n)', params: [{ name: 'col', type: 'any', required: true }, { name: 'n', type: 'int', required: true }], returnType: 'any', description: 'databench.pbFunctions.nth_value', example: 'nth_value(amount, 3) → 窗口第3行 amount' },
  { name: 'percent_rank', category: 'window', signature: 'percent_rank()', params: [], returnType: 'double', description: 'databench.pbFunctions.percent_rank', example: 'percent_rank() → 0.25' },
  { name: 'cume_dist', category: 'window', signature: 'cume_dist()', params: [], returnType: 'double', description: 'databench.pbFunctions.cume_dist', example: 'cume_dist() → 0.5' },
  { name: 'ntile', category: 'window', signature: 'ntile(n)', params: [{ name: 'n', type: 'int', required: true }], returnType: 'int', description: 'databench.pbFunctions.ntile', example: 'ntile(4) → 1, 2, 3, 4' },
  { name: 'sum_over', category: 'window', signature: 'sum(col)', params: [{ name: 'col', type: 'number', required: true }], returnType: 'number', description: 'databench.pbFunctions.sum_over', example: 'sum(amount) over(...) → 窗口内累计求和', isAggregate: true },

  // ═══ Casting (8) ═══
  { name: 'cast', category: 'casting', signature: 'cast(expr as type)', params: [{ name: 'expr', type: 'any', required: true }, { name: 'type', type: 'string', required: true }], returnType: 'any', description: 'databench.pbFunctions.cast', example: "cast('42' as int) → 42" },
  { name: 'to_string', category: 'casting', signature: 'to_string(x)', params: [{ name: 'x', type: 'any', required: true }], returnType: 'string', description: 'databench.pbFunctions.to_string', example: 'to_string(42) → "42"' },
  { name: 'to_int', category: 'casting', signature: 'to_int(x)', params: [{ name: 'x', type: 'any', required: true }], returnType: 'int', description: 'databench.pbFunctions.to_int', example: 'to_int("42") → 42' },
  { name: 'to_long', category: 'casting', signature: 'to_long(x)', params: [{ name: 'x', type: 'any', required: true }], returnType: 'long', description: 'databench.pbFunctions.to_long', example: 'to_long("10000000000") → 10000000000L' },
  { name: 'to_double', category: 'casting', signature: 'to_double(x)', params: [{ name: 'x', type: 'any', required: true }], returnType: 'double', description: 'databench.pbFunctions.to_double', example: 'to_double("3.14") → 3.14' },
  { name: 'to_float', category: 'casting', signature: 'to_float(x)', params: [{ name: 'x', type: 'any', required: true }], returnType: 'float', description: 'databench.pbFunctions.to_float', example: 'to_float("3.14") → 3.14f' },
  { name: 'to_decimal', category: 'casting', signature: 'to_decimal(x, p, s)', params: [{ name: 'x', type: 'any', required: true }, { name: 'p', type: 'int', required: true }, { name: 's', type: 'int', required: true }], returnType: 'decimal', description: 'databench.pbFunctions.to_decimal', example: 'to_decimal("3.14159", 5, 2) → 3.14' },
  { name: 'to_boolean', category: 'casting', signature: 'to_boolean(x)', params: [{ name: 'x', type: 'any', required: true }], returnType: 'boolean', description: 'databench.pbFunctions.to_boolean', example: 'to_boolean(1) → true' },

  // ═══ Hash (3) ═══
  { name: 'md5', category: 'hash', signature: 'md5(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.md5', example: "md5('hello') → '5d41402abc4b2a76b9719d911017c592'" },
  { name: 'sha256', category: 'hash', signature: 'sha256(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: 'databench.pbFunctions.sha256', example: "sha256('hello') → '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824'" },
  { name: 'hash', category: 'hash', signature: 'hash(col1, col2, ...)', params: [{ name: '...cols', type: 'any...', required: true }], returnType: 'int', description: 'databench.pbFunctions.hash', example: 'hash(name, birthday) → 1742019284' },
];
