// @vitest-environment node
import { readFileSync, readdirSync, statSync } from "node:fs";
import { extname, join, resolve } from "node:path";
import { describe, expect, it, vi } from "vitest";
vi.setConfig({ testTimeout: 30_000 });

/**
 * 详细设计-06 F06-19-7（X-82 身份伪造面归零）AI 侧验收断言。
 *
 * <p>主身份只能来自 token（`services/auth.ts`），禁止前端自填 `userId` /
 * `tenantId` 字面量注入请求体/头。反向案例 = `pages/aiworkbench/api.ts`
 * 的 `oagPlaygroundStream` 曾硬编码 `userId:'admin', tenantId:'default'`。
 * 本 spec 以源码扫描固化：全 `src/` 的 AI 面请求构造里不得再出现"自填主体身份"
 * 的字面量形态；一旦回归即 FAIL。
 */
const SRC_ROOT = resolve(__dirname, "../..");
const AI_DIR = resolve(SRC_ROOT, "src/pages/aiworkbench");

function listTsFiles(dir: string): string[] {
  const out: string[] = [];
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) out.push(...listTsFiles(full));
    else if (extname(full) === ".ts" || extname(full) === ".tsx") out.push(full);
  }
  return out;
}

describe("ai › 前端不再自填主体身份（F06-19-7 / X-82 safeguard）", () => {
  it("oagPlaygroundStream 请求体不再注入 userId/tenantId 字面量", () => {
    const apiSrc = readFileSync(join(AI_DIR, "api.ts"), "utf8");
    const fns = apiSrc
      .split("export async function")
      .filter((p) => p.trimStart().startsWith("oagPlaygroundStream"));
    expect(fns.length).toBe(1);
    const slice = fns[0].slice(0, fns[0].indexOf("});") + 3);
    expect(slice).not.toMatch(/userId\s*:\s*['"]/);
    expect(slice).not.toMatch(/tenantId\s*:\s*['"]/);
  });

  /**
   * #143/B5-13 形态订正（判据意图不变、指纹换形）：本门原断言
   * `import { authHeaders } from '../../services/auth'` + 腿内 `authHeaders()`，
   * 而该形态已被「传输合流」取代——流式腿改调 httpClient 具名通道，Bearer 在单源内拼。
   * 若只把断言删掉，本门就退化成「没人自填」的空门；故改成钉三段可核事实：
   * ① 腿内不出现裸 `fetch(`（传输在单源）；② 腿内不出现 `Authorization`（身份不在域内造）；
   * ③ 单源通道本体确实从 `services/auth#getAuthToken()` 取号（防止「换个地方自填」）。
   */
  it("oagPlaygroundStream 鉴权走 token 单源（#143/B5-13 起经具名流式通道）", () => {
    const apiSrc = readFileSync(join(AI_DIR, "api.ts"), "utf8");
    expect(apiSrc).not.toMatch(/[^A-Za-z0-9_.$-]fetch\s*\(/);
    expect(apiSrc).not.toMatch(/Authorization\s*[:=]/);
    const slice = apiSrc
      .split("export async function")
      .filter((p) => p.trimStart().startsWith("oagPlaygroundStream"))[0];
    expect(slice).toContain("rawStreamUrlFetch(");

    const httpSrc = readFileSync(join(SRC_ROOT, "src/services/httpClient.ts"), "utf8");
    const channel = httpSrc.slice(httpSrc.indexOf("export async function rawStreamUrlFetch"));
    expect(channel.slice(0, channel.indexOf("\n}"))).toMatch(/getAuthToken\(\)/);
  });

  it("全 AI 面源码无 userId/tenantId 字符串字面量自填（护栏）", () => {
    const leak: string[] = [];
    for (const f of listTsFiles(AI_DIR)) {
      const src = readFileSync(f, "utf8");
      const lines = src.split("\n");
      for (let i = 0; i < lines.length; i++) {
        if (/(?:userId|tenantId)\s*:\s*['"](?:admin|default|guest|\d)/.test(lines[i])) {
          leak.push(`${f}:${i + 1}  ${lines[i].trim()}`);
        }
      }
    }
    expect(leak).toEqual([]);
  });
});
