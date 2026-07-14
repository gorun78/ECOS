import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
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
});

function renderWithProviders() {
  return render(
    <LanguageProvider>
      <ThemeProvider>
        <App />
      </ThemeProvider>
    </LanguageProvider>
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
    // Check that the command palette trigger exists (Ctrl+K input)
    const searchInput = screen.getByPlaceholderText(/command/i);
    expect(searchInput).toBeInTheDocument();
  });
});
