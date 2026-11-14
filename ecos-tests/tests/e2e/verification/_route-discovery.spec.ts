import { test } from '@playwright/test';
import fs from 'node:fs';
import { injectAuth, loginAsAdmin } from '../../helpers/ecos';

/**
 * 一次性发现性探针（非交付用例，跑完即删）：
 * 逐条访问 main.tsx 的全部 66 条 Route，记录真实渲染/网络/console 证据，
 * 供人工判定后固化为 route-coverage.spec.ts 的声明表。
 */
test.setTimeout(900_000);

const ROUTES: { key: string; url: string }[] = [
  { key: 'login', url: '/#/login' },
  { key: 'no-access', url: '/#/no-access' },
  { key: 'index-redirect', url: '/#/' },
  { key: 'decision', url: '/#/decision' },
  { key: 'decision/mind', url: '/#/decision/mind' },
  { key: 'decision/mind/:mindId', url: '/#/decision/mind/__MIND_ID__' },
  { key: 'mission_control', url: '/#/mission_control' },
  { key: 'monitor', url: '/#/monitor' },
  { key: 'biz_dashboard', url: '/#/biz_dashboard' },
  { key: 'project_tracker', url: '/#/project_tracker' },
  { key: 'contract_manager', url: '/#/contract_manager' },
  { key: 'ops_dashboard', url: '/#/ops_dashboard' },
  { key: 'ops_apps', url: '/#/ops_apps' },
  { key: 'marketplace', url: '/#/marketplace' },
  { key: 'resource-center', url: '/#/resource-center' },
  { key: 'objects', url: '/#/objects' },
  { key: 'ontology_workbench', url: '/#/ontology_workbench' },
  { key: 'business-workbench', url: '/#/business-workbench' },
  { key: 'data-workbench', url: '/#/data-workbench' },
  { key: 'dq_dashboard', url: '/#/dq_dashboard' },
  { key: 'gateway-diagnostics', url: '/#/gateway-diagnostics' },
  { key: 'alerts-center', url: '/#/alerts-center' },
  { key: 'domains(redirect)', url: '/#/domains' },
  { key: 'domain_designer(redirect)', url: '/#/domain_designer' },
  { key: 'ontology(redirect)', url: '/#/ontology' },
  { key: 'ontology_designer(redirect)', url: '/#/ontology_designer' },
  { key: 'knowledge_graph', url: '/#/knowledge_graph' },
  { key: 'glossary(redirect)', url: '/#/glossary' },
  { key: 'dict', url: '/#/dict' },
  { key: 'system-config', url: '/#/system-config' },
  { key: 'platform-governance', url: '/#/platform-governance' },
  { key: 'workflow_designer', url: '/#/workflow_designer' },
  { key: 'project_workbench', url: '/#/project_workbench' },
  { key: 'cognitive-workbench', url: '/#/cognitive-workbench' },
  { key: 'workbench', url: '/#/workbench' },
  { key: 'workbench/:scenarioId', url: '/#/workbench/sc001' },
  { key: 'workbench/:scenarioId/forecast', url: '/#/workbench/sc001/forecast' },
  { key: 'workbench/:scenarioId/decisions', url: '/#/workbench/sc001/decisions' },
  { key: 'scenario-sandbox/:scenarioId', url: '/#/scenario-sandbox/sc001' },
  { key: 'world_model', url: '/#/world_model' },
  { key: 'knowledge(legacy)', url: '/#/knowledge' },
  { key: 'knowledge_view', url: '/#/knowledge_view' },
  { key: 'agent_studio(redirect)', url: '/#/agent_studio' },
  { key: 'ai-workbench', url: '/#/ai-workbench' },
  { key: 'chatbot_studio', url: '/#/chatbot_studio' },
  { key: 'workshop', url: '/#/workshop' },
  { key: 'agent_mesh', url: '/#/agent_mesh' },
  { key: 'agent-builder(no id)', url: '/#/agent-builder' },
  { key: 'agent-test/:agentId', url: '/#/agent-test/__AGENT_ID__' },
  { key: 'case_library', url: '/#/case_library' },
  { key: 'alerts', url: '/#/alerts' },
  { key: 'security-center', url: '/#/security-center' },
  { key: 'guardrails', url: '/#/guardrails' },
  { key: 'iam', url: '/#/iam' },
  { key: 'kanban', url: '/#/kanban' },
  { key: 'telemetry', url: '/#/telemetry' },
  { key: 'tokens', url: '/#/tokens' },
  { key: 'tenants', url: '/#/tenants' },
  { key: 'engine-security', url: '/#/engine-security' },
  { key: 'engine-data', url: '/#/engine-data' },
  { key: 'engine-ontology', url: '/#/engine-ontology' },
  { key: 'engine-cognitive', url: '/#/engine-cognitive' },
  { key: 'engine-knowledge', url: '/#/engine-knowledge' },
  { key: 'engine-ai', url: '/#/engine-ai' },
  { key: 'engine-tasks', url: '/#/engine-tasks' },
  { key: '*(404 fallback)', url: '/#/zzz-nonexistent-route' },
];

