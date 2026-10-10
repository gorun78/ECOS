// @vitest-environment node
/**
 * W190/C172 门禁 — 导航单源化
 * 冻结门禁名：unit: navConfig.test.ts › everyNavRouteExistsInMainTsx
 * @license Apache-2.0
 */
import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import {
  NAV_ENTRIES,
  TAB_LABEL_KEY,
  SIDEBAR_ITEMS,
  SIDEBAR_GROUPS,
  SIDEBAR_PRODUCT_ITEMS,
  PALETTE_ITEMS,
  TYPE_NAV_MAP,
  TYPE_META_MAP,
  DELIBERATELY_UNLINKED,
  NON_NAV_ROUTE_ROOTS,
} from '../navigation/navConfig';

const SRC = fileURLToPath(new URL('..', import.meta.url)); // src/

describe('navConfig — single source structure', () => {
  it('ids are unique and routes are absolute paths', () => {
    const ids = NAV_ENTRIES.map((e) => e.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const e of NAV_ENTRIES) {
      expect(e.route).toMatch(/^\//);
    }
  });

  it('TAB_LABEL_KEY is derived 1:1 from tabbable entries', () => {
    const tabbable = NAV_ENTRIES.filter((e) => e.tabbable);
    expect(Object.keys(TAB_LABEL_KEY).length).toBe(tabbable.length);
    for (const e of tabbable) {
      expect(TAB_LABEL_KEY[e.id]).toBe(e.labelKey);
    }
  });

  it('sidebar derivation yields exactly 12 items in 3 groups + products', () => {
    expect(SIDEBAR_ITEMS.length).toBe(12);
    expect(SIDEBAR_GROUPS.length).toBe(3);
    expect(SIDEBAR_PRODUCT_ITEMS.length).toBe(5);
    for (const item of SIDEBAR_ITEMS) {
      expect(["function", "object"]).toContain(typeof item.icon); // lucide = forwardRef object
      expect(item.descKey).toBeTruthy();
    }
  });

  it('palette derivation keeps the 7 legacy static entries', () => {
    const ids = PALETTE_ITEMS.map((i) => i.id).sort();
    expect(ids).toEqual(
      ['agent_studio', 'catalog', 'lineage', 'mission_control', 'ontology', 'pipeline', 'security-center'].sort(),
    );
    for (const item of PALETTE_ITEMS) {
      expect(["function", "object"]).toContain(typeof item.icon);
      expect(item.titleKey).toMatch(/^palette\.item\./);
    }
  });

  it('TYPE_NAV_MAP reverse-lookup covers all nine search hit types', () => {
    expect(TYPE_NAV_MAP).toMatchObject({
      OntologyEntity: 'ontology',
      Asset: 'marketplace',
      Goal: 'mission_control',
      Scenario: 'mission_control',
      Object: 'catalog',
      Workflow: 'pipeline',
      Pipeline: 'pipeline',
      Knowledge: 'knowledge_graph',
      Agent: 'agent_studio',
    });
    for (const [type, meta] of Object.entries(TYPE_META_MAP)) {
      expect(meta.navId, `meta for ${type}`).toBeTruthy();
      expect(meta.categoryKey, `category for ${type}`).toBeTruthy();
    }
  });
});

describe('everyNavRouteExistsInMainTsx (W190 gate)', () => {
  it('every content route root in main.tsx is in NAV_CONFIG or the deliberate-unlink whitelist', () => {
    const mainSrc = readFileSync(path.join(SRC, 'main.tsx'), 'utf8');
    const roots = new Set<string>();
    const re = /<Route\s+path="([^"]+)"/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(mainSrc)) !== null) {
      const first = m[1].split('/').filter(Boolean)[0] ?? '';
      if (!first) continue; // index route
      roots.add(first);
    }
    const navIds = new Set(NAV_ENTRIES.map((e) => e.id));
    const violations: string[] = [];
    for (const root of roots) {
      if (NON_NAV_ROUTE_ROOTS.includes(root)) continue; // login / no-access / wildcard
      if (navIds.has(root)) continue;
      if (DELIBERATELY_UNLINKED[root]) continue;
      violations.push(root);
    }
    expect(
      violations,
      `route roots missing from navConfig AND unregistered in DELIBERATELY_UNLINKED: ${violations.join(', ')}`,
    ).toEqual([]);
  });

  it('every DELIBERATELY_UNLINKED root actually exists in main.tsx and carries a reason', () => {
    const mainSrc = readFileSync(path.join(SRC, 'main.tsx'), 'utf8');
    const roots = new Set<string>();
    const re = /<Route\s+path="([^"]+)"/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(mainSrc)) !== null) {
      const first = m[1].split('/').filter(Boolean)[0] ?? '';
      if (first) roots.add(first);
    }
    for (const [root, reason] of Object.entries(DELIBERATELY_UNLINKED)) {
      expect(roots.has(root), `whitelisted root "${root}" no longer exists in main.tsx — prune the whitelist`).toBe(true);
      expect(reason.trim().length, `"${root}" whitelist reason must be non-empty`).toBeGreaterThan(0);
    }
  });
});

