/**
 * 数据资产看板 REST 收口 (H6-T2，#143/B5-11b 改单源) — 传输/Bearer/解包/401 退登/
 * 403 广播全部委托 `httpClient#apiFetchData`，本文件不再自起 fetch。
 *
 * 形状变更（调用方同批改）：单源已解一层 `data`，返回值即信封 `data` 内层，
 * 不再是整包 ⇒ 消费侧不得再写 `resp.data`（否则恒 undefined ⇒ 空列表假象）。
 *
 * 不传 `headers`：单源 `{ headers, ...options }` 形态下调用方自定义头会整组替换掉
 * Authorization（登记为 D-B5-34，属主 httpClient 共享原语，本批不改）。
 */
import { apiFetchData } from "./httpClient";

export async function dataAssetsRequest<T>(url: string, init?: RequestInit): Promise<T> {
  if (init?.headers !== undefined) {
    throw new Error(
      "dataAssetsRequest: 自定义 headers 会整组替换单源的 Content-Type/Authorization（D-B5-34），请改用单源支持的入参",
    );
  }
  return apiFetchData<T>(url, init);
}
