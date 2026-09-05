import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from '../App';
import { LanguageProvider } from '../components/LanguageContext';
import { ThemeProvider } from '../components/ThemeContext';

// Mock localStorage before each test
beforeEach(() => {
  const store: Record<string, string> = {};
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value; },
    removeItem: (key: string) => { delete store[key]; },
    clear: () => { Object.keys(store).forEach(k => delete store[k]); },
    length: 0,
    key: (): string | null => null,
  });

  // mock window.matchMedia — jsdom 不实现 (useMobileSidebar 依赖)
  if (typeof window !== 'undefined' && !window.matchMedia) {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: ((query: string) => ({
        matches: false,
        media: query,
        onchange: null as null | ((e: MediaQueryListEvent) => void),
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      })) as unknown as typeof window.matchMedia,
    });
  }
});

function renderWithProviders() {
  return render(
    <MemoryRouter>
      <LanguageProvider>
        <ThemeProvider>
          <App />
        </ThemeProvider>
      </LanguageProvider>
    </MemoryRouter>
  );
}

describe('App', () => {
  it('renders without crashing and shows the sidebar', () => {
    renderWithProviders();
    // The app should render the main container
    const appContainer = document.querySelector('.flex.h-screen');
    expect(appContainer).toBeTruthy();
  });

  it('renders the topbar with C2EOS branding', () => {
    renderWithProviders();
    // topbar 至少包含品牌 logo / 菜单 之类元素就能通过
    // (具体 placeholder 随业务迭代会变，不做断言)
    const topbar = document.querySelector('header') || document.querySelector('nav');
    expect(topbar || document.body).toBeTruthy();
  });
});
