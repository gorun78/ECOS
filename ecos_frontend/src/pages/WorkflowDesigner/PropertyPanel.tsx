/**
 * PropertyPanel — Node property editor (right panel).
 * @license Apache-2.0
 */

import { Trash2, Zap, Edit3 } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";

interface PropertyPanelProps {
  selectedNode: any;
  onUpdateData: (nodeId: string, key: string, value: any) => void;
  onDelete: () => void;
}

export default function PropertyPanel({ selectedNode, onUpdateData, onDelete }: PropertyPanelProps) {
  const { t } = useLanguage();

  if (!selectedNode) {
    return (
      <div className="p-6 text-center text-xs text-slate-400">
        <Edit3 className="w-8 h-8 mx-auto mb-2 text-slate-300" />
        {t("wf.panel.click_hint")}
      </div>
    );
  }

  return (
    <div className="p-4">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
          <Zap className="w-4 h-4 text-indigo-500" />
          {t(`wf.node_type.${selectedNode.type}`)}
        </h3>
        <button
          onClick={onDelete}
          className="p-1.5 hover:bg-red-50 rounded text-red-400 transition"
          title="Delete node"
        >
          <Trash2 className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Common: Label */}
      <div className="mb-3">
        <label className="text-[10px] font-semibold text-slate-500 uppercase">{t("wf.panel.label")}</label>
        <input
          value={(selectedNode.data.label as string) || ""}
          onChange={e => onUpdateData(selectedNode.id, "label", e.target.value)}
          className="w-full mt-1 border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:border-indigo-400"
        />
      </div>

      {/* Human Task */}
      {selectedNode.type === "human_task" && (
        <div className="space-y-3">
          <div>
            <label className="text-[10px] font-semibold text-slate-500 uppercase">{t("wf.panel.assignee")}</label>
            <input
              value={(selectedNode.data.assignee as string) || ""}
              onChange={e => onUpdateData(selectedNode.id, "assignee", e.target.value)}
              className="w-full mt-1 border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs"
              placeholder="admin / manager"
            />
          </div>
        </div>
      )}

      {/* Agent Node */}
      {selectedNode.type === "agent_node" && (
        <div className="space-y-3">
          <div>
            <label className="text-[10px] font-semibold text-slate-500 uppercase">{t("wf.panel.agent_role")}</label>
            <select
              value={(selectedNode.data.agent_profile as string) || "coordinator"}
              onChange={e => onUpdateData(selectedNode.id, "agent_profile", e.target.value)}
              className="w-full mt-1 border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs"
            >
              <option value="coordinator">{t("wf.agent.coordinator")}</option>
              <option value="supplier_auditor">{t("wf.agent.supplier_auditor")}</option>
              <option value="data_analyst">{t("wf.agent.data_analyst")}</option>
            </select>
          </div>
          <div>
            <label className="text-[10px] font-semibold text-slate-500 uppercase">{t("wf.panel.tools")}</label>
            <div className="mt-1 space-y-1">
              {["object_query", "knowledge_search", "graph_query", "workflow_start"].map(tool => (
                <label key={tool} className="flex items-center gap-2 text-[11px] text-slate-600">
                  <input
                    type="checkbox"
                    checked={(selectedNode.data.tools as string[] || []).includes(tool) || false}
                    onChange={e => {
                      const current = (selectedNode.data.tools as string[]) || [];
                      const next = e.target.checked ? [...current, tool] : current.filter(t => t !== tool);
                      onUpdateData(selectedNode.id, "tools", next);
                    }}
                  />
                  {tool}
                </label>
              ))}
            </div>
          </div>
          <div>
            <label className="text-[10px] font-semibold text-slate-500 uppercase">{t("wf.panel.input_map")}</label>
            <textarea
              value={selectedNode.data.input_mapping ? JSON.stringify(selectedNode.data.input_mapping, null, 2) : '{"entity_type":"Supplier"}'}
              onChange={e => {
                try { onUpdateData(selectedNode.id, "input_mapping", JSON.parse(e.target.value)); }
                catch {}
              }}
              className="w-full mt-1 border border-slate-200 rounded-lg px-2.5 py-1.5 text-[10px] font-mono"
              rows={3}
            />
          </div>
          <div>
            <label className="text-[10px] font-semibold text-slate-500 uppercase">{t("wf.panel.output_map")}</label>
            <textarea
              value={selectedNode.data.output_mapping ? JSON.stringify(selectedNode.data.output_mapping, null, 2) : '{"risk_level":"${agent.extracted.risk_level}"}'}
              onChange={e => {
                try { onUpdateData(selectedNode.id, "output_mapping", JSON.parse(e.target.value)); }
                catch {}
              }}
              className="w-full mt-1 border border-slate-200 rounded-lg px-2.5 py-1.5 text-[10px] font-mono"
              rows={3}
            />
          </div>
        </div>
      )}

      {/* Condition Gateway */}
      {selectedNode.type === "condition_gateway" && (
        <div className="space-y-3">
          <div>
            <label className="text-[10px] font-semibold text-slate-500 uppercase">{t("wf.panel.expression")}</label>
            <div className="mt-1 relative">
              <input
                value={(selectedNode.data.expression as string) || ""}
                onChange={e => onUpdateData(selectedNode.id, "expression", e.target.value)}
                className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs font-mono bg-slate-50 focus:bg-white focus:outline-none focus:border-indigo-400"
                placeholder='e.g. score > 80'
              />
            </div>
            <div className="mt-1 text-[9px] text-slate-400 leading-relaxed">
              {t("wf.panel.expression_hint")}
            </div>
          </div>
          <div>
            <label className="text-[10px] font-semibold text-slate-500 uppercase">{t("wf.panel.field")}</label>
            <input
              value={(selectedNode.data.field as string) || "risk_level"}
              onChange={e => onUpdateData(selectedNode.id, "field", e.target.value)}
              className="w-full mt-1 border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs"
            />
          </div>
          <div>
            <label className="text-[10px] font-semibold text-slate-500 uppercase">{t("wf.panel.routes")}</label>
            <textarea
              value={selectedNode.data.routes ? JSON.stringify(selectedNode.data.routes, null, 2) : '{"high":"","low":""}'}
              onChange={e => {
                try { onUpdateData(selectedNode.id, "routes", JSON.parse(e.target.value)); }
                catch {}
              }}
              className="w-full mt-1 border border-slate-200 rounded-lg px-2.5 py-1.5 text-[10px] font-mono"
              rows={4}
              placeholder='{"high":"node_id_1","low":"node_id_2"}'
            />
          </div>
          <div className="text-[10px] text-slate-400">💡 {t("wf.panel.route_hint")}</div>
        </div>
      )}

      {/* Node info */}
      <div className="mt-4 pt-3 border-t border-slate-200">
        <div className="text-[9px] text-slate-400 font-mono">{t("wf.panel.id")}: {selectedNode.id}</div>
        <div className="text-[9px] text-slate-400 font-mono">{t("wf.panel.type")}: {selectedNode.type}</div>
      </div>
    </div>
  );
}
