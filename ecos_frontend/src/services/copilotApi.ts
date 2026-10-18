/**
 * Copilot REST 收口 (H6-T2) — 自 src/components/CopilotPanel.tsx 迁入。
 * 原语义保持：无鉴权头；返回 `{ok, json}`，仅 ok 时解析（非 2xx **不抛错**，
 * ok 面由调用方 `CopilotPanel` 自行分态）。
 *
 * <p>**传输合流（#143/B5-11e）**：两条腿改走单源匿名通道 `rawAnonUrlFetch`
 * （C2′「匿名腿不算豁免」判据，见台账 §13.2.4 C2′ 注⑬），基址由 `API_BASE` 派生，
 * 两条 URL 逐字不变；同时从 `api-base-single-source` 基线剪除本文件。</p>
 *
 * <p>**D-B5-36 同族扩展（本批源级 + 配置级取证，未起服 curl、未 live 复验）**：
 * 与 #143/B5-11d 那四条匿名腿不同，这两条的后端映射**实存**——
 * ai-engine-impl `CopilotController` @ `/api/v1/agent/copilot`（`@GetMapping("/quick-questions")`、
 * `@PostMapping("/chat")`）。而 `AnonymousEndpointRegistry` 的 ANONYMOUS 面零条 `/api/v1/agent`，
 * sysman-impl `SecurityConfig` 收口为 `anyRequest().authenticated()` ⇒
 * **不带 `Authorization` 的两条腿走 gateway 必 401**；dev 下 `config/route-manifest.ts`
 * 的 `/api/v1/agent` 条目 `directInDev: true` 直连 :18084（引擎侧无 SecurityFilterChain）
 * 才拿得到应答。补 Bearer 会把这两条腿从「匿名面」搬到「身份 + clearance 裁决面」，
 * 属语义变化、不在传输批授权内 ⇒ 与 D-B5-36 一并待产品裁决（加身份 or 登记 permitAll）。</p>
 */
import { API_BASE, rawAnonUrlFetch } from './httpClient';

export async function fetchCopilotQuickQuestions(agentType: string): Promise<{ ok: boolean; json?: any }> {
  const res = await rawAnonUrlFetch(`${API_BASE}/v1/agent/copilot/quick-questions?agentId=agent-${agentType}`);
  return { ok: res.ok, json: res.ok ? await res.json() : undefined };
}

export async function copilotChat(body: { agentId: string; message: string; sessionId: string }): Promise<{ ok: boolean; json?: any }> {
  const res = await rawAnonUrlFetch(`${API_BASE}/v1/agent/copilot/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return { ok: res.ok, json: res.ok ? await res.json() : undefined };
}
