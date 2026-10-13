import { useCallback, useEffect, useState } from 'react';
import type { WorkshopApp, WorkshopWidget, WorkshopVariable, WorkshopPage, WorkshopObjectRow } from './types';
import { useLanguage } from '../../components/LanguageContext';
import { logger } from '../../services/logger';
import { fetchOntologyData, updateOntologyData } from '../../services/ontologyApi';
import { useTheme } from '../../components/ThemeContext';
import type { DataRecord } from '../../types/ontology';

interface WorkshopOpsState {
  newVarName: string;
  setNewVarName: (v: string) => void;
  newVarDesc: string;
  setNewVarDesc: (v: string) => void;
  newVarType: WorkshopVariable['type'];
  setNewVarType: (v: WorkshopVariable['type']) => void;
  newVarObjType: string;
  setNewVarObjType: (v: string) => void;
  showAddVarModal: boolean;
  setShowAddVarModal: (v: boolean) => void;
  showAddWidgetModal: boolean;
  setShowAddWidgetModal: (v: boolean) => void;
  addWidgetSlot: string;
  setAddWidgetSlot: (v: string) => void;
  selectedWidgetId: string | null;
  setSelectedWidgetId: (v: string | null) => void;
  activePageId: string | null;
  setActivePageId: (v: string | null) => void;
  persistApps: (apps: WorkshopApp[]) => void;
}

/** 本体记录 → 预览行：保留 id 供真实写回，properties 平铺供渲染。 */
function toSimRow(rec: DataRecord): WorkshopObjectRow {
  return { id: rec.id, ...(rec.properties as Record<string, string | undefined>) };
}