test('discovery: sweep all 66 routes', async ({ page, request, context }) => {
  const token = await loginAsAdmin(request);
  await injectAuth(context, token);

  // 取真实 id 用于参数化路由（不猜常量）
  const mindRes = await request.get('/api/v1/decision/minds?page=1&pageSize=1', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const mindJson = (await mindRes.json().catch(() => ({}))) as any;
  const minds = mindJson?.data?.items ?? mindJson?.data?.records ?? mindJson?.data ?? [];
  const mindId = (Array.isArray(minds) && minds[0]?.id) || '';

  const agentsRes = await request.get('/api/v1/agent/copilot/agents', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const agentsJson = (await agentsRes.json().catch(() => ({}))) as any;
  const agentList = agentsJson?.data ?? [];
  const agentId = (Array.isArray(agentList) && agentList[0]?.id) || '';

  const results: any[] = [];
  for (const r of ROUTES) {
    const url = r.url.replace('__MIND_ID__', mindId || 'none').replace('__AGENT_ID__', agentId || 'none');
    const consoleErrors: string[] = [];
    const bad: string[] = [];
    const onConsole = (m: any) => {
      if (m.type() === 'error' && !/^Failed to load resource/i.test(m.text())) consoleErrors.push(m.text().slice(0, 300));
    };
    const onPageError = (e: any) => consoleErrors.push('pageerror: ' + String(e).slice(0, 300));
    const onResponse = (res: any) => {
      if (res.url().includes('/api/') && res.status() >= 400) bad.push(`${res.status()} ${res.url().replace('http://localhost:3000', '')}`);
    };
    page.on('console', onConsole);
    page.on('pageerror', onPageError);
    page.on('response', onResponse);
    let navError = '';
    try {
      await page.goto(url, { waitUntil: 'domcontentloaded' });
    } catch (e) {
      navError = String(e).slice(0, 200);
    }
    // 等待 SPA 挂载 + 首屏请求沉降
    await page.waitForTimeout(4000);
    const bodyLen = await page.evaluate(() => (document.body.innerText || '').length).catch(() => -1);
    const bodyHead = await page
      .evaluate(() => (document.body.innerText || '').replace(/\s+/g, ' ').slice(0, 160))
      .catch(() => '');
    const hash = await page.evaluate(() => location.hash).catch(() => '');
    const errorBoundary = await page
      .evaluate(() =>
        /Something went wrong|Unexpected Application Error|应用发生错误|useTheme must be used within/i.test(
          document.body.innerText || ''
        )
      )
      .catch(() => false);
    page.off('console', onConsole);
    page.off('pageerror', onPageError);
    page.off('response', onResponse);
    results.push({ key: r.key, url, hash, bodyLen, bodyHead, errorBoundary, navError, consoleErrors, bad });
    console.log(`[ROUTE] ${r.key} hash=${hash} len=${bodyLen} eb=${errorBoundary} cerr=${consoleErrors.length} bad=${bad.length}`);
  }

  // 登录页需清 localStorage 单独探（RequireAuth 会让已登录用户从 /login 跳走）
  fs.writeFileSync('.tmp-route-discovery.json', JSON.stringify({ mindId, agentId, results }, null, 2), 'utf8');
});

test('discovery: login page without auth', async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !/^Failed to load resource/i.test(m.text())) consoleErrors.push(m.text().slice(0, 300));
  });
  await page.goto('/#/login', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);
  const bodyLen = await page.evaluate(() => (document.body.innerText || '').length);
  const bodyHead = await page.evaluate(() => (document.body.innerText || '').replace(/\s+/g, ' ').slice(0, 200));
  const hash = await page.evaluate(() => location.hash);
  fs.writeFileSync(
    '.tmp-route-login.json',
    JSON.stringify({ hash, bodyLen, bodyHead, consoleErrors }, null, 2),
    'utf8'
  );
  console.log(`[LOGIN] hash=${hash} len=${bodyLen} cerr=${consoleErrors.length}`);
});
