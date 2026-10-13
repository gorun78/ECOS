/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useMemo, useEffect } from 'react';

import SaveSearchModal from './object-explorer/SaveSearchModal';
import ActionExecutorModal from './object-explorer/ActionExecutorModal';
import ExplorerLeftPanel from './object-explorer/ExplorerLeftPanel';
import ExplorerStageHeader from './object-explorer/ExplorerStageHeader';
import ExplorerWelcomeView from './object-explorer/ExplorerWelcomeView';
import ExplorerTableView from './object-explorer/ExplorerTableView';
import ExplorerAnalyticsView from './object-explorer/ExplorerAnalyticsView';
import InstanceDetailPanel from './object-explorer/InstanceDetailPanel';
import { ObjectType, LinkType, ActionType, Dataset, DataRecord } from '../types/ontology';
import type { FilterQuery, SavedSearch } from './object-explorer/types';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { fetchOntologyData } from '../services/ontologyApi';

interface ObjectExplorerViewProps {
  objectTypes: ObjectType[];
  linkTypes: LinkType[];
  actionTypes: ActionType[];
  datasets: Dataset[];
  onUpdateDatasets: (updated: Dataset[]) => void;
  showToast: (type: 'success' | 'info' | 'error', message: string) => void;
  initialActiveObjectTypeId?: string | null;
  onActiveObjectTypeIdChange?: (id: string | null) => void;
}

