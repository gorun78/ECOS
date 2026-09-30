/**
 * objectExplorerTypes — ObjectExplorer 拆分组件间共享的表单/状态类型（H6-T4 新增，helpers.ts 未改动）
 * 结构与原 ObjectExplorer.tsx 内 useState 推断出的字面量类型一致。
 * @license Apache-2.0
 */

export interface RelFormData {
  targetObjectId: string;
  targetEntityCode: string;
  relationshipCode: string;
  relationshipType: string;
}

export interface AvailableTransition {
  transitionCode: string;
  toStatus: string;
  transitionName: string;
}
