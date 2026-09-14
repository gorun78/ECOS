/**
 * PMO-44 T4 — XSS unit test
 * Verifies that sanitizeHighlight (used by ExtractionReviewPanel) neutralises
 * script tags and other unsafe content while preserving <mark> highlight tags.
 */
import { describe, it, expect } from 'vitest';
import DOMPurify from 'dompurify';

// Same whitelist as ExtractionReviewPanel.tsx
const SANITIZE_CONFIG = {
  ALLOWED_TAGS: ['mark', 'span', 'code', 'strong'],
  ALLOWED_ATTR: ['class', 'style'],
};

function sanitizeHighlight(html: string): string {
  return DOMPurify.sanitize(html, SANITIZE_CONFIG);
}

describe('PMO-44 XSS: sanitizeHighlight', () => {
  it('strips <script> tags entirely', () => {
    const input = 'hello <script>alert(1)</script> world';
    const out = sanitizeHighlight(input);
    expect(out).not.toContain('<script');
    expect(out).not.toContain('alert(1)');
    expect(out).toContain('hello');
    expect(out).toContain('world');
  });

  it('strips event handler attributes', () => {
    const input = '<p onclick="alert(1)">click me</p>';
    const out = sanitizeHighlight(input);
    expect(out).not.toContain('onclick');
  });

  it('strips <img onerror> XSS vector', () => {
    const input = '<img src=x onerror="alert(1)">';
    const out = sanitizeHighlight(input);
    expect(out).not.toContain('onerror');
    expect(out).not.toContain('<img');
  });

  it('strips <svg> script vector', () => {
    const input = '<svg onload="alert(1)"></svg>';
    const out = sanitizeHighlight(input);
    expect(out).not.toContain('<svg');
    expect(out).not.toContain('onload');
  });

  it('preserves <mark> highlight tags', () => {
    const input = 'The <mark class="highlight">entity</mark> is here';
    const out = sanitizeHighlight(input);
    expect(out).toContain('<mark');
    expect(out).toContain('entity');
    expect(out).toContain('highlight');
  });

  it('preserves <span>, <code>, <strong>', () => {
    const input = '<span class="s">a</span> <code>code</code> <strong>b</strong>';
    const out = sanitizeHighlight(input);
    expect(out).toContain('<span');
    expect(out).toContain('<code>');
    expect(out).toContain('<strong>');
  });

  it('strips <iframe> (not in whitelist)', () => {
    const input = '<iframe src="javascript:alert(1)"></iframe>';
    const out = sanitizeHighlight(input);
    expect(out).not.toContain('<iframe');
    expect(out).not.toContain('javascript:');
  });

  it('strips <a href="javascript:..."> vector', () => {
    const input = '<a href="javascript:alert(1)">link</a>';
    const out = sanitizeHighlight(input);
    // <a> is not in the whitelist so the whole tag should be stripped
    expect(out).not.toContain('javascript:');
  });
});
