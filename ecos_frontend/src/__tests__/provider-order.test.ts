// @vitest-environment node
/**
 * 详细设计-08 §九 门禁「Provider 顺序」冻结标识（M0）：
 *   unit: provider-order.test.ts › languageProviderWrapsStrictMode （W202/C184）
 *
 * 背景：main.tsx 现网形态已是 <LanguageProvider> 包 <StrictMode>（112-114 与 216-217），
 * 本任务只补门禁固化该包装顺序，防止后续改造（如 GPTs Global 简化分支）把
 * LanguageProvider 挪进 StrictMode 内部导致双语上下文在 StrictMode 双调用下失稳。
 * 归属声明：main.tsx 由其他 agent 拥有 —— 本文件只做源码面断言，不修改 main.tsx。
 */
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

describe("provider-order › main.tsx 包装顺序 (W202/C184)", () => {
  const mainSrc = readFileSync(resolve(__dirname, "../main.tsx"), "utf8");

  it("languageProviderWrapsStrictMode — LanguageProvider 在 StrictMode 外层", () => {
    // 定位挂载入口：createRoot(...).render(...) 之后的首个 JSX 包装层
    const renderIdx = mainSrc.indexOf("createRoot(");
    expect(renderIdx).toBeGreaterThanOrEqual(0);
    const after = mainSrc.slice(renderIdx);

    const openLP = after.indexOf("<LanguageProvider>");
    const openSM = after.indexOf("<StrictMode>");
    expect(openLP).toBeGreaterThanOrEqual(0);
    expect(openSM).toBeGreaterThanOrEqual(0);
    // 外层 provider 的开标签必须先于 StrictMode 开标签出现
    expect(openLP).toBeLessThan(openSM);

    const closeSM = after.indexOf("</StrictMode>");
    const closeLP = after.indexOf("</LanguageProvider>");
    expect(closeSM).toBeGreaterThanOrEqual(0);
    expect(closeLP).toBeGreaterThanOrEqual(0);
    // 对称闭合：StrictMode 先关、LanguageProvider 后关 → 确认包含关系而非并列
    expect(closeSM).toBeLessThan(closeLP);

    // LanguageProvider 必须是根包装（紧跟 render( 后的第一个 JSX 元素）
    const firstTag = after.slice("createRoot(".length).match(/<([A-Za-z][\w.]*)/);
    expect(firstTag?.[1]).toBe("LanguageProvider");
  });
});
