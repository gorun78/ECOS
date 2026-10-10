/**
 * RunProgress 冻结门禁用例（详设-08 §2.3「运行中」行）：
 * `unit: RunProgress.test.tsx › cancel button posts to kill-switch endpoint`
 * @license Apache-2.0
 */
import { describe, it, expect, vi, afterEach } from "vitest";
import React from "react";
import { render, cleanup, fireEvent, waitFor, screen } from "@testing-library/react";
import { ThemeProvider } from "../../ThemeContext";
import { LanguageProvider } from "../../LanguageContext";
import RunProgress from "./RunProgress";
import { apiFetch } from "../../../services/httpClient";

// 收敛层桩：断言取消走 httpClient（apiFetch），组件内不裸 fetch
vi.mock("../../../services/httpClient", () => ({
  apiFetch: vi.fn(() => Promise.resolve({ code: 200 })),
}));

const mockedApiFetch = vi.mocked(apiFetch);

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
  mockedApiFetch.mockClear();
  delete testGlobal.__ecos_test_locale;
});

describe("RunProgress", () => {
  it("cancel button posts to kill-switch endpoint", async () => {
    renderWithProviders(
      <RunProgress
        runId="run-42"
        progressChannel="poll"
        pollIntervalSeconds={3}
        steps={[{ id: "s1", label: "Fetch forecast baseline", state: "running" }]}
      />
    );

    // 不可逆二次确认：先点「Cancel run」打开确认框，再点确认才发请求（§5.2 Kill Switch 约束）
    fireEvent.click(screen.getByRole("button", { name: /Cancel run/i }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm kill switch" }));

    await waitFor(() =>
      expect(mockedApiFetch).toHaveBeenCalledWith(
        "/v1/wagent/kill-switch",
        expect.objectContaining({ method: "POST" })
      )
    );
  });

  it("poll channel shows non-realtime fallback note and never claims streaming", () => {
    renderWithProviders(<RunProgress runId="run-1" progressChannel="poll" pollIntervalSeconds={5} steps={[]} />);
    expect(screen.getByText(/Degraded: not real-time \(polling every 5s\)/i)).toBeInTheDocument();
  });

  it("switches to poll note after 3 sse disconnects and fires onFallbackToPoll once", () => {
    const onFallbackToPoll = vi.fn();
    const { rerender } = renderWithProviders(
      <RunProgress runId="run-2" progressChannel="sse" steps={[]} onFallbackToPoll={onFallbackToPoll} />
    );
    expect(onFallbackToPoll).not.toHaveBeenCalled();

    rerender(
      <LanguageProvider>
        <ThemeProvider>
          <RunProgress runId="run-2" progressChannel="sse" sseDisconnectCount={3} steps={[]} onFallbackToPoll={onFallbackToPoll} />
        </ThemeProvider>
      </LanguageProvider>
    );
    expect(onFallbackToPoll).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/Degraded: not real-time/i)).toBeInTheDocument();
  });
});
