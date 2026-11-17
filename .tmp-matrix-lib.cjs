const fs = require('fs');
const path = require('path');
const dir = 'docs/50-测试';
const files = fs.readdirSync(dir).filter(f => /^ECOS逐功能验证矩阵-\d\d-/.test(f)).sort();
const VERDICTS = ['PASS','PARTIAL','BLOCKED','NOT_EXECUTED','N/A','FAIL'];
function verdictOf(line) {
  const cells = line.split('|');
  for (let i = cells.length - 1; i >= 2; i--) {
    const cell = cells[i].trim().replace(/[*`]/g, '');
    const hit = VERDICTS.find(v => cell === v || (cell.startsWith(v) && /^[（(\s:：]/.test(cell.charAt(v.length))));
    if (hit) return hit;
  }
  return null;
}
const perFile = [];
const uniq = Object.fromEntries(VERDICTS.map(v => [v, 0]));
let totalRows = 0, totalUnique = 0;
for (const f of files) {
  const dom = f.replace(/^ECOS逐功能验证矩阵-/, '').replace(/-2026-11-13\.md$/, '');
  const lines = fs.readFileSync(path.join(dir, f), 'utf8').split(/\r?\n/);
  const local = new Map();
  let n = 0;
  lines.forEach((line) => {
    if (!/^\|\s*V-/.test(line)) return;
    const m = line.match(/^\|\s*(V-[A-Z0-9-]+)/);
    if (!m) return;
    n++;
    if (!local.has(m[1])) local.set(m[1], verdictOf(line));
  });
  const c = Object.fromEntries(VERDICTS.map(v => [v, 0]));
  let unknown = 0;
  for (const [, v] of local) { if (v) c[v]++; else { unknown++; c.UNKNOWN = (c.UNKNOWN||0)+1; } }
  for (const v of VERDICTS) uniq[v] += c[v];
  totalRows += n; totalUnique += local.size;
  perFile.push(`${dom}|${local.size}|${c.PASS}|${c.PARTIAL}|${c.BLOCKED}|${c.NOT_EXECUTED}|${c['N/A']}|${c.FAIL}|${unknown}`);
}
console.log('rows=' + totalRows + ' unique=' + totalUnique);
console.log('UNIQUE_TALLY ' + JSON.stringify(uniq) + ' sum=' + VERDICTS.reduce((s,v)=>s+uniq[v],0));
console.log('dom|unique|PASS|PARTIAL|BLOCKED|NOT_EXEC|NA|FAIL|unknown');
perFile.forEach(l => console.log(l));
