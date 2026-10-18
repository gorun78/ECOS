/**
 * 案例库 REST 收口 (H6-T2) — 自 src/components/CaseLibraryView.tsx 迁入。
 * 原语义保持：无鉴权头；返回整包 JSON（**连非 2xx 也照原样 `resp.json()`**，
 * 本域改前就没有 ok 判定，合流单源不得顺手加上）。
 *
 * <p>**传输合流（#143/B5-11e）**：四条腿改走单源匿名通道 `rawAnonUrlFetch`
 * （C2′「匿名腿不算豁免」判据，订立依据见台账 §13.2.4 C2′ 注⑬）。基址 `/cases`
 * 不在 `/api` 之下 ⇒ 必须走**全 URL** 通道而不拼 `API_BASE`：URL 逐字不变是本类
 * 传输批的硬约束（一旦拼前缀，dev 代理表里 `/cases` 这条 vite 兜底条目就会被改写成
 * 另一条路由，等于偷换承流路径）。</p>
 *
 * <p>**D-B5-37（本批只做源码 + 配置两面取证，未起服 curl、未 live 复验，
 * 不宣布这四条腿功能可用，也不宣布已修）**：`/cases` 族在后端**零映射**——
 * 全仓 `ecos_backend` 的 `*.java`/`*.yml` 搜 `/cases` 与 `*Case*Controller` 均无命中。
 * 前端侧 `config/route-manifest.ts` 把它登记为 `engine: gateway / viteFallback: true /
 * alwaysGateway: false`，且同文件的构建期校验 ③b 明写「vite 兜底条目（如 /datanet、/cases）
 * 历史上即无 BFF 显式承流」并把「/api 之外的 vite 兜底 + alwaysGateway」判为清单错误 ⇒
 * 生产链路（Express BFF）下 `/cases/*` 无归属、只会被 SPA 兜底吞掉（`resp.json()`
 * 解 HTML ⇒ SyntaxError）。⇒ 这四条腿是一组**死腿**，与 D-B5-33（世界模型四死腿，
 * 后端零映射）同族，而不同于 D-B5-36（映射实存、只缺身份面）。
 * 消费面 `CaseLibraryView` 的 loadFailed/searchFailed/recordFailed 是真实错误态
 * （不折 mock、不播种假数据），但 `viewDetail` 的 `catch → setDetail(null)`
 * 会把「死腿」折成「该案例没有详情」——该折叠属前端缺陷 #105 队列，不在本传输批改动。</p>
 */
import { rawAnonUrlFetch } from './httpClient';

/** 案例库基址：`/cases`（非 `/api` 族，见文件头 D-B5-37）。 */
const CASES_BASE = '/cases';

export async function fetchCaseLibrary(): Promise<any> {
  const resp = await rawAnonUrlFetch(CASES_BASE);
  return resp.json();
}

export async function searchCaseLibrary(q: string, k = 10): Promise<any> {
  const resp = await rawAnonUrlFetch(`${CASES_BASE}/search?q=${encodeURIComponent(q)}&k=${k}`);
  return resp.json();
}

export async function fetchCaseDetail(id: number): Promise<any> {
  const resp = await rawAnonUrlFetch(`${CASES_BASE}/${id}`);
  return resp.json();
}

export async function recordCase(body: Record<string, unknown>): Promise<any> {
  const resp = await rawAnonUrlFetch(`${CASES_BASE}/record`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return resp.json();
}
