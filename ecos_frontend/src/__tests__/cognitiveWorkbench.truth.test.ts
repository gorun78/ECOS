// @vitest-environment node
import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
const source = readFileSync(fileURLToPath(new URL("../pages/CognitiveWorkbench.tsx", import.meta.url)), "utf8");
describe("CognitiveWorkbench integration boundary", () => {
  it("uses existing scenario, completeness and Mind API clients", () => {
    expect(source).toContain("WORKSPACE_SCENARIOS_BASE");
    expect(source).toContain("getCompleteness(scenarioId)");
    expect(source).toContain("loadCompleteness(selectedScenarioId)");
    expect(source).toContain("fetchMindList(50)");
  });
  it("links to authoritative scenario and Mind governance routes", () => {
    expect(source).toContain('to="/workbench"');
    expect(source).toContain('to="/decision/mind"');
    expect(source).toContain("/decision/mind/");
  });
  it("does not contain sample business truth or fake local governance actions", () => {
    expect(source).not.toContain("惠企政策申报量上升");
    expect(source).not.toContain("setCandidateState");
    expect(source).not.toContain("示例场景 / SAMPLE");
    expect(source).not.toContain("Math.random");
  });
  it("keeps backend completeness and Mind migration state authoritative", () => {
    expect(source).toContain("report.blocking");
    expect(source).toContain("report.requiredEdges");
    expect(source).toContain("response.r4ColumnsProvisioned");
    expect(source).toContain("mind.validityStatus");
  });
});
