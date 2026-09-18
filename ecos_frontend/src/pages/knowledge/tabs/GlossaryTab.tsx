// Wiki Tab —— 本体工作台的 Wiki（术语/词条）管理入口。
//
// 数据来源：GlossaryController（/api/v1/ontology/glossary/**，术语状态机
// DRAFT → REVIEW → PUBLISHED → DEPRECATED），由 buszhi 服务层（ontology-engine 聚合）提供，
// 与本体工作台其余菜单同源，均为 /api/v1/ontology/** 契约。
//
// 复用 GlossaryManager（术语全生命周期管理）以避免重复实现列表/表单/状态流转逻辑；
// 其根节点为 h-full flex 布局，故此处需给出确定高度。
import GlossaryManager from '../../GlossaryManager';

export default function GlossaryTab() {
  return (
    <div className="h-full min-h-[520px]">
      <GlossaryManager />
    </div>
  );
}