describe('W196/C178 owner files carry zero literal palette colors', () => {
  const COLOR_LITERAL =
    /(bg|text|border)-(white|slate|gray|zinc|neutral|stone|red|blue|green|amber|emerald|indigo|violet|purple|cyan|teal)-[0-9]+|#[0-9A-Fa-f]{3,8}\b|rgba?\(\s*\d/;
  const owners = [
    'App.tsx',
    'components/Sidebar.tsx',
    'components/CommandPalette.tsx',
    'components/common/ErrorBoundary.tsx',
  ];
  for (const rel of owners) {
    it(`${rel} has no literal color-scale/hex/rgb values`, () => {
      const src = readFileSync(path.join(SRC, rel), 'utf8');
      const hits = src
        .split(/\r?\n/)
        .map((line, i) => ({ line, i }))
        .filter(({ line }) => {
          const trimmed = line.trim();
          return !trimmed.startsWith('*') && !trimmed.startsWith('//') && COLOR_LITERAL.test(line);
        });
      expect(
        hits.map((h) => `${h.i + 1}: ${h.line.trim().slice(0, 120)}`),
        `${rel} still contains literal colors`,
      ).toEqual([]);
    });
  }
});

describe('W209/C191 — 本册实现轮未新增内容路由根（§2.1 落地只挂既有域内 Tab）', () => {
  /**
   * 内容路由根基线 = 本册 §1.2 冻结口径（`<Route path=` 首段去重，排除通配 `*` 与守卫根 login/no-access）。
   * 2026-09-29 设计窗实测 49；实现轮起点（HEAD a3372422）实测 53，
   * 差值 +4 全部来自其他分册已批准实现轮的既有提交，非本册新增：
   *   alerts-center / gateway-diagnostics（详设-00 M2，1bde9530）
   *   knowledge（详设-04 B 章，b617c8bc）· workbench（详设-07 WP-D，fdee86d6）。
   * 本册 W209/R-49 红线 = 在此基线上**新增即红**；下线须走对应 W191/R-52 定档后同步本清单。
   */
  const CONTENT_ROUTE_ROOT_BASELINE = [
    'agent-builder', 'agent-test', 'agent_mesh', 'agent_studio', 'ai-workbench',
    'alerts', 'alerts-center', 'biz_dashboard', 'business-workbench', 'case_library',
    'chatbot_studio', 'contract_manager', 'data-workbench', 'dict', 'domain_designer',
    'domains', 'dq_dashboard', 'engine-ai', 'engine-cognitive', 'engine-data',
    'engine-knowledge', 'engine-ontology', 'engine-security', 'engine-tasks',
    'gateway-diagnostics', 'glossary', 'guardrails', 'iam', 'kanban', 'knowledge',
    'knowledge_graph', 'knowledge_view', 'marketplace', 'mission_control', 'monitor',
    'objects', 'ontology', 'ontology_designer', 'ontology_workbench', 'ops_apps',
    'ops_dashboard', 'project_tracker', 'project_workbench', 'scenario-sandbox',
    'security-center', 'system-config', 'telemetry', 'tenants', 'tokens', 'workbench',
    'workflow_designer', 'workshop', 'world_model',
  ];

  it('content route roots in main.tsx equal the frozen baseline (no new roots)', () => {
    const mainSrc = readFileSync(path.join(SRC, 'main.tsx'), 'utf8');
    const roots = new Set<string>();
    const re = /<Route\s+path="([^"]+)"/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(mainSrc)) !== null) {
      const first = m[1].split('/').filter(Boolean)[0] ?? '';
      if (!first || first === '*') continue;
      if (first === 'login' || first === 'no-access') continue; // 守卫根不入内容基数
      roots.add(first);
    }
    const baseline = new Set(CONTENT_ROUTE_ROOT_BASELINE);
    const added = [...roots].filter((r) => !baseline.has(r));
    const removed = CONTENT_ROUTE_ROOT_BASELINE.filter((r) => !roots.has(r));
    expect(
      added,
      `W209/C191 红线：内容路由根新增（P01~P13 只挂既有域内 Tab，禁开新路由根）：${added.join(', ')}`,
    ).toEqual([]);
    expect(
      removed,
      `路由根消失须先经 W191/R-52 定档并同步本基线清单：${removed.join(', ')}`,
    ).toEqual([]);
    expect(CONTENT_ROUTE_ROOT_BASELINE.length).toBe(53); // 49（2026-09-29 设计窗）+4（他册实现轮，见上注释）
  });
});

describe('locales sanity for the navigation namespace', () => {
  it('navigation namespace files parse and are non-empty', () => {
    const dir = path.join(SRC, 'locales');
    const namespaces = readdirSync(dir);
    expect(namespaces).toContain('navigation');
    const zh = JSON.parse(readFileSync(path.join(dir, 'navigation', 'zh-CN.json'), 'utf8')) as Record<string, string>;
    const en = JSON.parse(readFileSync(path.join(dir, 'navigation', 'en.json'), 'utf8')) as Record<string, string>;
    expect(Object.keys(zh).length).toBeGreaterThan(0);
    expect(Object.keys(en).sort()).toEqual(Object.keys(zh).sort());
  });
});
