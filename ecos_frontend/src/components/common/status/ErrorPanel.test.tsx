/**
 * ErrorPanel 冻结门禁用例（详设-08 §2.3「失败」行）：
 * `unit: ErrorPanel.test.tsx › no stack trace rendered`
 * @license Apache-2.0
 */
import { describe, it, expect, afterEach, vi } from "vitest";
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import { ThemeProvider } from "../../ThemeContext";
import { LanguageProvider } from "../../LanguageContext";
import ErrorPanel from "./ErrorPanel";

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

/** 仿造异常堆栈样本（含典型 "at " 行与源码位置），断言一律不得出现在 DOM */
const FAKE_STACK = [
  "NullPointerException: Cannot invoke service",
  "    at KbEngineClient.query(KbEngineClient.java:128)",
  "    at DispatchService.handle(DispatchService.java:42)",
  "    at java.base/java.util.Thread.run(Thread.java:833)",
].join("\n");

describe("ErrorPanel", () => {
  it("no stack trace rendered", () => {
    const { container } = renderWithProviders(
      <ErrorPanel
        reason="Knowledge engine is temporarily unavailable, please retry later."
        traceId="trace-abc-123"
        stack={FAKE_STACK}
      />
    );

    const text = container.textContent ?? "";
    // 业务语言原因与 traceId 正常展示
    expect(screen.getByText(/Knowledge engine is temporarily unavailable/)).toBeInTheDocument();
    expect(text).toContain("trace-abc-123");
    // 堆栈被丢弃：既无堆栈原文，也无任何 "at X (File:Line)" 形态行
    expect(text).not.toContain("KbEngineClient.java");
    expect(text).not.toContain("NullPointerException");
    expect(text).not.toMatch(/\n?\s+at\s+\S+/);
  });

  it("offers retry / degrade / contact-admin actions", () => {
    const onRetry = vi.fn();
    const onDegrade = vi.fn();
    const onContactAdmin = vi.fn();
    renderWithProviders(
      <ErrorPanel reason="upstream 503" onRetry={onRetry} onDegrade={onDegrade} onContactAdmin={onContactAdmin} />
    );
    fireEvent.click(screen.getByRole("button", { name: /Retry/i }));
    fireEvent.click(screen.getByRole("button", { name: /Use degraded mode/i }));
    fireEvent.click(screen.getByRole("button", { name: /Contact administrator/i }));
    expect(onRetry).toHaveBeenCalledTimes(1);
    expect(onDegrade).toHaveBeenCalledTimes(1);
    expect(onContactAdmin).toHaveBeenCalledTimes(1);
  });
});
