/**
 * stepGuideData — InteractiveStepGuide 的静态定义与 mock 数据
 *
 * 从 `components/pipeline/InteractiveStepGuide.tsx` 机械抽取（H6-T4 组件行数治理）：
 * 类型、stub 产物、算子表、入湖原始数据与飞行员维度表均逐字保留（含原有中文演示文案）。
 *
 * @license Apache-2.0
 */

export interface PipelineBuilderOutput {
  datasetPath: string;
  columns: string[];
  rowCount: number;
  lastCompiled: string;
  expressionsCount: number;
}

// Pipeline 后端未就绪时的占位产物
export const STUB_PIPELINE_OUTPUT: PipelineBuilderOutput = {
  datasetPath: '/aviation/silver/ds_flights_clean',
  columns: ['flight_id', 'carrier', 'origin', 'dest', 'delay_minutes', 'pilot_id', 'pilot_name'],
  rowCount: 15000,
  lastCompiled: new Date().toISOString(),
  expressionsCount: 6,
};

// ── 算子定义 ─────────────────────────────────────────────────

export interface Operator {
  id: string;
  name: string;
  desc: string;
  icon: string;
  color: string;
  type: 'filter' | 'regex' | 'nulls' | 'join' | 'cast';
}

export const STATIC_OPERATORS: Operator[] = [
  { id: 'op-filter', name: 'Row Filter (行过滤算子)', desc: '筛选 delay_minutes 大于特定数值的异常飞行记录。', icon: 'Sliders', color: 'bg-blue-100 text-blue-700 border-blue-200', type: 'filter' },
  { id: 'op-regex', name: 'Regex Clean (正则清洗)', desc: '自动剔除航司 carrier 名称首尾的空白字符并转大写。', icon: 'Code', color: 'bg-indigo-100 text-indigo-700 border-indigo-200', type: 'regex' },
  { id: 'op-nulls', name: 'Null Coalesce (空值填充)', desc: '发现 pilot_name 为 Null 时，自动填充为默认值。', icon: 'AlertTriangle', color: 'bg-amber-100 text-amber-700 border-amber-200', type: 'nulls' },
  { id: 'op-join', name: 'Hash Join (主外键关联)', desc: '将 flights 表与 pilots 维度表通过 pilot_id 进行物理 Hash Join。', icon: 'GitBranch', color: 'bg-purple-100 text-purple-700 border-purple-200', type: 'join' },
  { id: 'op-cast', name: 'Type Cast (类型强转)', desc: '将 delay_minutes 的 String 类型转为 Integer 强类型。', icon: 'RefreshCw', color: 'bg-emerald-100 text-emerald-700 border-emerald-200', type: 'cast' },
];

// ── Mock 入湖数据 ────────────────────────────────────────────

export interface IngressRow {
  flight_id: string;
  carrier: string;
  origin: string;
  dest: string;
  delay_minutes: string | number | null;
  pilot_id: string;
  pilot_name?: string;
}

export const INGEST_RAW_DATA: IngressRow[] = [
  { flight_id: 'FL-102', carrier: '  airchina  ', origin: 'pek', dest: 'sha', delay_minutes: '0', pilot_id: 'PL-001' },
  { flight_id: 'FL-224', carrier: 'chinaeastern ', origin: 'pvg', dest: 'can', delay_minutes: '24', pilot_id: 'PL-002' },
  { flight_id: 'FL-509', carrier: ' sichuanair', origin: 'tfu', dest: 'pek', delay_minutes: '5', pilot_id: 'PL-001' },
  { flight_id: 'FL-771', carrier: 'chinasouthern', origin: 'can', dest: 'hkg', delay_minutes: '45', pilot_id: 'PL-003' },
  { flight_id: 'FL-088', carrier: 'springair', origin: 'sha', dest: 'szx', delay_minutes: '0', pilot_id: 'PL-004' },
  { flight_id: 'FL-912', carrier: ' hainanair ', origin: 'hak', dest: 'pek', delay_minutes: '12', pilot_id: 'PL-002' },
];

export const MOCK_PILOTS_DIM: Record<string, string> = {
  'PL-001': '张伟 (经验:12年)',
  'PL-002': '王芳 (经验:8年)',
  'PL-003': '李杰 (经验:15年)',
  'PL-004': '赵敏 (经验:5年)',
};

// 实时响应式数据预览（原组件内 getTransformedData，逻辑逐字保留）
export function applyTransformOperators(
  appliedOperators: Operator[],
  filterMinutes: number,
  nullFillerValue: string,
): IngressRow[] {
  let result = [...INGEST_RAW_DATA];

  appliedOperators.forEach((op) => {
    if (op.type === 'filter') {
      result = result.filter((r) => {
        const val = parseInt(r.delay_minutes?.toString() || '0', 10);
        return val >= filterMinutes;
      });
    } else if (op.type === 'regex') {
      result = result.map((r) => ({ ...r, carrier: r.carrier.trim().toUpperCase() }));
    } else if (op.type === 'nulls') {
      result = result.map((r) => ({ ...r, pilot_name: r.pilot_name || nullFillerValue }));
    } else if (op.type === 'join') {
      result = result.map((r) => ({ ...r, pilot_name: MOCK_PILOTS_DIM[r.pilot_id] || nullFillerValue }));
    } else if (op.type === 'cast') {
      result = result.map((r) => ({ ...r, delay_minutes: parseInt(r.delay_minutes?.toString() || '0', 10) }));
    }
  });

  return result;
}
