/**
 * 认知资产只读展示的派生逻辑（议题1 / 补充设计 §8、§13 SUP-COG-01/02）。
 *
 * <p>纯展示层，无 IO、无状态。资产实例来自 governed-class 既有列表端点
 * （fetchBeliefs / fetchHypotheses / fetchModels，均在 cognitiveClient 单源出口后 re-export），
 * 本模块把三种异构行归一为统一的 {@link CognitiveAssetRow} 供卡片渲染，
 * 并把服务端下发的枚举名（生命周期态 / 置信种类）映射为 i18n key（带原值兜底，禁伪造语义）。</p>
 *
 * <p><b>C/K 互斥守护（§8.4）</b>：本模块只读取、只展示，不提供任何写通道，
 * 不 import KB extract/ingest 服务。</p>
 *
 * @license Apache-2.0
 */

/** 治理资产类（服务端 governedAssetClasses = BELIEF/HYPOTHESIS/MODEL；保留开放字符串兼容后端扩类）。 */
export type CognitiveAssetClass = 'BELIEF' | 'HYPOTHESIS' | 'MODEL' | (string & {});

/** 归一后的只读资产行（字段全部来自服务端真实响应；缺失即 null，禁合成）。 */
export interface CognitiveAssetRow {
  /** 传给 resolve 的资产 id（后端 parseRef 校验 bel/hyp/mod 前缀） */
  assetId: string;
  assetClass: CognitiveAssetClass;
  /** 主标题：从行内首个非空语义字段取，取不到则回退到 assetId（真实可见，非占位假名） */
  label: string;
  /** 副文本：假设陈述 / 信念说明 / 模型类型等，可空 */
  detail: string | null;
  /** 原始状态（lifecycle_state 优先，回落 legacy status）；null 表示服务端未给 */
  status: string | null;
  updatedAt: string | null;
  /** belief 专属：概率值（0~1，来自 probability 列） */
  probability: number | null;
  /** belief 专属：置信种类（BeliefProbabilityKind 名） */
  probabilityKind: string | null;
}

type Rec = Record<string, unknown>;

function asRecord(v: unknown): Rec | null {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Rec) : null;
}

/** 取首个非空字符串字段；全部缺失返回 null（不造默认名）。 */
function firstString(row: Rec, keys: readonly string[]): string | null {
  for (const k of keys) {
    const v = row[k];
    if (typeof v === 'string' && v.trim() !== '') return v;
  }
  return null;
}

/** 取首个有限数值字段。 */
function firstNumber(row: Rec, keys: readonly string[]): number | null {
  for (const k of keys) {
    const v = row[k];
    if (typeof v === 'number' && Number.isFinite(v)) return v;
  }
  return null;
}

/**
 * 把某 governed-class 的列表响应归一为行集。
 * 列表端点返回形态三态兼容：裸数组 / { rows } / { data }（与后端 ApiResponse 解包后一致）。
 */
export function normalizeAssetRows(raw: unknown, assetClass: CognitiveAssetClass): CognitiveAssetRow[] {
  const arr: unknown[] = Array.isArray(raw)
    ? raw
    : asRecord(raw)
      ? (Array.isArray((raw as Rec).rows)
          ? ((raw as Rec).rows as unknown[])
          : Array.isArray((raw as Rec).data)
            ? ((raw as Rec).data as unknown[])
            : [])
      : [];

  return arr
    .map((item): CognitiveAssetRow | null => {
      const row = asRecord(item);
      if (!row) return null;
      const assetId = firstString(row, ['assetId', 'id', 'hypothesisId', 'beliefId', 'modelId', 'model_id']);
      if (!assetId) return null; // 无 id 无法 resolve，丢弃（不显示假行）
      const label =
        firstString(row, ['statement', 'variableName', 'variable_name', 'name', 'title', 'modelName', 'reason']) ?? assetId;
      const detail =
        firstString(row, assetClass === 'BELIEF'
          ? ['reason', 'description', 'domain']
          : assetClass === 'MODEL'
            ? ['modelType', 'model_type', 'description', 'status']
            : ['reason', 'description', 'mindId', 'domain']);
      const status = firstString(row, ['lifecycleState', 'lifecycle_state', 'status']);
      const updatedAt = firstString(row, ['updatedAt', 'updated_at', 'updateTime', 'update_time']);
      return {
        assetId,
        assetClass,
        label,
        detail,
        status,
        updatedAt,
        probability: firstNumber(row, ['probability']),
        probabilityKind: firstString(row, ['confidenceKind', 'probKind', 'prob_kind', 'confidence_kind']),
      };
    })
    .filter((r): r is CognitiveAssetRow => r !== null);
}

/**
 * 补充设计 §1.2 / 前端设计方案 §1.4.2 对 B 层承诺、但后端**尚无治理列表端点**的类型
 * （取证 2026-10-12：cognitive-engine `CognitiveApiPaths` 资产路径池仅
 * catalog/resolve/proposals/transitions；catalog.governedAssetClasses 恒为
 * BELIEF/HYPOTHESIS/MODEL；Decision Basis / Pattern·Case 无列表端点）。
 * 仅用于列表页「端点未就绪」引导块渲染（复盘 S6），不参与 LIST_DISPATCH、不伪装成资产类。
 */
export const PENDING_GOVERNED_CLASSES = ['DECISION_BASIS', 'PATTERN_CASE'] as const;

/** 生命周期态 → i18n key（服务端 CognitiveAssetStatus 名单源；未知值回落 raw 展示，禁伪造映射）。 */
export function assetStatusLabelKey(state: string | null): string | null {
  if (!state) return null;
  return `knowledge.cognitiveAsset.status.${state}`;
}

/** 置信种类 → i18n key（BeliefProbabilityKind；未知回落 raw）。 */
export function probKindLabelKey(kind: string | null): string | null {
  if (!kind) return null;
  return `knowledge.cognitiveAsset.probKind.${kind}`;
}

/** 未校准族（§8.3 红线：UNCALIBRATED_* 必须视觉区分统计概率，禁与概率混同）。 */
const UNCALIBRATED_KINDS = new Set(['UNCALIBRATED_CONFIDENCE', 'UNCALIBRATED_MODEL_ESTIMATE']);
const LEGACY_KINDS = new Set(['UNKNOWN_LEGACY']);

export function isUncalibratedKind(kind: string | null): boolean {
  return kind !== null && UNCALIBRATED_KINDS.has(kind);
}

export function isLegacyKind(kind: string | null): boolean {
  return kind !== null && LEGACY_KINDS.has(kind);
}

/**
 * 生命周期态分类（仅用于徽标语义色；不改变服务端原值，不做状态推断）。
 * 命中 ACTIVE/APPROVED → 正向；异常族 → 警示；终态 → 中性。
 */
export type AssetStatusTone = 'positive' | 'pending' | 'warning' | 'terminal' | 'unknown';
export function assetStatusTone(state: string | null): AssetStatusTone {
  if (!state) return 'unknown';
  if (state === 'ACTIVE' || state === 'APPROVED') return 'positive';
  if (state === 'VALIDATED' || state === 'EVIDENCE_CHECKED' || state === 'CANDIDATE' || state === 'DRAFT') return 'pending';
  if (state === 'CONTRADICTED' || state === 'EXPIRED' || state === 'SUSPENDED') return 'warning';
  if (state === 'SUPERSEDED' || state === 'REJECTED' || state === 'DEPRECATED') return 'terminal';
  return 'unknown';
}
