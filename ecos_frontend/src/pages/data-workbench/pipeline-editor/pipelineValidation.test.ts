/**
 * pipelineValidation.test.ts — 三场景 + 反例（Wave 4 T1）
 *
 * 覆盖环检测 / 必填 / 连通性；反例 = 画布空 / 所有反边界 case。
 * 节点构造用最小 Node 形态（id/type/data），不依赖 ReactFlow 渲染。
 */
import { describe, it, expect } from 'vitest';
import type { Node } from '@xyflow/react';
import {
  checkRequiredConfigs, findCycleNodes, findOrphanedNodes, runPreFlightCheck,
} from './pipelineValidation';
import type { NodeConfig } from './types';

/** 构造最小 Node —— 不触发 ReactFlow 渲染管线 */
function mkNode(id: string, nodeType: NodeConfig['nodeType'], config: Record<string, unknown> = {}): Node {
  return {
    id,
    type: nodeType as unknown as string,
    position: { x: 0, y: 0 },
    data: { nodeType, label: id, config } as unknown as Node['data'],
  } as unknown as Node;
}

const src = (id: string) => mkNode(id, 'SOURCE_CSV', { filePath: '/tmp/a.csv' });
const transform = (id: string) => mkNode(id, 'TRANSFORM_SQL', { transformSql: 'SELECT 1' });
const out = (id: string) => mkNode(id, 'OUTPUT_OBJECT', { targetTable: 't_out' });

// =============== 1. 必填字段（missingConfig） ===============

describe('checkRequiredConfigs', () => {
  it('SOURCE_JDBC 缺 datasourceId 与 sql → 两条 missingConfig', () => {
    const n = mkNode('n1', 'SOURCE_JDBC', {});
    const issues = checkRequiredConfigs([n]);
    expect(issues).toHaveLength(2);
    const reasons = issues.map((i) => i.reason).sort();
    expect(reasons).toEqual(['datasourceId', 'sql']);
  });

  it('SOURCE_CSV 缺 filePath → 一条 missingConfig', () => {
    const n = mkNode('n1', 'SOURCE_CSV', {});
    const issues = checkRequiredConfigs([n]);
    expect(issues).toHaveLength(1);
    expect(issues[0].reason).toBe('filePath');
    expect(issues[0].nodeId).toBe('n1');
  });

  it('SOURCE_REST 缺 url → missingConfig', () => {
    const n = mkNode('n1', 'SOURCE_REST', {});
    const issues = checkRequiredConfigs([n]);
    expect(issues[0].reason).toBe('url');
  });

  it('TRANSFORM_SQL 缺 transformSql → missingConfig', () => {
    const n = mkNode('n1', 'TRANSFORM_SQL', {});
    const issues = checkRequiredConfigs([n]);
    expect(issues[0].reason).toBe('transformSql');
  });

  it('OUTPUT_OBJECT 缺 targetTable → missingConfig', () => {
    const n = mkNode('n1', 'OUTPUT_OBJECT', {});
    const issues = checkRequiredConfigs([n]);
    expect(issues[0].reason).toBe('targetTable');
  });

  it('SOURCE_CDC 必填字段为空数组 → 通过（边界：P2-01 枚举）', () => {
    const n = mkNode('n1', 'SOURCE_CDC', {});
    expect(checkRequiredConfigs([n])).toHaveLength(0);
  });

  it('所有必填齐 → []（无问题）', () => {
    const nodes = [
      src('a'),
      mkNode('b', 'SOURCE_JDBC', { datasourceId: 'ds1', sql: 'SELECT 1' }),
      transform('t'),
      out('o'),
    ];
    expect(checkRequiredConfigs(nodes)).toHaveLength(0);
  });

  it('空字符串值视为缺失（""/whitespace）', () => {
    const n = mkNode('n1', 'SOURCE_CSV', { filePath: '' });
    expect(checkRequiredConfigs([n])).toHaveLength(1);
    const n2 = mkNode('n2', 'SOURCE_CSV', { filePath: '   ' });
    expect(checkRequiredConfigs([n2])).toHaveLength(1);
  });
});

// =============== 2. 环检测（cycle） ===============

