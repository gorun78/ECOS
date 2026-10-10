// @vitest-environment node
import { describe, it, expect } from "vitest";
import { readBff, findLines } from "./bff.mri";

// C201/W219 (2026-10-10 登录饿死 body 事故)：BFF 内 **禁止** app 级全局 body 解析器。
// 根因：vite.middlewares（dev 分支）自带 /api 代理，全局 express.json 先于它抽干 req 流，
//       而 http-proxy 仍按原 Content-Length 透传 ⇒ 上游（gateway）等不到任何 body 字节，
//       阻塞至 socket 超时。实证：POST /api/v1/auth/login 经 :3000 = HTTP 000/25s，
//       同一 body 直连 :8080 = 200/0.16s；GET 与零长 POST 正常 ⇒ 仅"带 body 的方法"受害。
// 口径：解析器只能作为**中间件参数**挂在 BFF 自有 forwardProxy 挂载点上（forwardProxy 需要
//       req.body 做再序列化），且每个 forwardProxy 挂载点都必须挂上它，否则该点丢 body。
describe("bff-body-parser-scope: 禁止 app 级 express.json，解析器仅随 forwardProxy 挂载（C201/W219）", () => {
  const src = readBff();

  const MOUNT_RE = /app\.use\([^,]+,\s*(?:jsonBody,\s*)?forwardProxy\(/;
  const SCOPED_MOUNT_RE = /app\.use\([^,]+,\s*jsonBody,\s*forwardProxy\(/;

  it("不存在 app 级全局 body 解析器（app.use(express.json…) 命中数 = 0）", () => {
    expect(findLines(src, /app\.use\(\s*express\.json\s*\(/).length).toBe(0);
  });

  it("解析器以命名中间件形态声明一次（const jsonBody = express.json(… )）", () => {
    expect(findLines(src, /^const jsonBody = express\.json\(/m).length).toBe(1);
  });

  it("每个 forwardProxy 挂载点都带 jsonBody（细节前缀 + dev/prod 两支 /api 兜底，共 ≥9）", () => {
    const mounts = findLines(src, MOUNT_RE);
    const scoped = findLines(src, SCOPED_MOUNT_RE);
    expect(mounts.length).toBeGreaterThanOrEqual(9);
    expect(scoped.length).toBe(mounts.length);
  });

  it("dev 支 /api 兜底仍是 vite.middlewares → /api（C175 顺序未被本次改动破坏）", () => {
    const viteLine = findLines(src, /app\.use\(vite\.middlewares\)/)[0]?.line;
    const catchAll = findLines(src, /app\.use\(\s*["']\/api["']\s*,\s*jsonBody,\s*forwardProxy\(GATEWAY,\s*["']gateway\/default["']\)/);
    expect(viteLine).toBeDefined();
    expect(catchAll.length).toBe(2); // dev + prod 各一
    expect(viteLine!).toBeLessThan(Math.min(...catchAll.map((h) => h.line)));
  });
});
