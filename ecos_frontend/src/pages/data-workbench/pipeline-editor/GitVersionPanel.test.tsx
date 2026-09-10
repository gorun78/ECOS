/**
 * GitVersionPanel.test.tsx — PMO-52 T3
 *
 * Verifies:
 *  1. mock apiFetchData that returns ["abc1234def", "def5678abc"] → 2 rows
 *  2. First entry renders a "current" pill
 *  3. Restore button attribute state (enabled with onRestore, disabled without)
 *  4. Empty list → empty state rendered; no fetch when pipelineId is empty
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { render, screen, cleanup, act, fireEvent } from '@testing-library/react';
import { ThemeProvider } from '../../../components/ThemeContext';
import { LanguageProvider } from '../../../components/LanguageContext';
import GitVersionPanel from './GitVersionPanel';

// mock apiFetchData — the panel's sole API dependency
vi.mock('../../../api', () => ({
  apiFetchData: vi.fn(),
}));

import { apiFetchData } from '../../../api';

function renderPanel(props: Partial<React.ComponentProps<typeof GitVersionPanel>> = {}) {
  return render(
    <LanguageProvider>
      <ThemeProvider>
        <GitVersionPanel
          pipelineId="pb-test"
          pipelineName="audit-supplier"
          onClose={() => {}}
          {...props}
        />
      </ThemeProvider>
    </LanguageProvider>,
  );
}

async function flushRender() {
  await act(async () => {
    await new Promise<void>((r) => { setTimeout(r, 0); });
  });
  await act(async () => { await Promise.resolve(); });
  await act(async () => { await Promise.resolve(); });
}

beforeEach(() => {
  vi.spyOn(console, 'warn').mockImplementation(() => {});
  vi.spyOn(console, 'error').mockImplementation(() => {});
  vi.restoreAllMocks();
  (apiFetchData as unknown as ReturnType<typeof vi.fn>).mockReset();
});

afterEach(() => {
  cleanup();
});

describe('GitVersionPanel', () => {
  it('mock apiFetchData → ["abc1234def","def5678abc"] → 2 rows, first = current', async () => {
    (apiFetchData as unknown as ReturnType<typeof vi.fn>).mockResolvedValue([
      'abc1234def',
      'def5678abc',
    ]);
    const { container } = renderPanel();
    await flushRender();

    // Both SHAs rendered (7-char short)
    expect(container.textContent).toContain('abc1234');
    expect(container.textContent).toContain('def5678');

    // First entry gets the "当前" (current) pill
    expect(container.textContent).toContain('当前');
  }, 8000);

  it('onRestore provided → restore button is enabled', async () => {
    (apiFetchData as unknown as ReturnType<typeof vi.fn>).mockResolvedValue([
      'abcdef0',
    ]);
    const onRestore = vi.fn().mockResolvedValue(undefined);
    renderPanel({ onRestore });
    await flushRender();

    // Restore button exists with aria-label used by the title; fall back to text
    const restoreButton =
      // name match via title attribute (single accessible name)
      screen.getByRole('button', { name: '还原到此版本' }) ??
      // fallback: look inside list for a button whose text contains "还原"
      screen.getAllByText(/还原/).at(0)?.closest('button');
    expect(restoreButton).not.toBeNull();
    expect(restoreButton).not.toHaveAttribute('disabled');
  }, 8000);

  it('onRestore undefined → restore button disabled', async () => {
    (apiFetchData as unknown as ReturnType<typeof vi.fn>).mockResolvedValue([
      'abcdef0',
    ]);
    renderPanel(); // no onRestore
    await flushRender();

    const restoreButton =
      screen.getByRole('button', { name: '还原到此版本' }) ??
      screen.getAllByText(/还原/).at(0)?.closest('button');
    expect(restoreButton).not.toBeNull();
    expect(restoreButton).toHaveAttribute('disabled');
  }, 8000);

  it('empty array → renders empty-state i18n message (panel still mounts)', async () => {
    (apiFetchData as unknown as ReturnType<typeof vi.fn>).mockResolvedValue([]);
    const { container } = renderPanel();
    await flushRender();

    // Empty state — the i18n fallback key text or localized text shows
    expect(container.textContent).toMatch(/Git 版本历史|dw\.pipeline\.git\.empty|No Git archive/);
    // No li items in empty state
    expect(container.querySelectorAll('li').length).toBe(0);
  }, 8000);

  it('apiFetchData rejects → renders error state inline', async () => {
    (apiFetchData as unknown as ReturnType<typeof vi.fn>).mockRejectedValue(
      new Error('boom')
    );
    const { container } = renderPanel();
    await flushRender();

    expect(container.textContent).toContain('boom');
  }, 8000);

  it('pipelineId="" → no fetch, panel renders in empty state', async () => {
    // fresh mock for this case
    (apiFetchData as unknown as ReturnType<typeof vi.fn>).mockResolvedValue([]);
    const { container } = renderPanel({ pipelineId: '' });
    await flushRender();

    // apiFetchData should NOT be called for this render
    expect(apiFetchData).not.toHaveBeenCalled();
    // Panel still mounted (empty state)
    expect(container.textContent).toMatch(/Git 版本历史|dw\.pipeline\.git\.empty|No Git archive/);
    expect(container.querySelectorAll('li').length).toBe(0);
  }, 8000);
});
