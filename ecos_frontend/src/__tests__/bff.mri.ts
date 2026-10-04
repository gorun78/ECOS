/**
 * Tiny source-scanner helper for BFF guard tests (C175/C189/C190 等).
 * No dependencies; reads files via node:fs; returns array of { line, text } hits.
 */
import { readFileSync } from "node:fs";
import path from "node:path";

const BFF_PATH = path.resolve(__dirname, "../../server.ts");

export function readBff(): string {
  return readFileSync(BFF_PATH, "utf8");
}

export interface LineHit {
  line: number;
  text: string;
}

export function findLines(src: string, re: RegExp): LineHit[] {
  const out: LineHit[] = [];
  const lines = src.split(/\r?\n/);
  lines.forEach((text, i) => {
    if (re.test(text)) out.push({ line: i + 1, text });
  });
  return out;
}
