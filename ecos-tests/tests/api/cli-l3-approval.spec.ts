import { expect, test } from '@playwright/test';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { creds, loginAsAdmin } from '../helpers/ecos';

/**
 * CLI 门禁（ADR-21 §3 验收标识 cli-l3-approval.spec.ts）
 *
 * 覆盖四件事：
 * 1) **命令树守卫**：每条命令 operationId 均可解析；L3 必须同时具备 requiresApproval 与 WRITE；
 *    未接入命令（wired=false）必须自证原因，执行输出 UNAVAILABLE 而非空集合。
 * 2) **活体命令行**：`ecos doctor` / `cognition models list` 经 gateway:8080 真跑，
 *    输出必须符合六字段契约与五值 status；依赖不可用时只能报 FAIL/BLOCKED/UNAVAILABLE。
 * 3) **凭据不回显**：stdout/stderr 不得出现口令或 token 明文（对齐 credentials-never-echo 口径）。
 * 4) **零副作用复核**：--dry-run 只打印调用计划；L3 未就绪时不得发起写请求。
 * 5) **夹具防污染**：所有写入夹具体带 `batch_id` 前缀 `cli-e2e-<runId>-`，`afterAll` 按前缀
 *    软删（`is_deleted=1`）——**禁物理 DELETE**，也不触碰非本前缀的历史数据。
 *
 * 前置：gateway :8080 已启动；`tools/ecos-cli/target/ecos-cli.jar` 已构建
 *      （mvn -o -f tools/ecos-cli/pom.xml package）；凭据只从 .env 读。
 */

const REPO_ROOT = path.resolve(__dirname, '..', '..', '..');
const MANIFEST = path.join(REPO_ROOT, 'tools', 'ecos-cli', 'src', 'main', 'resources', 'contract', 'ops.json');
const CLI_JAR = path.join(REPO_ROOT, 'tools', 'ecos-cli', 'target', 'ecos-cli.jar');

/** 防污染夹具命名：本次运行独占前缀，afterAll 只按此前缀软删，绝不碰他人数据。 */
const RUN_ID = process.env.CLI_E2E_RUN_ID ?? `r${Date.now()}`;
const FIXTURE_PREFIX = `cli-e2e-${RUN_ID}-`;

/**
 * 库面直连（仅用于防污染清理与零副作用取证）。
 *
 * <p>本工程不引入 pg 驱动，故走 psql 客户端：CI（ubuntu + postgres service）用 PATH 上的
 * `psql`，本机（Windows + Docker Desktop）回退 `docker exec ecos-postgres`。
 * 两条路都不通时由调用方断言失败——**清理没真跑过就不许算过**。</p>
 */
function sql(statement: string): { ok: boolean; out: string } {
  const args = ['-U', process.env.ECOS_PG_USER ?? 'postgres', '-d', process.env.ECOS_PG_DB ?? 'sys_man', '-t', '-A', '-c', statement];
  const env = { ...process.env, PGPASSWORD: process.env.ECOS_PG_PASSWORD ?? 'postgres' };
  const conn = ['-h', process.env.ECOS_PG_HOST ?? '127.0.0.1', '-p', process.env.ECOS_PG_PORT ?? '5432'];
  const direct = spawnSync('psql', [...conn, ...args], { env, encoding: 'utf8', timeout: 30_000 });
  if (!direct.error && direct.status === 0) {
    return { ok: true, out: direct.stdout ?? '' };
  }
  const container = process.env.ECOS_PG_CONTAINER ?? 'ecos-postgres';
  const docker = spawnSync('docker', ['exec', '-i', container, 'psql', ...args], { env, encoding: 'utf8', timeout: 30_000 });
  if (!docker.error && docker.status === 0) {
    return { ok: true, out: docker.stdout ?? '' };
  }
  const why = [direct.error?.message ?? direct.stderr, docker.error?.message ?? docker.stderr]
    .filter(Boolean).join(' | ').trim();
  return { ok: false, out: why };
}

