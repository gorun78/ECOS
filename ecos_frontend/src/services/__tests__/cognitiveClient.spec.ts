/**
 * cognitiveClient auth 单源断言（design-05 F05-17 验收 / X-51 / B.9-2）。
 *
 * 验收命令：`npx vitest run src/services/__tests__/cognitiveClient.spec.ts -t "authHeaders come only from services/auth"`
 *
 * 约束：`services/cognitiveClient.ts` 的 Bearer 头**只能**来自
 * `services/auth.ts#getAuthToken()`（全仓唯一 auth 通道）；任何
 * `localStorage['token' | 'accessToken' | 'jwt']` / `sessionStorage['jwt']`
 * 直读都视为违例（本用例将直读埋雷为 DECOY 并在 storage 替身上守卫）。
 *
 * 环境：node（仓库 jsdom@29 在 node 20.18.1 下 ERR_REQUIRE_ESM，见同目录约定
 * `src/__tests__/file-size-lint.test.ts` 的 `// @vitest-environment node`）。
 */
// @vitest-environment node
import { describe, it, expect, vi, afterEach } from "vitest";

const TOKEN = "cog-single-source-token";
const TOKEN_KEYS = ["token", "accessToken", "jwt"];

const authMock = vi.hoisted(() => ({
  getAuthToken: vi.fn(() => TOKEN),
}));

// ../auth 与 cognitiveClient 的 "./auth" 解析到同一模块 id → 单源通道被整体替换
vi.mock("../auth", () => authMock);

import {
  detectScenarioMinds,
  fetchOperationEval,
  fetchScenarioHypotheses,
  fetchScenarioBeliefs,
  CognitiveApiError,
} from "../cognitiveClient";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** 敌意 storage 替身：token 三键的任何读取都被记录且返回 DECOY 值。 */
type FakeStorage = {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
  clear(): void;
};

function hostileStorage(name: string, reads: string[]): FakeStorage {
  const real: Record<string, string> = {
    token: "DECOY-FORBIDDEN-DIRECT-READ",
    accessToken: "DECOY-FORBIDDEN-DIRECT-READ",
    jwt: "DECOY-FORBIDDEN-DIRECT-READ",
  };
  return {
    getItem(key: string): string | null {
      if (TOKEN_KEYS.includes(key)) reads.push(`${name}['${key}']`);
      return key in real ? real[key] : null;
    },
    setItem(key: string, value: string): void {
      real[key] = value;
    },
    removeItem(key: string): void {
      delete real[key];
    },
    clear(): void {
      for (const k of Object.keys(real)) delete real[k];
    },
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("cognitiveClient auth 单源（X-51）", () => {
  it("authHeaders come only from services/auth", async () => {
    // 1) 守卫 + 埋雷：任何绕过 services/auth 的 storage token 键直读都会被记录，
    //    且拿到的值是 DECOY —— 若拼进 Authorization 也会在第 3 步被抓现行
    const illegalReads: string[] = [];
    vi.stubGlobal("localStorage", hostileStorage("localStorage", illegalReads));
    vi.stubGlobal("sessionStorage", hostileStorage("sessionStorage", illegalReads));

    // 2) fetch 打桩，捕获每次请求实际发出的 Authorization 头
    const fetchMock = vi.fn(async (url: string, init?: RequestInit): Promise<Response> => {
      void url;
      void init;
      return jsonResponse(200, { success: true, data: [] });
    });
    vi.stubGlobal("fetch", fetchMock as unknown as typeof fetch);

    await detectScenarioMinds("sc-1");
    await fetchOperationEval("sc-1", { mind: "m-1", limit: 10 });
    await fetchScenarioHypotheses("sc-1");
    await fetchScenarioBeliefs("sc-1", { mind: "m-1" });

    expect(fetchMock).toHaveBeenCalledTimes(4);
    for (const [url, init] of fetchMock.mock.calls) {
      const headers = (init?.headers ?? {}) as Record<string, string>;
      expect(headers["Authorization"], `Bearer 头必须由 getAuthToken 构造：${url}`).toBe(
        `Bearer ${TOKEN}`,
      );
      expect(headers["Authorization"]).not.toContain("DECOY");
      expect(url).toMatch(
        /^\/api\/v1\/business\/scenarios\/sc-1\/cognition(\/detect|\/operation-eval|\/hypotheses|\/beliefs)/,
      );
    }
    expect(authMock.getAuthToken).toHaveBeenCalled();

    // 3) 错误矩阵路径（503 ENGINE_DISABLED）同样只经 getAuthToken
    fetchMock.mockImplementation(async () =>
      jsonResponse(503, { success: false, message: "engine not enabled" }),
    );
    const err = await detectScenarioMinds("sc-1").catch((e: unknown) => e);
    expect(err).toBeInstanceOf(CognitiveApiError);
    expect((err as CognitiveApiError).code).toBe("ENGINE_DISABLED");
    expect((err as CognitiveApiError).status).toBe(503);

    // 4) 全程零次 storage token 键直读
    expect(illegalReads, "cognitiveClient 不得直读 localStorage/sessionStorage 的 token 键").toEqual(
      [],
    );
  });
});
