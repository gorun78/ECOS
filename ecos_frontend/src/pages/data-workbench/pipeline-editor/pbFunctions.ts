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
  string: '字符串函数',
  numeric: '数值函数',
  date_time: '日期时间函数',
  conditional: '条件/逻辑函数',
  array: '数组/结构体函数',
  window: '窗口函数',
  casting: '类型转换函数',
  hash: '哈希函数',
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
  { name: 'lower', category: 'string', signature: 'lower(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: '将字符串转换为小写', example: "lower('HELLO') → 'hello'" },
  { name: 'upper', category: 'string', signature: 'upper(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: '将字符串转换为大写', example: "upper('hello') → 'HELLO'" },
  { name: 'trim', category: 'string', signature: 'trim(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: '去除首尾空白字符', example: "trim('  abc  ') → 'abc'" },
  { name: 'ltrim', category: 'string', signature: 'ltrim(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: '去除左侧空白字符', example: "ltrim('  abc') → 'abc'" },
  { name: 'rtrim', category: 'string', signature: 'rtrim(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: '去除右侧空白字符', example: "rtrim('abc  ') → 'abc'" },
  { name: 'concat', category: 'string', signature: 'concat(a, b, ...)', params: [{ name: '...strings', type: 'string...', required: true }], returnType: 'string', description: '拼接多个字符串', example: "concat('Hello', ' ', 'World') → 'Hello World'" },
  { name: 'substring', category: 'string', signature: 'substring(str, start, len)', params: [{ name: 'str', type: 'string', required: true }, { name: 'start', type: 'int', required: true }, { name: 'len', type: 'int', required: false }], returnType: 'string', description: '截取子字符串 (1-indexed)', example: "substring('Hello', 1, 2) → 'He'" },
  { name: 'left', category: 'string', signature: 'left(str, n)', params: [{ name: 'str', type: 'string', required: true }, { name: 'n', type: 'int', required: true }], returnType: 'string', description: '取左侧 n 个字符', example: "left('Hello', 2) → 'He'" },
  { name: 'right', category: 'string', signature: 'right(str, n)', params: [{ name: 'str', type: 'string', required: true }, { name: 'n', type: 'int', required: true }], returnType: 'string', description: '取右侧 n 个字符', example: "right('Hello', 2) → 'lo'" },
  { name: 'length', category: 'string', signature: 'length(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'int', description: '计算字符串长度', example: "length('Hello') → 5" },
  { name: 'replace', category: 'string', signature: 'replace(str, from, to)', params: [{ name: 'str', type: 'string', required: true }, { name: 'from', type: 'string', required: true }, { name: 'to', type: 'string', required: true }], returnType: 'string', description: '替换字符串中的子串', example: "replace('Hello', 'l', 'x') → 'Hexxo'" },
  { name: 'split', category: 'string', signature: 'split(str, delimiter)', params: [{ name: 'str', type: 'string', required: true }, { name: 'delimiter', type: 'string', required: true }], returnType: 'array<string>', description: '按分隔符分割字符串为数组', example: "split('a,b,c', ',') → ['a','b','c']" },
  { name: 'regex_extract', category: 'string', signature: 'regex_extract(str, regex, group)', params: [{ name: 'str', type: 'string', required: true }, { name: 'regex', type: 'string', required: true }, { name: 'group', type: 'int', required: false, defaultValue: '0' }], returnType: 'string', description: '正则提取指定捕获组', example: "regex_extract('CA1928', '([A-Z]+)', 1) → 'CA'" },
  { name: 'regex_replace', category: 'string', signature: 'regex_replace(str, regex, replacement)', params: [{ name: 'str', type: 'string', required: true }, { name: 'regex', type: 'string', required: true }, { name: 'replacement', type: 'string', required: true }], returnType: 'string', description: '正则替换', example: "regex_replace('13812345678', '(\\d{3})\\d{4}', '$1****') → '138****5678'" },
  { name: 'starts_with', category: 'string', signature: 'starts_with(str, prefix)', params: [{ name: 'str', type: 'string', required: true }, { name: 'prefix', type: 'string', required: true }], returnType: 'boolean', description: '判断字符串是否以指定前缀开头', example: "starts_with('Hello', 'He') → true" },
  { name: 'ends_with', category: 'string', signature: 'ends_with(str, suffix)', params: [{ name: 'str', type: 'string', required: true }, { name: 'suffix', type: 'string', required: true }], returnType: 'boolean', description: '判断字符串是否以指定后缀结尾', example: "ends_with('Hello', 'lo') → true" },
  { name: 'contains', category: 'string', signature: 'contains(str, substring)', params: [{ name: 'str', type: 'string', required: true }, { name: 'substring', type: 'string', required: true }], returnType: 'boolean', description: '判断字符串是否包含子串', example: "contains('Hello', 'ell') → true" },
  { name: 'initcap', category: 'string', signature: 'initcap(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: '首字母大写', example: "initcap('hello world') → 'Hello World'" },
  { name: 'reverse', category: 'string', signature: 'reverse(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: '反转字符串', example: "reverse('abc') → 'cba'" },
  { name: 'lpad', category: 'string', signature: 'lpad(str, len, pad)', params: [{ name: 'str', type: 'string', required: true }, { name: 'len', type: 'int', required: true }, { name: 'pad', type: 'string', required: true }], returnType: 'string', description: '左填充到指定长度', example: "lpad('42', 5, '0') → '00042'" },
  { name: 'rpad', category: 'string', signature: 'rpad(str, len, pad)', params: [{ name: 'str', type: 'string', required: true }, { name: 'len', type: 'int', required: true }, { name: 'pad', type: 'string', required: true }], returnType: 'string', description: '右填充到指定长度', example: "rpad('42', 5, '0') → '42000'" },
  { name: 'repeat', category: 'string', signature: 'repeat(str, n)', params: [{ name: 'str', type: 'string', required: true }, { name: 'n', type: 'int', required: true }], returnType: 'string', description: '重复字符串 n 次', example: "repeat('ab', 3) → 'ababab'" },
  { name: 'translate', category: 'string', signature: 'translate(str, from, to)', params: [{ name: 'str', type: 'string', required: true }, { name: 'from', type: 'string', required: true }, { name: 'to', type: 'string', required: true }], returnType: 'string', description: '字符映射替换', example: "translate('abc', 'ac', 'xy') → 'xby'" },
  { name: 'instr', category: 'string', signature: 'instr(str, substr)', params: [{ name: 'str', type: 'string', required: true }, { name: 'substr', type: 'string', required: true }], returnType: 'int', description: '返回子串首次出现位置 (1-indexed)', example: "instr('Hello', 'l') → 3" },
  { name: 'locate', category: 'string', signature: 'locate(substr, str, pos)', params: [{ name: 'substr', type: 'string', required: true }, { name: 'str', type: 'string', required: true }, { name: 'pos', type: 'int', required: false, defaultValue: '1' }], returnType: 'int', description: '从指定位置开始查找子串位置', example: "locate('l', 'Hello', 4) → 4" },

  // ═══ Numeric (25) ═══
  { name: 'abs', category: 'numeric', signature: 'abs(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: '计算绝对值', example: 'abs(-5) → 5' },
  { name: 'ceil', category: 'numeric', signature: 'ceil(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'int', description: '向上取整', example: 'ceil(4.1) → 5' },
  { name: 'floor', category: 'numeric', signature: 'floor(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'int', description: '向下取整', example: 'floor(4.9) → 4' },
  { name: 'round', category: 'numeric', signature: 'round(x, d)', params: [{ name: 'x', type: 'number', required: true }, { name: 'd', type: 'int', required: false, defaultValue: '0' }], returnType: 'number', description: '四舍五入到 d 位小数', example: 'round(3.14159, 2) → 3.14' },
  { name: 'power', category: 'numeric', signature: 'power(base, exp)', params: [{ name: 'base', type: 'number', required: true }, { name: 'exp', type: 'number', required: true }], returnType: 'number', description: '幂运算', example: 'power(2, 3) → 8' },
  { name: 'sqrt', category: 'numeric', signature: 'sqrt(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: '平方根', example: 'sqrt(16) → 4' },
  { name: 'mod', category: 'numeric', signature: 'mod(a, b)', params: [{ name: 'a', type: 'number', required: true }, { name: 'b', type: 'number', required: true }], returnType: 'number', description: '取模运算', example: 'mod(10, 3) → 1' },
  { name: 'exp', category: 'numeric', signature: 'exp(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: 'e 的 x 次方', example: 'exp(1) → 2.71828' },
  { name: 'ln', category: 'numeric', signature: 'ln(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: '自然对数', example: 'ln(2.718) → 1' },
  { name: 'log', category: 'numeric', signature: 'log(base, x)', params: [{ name: 'base', type: 'number', required: true }, { name: 'x', type: 'number', required: true }], returnType: 'number', description: '以 base 为底的对数', example: 'log(10, 100) → 2' },
  { name: 'log10', category: 'numeric', signature: 'log10(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: '以10为底的对数', example: 'log10(100) → 2' },
  { name: 'sign', category: 'numeric', signature: 'sign(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'int', description: '符号位 (-1/0/1)', example: 'sign(-5) → -1' },
  { name: 'greatest', category: 'numeric', signature: 'greatest(a, b, ...)', params: [{ name: '...values', type: 'number...', required: true }], returnType: 'number', description: '取最大值', example: 'greatest(3, 7, 2) → 7' },
  { name: 'least', category: 'numeric', signature: 'least(a, b, ...)', params: [{ name: '...values', type: 'number...', required: true }], returnType: 'number', description: '取最小值', example: 'least(3, 7, 2) → 2' },
  { name: 'rand', category: 'numeric', signature: 'rand()', params: [], returnType: 'double', description: '生成 0~1 随机数', example: 'rand() → 0.7243' },
  { name: 'radians', category: 'numeric', signature: 'radians(deg)', params: [{ name: 'deg', type: 'number', required: true }], returnType: 'number', description: '角度转弧度', example: 'radians(180) → 3.14159' },
  { name: 'degrees', category: 'numeric', signature: 'degrees(rad)', params: [{ name: 'rad', type: 'number', required: true }], returnType: 'number', description: '弧度转角度', example: 'degrees(3.14159) → 180' },
  { name: 'sin', category: 'numeric', signature: 'sin(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: '正弦', example: 'sin(0) → 0' },
  { name: 'cos', category: 'numeric', signature: 'cos(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: '余弦', example: 'cos(0) → 1' },
  { name: 'tan', category: 'numeric', signature: 'tan(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: '正切', example: 'tan(0) → 0' },
  { name: 'asin', category: 'numeric', signature: 'asin(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: '反正弦', example: 'asin(0) → 0' },
  { name: 'acos', category: 'numeric', signature: 'acos(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: '反余弦', example: 'acos(1) → 0' },
  { name: 'atan', category: 'numeric', signature: 'atan(x)', params: [{ name: 'x', type: 'number', required: true }], returnType: 'number', description: '反正切', example: 'atan(0) → 0' },
  { name: 'atan2', category: 'numeric', signature: 'atan2(y, x)', params: [{ name: 'y', type: 'number', required: true }, { name: 'x', type: 'number', required: true }], returnType: 'number', description: '双参数反正切', example: 'atan2(1, 1) → 0.785' },
  { name: 'crc32', category: 'numeric', signature: 'crc32(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'int', description: 'CRC32 哈希', example: "crc32('hello') → 907060870" },

  // ═══ Date/Time (25) ═══
  { name: 'year', category: 'date_time', signature: 'year(date)', params: [{ name: 'date', type: 'date/timestamp', required: true }], returnType: 'int', description: '提取年份', example: "year('2026-07-11') → 2026" },
  { name: 'month', category: 'date_time', signature: 'month(date)', params: [{ name: 'date', type: 'date/timestamp', required: true }], returnType: 'int', description: '提取月份 (1-12)', example: "month('2026-07-11') → 7" },
  { name: 'day', category: 'date_time', signature: 'day(date)', params: [{ name: 'date', type: 'date/timestamp', required: true }], returnType: 'int', description: '提取日期', example: "day('2026-07-11') → 11" },
  { name: 'hour', category: 'date_time', signature: 'hour(ts)', params: [{ name: 'ts', type: 'timestamp', required: true }], returnType: 'int', description: '提取小时', example: "hour('2026-07-11 14:30:00') → 14" },
  { name: 'minute', category: 'date_time', signature: 'minute(ts)', params: [{ name: 'ts', type: 'timestamp', required: true }], returnType: 'int', description: '提取分钟', example: "minute('2026-07-11 14:30:00') → 30" },
  { name: 'second', category: 'date_time', signature: 'second(ts)', params: [{ name: 'ts', type: 'timestamp', required: true }], returnType: 'int', description: '提取秒', example: "second('2026-07-11 14:30:45') → 45" },
  { name: 'dayofweek', category: 'date_time', signature: 'dayofweek(date)', params: [{ name: 'date', type: 'date', required: true }], returnType: 'int', description: '星期几 (1=周日, 7=周六)', example: "dayofweek('2026-07-12') → 1" },
  { name: 'dayofyear', category: 'date_time', signature: 'dayofyear(date)', params: [{ name: 'date', type: 'date', required: true }], returnType: 'int', description: '一年中的第几天', example: "dayofyear('2026-07-11') → 192" },
  { name: 'weekofyear', category: 'date_time', signature: 'weekofyear(date)', params: [{ name: 'date', type: 'date', required: true }], returnType: 'int', description: '一年中的第几周', example: "weekofyear('2026-07-11') → 28" },
  { name: 'quarter', category: 'date_time', signature: 'quarter(date)', params: [{ name: 'date', type: 'date', required: true }], returnType: 'int', description: '季度 (1-4)', example: "quarter('2026-07-11') → 3" },
  { name: 'date_add', category: 'date_time', signature: 'date_add(date, days)', params: [{ name: 'date', type: 'date', required: true }, { name: 'days', type: 'int', required: true }], returnType: 'date', description: '加天数', example: "date_add('2026-07-11', 3) → '2026-07-14'" },
  { name: 'date_sub', category: 'date_time', signature: 'date_sub(date, days)', params: [{ name: 'date', type: 'date', required: true }, { name: 'days', type: 'int', required: true }], returnType: 'date', description: '减天数', example: "date_sub('2026-07-11', 1) → '2026-07-10'" },
  { name: 'datediff', category: 'date_time', signature: 'datediff(end, start)', params: [{ name: 'end', type: 'date', required: true }, { name: 'start', type: 'date', required: true }], returnType: 'int', description: '日期差（天数）', example: "datediff('2026-07-14', '2026-07-11') → 3" },
  { name: 'date_trunc', category: 'date_time', signature: 'date_trunc(date, unit)', params: [{ name: 'date', type: 'date', required: true }, { name: 'unit', type: 'string', required: true }], returnType: 'date', description: '截断到指定粒度 (year/month/day)', example: "date_trunc('2026-07-11', 'month') → '2026-07-01'" },
  { name: 'current_date', category: 'date_time', signature: 'current_date()', params: [], returnType: 'date', description: '返回当前日期', example: "current_date() → '2026-07-11'" },
  { name: 'current_timestamp', category: 'date_time', signature: 'current_timestamp()', params: [], returnType: 'timestamp', description: '返回当前时间戳', example: "current_timestamp() → '2026-07-11 14:30:00'" },
  { name: 'to_date', category: 'date_time', signature: 'to_date(str, fmt)', params: [{ name: 'str', type: 'string', required: true }, { name: 'fmt', type: 'string', required: false, defaultValue: "'yyyy-MM-dd'" }], returnType: 'date', description: '字符串转日期', example: "to_date('2026/07/11', 'yyyy/MM/dd') → '2026-07-11'" },
  { name: 'to_timestamp', category: 'date_time', signature: 'to_timestamp(str, fmt)', params: [{ name: 'str', type: 'string', required: true }, { name: 'fmt', type: 'string', required: false }], returnType: 'timestamp', description: '字符串转时间戳', example: "to_timestamp('2026-07-11 14:30:00', 'yyyy-MM-dd HH:mm:ss')" },
  { name: 'date_format', category: 'date_time', signature: 'date_format(date, fmt)', params: [{ name: 'date', type: 'date', required: true }, { name: 'fmt', type: 'string', required: true }], returnType: 'string', description: '日期格式化为字符串', example: "date_format('2026-07-11', 'yyyyMMdd') → '20260711'" },
  { name: 'unix_timestamp', category: 'date_time', signature: 'unix_timestamp(ts)', params: [{ name: 'ts', type: 'timestamp', required: false }], returnType: 'long', description: '转为 Unix 时间戳 (秒)', example: "unix_timestamp('2026-07-11 00:00:00') → 1753001600" },
  { name: 'from_unixtime', category: 'date_time', signature: 'from_unixtime(unix, fmt)', params: [{ name: 'unix', type: 'long', required: true }, { name: 'fmt', type: 'string', required: false }], returnType: 'string', description: 'Unix 时间戳转日期字符串', example: "from_unixtime(1753001600) → '2026-07-11 00:00:00'" },
  { name: 'add_months', category: 'date_time', signature: 'add_months(date, n)', params: [{ name: 'date', type: 'date', required: true }, { name: 'n', type: 'int', required: true }], returnType: 'date', description: '加 n 个月', example: "add_months('2026-07-11', 2) → '2026-09-11'" },
  { name: 'months_between', category: 'date_time', signature: 'months_between(end, start)', params: [{ name: 'end', type: 'date', required: true }, { name: 'start', type: 'date', required: true }], returnType: 'double', description: '月份差', example: "months_between('2026-09-11', '2026-07-11') → 2.0" },
  { name: 'last_day', category: 'date_time', signature: 'last_day(date)', params: [{ name: 'date', type: 'date', required: true }], returnType: 'date', description: '当月最后一天', example: "last_day('2026-07-11') → '2026-07-31'" },
  { name: 'next_day', category: 'date_time', signature: 'next_day(date, weekday)', params: [{ name: 'date', type: 'date', required: true }, { name: 'weekday', type: 'string', required: true }], returnType: 'date', description: '下一个指定星期几', example: "next_day('2026-07-11', 'Mon') → '2026-07-13'" },

  // ═══ Conditional (10) ═══
  { name: 'if', category: 'conditional', signature: 'if(condition, true_val, false_val)', params: [{ name: 'condition', type: 'boolean', required: true }, { name: 'true_val', type: 'any', required: true }, { name: 'false_val', type: 'any', required: true }], returnType: 'any', description: '条件分支', example: "if(amount > 100, 'high', 'low') → 'high'" },
  { name: 'case', category: 'conditional', signature: 'case(cond1, val1, cond2, val2, ..., default)', params: [{ name: '...pairs', type: '(boolean, any)...', required: true }], returnType: 'any', description: '多条件分支 (偶数个条件-值对，最后可选默认值)', example: "case(score > 90, 'A', score > 80, 'B', 'C')" },
  { name: 'coalesce', category: 'conditional', signature: 'coalesce(a, b, ...)', params: [{ name: '...values', type: 'any...', required: true }], returnType: 'any', description: '返回第一个非空值', example: 'coalesce(null, null, 42) → 42' },
  { name: 'nullif', category: 'conditional', signature: 'nullif(a, b)', params: [{ name: 'a', type: 'any', required: true }, { name: 'b', type: 'any', required: true }], returnType: 'any', description: '若 a==b 则返回 NULL', example: "nullif('N/A', 'N/A') → NULL" },
  { name: 'ifnull', category: 'conditional', signature: 'ifnull(a, b)', params: [{ name: 'a', type: 'any', required: true }, { name: 'b', type: 'any', required: true }], returnType: 'any', description: '若 a 为 NULL 返回 b', example: "ifnull(null, 'default') → 'default'" },
  { name: 'nvl', category: 'conditional', signature: 'nvl(a, b)', params: [{ name: 'a', type: 'any', required: true }, { name: 'b', type: 'any', required: true }], returnType: 'any', description: 'NULL 替换 (同 ifnull)', example: "nvl(null, 0) → 0" },
  { name: 'nvl2', category: 'conditional', signature: 'nvl2(a, b, c)', params: [{ name: 'a', type: 'any', required: true }, { name: 'b', type: 'any', required: true }, { name: 'c', type: 'any', required: true }], returnType: 'any', description: 'a 非 NULL 返回 b，否则返回 c', example: "nvl2(name, 'known', 'unknown') → 'known'" },
  { name: 'isnull', category: 'conditional', signature: 'isnull(a)', params: [{ name: 'a', type: 'any', required: true }], returnType: 'boolean', description: '判断是否为 NULL', example: 'isnull(null) → true' },
  { name: 'isnotnull', category: 'conditional', signature: 'isnotnull(a)', params: [{ name: 'a', type: 'any', required: true }], returnType: 'boolean', description: '判断是否非 NULL', example: 'isnotnull(42) → true' },
  { name: 'decode', category: 'conditional', signature: 'decode(expr, key1, val1, ..., default)', params: [{ name: 'expr', type: 'any', required: true }, { name: '...pairs', type: '(any, any)...', required: true }], returnType: 'any', description: '键值映射 (类似 switch)', example: "decode(status, 1, 'active', 2, 'inactive', 'unknown')" },

  // ═══ Array/Struct (15) ═══
  { name: 'array', category: 'array', signature: 'array(e1, e2, ...)', params: [{ name: '...elements', type: 'any...', required: true }], returnType: 'array', description: '构造数组', example: 'array(1, 2, 3) → [1, 2, 3]' },
  { name: 'array_contains', category: 'array', signature: 'array_contains(arr, elem)', params: [{ name: 'arr', type: 'array', required: true }, { name: 'elem', type: 'any', required: true }], returnType: 'boolean', description: '是否包含元素', example: "array_contains([1,2,3], 2) → true" },
  { name: 'array_join', category: 'array', signature: 'array_join(arr, delimiter)', params: [{ name: 'arr', type: 'array', required: true }, { name: 'delimiter', type: 'string', required: true }], returnType: 'string', description: '数组元素拼接为字符串', example: "array_join(['a','b','c'], ',') → 'a,b,c'" },
  { name: 'array_append', category: 'array', signature: 'array_append(arr, elem)', params: [{ name: 'arr', type: 'array', required: true }, { name: 'elem', type: 'any', required: true }], returnType: 'array', description: '追加元素到数组末尾', example: "array_append([1,2], 3) → [1, 2, 3]" },
  { name: 'array_prepend', category: 'array', signature: 'array_prepend(arr, elem)', params: [{ name: 'arr', type: 'array', required: true }, { name: 'elem', type: 'any', required: true }], returnType: 'array', description: '前置元素到数组开头', example: "array_prepend([2,3], 1) → [1, 2, 3]" },
  { name: 'explode', category: 'array', signature: 'explode(arr)', params: [{ name: 'arr', type: 'array', required: true }], returnType: 'rowset', description: '展开数组为多行', example: 'explode([1,2,3]) → 三行' },
  { name: 'size', category: 'array', signature: 'size(arr)', params: [{ name: 'arr', type: 'array', required: true }], returnType: 'int', description: '数组大小', example: 'size([1,2,3]) → 3' },
  { name: 'cardinality', category: 'array', signature: 'cardinality(arr)', params: [{ name: 'arr', type: 'array', required: true }], returnType: 'int', description: '数组元素数 (同 size)', example: 'cardinality([1,2,3]) → 3' },
  { name: 'element_at', category: 'array', signature: 'element_at(arr, idx)', params: [{ name: 'arr', type: 'array', required: true }, { name: 'idx', type: 'int', required: true }], returnType: 'any', description: '取指定下标元素 (1-indexed)', example: 'element_at([a,b,c], 2) → b' },
  { name: 'sort_array', category: 'array', signature: 'sort_array(arr)', params: [{ name: 'arr', type: 'array', required: true }], returnType: 'array', description: '数组升序排序', example: 'sort_array([3,1,2]) → [1,2,3]' },
  { name: 'slice', category: 'array', signature: 'slice(arr, start, len)', params: [{ name: 'arr', type: 'array', required: true }, { name: 'start', type: 'int', required: true }, { name: 'len', type: 'int', required: true }], returnType: 'array', description: '数组切片', example: 'slice([1,2,3,4], 2, 2) → [2,3]' },
  { name: 'map', category: 'array', signature: 'map(k1, v1, k2, v2, ...)', params: [{ name: '...pairs', type: '(any, any)...', required: true }], returnType: 'map', description: '构造 Map', example: "map('a', 1, 'b', 2) → {a:1, b:2}" },
  { name: 'map_keys', category: 'array', signature: 'map_keys(m)', params: [{ name: 'm', type: 'map', required: true }], returnType: 'array', description: '获取 Map 所有 key', example: "map_keys({a:1,b:2}) → ['a','b']" },
  { name: 'map_values', category: 'array', signature: 'map_values(m)', params: [{ name: 'm', type: 'map', required: true }], returnType: 'array', description: '获取 Map 所有 value', example: "map_values({a:1,b:2}) → [1,2]" },
  { name: 'struct', category: 'array', signature: 'struct(f1, f2, ...)', params: [{ name: '...fields', type: 'any...', required: true }], returnType: 'struct', description: '构造结构体', example: 'struct(name, age) → {name: "John", age: 30}' },

  // ═══ Window (12) ═══
  { name: 'row_number', category: 'window', signature: 'row_number()', params: [], returnType: 'int', description: '窗口内行号 (从1开始)', example: 'row_number() → 1, 2, 3...' },
  { name: 'rank', category: 'window', signature: 'rank()', params: [], returnType: 'int', description: '排名 (有间隔，并列跳号)', example: 'rank() → 1, 2, 2, 4' },
  { name: 'dense_rank', category: 'window', signature: 'dense_rank()', params: [], returnType: 'int', description: '密集排名 (无间隔)', example: 'dense_rank() → 1, 2, 2, 3' },
  { name: 'lead', category: 'window', signature: 'lead(col, offset, default)', params: [{ name: 'col', type: 'any', required: true }, { name: 'offset', type: 'int', required: false, defaultValue: '1' }, { name: 'default', type: 'any', required: false }], returnType: 'any', description: '取后行值', example: 'lead(amount, 1, 0) → 下一行的 amount' },
  { name: 'lag', category: 'window', signature: 'lag(col, offset, default)', params: [{ name: 'col', type: 'any', required: true }, { name: 'offset', type: 'int', required: false, defaultValue: '1' }, { name: 'default', type: 'any', required: false }], returnType: 'any', description: '取前行值', example: 'lag(amount, 1, 0) → 上一行的 amount' },
  { name: 'first_value', category: 'window', signature: 'first_value(col)', params: [{ name: 'col', type: 'any', required: true }], returnType: 'any', description: '窗口内第一个值', example: 'first_value(amount) → 窗口首行 amount' },
  { name: 'last_value', category: 'window', signature: 'last_value(col)', params: [{ name: 'col', type: 'any', required: true }], returnType: 'any', description: '窗口内最后一个值', example: 'last_value(amount) → 窗口末行 amount' },
  { name: 'nth_value', category: 'window', signature: 'nth_value(col, n)', params: [{ name: 'col', type: 'any', required: true }, { name: 'n', type: 'int', required: true }], returnType: 'any', description: '窗口内第 n 个值', example: 'nth_value(amount, 3) → 窗口第3行 amount' },
  { name: 'percent_rank', category: 'window', signature: 'percent_rank()', params: [], returnType: 'double', description: '百分位排名 (0-1)', example: 'percent_rank() → 0.25' },
  { name: 'cume_dist', category: 'window', signature: 'cume_dist()', params: [], returnType: 'double', description: '累积分布 (0-1)', example: 'cume_dist() → 0.5' },
  { name: 'ntile', category: 'window', signature: 'ntile(n)', params: [{ name: 'n', type: 'int', required: true }], returnType: 'int', description: '分桶编号 (1-n)', example: 'ntile(4) → 1, 2, 3, 4' },
  { name: 'sum_over', category: 'window', signature: 'sum(col)', params: [{ name: 'col', type: 'number', required: true }], returnType: 'number', description: '窗口求和', example: 'sum(amount) over(...) → 窗口内累计求和', isAggregate: true },

  // ═══ Casting (8) ═══
  { name: 'cast', category: 'casting', signature: 'cast(expr as type)', params: [{ name: 'expr', type: 'any', required: true }, { name: 'type', type: 'string', required: true }], returnType: 'any', description: '通用类型转换', example: "cast('42' as int) → 42" },
  { name: 'to_string', category: 'casting', signature: 'to_string(x)', params: [{ name: 'x', type: 'any', required: true }], returnType: 'string', description: '转为字符串', example: 'to_string(42) → "42"' },
  { name: 'to_int', category: 'casting', signature: 'to_int(x)', params: [{ name: 'x', type: 'any', required: true }], returnType: 'int', description: '转为整数', example: 'to_int("42") → 42' },
  { name: 'to_long', category: 'casting', signature: 'to_long(x)', params: [{ name: 'x', type: 'any', required: true }], returnType: 'long', description: '转为长整数', example: 'to_long("10000000000") → 10000000000L' },
  { name: 'to_double', category: 'casting', signature: 'to_double(x)', params: [{ name: 'x', type: 'any', required: true }], returnType: 'double', description: '转为双精度浮点', example: 'to_double("3.14") → 3.14' },
  { name: 'to_float', category: 'casting', signature: 'to_float(x)', params: [{ name: 'x', type: 'any', required: true }], returnType: 'float', description: '转为浮点', example: 'to_float("3.14") → 3.14f' },
  { name: 'to_decimal', category: 'casting', signature: 'to_decimal(x, p, s)', params: [{ name: 'x', type: 'any', required: true }, { name: 'p', type: 'int', required: true }, { name: 's', type: 'int', required: true }], returnType: 'decimal', description: '转为指定精度的十进制', example: 'to_decimal("3.14159", 5, 2) → 3.14' },
  { name: 'to_boolean', category: 'casting', signature: 'to_boolean(x)', params: [{ name: 'x', type: 'any', required: true }], returnType: 'boolean', description: '转为布尔值', example: 'to_boolean(1) → true' },

  // ═══ Hash (3) ═══
  { name: 'md5', category: 'hash', signature: 'md5(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: 'MD5 哈希 (32位)', example: "md5('hello') → '5d41402abc4b2a76b9719d911017c592'" },
  { name: 'sha256', category: 'hash', signature: 'sha256(str)', params: [{ name: 'str', type: 'string', required: true }], returnType: 'string', description: 'SHA-256 哈希 (64位)', example: "sha256('hello') → '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824'" },
  { name: 'hash', category: 'hash', signature: 'hash(col1, col2, ...)', params: [{ name: '...cols', type: 'any...', required: true }], returnType: 'int', description: '多列联合哈希', example: 'hash(name, birthday) → 1742019284' },
];
