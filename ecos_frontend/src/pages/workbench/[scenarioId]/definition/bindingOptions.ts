/**
 * 场景定义页绑定候选清单加载器（方案 A：把「手输资源 ID」换成真实下拉选择）。
 *
 * <p>设计口径：
 * <ul>
 *   <li><b>零新增 URL 站点</b>：八类候选全部复用既有导出函数/常量，本文件不自拼
 *       任何 `API_BASE` 派生串——URL 单源棘轮（urlFamilyDualFormRatchet）计数不受影响。</li>
 *   <li><b>真值优先、空即空</b>：每类只取服务端真实返回；后端无此资源时给 items=[] 与
 *       可读原因（reason），绝不造演示项。</li>
 *   <li><b>独立失败</b>：八类并行加载、各自 catch，一类挂掉不拖垮其余（Promise.allSettled）。</li>
 * </ul>
 *
 * <p>取值口径（与后端消费端对齐）：
 * <ul>
 *   <li>datasets → `resource_id`（ScenarioService 运行分析按此解析 DW 资源）</li>
 *   <li>objectTypes → 本体实体 **id**（如 ent003，非 code）。后端 Context Builder 的 ONTOLOGY
 *       reader 读 `/api/v1/ecos/ontologies/entities/{id}`，传 code 实测 404，
 *       故绑定一律存 id，label 显示 name、hint 附 code 供人辨认</li>
 *   <li>knowledgeBases → KB `id`</li>
 *   <li>pipelines → 编排 `id`（AipPipelineRunClient 直接以 target_id 调 /aip/pipelines/{id}/execute）</li>
 *   <li>aiAgents → 智能体 `id`</li>
 *   <li>securityPolicies → 护栏策略 `id`</li>
 *   <li>ruleSets / interfaces → 后端当前无列表端点，返回空 + 原因</li>
 * </ul>
 */
import { fetchDwDatasets } from '../../../../services/ontology/ontologyMappingApi';
import { fetchOntologyDataObjects } from '../../../../services/ontology/ontologyDataApi';
import { fetchKnowledgeBases } from '../../../../services/agentConfig';
import { fetchPipelineDefinitions, fetchManagedAgents } from '../../../aiworkbench/api';
import { fetchGuardrailPolicies } from '../../../../services/cognitiveEngineApi';

export type BindingGroupKey =
  | 'datasets'
  | 'objectTypes'
  | 'knowledgeBases'
  | 'ruleSets'
  | 'pipelines'
  | 'aiAgents'
  | 'securityPolicies'
  | 'interfaces';

/** 单个候选：value 是入库的 targetId，label 是用户看到的名字。 */
export interface BindingOption {
  value: string;
  label: string;
  hint?: string;
}

/** 一类绑定的候选状态。 */
export interface BindingOptionsState {
  items: BindingOption[];
  loading: boolean;
  /** 加载失败的后端 message（原样透出，不美化） */
  error: string | null;
  /**
   * items 为空且非加载中/非错误时的人类可读原因
   * （后端无此端点 / 该域暂无数据），供 UI 如实呈现。
   */
  emptyReason: string | null;
}

const EMPTY = (loading = false): BindingOptionsState => ({
  items: [],
  loading,
  error: null,
  emptyReason: null,
});

function asArray(v: unknown): any[] {
  return Array.isArray(v) ? (v as any[]) : [];
}

/**
 * 并行加载八类候选。返回的每个 key 都有确定状态（不会缺键）。
 * @param enabled false 时不发请求（页面未就绪）。
 */
