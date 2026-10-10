/**
 * MyTodoPanel 冻结门禁用例（详设-08 §2.3「等待用户」行）：
 * `unit: MyTodoPanel.test.tsx › renders require_approval items`
 * @license Apache-2.0
 */
import { describe, it, expect, afterEach } from "vitest";
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import { ThemeProvider } from "../../ThemeContext";
import { LanguageProvider } from "../../LanguageContext";
import MyTodoPanel from "./MyTodoPanel";

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

describe("MyTodoPanel", () => {
  it("renders require_approval items", () => {
    renderWithProviders(
      <MyTodoPanel
        items={[
          { id: "t1", title: "Release Q3 budget plan", state: "require_approval", deadline: "2026-10-12 18:00" },
          { id: "t2", title: "Auto-synced catalog refresh", state: "allow" },
          { id: "t3", title: "Blocked ontology publish", state: "deny" },
          { id: "t4", title: "Masked customer export", state: "allow_with_mask" },
        ]}
      />
    );

    // 醒目「等待你的确认」+ 仅 require_approval 条目入列
    expect(screen.getByText("Awaiting your confirmation")).toBeInTheDocument();
    expect(screen.getByText("Release Q3 budget plan")).toBeInTheDocument();
    expect(screen.getByText(/Deadline: 2026-10-12 18:00/)).toBeInTheDocument();
    // 其余三态不在队列中展开（数据源 = Policy Guard require_approval 队列，非前端改判）
    expect(screen.queryByText("Auto-synced catalog refresh")).not.toBeInTheDocument();
    expect(screen.queryByText("Blocked ontology publish")).not.toBeInTheDocument();
    expect(screen.queryByText("Masked customer export")).not.toBeInTheDocument();
  });

  it("renders explicit empty state when no item awaits approval", () => {
    renderWithProviders(<MyTodoPanel items={[{ id: "t2", title: "Auto-synced catalog refresh", state: "allow" }]} />);
    expect(screen.getByText("Nothing is waiting for your approval")).toBeInTheDocument();
  });
});
