// @vitest-environment node
/**
 * 详细设计-08 §九 门禁「承流不缓冲」冻结标识：
 *   unit: bff-streaming.spec.ts › sseChunksFlushWithoutBuffering （W192/C174，M0·A，P-4 前端侧落点）
 *
 * 载体（Node 侧，可先行，不依赖 7 JAR）：
 *   假上游按 chunked `text/event-stream` 逐块延时下发 → BFF（buildBffApp + forwardProxy）转发 →
 *   客户端在「首个 chunk」即收到数据，且首 chunk 到达时刻显著早于全量结束
 *   （若实现回归为 `await upstream.text()` 全缓冲，首 chunk 将与流结束同时到达 ⇒ 断言失败）。
 *
 * 与既有 src/__tests__/serverSsePassthrough.spec.ts（源码面断言）互补：本 spec 验证运行时行为。
 */
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { buildBffApp } from "../../server";

const CHUNK_COUNT = 3;
const CHUNK_INTERVAL_MS = 150;

describe("bff › streaming passthrough (W192/C174)", () => {
  let upstream: http.Server;
  let upstreamBase = "";
  let bff: http.Server;
  let bffUrl = "";

  beforeAll(async () => {
    // 假上游：SSE 测试路径逐块延时下发；其余路径回普通 JSON（验证非流式分支不回归）
    upstream = http.createServer((req, res) => {
      if ((req.url ?? "").includes("stream-test")) {
        res.writeHead(200, {
          "Content-Type": "text/event-stream",
          "Cache-Control": "no-cache",
          "Transfer-Encoding": "chunked",
        });
        let i = 0;
        const timer = setInterval(() => {
          i += 1;
          if (i <= CHUNK_COUNT) {
            res.write(`data: chunk-${i}\n\n`);
          } else {
            clearInterval(timer);
            res.end();
          }
        }, CHUNK_INTERVAL_MS);
        return;
      }
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ hit: "upstream-json", path: req.url }));
    });
    await new Promise<void>((r) => upstream.listen(0, "127.0.0.1", r));
    upstreamBase = `http://127.0.0.1:${(upstream.address() as AddressInfo).port}`;

    const { app } = buildBffApp({
      env: {
        NODE_ENV: "test",
        ECOS_LOCAL_DIRECT_SERVICE_PROXY: "false",
        GATEWAY_URL: upstreamBase,
      },
      devViteMiddleware: null,
    });
    await new Promise<void>((r) => {
      bff = app.listen(0, "127.0.0.1", r as () => void);
    });
    bffUrl = `http://127.0.0.1:${(bff.address() as AddressInfo).port}`;
  });

  afterAll(async () => {
    await new Promise<void>((r) => bff.close(() => r()));
    await new Promise<void>((r) => upstream.close(() => r()));
  });

  it("sseChunksFlushWithoutBuffering — 首个 SSE chunk 即刻透传，不等待全量缓冲", async () => {
    const started = Date.now();
    const res = await fetch(`${bffUrl}/api/v1/agent-loop/stream-test`);

    // 响应头口径：SSE 直通 + 关缓冲
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type") || "").toContain("text/event-stream");
    expect(res.headers.get("cache-control") || "").toContain("no-transform");
    expect(res.headers.get("x-accel-buffering")).toBe("no");

    const reader = res.body?.getReader();
    expect(reader).toBeTruthy();
    if (!reader) return;

    const firstChunkElapsedMs = { value: -1 };
    const texts: string[] = [];
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (firstChunkElapsedMs.value < 0) {
        firstChunkElapsedMs.value = Date.now() - started;
      }
      texts.push(new TextDecoder().decode(value));
    }
    const totalElapsedMs = Date.now() - started;

    // 内容完整：3 个 chunk 全部透传且保序
    const joined = texts.join("");
    for (let i = 1; i <= CHUNK_COUNT; i += 1) {
      expect(joined).toContain(`data: chunk-${i}`);
    }

    // 时序判据：上游逐块下发总时长 ≈ CHUNK_COUNT × INTERVAL（≈450ms）。
    // 不缓冲实现 ⇒ 首 chunk ≈ 150ms 即达；全缓冲实现 ⇒ 首 chunk ≈ 总时长（≈450ms+）。
    // 注：vitest 匹配子不支持自定义失败消息（TS2554），判读口径见上注释与下方断言顺序。
    expect(totalElapsedMs).toBeGreaterThanOrEqual((CHUNK_COUNT - 1) * CHUNK_INTERVAL_MS);
    expect(firstChunkElapsedMs.value).toBeLessThan(totalElapsedMs - CHUNK_INTERVAL_MS);
  });

  it("jsonUpstreamStillBufferedNormally — 非 SSE 上游保持既有 JSON 透传（行为不回归）", async () => {
    const res = await fetch(`${bffUrl}/api/whatever-json`);
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type") || "").toContain("application/json");
    const body = (await res.json()) as { hit: string; path: string };
    expect(body.hit).toBe("upstream-json");
    expect(body.path).toBe("/api/whatever-json");
  });
});
