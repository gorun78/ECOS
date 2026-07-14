import { describe, it, expect } from 'vitest';

describe('Test framework initialization', () => {
  it('vitest and testing-library are working', () => {
    expect(1 + 1).toBe(2);
  });

  it('jsdom environment is available', () => {
    expect(typeof document).toBe('object');
    expect(typeof window).toBe('object');
    expect(document.createElement('div').tagName).toBe('DIV');
  });
});
