import React from 'react';
import { describe, it, expect, afterEach } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import { OagProgress, OAG_NODES, normalizeOagNode, type OagStepState } from '../components/aiworkbench/playground/MessageBubble';
import { ThemeProvider } from '../components/ThemeContext';
import { LanguageProvider } from '../components/LanguageContext';

/**
 * D149 回归：试验场 OAG 进度条按后端真实节点名渲染。
 *
 * 缺陷原貌：后端 OagPipelineEngine 推的 node 是 OagNode.name() = 类单名
 * （IntentClassifierNode / SecurityCheckerNode），前端 OAG_NODES 用去后缀短键，
 * 比对恒不命中 ⇒ AgentPlayground 把每帧都当未知节点追加成第 9、10 项 ⇒
 * OagProgress 里 `OAG_NODES[idx]` 为 undefined，读 `.icon` 抛 TypeError，
 * 整棵消息树被 ErrorBoundary 接住，页面变成「页面渲染出错」。
 *
 * 本用例锁两件事：
 *   1) normalizeOagNode 能把后端类单名映射回短键；
 *   2) 喂入「后端真实帧 + 越界多余帧」时渲染不抛，且进度按命中节点计数。
 */
function renderOag(steps: OagStepState[]) {
  return render(
    <LanguageProvider>
      <ThemeProvider>
        <OagProgress steps={steps} hasError={false} />
      </ThemeProvider>
    </LanguageProvider>,
  );
}

afterEach(() => {
  cleanup();
});

describe('OAG 进度条 D149 回归', () => {
  it('normalizeOagNode 裁掉后端 Node 后缀', () => {
    expect(normalizeOagNode('IntentClassifierNode')).toBe('IntentClassifier');
    expect(normalizeOagNode('AuditLoggerNode')).toBe('AuditLogger');
    expect(normalizeOagNode('IntentClassifier')).toBe('IntentClassifier');
    expect(normalizeOagNode('')).toBe('');
    expect(normalizeOagNode(undefined)).toBe('');
  });

  it('后端类单名帧渲染为已完成节点，且不越界崩渲染', () => {
    const backendNodes = [
      'IntentClassifierNode',
      'ContextLoaderNode',
      'QueryRewriterNode',
      'SecurityCheckerNode',
      'AuditLoggerNode',
    ];
    const steps: OagStepState[] = backendNodes.map((node) => ({
      node,
      stepState: 'done' as const,
      nodeElapsedMs: 1,
    }));

    const { container, getByText } = renderOag(steps);
    // 5 个真实帧全部命中 8 节点表中的已有槽位（不去重、不追加第 9 项）
    expect(getByText(`5/${OAG_NODES.length} · 63%`)).toBeTruthy();
    // 每个短键都命中自己的元数据（title 里带 label）
    for (const short of ['IntentClass', 'ContextLoad', 'QueryRewrite', 'SecurityCheck', 'AuditLog']) {
      expect(container.querySelector(`[title^="${short}"]`)).toBeTruthy();
    }
    // 5 帧 ⇒ 5 个节点徽标（不出现越界追加的第 9 项）
    expect(container.querySelectorAll('[title]').length).toBe(5);
  });

  it('多余未知帧（超出 8 节点）仍可渲染', () => {
    const steps: OagStepState[] = Array.from({ length: 11 }, (_, i) => ({
      node: OAG_NODES[i]?.key ?? `Unknown${i}Node`,
      stepState: 'pending' as const,
    }));
    expect(() => renderOag(steps)).not.toThrow();
  });
});