interface Manifest {
  operations: { operationId: string; method: string; path: string; status: string }[];
  commands: {
    name: string;
    accessMode: string;
    automationLevel: string;
    requiresApproval: boolean;
    wired: boolean;
    operationIds: string[];
    flags: { flag: string; parameter: string }[];
    pathParams: { flag: string; parameter: string }[];
    bodyParams: { flag: string; parameter: string }[];
  }[];
}

function manifest(): Manifest {
  expect(fs.existsSync(MANIFEST), `缺少 CLI 契约清单：${MANIFEST}`).toBeTruthy();
  return JSON.parse(fs.readFileSync(MANIFEST, 'utf8')) as Manifest;
}

function resolveJava(): string {
  const candidates: string[] = [];
  if (process.env.ECOS_JAVA) candidates.push(process.env.ECOS_JAVA);
  if (process.env.JAVA_HOME) {
    candidates.push(path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java'));
  }
  // 本机工具链目录（~/env/jdk-*）：动态发现，不把机器绝对路径写进用例
  const envDir = path.join(os.homedir(), 'env');
  if (fs.existsSync(envDir)) {
    for (const name of fs.readdirSync(envDir).filter((n) => n.startsWith('jdk-'))) {
      candidates.push(path.join(envDir, name, 'bin', process.platform === 'win32' ? 'java.exe' : 'java'));
    }
  }
  candidates.push('java');

  for (const candidate of candidates) {
    if (candidate !== 'java' && !fs.existsSync(candidate)) continue;
    const probe = spawnSync(candidate, ['-version'], { encoding: 'utf8', timeout: 20_000 });
    if (!probe.error && probe.status === 0) return candidate;
  }
  throw new Error(
    '未找到可用 JDK（JAVA_HOME 可能指向无效路径）。设置 ECOS_JAVA 或修正 JAVA_HOME 后重跑；CLI 需 JDK 17+。'
  );
}

let cachedJava: string | null = null;

function javaBin(): string {
  if (cachedJava === null) cachedJava = resolveJava();
  return cachedJava;
}

interface CliRun {
  status: number;
  stdout: string;
  stderr: string;
  json: any;
}

function runCli(args: string[], token?: string): CliRun {
  expect(
    fs.existsSync(CLI_JAR),
    `缺少 CLI jar：${CLI_JAR}\n先构建：mvn -o -f tools/ecos-cli/pom.xml package`
  ).toBeTruthy();
  const env = { ...process.env };
  if (token) env.ECOS_TOKEN = token;
  const res = spawnSync(javaBin(), ['-Dfile.encoding=UTF-8', '-jar', CLI_JAR, ...args], {
    cwd: REPO_ROOT,
    env,
    encoding: 'utf8',
    timeout: 120_000,
  });
  if (res.error) throw res.error;
  let json: any = null;
  try {
    json = JSON.parse(res.stdout ?? '');
  } catch {
    json = null;
  }
  return { status: res.status ?? -1, stdout: res.stdout ?? '', stderr: res.stderr ?? '', json };
}

test.describe('CLI 控制面（14 条命令 / L0~L3）', () => {
  test('命令树守卫：operationId 均可解析，L3 必带审批，未接入命令自证原因', () => {
    const m = manifest();
    expect(m.commands.length).toBeGreaterThanOrEqual(14);
    const ids = new Set(m.operations.map((o) => o.operationId));
    for (const cmd of m.commands) {
      expect(['READ', 'WRITE']).toContain(cmd.accessMode);
      expect(['L0', 'L1', 'L2', 'L3']).toContain(cmd.automationLevel);
      if (cmd.requiresApproval) {
        expect(cmd.automationLevel, `${cmd.name} 需要审批但不是 L3`).toBe('L3');
        expect(cmd.accessMode, `${cmd.name} 需要审批但不是写命令`).toBe('WRITE');
      }
      for (const id of cmd.operationIds) {
        expect(ids.has(id), `命令 ${cmd.name} 指向不存在的 operationId: ${id}`).toBeTruthy();
      }
      for (const flag of cmd.flags) {
        expect(flag.parameter, `flag ${flag.flag} 缺契约参数名`).toBeTruthy();
      }
      for (const p of cmd.pathParams) {
        expect(p.parameter, `路径参数 ${p.flag} 缺契约参数名`).toBeTruthy();
      }
      for (const b of cmd.bodyParams) {
        expect(b.parameter, `请求体参数 ${b.flag} 缺字段名`).toBeTruthy();
      }
    }
  });

  test('doctor：六字段 + 五值 status，且逐项检查不含 UNKNOWN 造假', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const run = runCli(['doctor', '--output', 'json', '--timeout', '15000'], token);
    expect(run.json, `doctor 输出不是 JSON：${run.stderr.slice(0, 300)}`).toBeTruthy();

    expect(Object.keys(run.json).sort()).toEqual(['data', 'error', 'ok', 'source_refs', 'status', 'trace_id']);
    expect(['SUCCESS', 'FAILED', 'BLOCKED', 'UNAVAILABLE', 'PARTIAL']).toContain(run.json.status);
    const checks = run.json.data?.checks ?? [];
    expect(checks.length, 'doctor 必须有四项检查').toBe(4);
    for (const c of checks) {
      expect(['PASS', 'FAIL', 'BLOCKED', 'UNKNOWN']).toContain(c.status);
      expect(c.detail, `${c.name} 缺 detail（禁空默认值）`).toBeTruthy();
    }
    if (run.json.status === 'SUCCESS') {
      expect(checks.every((c: any) => c.status === 'PASS'), 'SUCCESS 必须全部 PASS').toBeTruthy();
      expect(run.status, 'SUCCESS 退出码必须为 0').toBe(0);
    } else {
      expect(run.status, '非 SUCCESS 不得返回 0 退出码').not.toBe(0);
    }
  });

  test('cognition models list：活体只读成功，且凭据不回显', async ({ request }) => {
    const { password } = creds();
    const token = await loginAsAdmin(request);
    const run = runCli(['cognition', 'models', 'list', '--output', 'json', '--model-kind', 'CM-03'], token);

    expect(run.json, `输出不是 JSON：${run.stderr.slice(0, 300)}`).toBeTruthy();
    expect(['SUCCESS', 'BLOCKED', 'UNAVAILABLE', 'FAILED']).toContain(run.json.status);
    if (run.json.status === 'SUCCESS') {
      expect(run.json.ok).toBe(true);
      expect(Array.isArray(run.json.data), 'data 应为模型行数组').toBeTruthy();
      expect(run.json.source_refs?.[0]?.operationId).toBe('listCognitiveModels');
      expect(run.status).toBe(0);
    }

    const combined = `${run.stdout}\n${run.stderr}`;
    expect(combined.includes(token), 'stdout/stderr 回显了 token').toBe(false);
    if (password) {
      expect(combined.includes(password), 'stdout/stderr 回显了口令').toBe(false);
    }
  });

  test('--dry-run 不发请求：doctor 全项 UNKNOWN 且无 source 调用副作用', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const run = runCli(['doctor', '--dry-run', '--output', 'json'], token);
    expect(run.json?.status).toBe('SUCCESS');
    const checks = run.json.data?.checks ?? [];
    expect(checks.length).toBe(4);
    expect(checks.every((c: any) => c.status === 'UNKNOWN')).toBeTruthy();
  });

  test('agent inspect --dry-run：调用计划取自契约路径模板，零请求', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const run = runCli(['agent', 'inspect', '--run-id', 'R-DRYRUN', '--dry-run'], token);
    expect(run.json?.status).toBe('SUCCESS');
    expect(run.json.source_refs?.[0]?.operationId).toBe('getRun');
    expect(run.json.data?.plannedCall).toContain('/api/v1/wagent/runs/R-DRYRUN');
    expect(run.status).toBe(0);
  });

  test('未接入命令：UNAVAILABLE + 非零退出码（禁空集合伪成功）', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const run = runCli(['model', 'route', '--token', token], token);
    expect(run.json?.status).toBe('UNAVAILABLE');
    expect(run.json?.ok).toBe(false);
    expect(run.json?.data).toBeNull();
    expect(run.json?.error?.code).toBe('ECOS-CLI-NOT-IMPLEMENTED');
    expect(run.json?.source_refs?.[0]?.operationId).toBe('resolveModelRoute');
    expect(run.status).toBe(3);
  });

  test('L3 命令：无 --approval-token ⇒ BLOCKED(2)，服务端未就绪 ⇒ 不发起写请求', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const run = runCli(['ontology', 'publish', '--candidate-id', 'C-DRYRUN', '--token', token], token);
    expect(['BLOCKED', 'UNAVAILABLE']).toContain(run.json?.status);
    expect(run.json?.ok).toBe(false);
    expect(run.status).not.toBe(0);
  });
});

