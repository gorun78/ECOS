/**
 * TestDialog — Workflow test execution modal.
 * @license Apache-2.0
 */

import { Activity, Loader2, X } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { NODE_TYPES, BoxIcon } from "./helpers";

interface TestDialogProps {
  testResult: any;
  onClose: () => void;
}

export default function TestDialog({ testResult, onClose }: TestDialogProps) {
  const { t } = useLanguage();

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="bg-white rounded-xl shadow-2xl w-full sm:w-[550px] mx-4 sm:mx-auto max-h-[80vh] overflow-y-auto animate-in zoom-in-95">
        <div className="p-4 border-b border-slate-200 flex items-center justify-between">
          <h3 className="text-sm font-bold flex items-center gap-2">
            <Activity className="w-4 h-4 text-emerald-500" /> {t("wf.test.title")}
          </h3>
          <button onClick={onClose}>
            <X className="w-4 h-4 text-slate-400" />
          </button>
        </div>
        <div className="p-4">
          {!testResult ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="w-6 h-6 text-emerald-500 animate-spin" />
              <span className="ml-2 text-xs text-slate-500">{t("wf.test.running")}</span>
            </div>
          ) : testResult.error ? (
            <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-xs text-red-700">{testResult.error}</div>
          ) : (
            <div>
              <div className="text-xs text-slate-600 mb-3">
                {t("wf.test.workflow")}: <span className="font-semibold">{testResult.workflowName}</span>
                <span className="ml-3 text-slate-400">{t("wf.test.nodes")}: {testResult.nodesCount} · {t("wf.test.edges")}: {testResult.edgesCount}</span>
              </div>
              <div className="space-y-2">
                {(testResult.trace || []).map((step: any, i: number) => {
                  const typeKey = step.nodeType === "START" ? "start" :
                    step.nodeType === "END" ? "end" :
                    step.nodeType === "GATEWAY" ? "condition_gateway" :
                    step.nodeType === "AGENT_NODE" ? "agent_node" : "human_task";
                  const def = NODE_TYPES[typeKey as keyof typeof NODE_TYPES];
                  const Icon = def?.icon || BoxIcon;
                  return (
                    <div key={i} className="flex items-start gap-2.5 bg-slate-50 rounded-lg p-2.5 border border-slate-100">
                      <div className={`p-1 rounded mt-0.5 ${def?.color || "bg-slate-100"}`}>
                        <Icon className="w-3.5 h-3.5 text-slate-600" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="text-[11px] font-semibold text-slate-700">
                          {step.nodeName || step.nodeId}
                          <span className="ml-2 text-[9px] text-slate-400 font-normal">{step.nodeType}</span>
                        </div>
                        {step.agentProfile && (
                          <div className="text-[10px] text-slate-500 mt-0.5">
                            {t("wf.panel.agent_role")}: {step.agentProfile} · {t("wf.panel.tools")}: {(step.tools || []).join(", ")}
                          </div>
                        )}
                        {step.field && (
                          <div className="text-[10px] text-slate-500 mt-0.5">
                            {step.expression ? <span>{t("wf.panel.expression")}: <code className="bg-slate-100 px-1 rounded">{step.expression}</code> · </span> : ""}
                            {t("wf.panel.field")}: {step.field} · {t("wf.panel.routes")}: {JSON.stringify(step.routes)}
                          </div>
                        )}
                        <div className="flex items-center gap-2 mt-1">
                          <span className={`text-[9px] px-1.5 py-0.5 rounded-full font-semibold ${
                            step.status === "SIMULATED" ? "bg-purple-100 text-purple-700" :
                            step.status === "REACHED" ? "bg-green-100 text-green-700" :
                            step.status === "PENDING" ? "bg-blue-100 text-blue-700" :
                            "bg-slate-100 text-slate-600"
                          }`}>
                            {step.status}
                          </span>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
        <div className="p-4 border-t border-slate-200 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 text-xs bg-slate-100 rounded-lg hover:bg-slate-200"
          >
            {t("wf.btn.close")}
          </button>
        </div>
      </div>
    </div>
  );
}
