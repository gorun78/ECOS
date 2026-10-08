/**
 * scenarioClient.ts — src/api.ts 按域拆分产物（design-05 B.9-1 / X-50 / F05-17）。
 * 经 src/api.ts re-export shim 保持既有 from "../api" 导入面零破坏。
 */
import { apiFetchData } from "../services/httpClient";
import { COGNITIVE_BASE } from "../services/cognitiveClient";

// ── Cognitive Engine (S5-1.5) ──────────────────────────────────
// COGNITIVE_BASE 路径常量单源：services/cognitiveClient.ts（X-47/X-50 收敛）

/** POST /api/v1/cognitive/reason — 规则推理 / 因果分析 */
export async function apiCognitiveReason(body: {
  mode: string;
  facts: Record<string, any>;
  context?: Record<string, any>;
  options?: Record<string, any>;
}): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/reason`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** POST /api/v1/cognitive/optimize — 帕累托优化 */
export async function apiCognitiveOptimize(body: {
  problem: Record<string, any>;
  params?: Record<string, any>;
}): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/optimize`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** GET /api/v1/cognitive/blueprint — 六层蓝图健康度 */
export async function apiCognitiveBlueprint(layer?: string): Promise<any> {
  const qs = layer ? `?layer=${encodeURIComponent(layer)}` : '';
  return apiFetchData(`${COGNITIVE_BASE}/blueprint${qs}`);
}

/** POST /api/v1/cognitive/plan — 创建执行计划 */
export async function apiCognitiveCreatePlan(body: {
  source: Record<string, any>;
  priority?: string;
  targets?: Record<string, any>;
}): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/plan`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** GET /api/v1/cognitive/plan/{id} — 查询执行计划 */
export async function apiCognitiveGetPlan(id: string): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/plan/${encodeURIComponent(id)}`);
}

/** GET /api/v1/cognitive/health — 认知引擎健康检查 */
export async function apiCognitiveHealth(): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/health`);
}