describe('findCycleNodes', () => {
  it('直线链 A → B → C 无环', () => {
    const nodes = [src('a'), transform('b'), out('c')];
    const edges = [{ source: 'a', target: 'b' }, { source: 'b', target: 'c' }];
    expect(findCycleNodes(nodes, edges)).toHaveLength(0);
  });

  it('A ↔ B 双向边 → 环包含 a 和 b', () => {
    const nodes = [src('a'), transform('b')];
    const edges = [{ source: 'a', target: 'b' }, { source: 'b', target: 'a' }];
    const cyclic = findCycleNodes(nodes, edges);
    expect(cyclic.has('a')).toBe(true);
    expect(cyclic.has('b')).toBe(true);
  });

  it('自环 A → A', () => {
    const nodes = [src('a')];
    const edges = [{ source: 'a', target: 'a' }];
    expect(findCycleNodes(nodes, edges).has('a')).toBe(true);
  });

  it('三角 A→B→C→A 全环', () => {
    const nodes = [src('a'), transform('b'), out('c')];
    const edges = [
      { source: 'a', target: 'b' },
      { source: 'b', target: 'c' },
      { source: 'c', target: 'a' },
    ];
    const cyclic = findCycleNodes(nodes, edges);
    expect(cyclic).toHaveLength(3);
  });

  it('下游在环之外但可走到环：仍被标记（Kahn 性质）', () => {
    const nodes = [src('a'), transform('b'), out('c'), transform('d')];
    // a→b→c→b (b 在环里)；d 不依赖任何节点
    const edges = [
      { source: 'a', target: 'b' },
      { source: 'b', target: 'c' },
      { source: 'c', target: 'b' },
    ];
    const cyclic = findCycleNodes(nodes, edges);
    // b, c 必在环内；a/d 不在
    expect(cyclic.has('b')).toBe(true);
    expect(cyclic.has('c')).toBe(true);
    expect(cyclic.has('a')).toBe(false);
    expect(cyclic.has('d')).toBe(false);
  });

  it('边引用不存在的节点 → 忽略（防脏数据）', () => {
    const nodes = [src('a')];
    const edges = [{ source: 'a', target: 'ghost' }];
    expect(findCycleNodes(nodes, edges)).toHaveLength(0);
  });
});

// =============== 3. 连通性（reachability） ===============

describe('findOrphanedNodes', () => {
  it('孤立节点（无任何边）→ 孤儿', () => {
    const nodes = [src('a'), transform('b')];
    expect(findOrphanedNodes(nodes, []).has('a')).toBe(true);
    expect(findOrphanedNodes(nodes, []).has('b')).toBe(true);
  });

  it('SOURCE → TRANSFORM → OUTPUT 全连 → 无孤儿', () => {
    const nodes = [src('a'), transform('b'), out('c')];
    const edges = [{ source: 'a', target: 'b' }, { source: 'b', target: 'c' }];
    expect(findOrphanedNodes(nodes, edges)).toHaveLength(0);
  });

  it('有 SOURCE 但没有 OUTPUT（F 节点出不到）→ TRANSFORM 是孤儿', () => {
    const nodes = [src('a'), transform('b')];
    const edges = [{ source: 'a', target: 'b' }];
    // b 可达 FORWARD（a→b），但无法 REACH output
    const orphans = findOrphanedNodes(nodes, edges);
    expect(orphans.has('b')).toBe(true);
  });

  it('有 OUTPUT 但没有 SOURCE 流入（C 节点 bwd 不到 source）→ 孤儿', () => {
    const nodes = [src('a'), out('c')];
    // 没有边，a 和 c 都孤立（即便 c 是 sink，bwd 需 source 起点）
    const orphans = findOrphanedNodes(nodes, []);
    expect(orphans.has('a')).toBe(true);
    expect(orphans.has('c')).toBe(true);
  });

  it('同一个 TRANSFORM 既可前向又可后向 → 不算孤儿', () => {
    const nodes = [src('a'), transform('t'), out('c')];
    const edges = [{ source: 'a', target: 't' }, { source: 't', target: 'c' }];
    expect(findOrphanedNodes(nodes, edges)).toHaveLength(0);
  });
});

// =============== 4. runPreFlightCheck 三合一 ===============

describe('runPreFlightCheck', () => {
  it('空画布 → 一条 canvasEmpty 异常（反例：画布未初始化就保存）', () => {
    const { ok, issues, invalidNodeIds } = runPreFlightCheck([], []);
    expect(ok).toBe(false);
    expect(issues).toHaveLength(1);
    expect(issues[0]).toMatchObject({ key: 'missingConfig', reason: 'canvasEmpty' });
    expect(invalidNodeIds).toHaveLength(0);
  });

  it('合法链路 → ok=true, issues=[]', () => {
    const nodes = [src('a'), transform('b'), out('c')];
    const edges = [{ source: 'a', target: 'b' }, { source: 'b', target: 'c' }];
    const result = runPreFlightCheck(nodes, edges);
    expect(result.ok).toBe(true);
    expect(result.issues).toHaveLength(0);
  });

  it('混合失败（环 + 必填 + 连通）— invalid 取并集反例', () => {
    // a 缺 filePath + a↔b 环 + c 孤立
    const nodes = [
      mkNode('a', 'SOURCE_CSV', {}),
      transform('b'),
      out('c'),
    ];
    const edges = [{ source: 'a', target: 'b' }, { source: 'b', target: 'a' }];
    const { ok, issues } = runPreFlightCheck(nodes, edges);
    expect(ok).toBe(false);
    const keys = new Set(issues.map((i) => i.key));
    expect(keys.has('missingConfig')).toBe(true);
    expect(keys.has('cycle')).toBe(true);
    expect(keys.has('reachability')).toBe(true);
  });
});
