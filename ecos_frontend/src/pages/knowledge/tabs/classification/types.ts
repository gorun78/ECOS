/** 目录编辑态（新增 / 重命名），自 ClassificationTab 拆出 */
export interface CatEditMode {
  mode: 'create' | 'rename';
  parentId?: string | null;
  targetId?: string;
}