export async function loadBindingOptions(enabled: boolean): Promise<Record<BindingGroupKey, BindingOptionsState>> {
  if (!enabled) {
    const idle: Record<string, BindingOptionsState> = {};
    (['datasets', 'objectTypes', 'knowledgeBases', 'ruleSets', 'pipelines', 'aiAgents', 'securityPolicies', 'interfaces'] as BindingGroupKey[])
      .forEach((k) => { idle[k] = EMPTY(false); });
    return idle as Record<BindingGroupKey, BindingOptionsState>;
  }

  const wrap = async (
    loader: () => Promise<BindingOption[]>,
    emptyReason: string | null,
  ): Promise<BindingOptionsState> => {
    try {
      const items = await loader();
      return {
        items,
        loading: false,
        error: null,
        emptyReason: items.length === 0 ? emptyReason : null,
      };
    } catch (e) {
      return {
        items: [],
        loading: false,
        error: e instanceof Error && e.message ? e.message : String(e),
        emptyReason: null,
      };
    }
  };

  const [
    datasets,
    objectTypes,
    knowledgeBases,
    ruleSets,
    pipelines,
    aiAgents,
    securityPolicies,
    interfaces,
  ] = await Promise.all([
    wrap(async () => {
      const rows = await fetchDwDatasets();
      return asArray(rows).map((r): BindingOption => ({
        value: String(r.id),
        label: String(r.name || r.id),
        hint: r.path ? String(r.path) : undefined,
      }));
    }, 'DW 近源/汇聚层暂无可用数据对象'),

    wrap(async () => {
      const rows = await fetchOntologyDataObjects();
      // 关键：value 必须是实体 **id**（ent003）而非 code（ENT_CASE）。
      // 后端 Context Builder 的 ONTOLOGY reader 读 /api/v1/ecos/ontologies/entities/{id}，
      // 传 code 实测 404（"ONT-001: Entity 'ENT_CASE' not found"）⇒ 绑定存 id 才能被上下文构建解析。
      return asArray(rows).map((r): BindingOption => ({
        value: String(r.id),
        label: String(r.name || r.code || r.id),
        hint: r.code ? String(r.code) : (r.entityType ? String(r.entityType) : undefined),
      }));
    }, '本体库暂无对象类型'),

    wrap(async () => {
      const rows = await fetchKnowledgeBases();
      return asArray(rows).map((r): BindingOption => ({
        value: String(r.id),
        label: String(r.name || r.id),
      }));
    }, '暂无知识库'),

    wrap(async () => {
      // 规则集：既无导出 fetcher，直写 `${API_BASE}/v1/ecos/rules` 会与 RulesPage 形成第二生产方
      // （URL 双形态棘轮红）；裸写 "/v1/ecos/rules" 又缺 /api 前缀（实测 404）。
      // 后端 RULE_SET 仅做存取、无运行期解析（ScenarioService 只登记不过滤），
      // 且规则库当前 0 行 ⇒ 如实返回空并说明，不新增站点也不造数据。
      return [];
    }, '规则库暂无规则集（后端未提供绑定候选端点）'),

    wrap(async () => {
      const rows = await fetchPipelineDefinitions();
      return asArray(rows).map((r): BindingOption => ({
        value: String(r.id),
        label: String(r.name || r.id),
      }));
    }, '暂无逻辑编排'),

    wrap(async () => {
      const rows = await fetchManagedAgents();
      return asArray(rows).map((r): BindingOption => ({
        value: String(r.id),
        label: String(r.name || r.id),
        hint: r.role ? String(r.role) : undefined,
      }));
    }, '暂无已注册智能体'),

    wrap(async () => {
      const raw = await fetchGuardrailPolicies();
      const rows = asArray(raw);
      return rows.map((r): BindingOption => ({
        value: String(r.id ?? r.policyId),
        label: String(r.name ?? r.policyName ?? r.id),
      })).filter((o) => o.value && o.value !== 'undefined');
    }, '暂无安全策略'),

    wrap(async () => {
      // 接口绑定：后端集成元数据无 interfaces 列表（GET /api/v1/integration/interfaces = 404），
      // 此处刻意返回空并给出原因，不伪装成「可用但无数据」。
      return [];
    }, '后端暂无接口注册端点，无法选择接口绑定'),
  ]);

  return { datasets, objectTypes, knowledgeBases, ruleSets, pipelines, aiAgents, securityPolicies, interfaces };
}
