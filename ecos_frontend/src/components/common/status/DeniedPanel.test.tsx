/**
 * DeniedPanel 冻结门禁用例（详设-08 §2.3「无权限」行）：
 * `unit: DeniedPanel.test.tsx › mask placeholder is not empty string`
 * @license Apache-2.0
 */
import { describe, it, expect, afterEach, vi } from "vitest";
import React from "react";
import { render, cleanup, screen, fireEvent, within } from "@testing-library/react";
import { ThemeProvider } from "../../ThemeContext";
import { LanguageProvider } from "../../LanguageContext";
import DeniedPanel, { MASK_PLACEHOLDER } from "./DeniedPanel";

const testGlobal = globalThis as unknown as { __ecos_test_locale?: "zh" | "en" };

function renderWithProviders(ui: React.ReactElement) {
  testGlobal.__ecos_test_locale = "en";
  return render(
    <LanguageProvider>
      <ThemeProvider>{ui}</ThemeProvider>
    </LanguageProvider>
  );
}

afterEach(() => {
  cleanup();
  delete testGlobal.__ecos_test_locale;
});

describe("DeniedPanel", () => {
  it("mask placeholder is not empty string", () => {
    const { container } = renderWithProviders(
      <DeniedPanel
        missingPermissions={["ontology:publish", "kb:rule:read"]}
        // 调用方误传真实值 / 空串：均只应出掩码符
        maskedFields={[
          { label: "id_card", value: "" },
          { label: "bank_account", value: "6222020200112233" },
        ]}
      />
    );

    expect(MASK_PLACEHOLDER.trim().length).toBeGreaterThan(0);
    const placeholders = Array.from(container.querySelectorAll("dd"));
    expect(placeholders.length).toBe(2);
    for (const node of placeholders) {
      // 掩码态不得渲染为空串
      expect((node.textContent ?? "").trim().length).toBeGreaterThan(0);
      expect(node.textContent).toBe(MASK_PLACEHOLDER);
    }
    // 真实敏感值绝不回显
    expect(container.textContent).not.toContain("6222020200112233");
  });

  it("explains missing permissions and renders the request-access entry", () => {
    const onRequestAccess = vi.fn();
    renderWithProviders(
      <DeniedPanel missingPermissions={["ontology:publish"]} onRequestAccess={onRequestAccess} traceId="trace-deny-1" />
    );
    expect(screen.getByText("Access denied")).toBeInTheDocument();
    expect(screen.getByText("ontology:publish")).toBeInTheDocument();
    expect(screen.getByText(/Trace ID/)).toBeInTheDocument();
    fireEvent.click(within(screen.getByRole("alert")).getByRole("button", { name: /Request access/i }));
    expect(onRequestAccess).toHaveBeenCalledTimes(1);
  });
});