export default function ObjectExplorerView({
  objectTypes,
  linkTypes,
  actionTypes,
  datasets,
  onUpdateDatasets,
  showToast,
  initialActiveObjectTypeId = null,
  onActiveObjectTypeIdChange
}: ObjectExplorerViewProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // Navigation & View States
  const [activeObjectTypeId, setActiveObjectTypeId] = useState<string | null>(initialActiveObjectTypeId);
  const [selectedInstance, setSelectedInstance] = useState<any | null>(null);
  const [activeTab, setActiveTab] = useState<'table' | 'analytics'>('table');
  const [detailTab, setDetailTab] = useState<'properties' | 'relations' | 'activity'>('properties');

  useEffect(() => {
    if (initialActiveObjectTypeId !== undefined) {
      setActiveObjectTypeId(initialActiveObjectTypeId);
    }
  }, [initialActiveObjectTypeId]);

  useEffect(() => {
    if (onActiveObjectTypeIdChange) {
      onActiveObjectTypeIdChange(activeObjectTypeId);
    }
  }, [activeObjectTypeId, onActiveObjectTypeIdChange]);

  // Query States
  const [localSearch, setLocalSearch] = useState('');
  const [activeFilters, setActiveFilters] = useState<FilterQuery[]>([]);
  const [sortBy, setSortBy] = useState<string>('');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');

  // Filter creation state
  const [newFilterProp, setNewFilterProp] = useState('');
  const [newFilterOp, setNewFilterOp] = useState<'equals' | 'contains' | 'gt' | 'lt' | 'is_empty' | 'is_not_empty'>('equals');
  const [newFilterVal, setNewFilterVal] = useState('');
  const [showFilterCreator, setShowFilterCreator] = useState(false);

  // Saved searches state
  const [savedSearches, setSavedSearches] = useState<SavedSearch[]>([]);
  const [newSearchName, setNewSearchName] = useState('');
  const [showSaveModal, setShowSaveModal] = useState(false);

  // Action execution state
  const [selectedAction, setSelectedAction] = useState<ActionType | null>(null);
  const [actionParams, setActionParams] = useState<Record<string, string>>({});
  const [actionError, setActionError] = useState<string | null>(null);

  const [instanceData, setInstanceData] = useState<DataRecord[]>([]);
  const [dataLoading, setDataLoading] = useState(false);
  const [dataPage, setDataPage] = useState(1);
  const [dataTotalPages, setDataTotalPages] = useState(1);
  const [dataTotal, setDataTotal] = useState(0);
  // 无假真理：实例拉取失败不再静默置空（空列表≠加载失败）。错误态 + 重试入口。
  const [instanceError, setInstanceError] = useState<string | null>(null);
  const [instanceRetryTick, setInstanceRetryTick] = useState(0);
  // 关系实例缓存未就绪时不得显示「0 关联」假象（fail-open），须显示加载中
  const [relatedLoading, setRelatedLoading] = useState(false);

  const [relatedInstanceCache, setRelatedInstanceCache] = useState<Record<string, DataRecord[]>>({});

  // Active object type metadata
  const activeObjectType = useMemo(() => {
    return objectTypes.find(ot => ot.id === activeObjectTypeId) || null;
  }, [objectTypes, activeObjectTypeId]);

  // Load saved searches from local storage
  useEffect(() => {
    const cached = localStorage.getItem('ecos_saved_searches');
    if (cached) {
      try {
        setSavedSearches(JSON.parse(cached));
      } catch (e) {
        console.error(e);
      }
    }
  }, []);

  // Sync saved searches
  const saveSearches = (updated: SavedSearch[]) => {
    setSavedSearches(updated);
    localStorage.setItem('ecos_saved_searches', JSON.stringify(updated));
  };

  // 1. Dynamic Object Instantiation from Mapping
  const allInstances = useMemo(() => {
    if (!activeObjectType || instanceData.length === 0) return [];
    return instanceData.map((record, index) => {
      const instance: Record<string, any> = {
        _index: index,
        _objectTypeId: activeObjectType.id
      };
      activeObjectType.properties.forEach(prop => {
        instance[prop.id] = record.properties[prop.id] ?? null;
      });
      return instance;
    });
  }, [activeObjectType, instanceData]);

  useEffect(() => {
    if (!activeObjectType) {
      setInstanceData([]);
      setInstanceError(null);
      return;
    }
    let cancelled = false;
    setDataLoading(true);
    setInstanceError(null);
    fetchOntologyData({ objectTypeId: activeObjectType.id, page: dataPage, size: 20 })
      .then(res => {
        if (cancelled) return;
        setInstanceData(res.data ?? []);
        setDataTotalPages(res.totalPages ?? 1);
        setDataTotal(res.total ?? 0);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        // 失败 ≠ 空数据：置错误态（UI 渲染错误横幅 + 重试），不再伪装「暂无实例」
        setInstanceData([]);
        setDataTotalPages(1);
        setInstanceError(String((e as { message?: string } | undefined)?.message || e));
      })
      .finally(() => {
        if (!cancelled) setDataLoading(false);
      });
    return () => { cancelled = true; };
  }, [activeObjectType, dataPage, instanceRetryTick]);

  // Instantiation helper for arbitrary object type (for relationships)
  const getInstancesOfObjectType = (otId: string): any[] => {
    const ot = objectTypes.find(o => o.id === otId);
    if (!ot) return [];
    const cached = relatedInstanceCache[otId];
    if (!cached) return [];
    return cached.map((record, idx) => {
      const inst: Record<string, any> = {
        _index: idx,
        _objectTypeId: ot.id,
      };
      ot.properties.forEach(prop => {
        inst[prop.id] = record.properties[prop.id] ?? null;
      });
      return inst;
    });
  };

  useEffect(() => {
    const otIds = new Set<string>();
    linkTypes.forEach(lt => {
      if (lt.sourceObjectType === activeObjectTypeId && lt.targetObjectType) otIds.add(lt.targetObjectType);
      if (lt.targetObjectType === activeObjectTypeId && lt.sourceObjectType) otIds.add(lt.sourceObjectType);
    });
    if (otIds.size === 0) return;
    let cancelled = false;
    setRelatedLoading(true);
    const fetchRelated = async () => {
      const newCache: Record<string, DataRecord[]> = {};
      let failed = 0;
      for (const otId of otIds) {
        try {
          const res = await fetchOntologyData({ objectTypeId: otId, page: 1, size: 50 });
          newCache[otId] = res.data ?? [];
        } catch { failed += 1; newCache[otId] = []; }
      }
      if (!cancelled) {
        setRelatedInstanceCache(prev => ({ ...prev, ...newCache }));
        setRelatedLoading(false);
        // 关联对象实例拉取失败时如实提示（「0 关联」可能只是拉取失败，不得静默）
        if (failed > 0) showToast('error', t('ontology.partialPropsLoadFailed').replace('{count}', String(failed)));
      }
    };
    fetchRelated();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeObjectTypeId, linkTypes]);

  // 2. Filter, Search and Sort Logic
  const processedInstances = useMemo(() => {
    let result = [...allInstances];

    // Local Search
    if (localSearch.trim()) {
      const q = localSearch.toLowerCase();
      result = result.filter(inst => {
        return Object.values(inst).some(val => 
          val !== null && val !== undefined && String(val).toLowerCase().includes(q)
        );
      });
    }

    // Active Filters
    activeFilters.forEach(f => {
      result = result.filter(inst => {
        const val = inst[f.propertyId];
        const fVal = f.value.toLowerCase();
        
        if (f.operator === 'equals') {
          return String(val ?? '').toLowerCase() === fVal;
        } else if (f.operator === 'contains') {
          return String(val ?? '').toLowerCase().includes(fVal);
        } else if (f.operator === 'gt') {
          return Number(val ?? 0) > Number(f.value);
        } else if (f.operator === 'lt') {
          return Number(val ?? 0) < Number(f.value);
        } else if (f.operator === 'is_empty') {
          return val === null || val === undefined || String(val).trim() === '';
        } else if (f.operator === 'is_not_empty') {
          return val !== null && val !== undefined && String(val).trim() !== '';
        }
        return true;
      });
    });

    // Sorting
    if (sortBy) {
      result.sort((a, b) => {
        const valA = a[sortBy];
        const valB = b[sortBy];
        
        if (valA === valB) return 0;
        if (valA === null || valA === undefined) return 1;
        if (valB === null || valB === undefined) return -1;

        const isNumeric = typeof valA === 'number' || (!isNaN(Number(valA)) && !isNaN(Number(valB)));
        if (isNumeric) {
          return sortOrder === 'asc' 
            ? Number(valA) - Number(valB)
            : Number(valB) - Number(valA);
        }

        return sortOrder === 'asc'
          ? String(valA).localeCompare(String(valB))
          : String(valB).localeCompare(String(valA));
      });
    }

    return result;
  }, [allInstances, localSearch, activeFilters, sortBy, sortOrder]);

  // Default Sort By Primary Key when Object Type changes
  useEffect(() => {
    if (activeObjectType) {
      setSortBy(activeObjectType.primaryKey);
      setSortOrder('asc');
      setActiveFilters([]);
      setLocalSearch('');
      setSelectedInstance(null);
      setDataPage(1);
    }
  }, [activeObjectTypeId]);

  // Reset selected instance if it disappears from processed list
  useEffect(() => {
    if (selectedInstance && activeObjectType) {
      const stillExists = processedInstances.some(inst => 
        inst[activeObjectType.primaryKey] === selectedInstance[activeObjectType.primaryKey]
      );
      if (!stillExists) {
        setSelectedInstance(null);
      }
    }
  }, [processedInstances, selectedInstance, activeObjectType]);

  // 3. Analytics Chart Data Construction
  const analyticsData = useMemo((): { property: typeof activeObjectType extends infer T ? T extends { properties: (infer P)[] } ? P : never : never; data: Array<{ name: string; count: number; percentage: string }> } | any[] => {
    if (!activeObjectType || processedInstances.length === 0) return [];

    // 动态选择分组字段（此前硬编码 status/manufacturer/model/city/rank 等演示 id，
    // 系 aviation 演示数据残留 —— 真实本体不存在这些属性时永远回落 properties[0]）。
    // 口径：在已加载实例上统计各属性的取值基数，优先取「基数 2~12 且基数最大」的
    // 非主键属性（类别型分布最适合分组）；无命中回落首个非主键属性，再回落 properties[0]。
    const pk = activeObjectType.primaryKey;
    let groupProp = activeObjectType.properties.find(p => p.id !== pk) || activeObjectType.properties[0];
    let bestDistinct = -1;
    for (const p of activeObjectType.properties) {
      if (p.id === pk) continue;
      const distinct = new Set(processedInstances.map(inst => String(inst[p.id] ?? '')));
      const n = distinct.size;
      if (n >= 2 && n <= 12 && n > bestDistinct) {
        bestDistinct = n;
        groupProp = p;
      }
    }

    const distribution: Record<string, number> = {};
    processedInstances.forEach(inst => {
      const key = String(inst[groupProp.id] || t('ow.label.unspecified'));
      distribution[key] = (distribution[key] || 0) + 1;
    });

    return {
      property: groupProp,
      data: Object.entries(distribution).map(([name, count]) => ({
        name,
        count,
        percentage: ((count / processedInstances.length) * 100).toFixed(1)
      })).sort((a, b) => b.count - a.count)
    };
  }, [activeObjectType, processedInstances]);

  // 4. Filter Creators
  const handleAddFilter = () => {
    if (!newFilterProp) return;
    
    // Check if filter already exists for this property
    const updated = [...activeFilters, {
      propertyId: newFilterProp,
      operator: newFilterOp,
      value: newFilterVal
    }];
    setActiveFilters(updated);
    
    // Reset creators
    setNewFilterProp('');
    setNewFilterVal('');
    setShowFilterCreator(false);
    showToast('info', t('ow.msg.filterAdded'));
  };

  const handleRemoveFilter = (index: number) => {
    const updated = activeFilters.filter((_, idx) => idx !== index);
    setActiveFilters(updated);
    showToast('info', t('ow.msg.filterRemoved'));
  };

  // 5. Saved Searches Manager
  const handleSaveSearch = () => {
    if (!newSearchName.trim() || !activeObjectTypeId) return;

    const newSearch: SavedSearch = {
      id: `search_${Date.now()}`,
      name: newSearchName.trim(),
      objectTypeId: activeObjectTypeId,
      filters: activeFilters,
      sortBy,
      sortOrder
    };

    saveSearches([...savedSearches, newSearch]);
    setNewSearchName('');
    setShowSaveModal(false);
    showToast('success', t('ow.msg.searchSaved').replace('{name}', newSearch.name));
  };

  const handleLoadSavedSearch = (search: SavedSearch) => {
    setActiveObjectTypeId(search.objectTypeId);
    setActiveFilters(search.filters);
    setSortBy(search.sortBy);
    setSortOrder(search.sortOrder);
    showToast('success', t('ow.msg.searchLoaded').replace('{name}', search.name));
  };

  const handleDeleteSavedSearch = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const updated = savedSearches.filter(s => s.id !== id);
    saveSearches(updated);
    showToast('info', t('ow.msg.searchRemoved'));
  };

  // 6. Relational Connection Traversal Parser
  const resolvedRelations = useMemo(() => {
    if (!selectedInstance || !activeObjectType) return [];

    const relations: Array<{
      linkType: LinkType;
      direction: 'forward' | 'reverse';
      otherObjectType: ObjectType;
      instances: any[];
      resolutionUnsupported?: boolean;
    }> = [];

    // Search linkTypes referencing the activeObjectType
    linkTypes.forEach(lt => {
      if (lt.sourceObjectType === activeObjectType.id) {
        // Forward relation (e.g. Flight -> Airport)
        const targetOt = objectTypes.find(o => o.id === lt.targetObjectType);
        if (!targetOt) return;

        const targetInstances = getInstancesOfObjectType(lt.targetObjectType);
        
        let matches: any[] = [];
        if (lt.mapping?.type === 'foreign_key' && lt.mapping.foreignKeyMapping) {
          const sourceVal = selectedInstance[lt.mapping.foreignKeyMapping.sourceKey];
          matches = targetInstances.filter(t => 
            String(t[lt.mapping.foreignKeyMapping!.targetKey]) === String(sourceVal)
          );
        }

        relations.push({
          linkType: lt,
          direction: 'forward',
          otherObjectType: targetOt,
          instances: matches,
          // join_table 无解析端点：空列表是「无法解析」而非「无关联」，UI 侧如实标注
          resolutionUnsupported: lt.mapping?.type === 'join_table'
        });
      } else if (lt.targetObjectType === activeObjectType.id) {
        // Reverse relation (e.g. Airport -> Flights, or Aircraft -> Flights)
        const sourceOt = objectTypes.find(o => o.id === lt.sourceObjectType);
        if (!sourceOt) return;

        const sourceInstances = getInstancesOfObjectType(lt.sourceObjectType);
        
        let matches: any[] = [];
        if (lt.mapping?.type === 'foreign_key' && lt.mapping.foreignKeyMapping) {
          const targetVal = selectedInstance[lt.mapping.foreignKeyMapping.targetKey];
          matches = sourceInstances.filter(s => 
            String(s[lt.mapping.foreignKeyMapping!.sourceKey]) === String(targetVal)
          );
        } else if (lt.mapping?.type === 'join_table' && lt.mapping.joinTableMapping) {
          // 无假真理：join_table 解析需独立后端端点（现不存在）。
          // 此前静默丢弃 → UI 显示「0 关联」误导；改为置 resolutionUnsupported，
          // 由 InstanceDetailPanel 渲染「join_table 解析未接入」标注（空 ≠ 无关联）。
        }

        relations.push({
          linkType: lt,
          direction: 'reverse',
          otherObjectType: sourceOt,
          instances: matches,
          resolutionUnsupported: lt.mapping?.type === 'join_table'
        });
      }
    });

    return relations;
  }, [selectedInstance, activeObjectType, linkTypes, objectTypes, relatedInstanceCache]);

  // Helper to jump to a linked object instance
  const handleJumpToInstance = (otId: string, instId: string) => {
    const ot = objectTypes.find(o => o.id === otId);
    if (!ot) return;
    
    // Jump to the type
    setActiveObjectTypeId(otId);
    
    // Find inst
    const instList = getInstancesOfObjectType(otId);
    const targetInst = instList.find(i => String(i[ot.primaryKey]) === String(instId));
    if (targetInst) {
      setSelectedInstance(targetInst);
      setDetailTab('properties');
      showToast('info', t('ow.msg.jumpToInstance').replace('{name}', ot.displayName).replace('{id}', instId));
    }
  };

  // 7. Actions Executer & Validations with Writeback
  const availableActions = useMemo(() => {
    if (!activeObjectType) return [];
    // Actions where at least one parameter takes an object of current type
    return actionTypes.filter(at => 
      at.parameters.some(p => p.dataType === 'object' && p.objectTypeId === activeObjectType.id)
    );
  }, [actionTypes, activeObjectType]);

  const handleOpenActionModal = (action: ActionType) => {
    setSelectedAction(action);
    setActionError(null);
    
    // Pre-fill target object param
    const objParam = action.parameters.find(p => p.dataType === 'object' && p.objectTypeId === activeObjectType?.id);
    const initialParams: Record<string, string> = {};
    if (objParam && selectedInstance && activeObjectType) {
      initialParams[objParam.id] = selectedInstance[activeObjectType.primaryKey];
    }
    
    // Initialize other params with empty strings
    action.parameters.forEach(p => {
      if (p.id !== objParam?.id) {
        initialParams[p.id] = '';
      }
    });
    
    setActionParams(initialParams);
  };

  /**
   * 动作执行（无假真理整改）：
   *   - 平台不存在动作执行/写回端点（services/ontologyApi 全表面核实：action-types 仅 GET），
   *     旧实现「前端校验一遍 → toast 已执行 → refetch 原数据」是假写回，已删除；
   *   - 校验不再按演示 id（update_flight_status 等）硬编码分支，改为通用的必填参数检查；
   *   - Modal 的执行按钮已置为禁用 + 如实文案；此处为双保险 —— 仅报错、绝不弹成功、不发假写回。
   */
  const handleExecuteAction = () => {
    if (!selectedAction || !activeObjectType || !selectedInstance) return;

    const missing = selectedAction.parameters.filter(
      p => p.isRequired && !String(actionParams[p.id] ?? '').trim()
    );
    if (missing.length > 0) {
      setActionError(t('ow.msg.validationFailed') + missing.map(p => p.displayName).join(', '));
      showToast('error', t('ow.msg.validationFailed') + missing.map(p => p.displayName).join(', '));
      return;
    }

    showToast('error', t('ow.explorer.action_exec_unsupported'));
  };

  return (
    <div className={`h-full flex overflow-hidden ${styles.appBg} relative select-none`}>
      
      {/* LEFT PANEL: Object Selector & Saved Searches */}
      <ExplorerLeftPanel
        objectTypes={objectTypes}
        activeObjectTypeId={activeObjectTypeId}
        instanceData={instanceData}
        dataTotal={dataTotal}
        relatedInstanceCache={relatedInstanceCache}
        savedSearches={savedSearches}
        setActiveObjectTypeId={setActiveObjectTypeId}
        setActiveTab={setActiveTab}
        handleLoadSavedSearch={handleLoadSavedSearch}
        handleDeleteSavedSearch={handleDeleteSavedSearch}
      />

      {/* CENTER & MAIN WORKSPACE */}
      <div className="flex-1 flex flex-col overflow-hidden">
        
        {/* Active Stage Header */}
        {activeObjectType ? (
          <ExplorerStageHeader
            activeObjectType={activeObjectType}
            activeTab={activeTab}
            setActiveTab={setActiveTab}
            activeFilters={activeFilters}
            handleRemoveFilter={handleRemoveFilter}
            showFilterCreator={showFilterCreator}
            setShowFilterCreator={setShowFilterCreator}
            newFilterProp={newFilterProp}
            setNewFilterProp={setNewFilterProp}
            newFilterOp={newFilterOp}
            setNewFilterOp={setNewFilterOp}
            newFilterVal={newFilterVal}
            setNewFilterVal={setNewFilterVal}
            handleAddFilter={handleAddFilter}
            setShowSaveModal={setShowSaveModal}
          />
        ) : null}

        {/* Workspace Central Canvas */}
        <div className="flex-1 overflow-hidden relative">
          
          {/* Welcome view when no activeObjectType selected */}
          {!activeObjectTypeId ? (
            <ExplorerWelcomeView
              objectTypes={objectTypes}
              activeObjectTypeId={activeObjectTypeId}
              instanceData={instanceData}
              dataTotal={dataTotal}
              relatedInstanceCache={relatedInstanceCache}
              setActiveObjectTypeId={setActiveObjectTypeId}
            />
          ) : (
            <div className="h-full flex overflow-hidden">
              
              {/* Work Desk Stage */}
              <div className="flex-1 flex flex-col overflow-hidden">
                {/* 六态·错误态：实例加载失败显式横幅 + 重试（不再伪装「暂无数据」） */}
                {instanceError && (
                  <div className={`m-3 flex items-center justify-between gap-3 rounded-lg border px-3 py-2 text-xs ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
                    <span className="truncate">{t('ow.explorer.instance_load_failed').replace('{error}', instanceError)}</span>
                    <button
                      type="button"
                      onClick={() => setInstanceRetryTick(v => v + 1)}
                      className={`shrink-0 rounded border px-2 py-0.5 font-semibold ${styles.dangerBorder}`}
                    >
                      {t('ontology.retry')}
                    </button>
                  </div>
                )}
                {activeTab === 'table' ? (
                  <ExplorerTableView
                    activeObjectType={activeObjectType}
                    localSearch={localSearch}
                    setLocalSearch={setLocalSearch}
                    dataLoading={dataLoading}
                    allInstances={allInstances}
                    processedInstances={processedInstances}
                    sortBy={sortBy}
                    sortOrder={sortOrder}
                    setSortBy={setSortBy}
                    setSortOrder={setSortOrder}
                    selectedInstance={selectedInstance}
                    setSelectedInstance={setSelectedInstance}
                    setDetailTab={setDetailTab}
                    dataPage={dataPage}
                    setDataPage={setDataPage}
                    dataTotalPages={dataTotalPages}
                  />
                ) : (
                  // ANALYTICS / CHART TAB
                  <ExplorerAnalyticsView
                    analyticsData={analyticsData}
                    processedCount={processedInstances.length}
                    activeFilters={activeFilters}
                    setActiveFilters={setActiveFilters}
                    setActiveTab={setActiveTab}
                    showToast={showToast}
                  />
                )}
              </div>

              {/* DETAILED SLIDE-OVER OR SPLIT PANEL (Right hand side) */}
              {selectedInstance ? (
                <InstanceDetailPanel
                  activeObjectType={activeObjectType}
                  selectedInstance={selectedInstance}
                  setSelectedInstance={setSelectedInstance}
                  detailTab={detailTab}
                  setDetailTab={setDetailTab}
                  availableActions={availableActions}
                  handleOpenActionModal={handleOpenActionModal}
                  resolvedRelations={resolvedRelations}
                  relationsLoading={relatedLoading}
                  handleJumpToInstance={handleJumpToInstance}
                />
              ) : null}

            </div>
          )}

        </div>
      </div>

      {/* Save Search Modal */}
      {showSaveModal && (
        <SaveSearchModal newSearchName={newSearchName} setNewSearchName={setNewSearchName}
          setShowSaveModal={setShowSaveModal} handleSaveSearch={handleSaveSearch} />
      )}

      {/* Execute Action Modal */}
      {selectedAction && (
        <ActionExecutorModal selectedAction={selectedAction} activeObjectType={activeObjectType}
          actionParams={actionParams} setActionParams={setActionParams}
          actionError={actionError} setSelectedAction={setSelectedAction}
          handleExecuteAction={handleExecuteAction} />
      )}

    </div>
  );
}
