// runner.mjs — 一括执行 ecos-tests/security/over-permission/ 下全部 .mjs 用例
// 用法:
//   node runner.mjs                 # 跑全部 50 套
//   node runner.mjs --filter=T1     # 只跑含 T1 编号
//   node runner.mjs --concurrency=10
import { readdir, readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join, basename } from "node:path";
import { spawn } from "node:child_process";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DIR = join(dirname(__dirname), "security", "over-permission");

const filter = process.argv.find((a) => a.startsWith("--filter="))?.split("=")[1];
const concurrency = Number(
  process.argv.find((a) => a.startsWith("--concurrency="))?.split("=")[1] || 10
);

// 扫描 .mjs 文件
const files = (await readdir(DIR)).filter((f) => f.endsWith(".mjs")).sort();
const filtered = filter ? files.filter((f) => f.includes(filter)) : files;

console.log(`[runner] 扫描目录: ${DIR}`);
console.log(`[runner] 共 ${filtered.length}/${files.length} 个用例, 并发=${concurrency}`);

const results = [];
async function runOne(file) {
  const t0 = Date.now();
  const child = spawn("node", [join(DIR, file)], { stdio: ["ignore", "pipe", "pipe"] });
  return new Promise((resolve) => {
    let out = "";
    let err = "";
    child.stdout.on("data", (d) => (out += d.toString()));
    child.stderr.on("data", (d) => (err += d.toString()));
    child.on("close", (code) => {
    const ms = Date.now() - t0;
    const pass = code === 0;
    const envBlocked = code === 77;
    const lastLine = (out || err).trim().split("\n").pop() || "";
    const tag = pass ? "PASS" : envBlocked ? "ENVB" : "FAIL";
    results.push({ file, pass, envBlocked, code, ms, lastLine });
    console.log(`  ${tag} [${code}] ${basename(file)} (${ms}ms) ${lastLine}`);
  });
  });
}

// simple concurrency limiter
async function limitPool(fns, n) {
  const res = [];
  const queue = [...fns];
  await Promise.all(
    Array.from({ length: Math.min(n, fns.length) }, async () => {
      while (queue.length) {
        const fn = queue.shift();
        if (fn) res.push(await fn());
      }
    })
  );
  return res;
}

const fns = filtered.map((f) => () => runOne(f));
const t0 = Date.now();
await limitPool(fns, concurrency);
const totalMs = Date.now() - t0;

const pass = results.filter((r) => r.pass).length;
const envBlocked = results.filter((r) => r.envBlocked).length;
const fail = results.filter((r) => !r.pass && !r.envBlocked).length;
console.log(`\n[runner] 总计: ${results.length} 用例, PASS=${pass} ENV_BLOCKED=${envBlocked} FAIL=${fail}, 总耗时 ${totalMs}ms`);
console.log(`[runner] 判定: ${fail === 0 ? (envBlocked > 0 ? "FAIL (ENV_BLOCKED, deliverable_allowed=false)" : "PASS, deliverable_allowed=true") : "FAIL (not-deliver)"}`);

if (fail > 0) {
  console.log("\n[runner] 失败用例 (非 ENV_BLOCKED) — P1+ 上报:");
  results.filter((r) => !r.pass && !r.envBlocked).forEach((r) => console.log(`  FAIL ${r.file} (exit=${r.code}): ${r.lastLine}`));
}
if (envBlocked > 0 && fail === 0) {
  // 所有失败都是 ENV_BLOCKED — 环境阻断, 需 Fullstack 修复后重跑
  console.log(`\n[runner] ${envBlocked} 个 ENV_BLOCKED (exit 77, Gateway 不可达)`);
  console.log("[runner] 这不算测试失败, 是环境未就绪. 不算 Wave-1A 合 tag, 等 gateway 重启后重跑");
  process.exit(2); // 环境阻断标记
}
if (fail > 0) process.exit(1); // 真 FAIL
process.exit(0);