// Widget operations hook — extracted from WorkshopView for file size compliance
export function useWidgetOps(
  apps: WorkshopApp[],
  activeApp: WorkshopApp | null,
  activePage: WorkshopPage | null,
  setApps: (apps: WorkshopApp[]) => void,
  showToast: (type: 'success' | 'info' | 'error', message: string) => void,
  state: WorkshopOpsState,
) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [showActionModal, setShowActionModal] = useState<{ id: string } | null>(null);
  const [flightsData, setFlightsData] = useState<WorkshopObjectRow[]>([]);
  const [aircraftData, setAircraftData] = useState<WorkshopObjectRow[]>([]);
  const [pilotsData, setPilotsData] = useState<WorkshopObjectRow[]>([]);
  // 危险操作确认（组件移除走 ConfirmDialog，不再 window.confirm）
  const [pendingDeleteWidgetId, setPendingDeleteWidgetId] = useState<string | null>(null);
  // 本体数据加载 / 写回的真实状态（无假真理：失败即错误态，不掩成空列表）
  const [simDataLoading, setSimDataLoading] = useState(false);
  const [simDataError, setSimDataError] = useState<string | null>(null);
  const [actionExecuting, setActionExecuting] = useState(false);
  // 目标记录未携带本体 id ⇒ 写回未接入，仅本地暂存（挂演示标识）
  const [actionWritebackLocalOnly, setActionWritebackLocalOnly] = useState(false);

  const {
    newVarName, setNewVarName,
    newVarDesc, setNewVarDesc,
    newVarType,
    newVarObjType,
    setShowAddVarModal,
    setShowAddWidgetModal,
    addWidgetSlot,
    selectedWidgetId, setSelectedWidgetId,
    persistApps,
  } = state;
  const saveAppsState = (next: WorkshopApp[]) => { persistApps(next); setApps(next); };

  // ── 真实本体数据加载：进入应用编辑器时按对象类型拉取 ecos ontology data ──
  const loadSimData = useCallback(async () => {
    setSimDataLoading(true);
    setSimDataError(null);
    try {
      const [flights, aircrafts] = await Promise.all([
        fetchOntologyData({ objectTypeId: 'flight', size: 200 }),
        fetchOntologyData({ objectTypeId: 'aircraft', size: 200 }),
      ]);
      setFlightsData((flights?.data ?? []).map(toSimRow));
      setAircraftData((aircrafts?.data ?? []).map(toSimRow));
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      logger.warn('workshop-sim-data', '本体数据加载失败', message);
      setFlightsData([]);
      setAircraftData([]);
      setSimDataError(message);
      showToast('error', t('aiworkbench.ws.ops.dataLoadFailed', { message }));
    } finally {
      setSimDataLoading(false);
    }
  }, [showToast, t]);

  useEffect(() => {
    if (activeApp) {
      void loadSimData();
      setActionWritebackLocalOnly(false);
    }
  }, [activeApp?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleAddVariable = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newVarName.trim() || !activeApp) return;

    const varId = `v_var_${Date.now().toString().slice(-4)}`;
    const newVar: WorkshopVariable = {
      id: varId,
      name: newVarName.trim(),
      type: newVarType,
      objectTypeId: newVarType === 'object_set' || newVarType === 'object' ? newVarObjType : undefined,
      initialValue: newVarType === 'object_set' ? 'all' : null,
      value: newVarType === 'object_set' ? 'all' : null,
      description: newVarDesc.trim() || t('aiworkbench.ws.ops.varDefaultDesc')
    };

    const updatedApps = apps.map(a => {
      if (a.id === activeApp.id) {
        return { ...a, variables: [...a.variables, newVar] };
      }
      return a;
    });

    saveAppsState(updatedApps);
    setShowAddVarModal(false);
    setNewVarName('');
    setNewVarDesc('');
    showToast('success', t('aiworkbench.ws.ops.varAdded', { name: newVar.name }));
  };

  // Widget management
  const handleAddWidget = (type: WorkshopWidget['type']) => {
    if (!activeApp || !activePage) return;

    const widgetId = `w_${type}_${Date.now().toString().slice(-4)}`;
    const newWidget: WorkshopWidget = {
      id: widgetId,
      type,
      title: `${t(`aiworkbench.ws.ops.widgetTitle.${type}`)} ${Date.now().toString().slice(-4)}`,
      slot: addWidgetSlot,
      config: {
        dataSourceVarId: activeApp.variables[0]?.id,
        targetVarId: activeApp.variables.find((v: WorkshopVariable) => v.type === 'object')?.id
      }
    };

    const updatedApps = apps.map(a => {
      if (a.id === activeApp.id) {
        return {
          ...a,
          pages: a.pages.map(p => {
            if (p.id === activePage.id) {
              return { ...p, widgets: [...p.widgets, newWidget] };
            }
            return p;
          })
        };
      }
      return a;
    });

    saveAppsState(updatedApps);
    setShowAddWidgetModal(false);
    setSelectedWidgetId(widgetId);
    showToast('success', t('aiworkbench.ws.ops.widgetAdded', { slot: addWidgetSlot }));
  };

  // 危险操作：只登记待确认目标，实际删除在 confirmDeleteWidget（ConfirmDialog 确认门）
  const handleDeleteWidget = (widgetId: string) => {
    setPendingDeleteWidgetId(widgetId);
  };

  const cancelDeleteWidget = () => setPendingDeleteWidgetId(null);

  const confirmDeleteWidget = () => {
    const widgetId = pendingDeleteWidgetId;
    setPendingDeleteWidgetId(null);
    if (!activeApp || !activePage || !widgetId) return;

    const updatedApps = apps.map(a => {
      if (a.id === activeApp.id) {
        return {
          ...a,
          pages: a.pages.map(p => {
            if (p.id === activePage.id) {
              return { ...p, widgets: p.widgets.filter((w: WorkshopWidget) => w.id !== widgetId) };
            }
            return p;
          })
        };
      }
      return a;
    });

    saveAppsState(updatedApps);
    if (selectedWidgetId === widgetId) setSelectedWidgetId(null);
    showToast('info', t('aiworkbench.ws.ops.widgetRemoved'));
  };

  const handleUpdateWidgetConfig = (config: Partial<WorkshopWidget['config']>, title?: string) => {
    if (!activeApp || !activePage || !selectedWidgetId) return;

    const updatedApps = apps.map(a => {
      if (a.id === activeApp.id) {
        return {
          ...a,
          pages: a.pages.map(p => {
            if (p.id === activePage.id) {
              return {
                ...p,
                widgets: p.widgets.map(w => {
                  if (w.id === selectedWidgetId) {
                    return {
                      ...w,
                      title: title !== undefined ? title : w.title,
                      config: { ...w.config, ...config }
                    };
                  }
                  return w;
                })
              };
            }
            return p;
          })
        };
      }
      return a;
    });

    saveAppsState(updatedApps);
  };

  // Run/Publish transactions — 无真实发布通道（services 无 workshop app publish 端点），
  // 真实切换逻辑与提示在 WorkshopView.handlePublishApp（标演示）。

  // ==========================================
  // 3. Preview runtime (interact mode)
  // ==========================================
  const handleVariableChange = (varId: string, value: unknown) => {
    if (!activeApp) return;
    const updatedApps = apps.map(a => {
      if (a.id === activeApp.id) {
        return {
          ...a,
          variables: a.variables.map(v => v.id === varId ? { ...v, value } : v)
        };
      }
      return a;
    });
    setApps(updatedApps);
  };

  // Get current active variable values
  const getVarValue = (varId?: string) => {
    if (!activeApp || !varId) return null;
    return activeApp.variables.find((v: WorkshopVariable) => v.id === varId)?.value;
  };

  // 预览表格数据 = 真实本体记录（经 loadSimData 写入），按运行时变量过滤
  const getSimulatedFlights = () => {
    const filterStatus = getVarValue('v_filter_status') || 'ALL';
    const filterAirport = getVarValue('v_filter_airport') || 'ALL';

    return flightsData.filter((flight) => {
      const matchStatus = filterStatus === 'ALL' || flight.status === filterStatus;
      const matchAirport = filterAirport === 'ALL' || flight.depAirport === filterAirport || flight.arrAirport === filterAirport;
      return matchStatus && matchAirport;
    });
  };

  /**
   * 本体写回单通道：目标记录带 ontology 数据 id ⇒ 走 services/ontologyApi.updateOntologyData
   * （PUT /api/v1/ontology/data/{id}）；写回失败 ⇒ 错误态，绝不谎报成功；
   * 记录无 id（本地暂存行）⇒ 仅本地暂存并挂「写回未接入」演示标识。
   */
  const handleExecuteSimulatedAction = async (actionId: string, formData: Record<string, string>) => {
    if (actionExecuting) return;

    const isFlight = actionId === 'update_flight_status';
    const keyField = isFlight ? 'flightNumber' : 'tailNumber';
    const targetKey = isFlight ? formData.flight_param : formData.aircraft_param;
    if (!targetKey) { setShowActionModal(null); return; }

    const source = isFlight ? flightsData : aircraftData;
    const changed = isFlight
      ? { status: formData.new_status_param }
      : { status: 'MAINTENANCE', lastMaintenance: formData.maintenance_date_param };
    const applyPatch = (row: WorkshopObjectRow): WorkshopObjectRow => ({ ...row, ...changed });
    const setter = isFlight ? setFlightsData : setAircraftData;

    const target = source.find((r) => r[keyField] === targetKey);
    if (!target) {
      showToast('error', t('aiworkbench.ws.action.targetMissing', { key: String(targetKey) }));
      setShowActionModal(null);
      return;
    }

    setActionExecuting(true);
    try {
      if (target.id) {
        const rowId = target.id;
        const { id: _omitId, ...baseProperties } = target;
        // 真实本体写回：PUT /api/v1/ontology/data/{id}（services 单源通道）
        await updateOntologyData(rowId, { properties: { ...baseProperties, ...changed } });
        // 写回成功后仅以确认过的结果更新本地视图
        const patch = applyPatch(target);
        setter(source.map((r) => (r.id === rowId ? patch : r)));
        const selectedVarId = isFlight ? 'v_selected_flight' : 'v_selected_ac';
        const selectedVar = activeApp?.variables.find((v: WorkshopVariable) => v.id === selectedVarId);
        if (selectedVar?.value?.[keyField] === targetKey) {
          handleVariableChange(selectedVarId, patch);
        }
        setActionWritebackLocalOnly(false);
        showToast(
          'success',
          isFlight
            ? t('aiworkbench.ws.action.successFlight', { flightNum: String(targetKey), status: String(formData.new_status_param) })
            : t('aiworkbench.ws.action.successAircraft', { tail: String(targetKey), date: String(formData.maintenance_date_param) }),
        );
      } else {
        // 无本体 id ⇒ 写回未接入：只允许诚实的本地暂存 + 演示标识
        const patch = applyPatch(target);
        const staged = source.map((r) => (r[keyField] === targetKey ? patch : r));
        setter(staged);
        try {
          localStorage.setItem(isFlight ? 'workshop_sim_flights' : 'workshop_sim_aircraft', JSON.stringify(staged));
        } catch {
          logger.warn('workshop-writeback', '本地暂存写入失败');
        }
        setActionWritebackLocalOnly(true);
        showToast('info', t('aiworkbench.ws.action.localOnlyNotice'));
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      logger.warn('workshop-writeback', '本体写回失败', message);
      showToast('error', t('aiworkbench.ws.action.failed', { message }));
    } finally {
      setActionExecuting(false);
      setShowActionModal(null);
    }
  };

  // Render variables mapping colors for badges — 语义 token（§七），不再输出硬编码色板/暗色兜底类
  const getVarTypeBadge = (_type?: string) => `${styles.badgeBg} ${styles.badgeText} ${styles.appBorder}`;

  // Helper to resolve tailwind theme primary colors
  const getPrimaryColorClass = (color: string) => {
    switch (color) {
      case 'blue': return 'bg-blue-600 text-white hover:bg-blue-500 hover:shadow-blue-200';
      case 'indigo': return 'bg-indigo-600 text-white hover:bg-indigo-500 hover:shadow-indigo-200';
      case 'violet': return 'bg-violet-600 text-white hover:bg-violet-500 hover:shadow-violet-200';
      case 'emerald': return 'bg-emerald-600 text-white hover:bg-emerald-500 hover:shadow-emerald-200';
      case 'rose': return 'bg-rose-600 text-white hover:bg-rose-500 hover:shadow-rose-200';
      default: return 'bg-blue-600 text-white hover:bg-blue-500';
    }
  };

  return {
    showActionModal, setShowActionModal,
    flightsData, setFlightsData,
    aircraftData, setAircraftData,
    pilotsData, setPilotsData,
    pendingDeleteWidgetId, cancelDeleteWidget, confirmDeleteWidget,
    simDataLoading, simDataError, retryLoadSimData: loadSimData,
    actionExecuting, actionWritebackLocalOnly,
    handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig,
    handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction,
    getVarTypeBadge, getPrimaryColorClass,
  };
}