/**
 * 批 D/E/F 活体面（2026-11-10）：preflight 11 步、候选落库、审批凭证四眼与作用域绑定。
 * 断言只认「服务端真实返回」，不接受脚本层自证。
 */
test.describe('CLI 生产化：preflight / candidate / approval-token', () => {
  const createdCandidateIds: string[] = [];
  const issuedApprovalIds: string[] = [];

  test.afterAll(() => {
    // 防污染收口：只按本次运行前缀软删，禁物理 DELETE、禁越界清理
    const res = sql(
      `UPDATE ecos_ai.ecos_wagent_candidate SET is_deleted = 1, update_time = CURRENT_TIMESTAMP ` +
      `WHERE batch_id LIKE '${FIXTURE_PREFIX}%' AND is_deleted = 0;`
    );
    expect(res.ok, `夹具软删未能执行（psql/docker 均不可用）：${res.out.slice(0, 200)}`).toBeTruthy();
    // 凭证表无 is_deleted 列（V50 定版，本批不动 DDL）⇒ 以「作废 + 立即过期」等效收口，
    // 仍不物理 DELETE；按 id 精确作废本轮签发的凭证，不按 scope LIKE 以免误伤同前缀他人行。
    if (issuedApprovalIds.length > 0) {
      const idList = issuedApprovalIds.map((id) => `'${id.replace(/'/g, "''")}'`).join(',');
      const revoke = sql(
        `UPDATE ecos_ai.agent_approval SET status = 'REVOKED', expired_at = NOW() ` +
        `WHERE id IN (${idList}) AND status <> 'REVOKED';`
      );
      expect(revoke.ok, `夹具凭证作废未执行：${revoke.out.slice(0, 200)}`).toBeTruthy();
    }
    const remaining = sql(
      `SELECT count(*) FROM ecos_ai.ecos_wagent_candidate ` +
      `WHERE batch_id LIKE '${FIXTURE_PREFIX}%' AND is_deleted = 0;`
    );
    expect(remaining.ok, `软删后复核未执行：${remaining.out.slice(0, 200)}`).toBeTruthy();
    expect(Number(remaining.out.trim()), '前缀内仍有未软删夹具').toBe(0);
    expect(createdCandidateIds.length, '本轮夹具应至少建过 2 个候选').toBeGreaterThanOrEqual(2);
  });

  test('preflight：11 步齐全、四值状态、createsRun=false（只报告不建 Run）', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const res = await request.post('/api/v1/cli/preflight', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect([200, 500, 503], `preflight 只允许这三档，实得 ${res.status()}`).toContain(res.status());
    const json = await res.json();
    const data = json?.data ?? {};
    expect(data.stepCount, '必须是 11 步').toBe(11);
    expect(data.createsRun, '预检不得创建 Run').toBe(false);
    const steps = data.steps ?? [];
    expect(steps.length).toBe(11);
    const allowed = ['PASS', 'FAIL', 'BLOCKED', 'NOT_VERIFIED'];
    for (const s of steps) {
      expect(allowed, `步骤 ${s.step} 状态越界: ${s.status}`).toContain(s.status);
      expect(s.step, '缺 step').toBeTruthy();
      expect(s.source, `步骤 ${s.step} 缺 source`).toBeTruthy();
      expect(s.traceId, `步骤 ${s.step} 缺 traceId`).toBeTruthy();
      // 不可判定不得写成 PASS
      if (s.status === 'NOT_VERIFIED') {
        expect(String(s.reason ?? '').length, `NOT_VERIFIED 必须给理由（${s.step}）`).toBeGreaterThan(0);
      }
    }
  });

  test('写命令 --dry-run：只打印计划，库面零新增（断言真实不落库）', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const batchId = `${FIXTURE_PREFIX}dryrun`;
    const before = sql(
      `SELECT count(*) FROM ecos_ai.ecos_wagent_candidate WHERE batch_id = '${batchId}';`
    );
    expect(before.ok, `dry-run 前置计数未执行：${before.out.slice(0, 200)}`).toBeTruthy();

    const run = runCli(['knowledge', 'extract', '--dry-run', '--candidate-type', 'KNOWLEDGE_ITEM',
      '--payload-json', '{"probe":"dryrun"}', '--body-json', `{"batchId":"${batchId}"}`,
      '--output', 'json', '--token', token], token);
    expect(run.json?.status).toBe('SUCCESS');
    expect(run.status).toBe(0);
    expect(String(run.json?.data?.plannedCall ?? ''), 'dry-run 未回显调用计划')
      .toContain('/api/v1/wagent/candidates');
    expect(run.json?.source_refs?.[0]?.operationId).toBe('createWagentCandidate');

    const after = sql(
      `SELECT count(*) FROM ecos_ai.ecos_wagent_candidate WHERE batch_id = '${batchId}';`
    );
    expect(after.ok, `dry-run 后置计数未执行：${after.out.slice(0, 200)}`).toBeTruthy();
    expect(Number(after.out.trim()), '--dry-run 竟然真落了库').toBe(Number(before.out.trim()));
  });


  test('建候选：201 + status=generated，payload 哈希由服务端计算', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const payload = JSON.stringify({ probe: 'cli-l3-e2e', at: Date.now() });
    const res = await request.post('/api/v1/wagent/candidates', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        candidateType: 'KNOWLEDGE_ITEM',
        payloadJson: payload,
        targetEngine: 'kb',
        batchId: `${FIXTURE_PREFIX}candidate`,
      },
    });
    expect(res.status(), `建候选期望 201，实得 ${res.status()}`).toBe(201);
    const json = await res.json();
    const c = json?.data ?? {};
    const id = c.candidate_id ?? c.candidateId ?? c.id;
    expect(id, '响应缺候选 id').toBeTruthy();
    createdCandidateIds.push(String(id));
    const status = c.status ?? c.candidate_status;
    expect(String(status), '初始状态必须是 generated').toBe('generated');
    const hash = c.payload_hash ?? c.payloadHash;
    expect(String(hash ?? '').length, 'payload_hash 必须由服务端给出（64 hex）').toBe(64);
    expect(c.batch_id ?? c.batchId, '夹具批次号未按前缀落库').toBe(`${FIXTURE_PREFIX}candidate`);
  });

  test('建候选：类型越界 ⇒ 400（禁静默默认）', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const res = await request.post('/api/v1/wagent/candidates', {
      headers: { Authorization: `Bearer ${token}` },
      data: { candidateType: 'NOT_A_TYPE', payloadJson: '{}' },
    });
    expect(res.status()).toBe(400);
  });

  test('审批凭证：admin 签发成功；自审批（requester=approver）⇒ 403', async ({ request }) => {
    const token = await loginAsAdmin(request);
    const ok = await request.post('/api/v1/wagent/approval-tokens', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        scopeType: 'CANDIDATE_PUBLISH',
        scopeId: `${FIXTURE_PREFIX}apv-1`,
        requestedBy: 'someone-else',
        ttlSeconds: 600,
      },
    });
    expect(ok.status(), '签发期望 200').toBe(200);
    const okJson = await ok.json();
    expect(String(okJson?.data?.token ?? ''), '缺明文凭证').toMatch(/^ecos-apv-/);
    expect(okJson?.data?.oneTimeUse).toBe(true);
    issuedApprovalIds.push(String(okJson?.data?.approvalId ?? ''));

    const self = await request.post('/api/v1/wagent/approval-tokens', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        scopeType: 'CANDIDATE_PUBLISH',
        scopeId: `${FIXTURE_PREFIX}apv-2`,
        requestedBy: creds().username,
      },
    });
    expect(self.status(), '自审批必须 403').toBe(403);
  });

  test('凭证闸生效：无效 token ⇒ 403；有效 token 越过凭证闸（批 A 后真链路）', async ({ request }) => {
    const token = await loginAsAdmin(request);
    // 造一个候选，专供发布闸验证（同批次前缀，afterAll 统一软删）
    const created = await request.post('/api/v1/wagent/candidates', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        candidateType: 'KNOWLEDGE_ITEM',
        payloadJson: JSON.stringify({ probe: 'publish-gate' }),
        batchId: `${FIXTURE_PREFIX}publish`,
      },
    });
    expect(created.status()).toBe(201);
    const createdJson = await created.json();
    const candidateId = createdJson?.data?.candidate_id ?? createdJson?.data?.id;
    createdCandidateIds.push(String(candidateId));

    // 无效凭证：凭证闸拒绝（403），不是 501
    const bad = await request.post(`/api/v1/wagent/candidates/${candidateId}/publish`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { approvalToken: 'ecos-apv-not-issued' },
    });
    expect(bad.status(), '无效凭证必须 403').toBe(403);

    // 有效凭证：越过凭证闸。批 A（rbac.rego admin 规则 + 角色真传）后发布腿为真闭环：
    // GENERATED 候选命中状态闸 409（状态闸先于裁决）；策略拒绝为 403——三态均证明不再是 501。
    const issued = await request.post('/api/v1/wagent/approval-tokens', {
      headers: { Authorization: `Bearer ${token}` },
      data: { scopeType: 'CANDIDATE_PUBLISH', scopeId: candidateId, requestedBy: 'generator-x' },
    });
    expect(issued.status()).toBe(200);
    const issuedJson = await issued.json();
    issuedApprovalIds.push(String(issuedJson?.data?.approvalId ?? ''));
    const good = await request.post(`/api/v1/wagent/candidates/${candidateId}/publish`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { approvalToken: issuedJson?.data?.token },
    });
    expect([200, 403, 409], `有效凭证下发布腿必须是真裁决（200 发布/403 策略/409 状态闸），禁 501（实得 ${good.status()}）`)
      .toContain(good.status());

    // 作用域绑定：同凭证换对象 ⇒ 403
    const mismatch = await request.post('/api/v1/wagent/candidates/c-other-object/publish', {
      headers: { Authorization: `Bearer ${token}` },
      data: { approvalToken: issuedJson?.data?.token },
    });
    expect([403, 404], `跨对象复用凭证必须被拒（实得 ${mismatch.status()}）`).toContain(mismatch.status());
  });

  test('批 A 授权墙双修：admin 角色 candidate:publish 经 OPA rbac 放行（策略可及）', async ({ request }) => {
    const token = await loginAsAdmin(request);
    // 直问 policy-engine：以 admin 角色评 candidate:publish ⇒ ALLOW（rbac.rego 批 A 规则已加载可达）
    const verdict = await request.post('/api/v1/security/policy-engine/evaluate', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        policy: 'rbac',
        input: { action: 'candidate:publish', role: 'admin' },
      },
    });
    expect(verdict.status(), 'policy-engine/evaluate 必须 200（security-engine 在位）').toBe(200);
    const body = await verdict.json();
    expect(body?.data?.allow, `admin 角色 candidate:publish 必须放行（rbac.rego 批 A 规则），实得 ${JSON.stringify(body?.data)}`)
      .toBe(true);

    // 反向：非特权角色（user）同一动作 ⇒ DENY（fail-closed 语义保持）
    const userVerdict = await request.post('/api/v1/security/policy-engine/evaluate', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        policy: 'rbac',
        input: { action: 'candidate:publish', role: 'user' },
      },
    });
    const userBody = await userVerdict.json();
    expect(userBody?.data?.allow, 'user 角色 candidate:publish 必须仍被拒（fail-closed 语义不变）').toBe(false);
  });
});
