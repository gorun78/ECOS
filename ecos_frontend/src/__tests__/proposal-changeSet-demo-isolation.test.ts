/**
 * proposal-changeSet-demo-isolation.test.ts — V-ONTO-007「demo 注入绑定不得进入 changeSet 提交」门禁
 * （逐功能验证矩阵 05-本体引擎 V-ONTO-007；缺陷台账 D140）
 *
 * 背景：本体工作台为了在「接口规范 / 共享属性」菜单无后端数据源时给出可浏览占位，
 * 运行期用 applyEnterpriseDemoBindings() 给对象类型补 interfaces / properties[].sharedPropertyId
 * （src/data/enterpriseOntologyDemo.ts，文件头自述「演示数据，非平台事实源，不得作为任何验收证据」）。
 * 而提案面板 ProposalPanel 的 changeSet 是「本地草稿属性 − 后端现存属性」的差集，
 * 直接作为 payload.propertiesAdded/Updated 提交回后端并落库（见 ProposalPanel handleCreate）。
 * 两条链路若接线错误，demo 注入的假绑定会被 diff 进提案 → 假数据落库 = 假真理 P0。
 *
 * 本门禁三腿（前两腿纯静态扫描，第三腿实调函数、不 mock）：
 *   ① 工作台 JSX 必须给 ProposalPanel 传 serverObjectTypes（后端权威、未叠加 demo），
 *      且 serverObjectTypes 的赋值源必须是 useOntologyData 的原始 loadedObjects；
 *   ② ProposalPanel 把草稿属性转成提交体的 toPropertyBody() 不得携带 sharedPropertyId / interfaces；
 *   ③ 函数级实证：applyEnterpriseDemoBindings 确实会给 Customer 补 interfaces + sharedPropertyId，
 *      因此①②不是"防一个不存在的问题"——注入是真的，隔离才必须是真的。
 *
 * 判据为「正向必需 + 反向禁止」双面：只断言"存在 serverObjectTypes"会放过
 * `objectTypes={serverObjectTypes}` 与 `objectTypes={applyEnterpriseDemoBindings(...)}` 并存的情形，
 * 故另加一条"ProposalPanel 的 objectTypes 属性不得接收 demo 派生值"。
 * @license Apache-2.0
 */
// @vitest-environment node
import { describe, it, expect, vi } from 'vitest';
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { applyEnterpriseDemoBindings } from '../data/enterpriseOntologyDemo';
import type { ObjectType } from '../types/ontology';

const SRC_ROOT = fileURLToPath(new URL('..', import.meta.url));
const LAYOUT_REL = 'pages/OntologyWorkbenchLayout.tsx';
const PANEL_REL = 'pages/ontology/ProposalPanel.tsx';

const read = (rel: string) => readFileSync(`${SRC_ROOT}${rel}`, 'utf8').replace(/\r\n/g, '\n');

/** 去注释（整行 `//` / `/* *\/` 与行尾注释），避免注释里的说明文字被当真代码命中。 */
function stripComments(text: string): string {
  return text
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .map(line => {
      const idx = line.indexOf('//');
      if (idx < 0) return line;
      // 忽略出现在字符串里的 //（本文件扫描面足够窄，粗判即可）
      const before = line.slice(0, idx);
      const quotes = (before.match(/['"`]/g) || []).length;
      return quotes % 2 === 1 ? line : before;
    })
    .join('\n');
}

/** 按花括号配平取出 `const NAME = (...) => { ... }` 的函数体文本（取不到返回空串）。 */
function extractArrowBody(text: string, name: string): string {
  const marker = text.indexOf(`const ${name}`);
  if (marker < 0) return '';
  const open = text.indexOf('{', text.indexOf('=>', marker));
  if (open < 0) return '';
  let depth = 0;
  for (let i = open; i < text.length; i += 1) {
    if (text[i] === '{') depth += 1;
    else if (text[i] === '}') {
      depth -= 1;
      if (depth === 0) return text.slice(open, i + 1);
    }
  }
  return '';
}

/** 取出 `<ProposalPanel ... />` 的 JSX 属性文本。 */
function extractProposalPanelProps(text: string): string {
  const start = text.indexOf('<ProposalPanel');
  if (start < 0) return '';
  const end = text.indexOf('/>', start);
  return end < 0 ? '' : text.slice(start, end);
}

describe('V-ONTO-007 demo 注入绑定不得进入 changeSet 提交', () => {
  it('工作台把后端权威数据（serverObjectTypes）交给 ProposalPanel', () => {
    const jsx = extractProposalPanelProps(stripComments(read(LAYOUT_REL)));
    expect(jsx, `${LAYOUT_REL} 未找到 ProposalPanel`).not.toBe('');
    expect(jsx).toMatch(/objectTypes=\{serverObjectTypes\}/);
  });

  it('ProposalPanel 的 objectTypes 不得接收 demo 派生值', () => {
    const jsx = extractProposalPanelProps(stripComments(read(LAYOUT_REL)));
    const objectTypesProp = (jsx.match(/objectTypes=\{([^}]*)\}/g) || []).join(' ');
    expect(objectTypesProp).not.toBe('');
    expect(objectTypesProp).not.toMatch(/applyEnterpriseDemoBindings/);
    expect(objectTypesProp).not.toMatch(/objectTypes=\{objectTypes\}/);
  });

  it('serverObjectTypes 只能来自后端原始数据，不得被 demo 绑定覆盖', () => {
    const code = stripComments(read(LAYOUT_REL));
    const setters = code.match(/setServerObjectTypes\([^)]*\)/g) || [];
    expect(setters.length, 'serverObjectTypes 必须有赋值源').toBeGreaterThan(0);
    for (const setter of setters) {
      expect(setter, `serverObjectTypes 赋值不得经 demo 绑定: ${setter}`)
        .not.toMatch(/applyEnterpriseDemoBindings/);
    }
    // 原始列表必须原样进入（useOntologyData 回调参数 loadedObjects）
    expect(code).toMatch(/setServerObjectTypes\(\s*loadedObjects\s*\)/);
  });

  it('toPropertyBody 不得把 sharedPropertyId / interfaces 带进提交体', () => {
    const body = extractArrowBody(stripComments(read(PANEL_REL)), 'toPropertyBody');
    expect(body, `${PANEL_REL} 未找到 toPropertyBody`).not.toBe('');
    expect(body).not.toMatch(/sharedPropertyId/);
    expect(body).not.toMatch(/interfaces/);
  });

  it('⑧ 实证：applyEnterpriseDemoBindings 确实注入 interfaces / sharedPropertyId（隔离有意义）', () => {
    const base: ObjectType = {
      id: 'ent_probe',
      displayName: '客户',
      apiName: 'Customer',
      description: '',
      icon: 'Users',
      color: 'bg-blue-500',
      primaryKey: 'p1',
      titleProperty: 'p1',
      properties: [
        {
          id: 'p1',
          displayName: '联系人',
          apiName: 'contact_name',
          dataType: 'string',
          isPrimaryKey: false,
          description: '',
        },
      ],
      status: 'ACTIVE',
    };
    const out = applyEnterpriseDemoBindings([base]);
    expect(out[0].interfaces).toContain('intf_contactable');
    expect(out[0].properties[0].sharedPropertyId).toBe('sp_contact_person');
    // 不修改入参：原始对象仍干净（否则隔离测试自身会被污染）
    expect(base.interfaces).toBeUndefined();
    expect(base.properties[0].sharedPropertyId).toBeUndefined();
  });
});
