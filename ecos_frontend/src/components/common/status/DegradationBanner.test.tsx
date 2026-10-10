/**
 * DegradationBanner 冻结门禁用例（详设-08 §2.3「降级」行）：
 * `unit: DegradationBanner.test.tsx › shows poll fallback note`
 * @license Apache-2.0
 */
import { describe, it, expect, afterEach, vi } from "vitest";
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import { ThemeProvider } from "../../ThemeContext";
import { LanguageProvider } from "../../LanguageContext";
import DegradationBanner from "./DegradationBanner";

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

describe("DegradationBanner", () => {
  it("shows poll fallback note", () => {
    renderWithProviders(
      <DegradationBanner active reasons={["Readiness B below target A"]} progressChannel="poll" pollIntervalSeconds={3} />
    );
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(screen.getByText("Degradation notice")).toBeInTheDocument();
    // 轮询兜底 ⇒ 必须显示「降级：非实时（轮询 3 秒）」，不得声称流式
    expect(screen.getByText("Degraded: not real-time (polling every 3s)")).toBeInTheDocument();
  });

  it("renders nothing when inactive and shows details entry when provided", () => {
    const { unmount } = renderWithProviders(<DegradationBanner active={false} />);
    expect(screen.queryByText("Degradation notice")).not.toBeInTheDocument();
    unmount();

    const onOpenDetails = vi.fn();
    renderWithProviders(<DegradationBanner active reasons={["engine kb degraded"]} onOpenDetails={onOpenDetails} progressChannel="sse" />);
    expect(screen.getByText("engine kb degraded")).toBeInTheDocument();
    // sse 通道不显示轮询注记
    expect(screen.queryByText(/not real-time/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "View details" }));
    expect(onOpenDetails).toHaveBeenCalledTimes(1);
  });
});
