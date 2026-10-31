// @vitest-environment node
/**
 * 台账附录E E.6 三件落点收口门禁（#146ax，v1.0.223）：
 *   unit: e6Placements.test.ts › E.6 落点收口
 *
 * 「三件」= 附录E 要求但工作树缺位的三个落点：
 *   1. src/config/navConfig.ts      —— 数据单源仍在 src/navigation/navConfig.ts（90+ 消费方不动）
 *   2. src/config/route-manifest.ts —— 数据单源仍在仓根 config/route-manifest.ts
 *                                      （vite.config.ts / server.ts / routeManifest.test.ts 以
 *                                        `./config/route-manifest` 相对串消费，详设-08 门禁钉死该串）
 *   3. src/theme/index.ts           —— 主题单源仍在 src/components/ThemeContext.tsx
 *
 * 落点形态 = 「再导出别名」：文件内只有 `export * from`，禁任何数据/实现行
 * （const/function/class/import 语句一律红）。变异探针取证后定档：
 *   注入 `export const X = 1` 红 / 删除落点文件红 → 复原绿。
 *
 * 判定不翻转登记：落点在位 ≠ 附录逐字合规终裁——消费面未变（数据本体未搬、
 * 消费路径未改），见台账 v1.0.223 订正注。
 */
import { readFileSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");

/** 剥注释后只留真实代码行（判据作用于代码面，注释不算数据行）。 */
function codeOf(rel: string): string[] {
  const raw = readFileSync(resolve(REPO, rel), "utf8");
  const noBlock = raw.replace(/\/\*[\s\S]*?\*\//g, "");
  return noBlock
    .split("\n")
    .map((l) => l.replace(/\/\/.*$/, ""))
    .map((l) => l.trim())
    .filter((l) => l.length > 0);
}

/** 每个落点：再导出目标 + 代码行数上限（JSDoc 剥掉后只剩 1 行 re-export + 容差）。 */
const PLACEMENTS: Array<{ file: string; reExport: string }> = [
  { file: "src/config/route-manifest.ts", reExport: 'export * from "../../config/route-manifest";' },
  { file: "src/config/navConfig.ts", reExport: 'export * from "../navigation/navConfig";' },
  { file: "src/theme/index.ts", reExport: 'export * from "../components/ThemeContext";' },
];

/** 数据/实现行判据：落点内出现即红（拦二次搬家 + 数据复制两份）。 */
const DATA_LINE = /^(export\s+(default\s+|async\s+)?(const|let|var|function|class)\b|import\s|\bconst\s|\bfunction\s|\bclass\s)/;

describe("E.6 落点收口（附录E 偏差三件，#146ax）", () => {
  it("三落点在位且每题恰一条再导出、零数据行", () => {
    for (const p of PLACEMENTS) {
      const code = codeOf(p.file);
      expect(
        code.filter((l) => l === p.reExport),
        `${p.file} 须恰含一次「${p.reExport}」（再导出落点锚）`,
      ).toHaveLength(1);
      const illegal = code.filter((l) => l !== p.reExport && DATA_LINE.test(l));
      expect(
        illegal,
        `${p.file} 发现数据/实现行（落点只允许 re-export，数据本体禁复制）: ${illegal.join(" | ")}`,
      ).toHaveLength(0);
    }
  });

  it("三个数据本体单源文件仍在原位（落点不得伴随搬家）", () => {
    for (const src of [
      "config/route-manifest.ts",
      "src/navigation/navConfig.ts",
      "src/components/ThemeContext.tsx",
    ]) {
      expect(existsSync(resolve(REPO, src)), `数据单源失位: ${src}`).toBe(true);
    }
  });

  it("再导出真承流：三落点 import 后可取到数据本体真值面", async () => {
    const rm = await import("../config/route-manifest");
    expect(Array.isArray(rm.ROUTE_MANIFEST)).toBe(true);
    expect(rm.ROUTE_MANIFEST.length).toBeGreaterThan(25);
    const nav = await import("../config/navConfig");
    const navKeys = Object.keys(nav).filter((k) => !k.startsWith("__"));
    expect(navKeys.length).toBeGreaterThan(0);
    const theme = await import("../theme");
    expect(typeof theme.useTheme).toBe("function");
  });
});
