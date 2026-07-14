import React, { useState, useEffect } from 'react';
import { Tag, Loader2, RefreshCw, Check, XCircle, Sparkles } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';

interface ClassificationNode {
  id: string;
  name: string;
  domain: string;
  level: number;
  parentId?: string;
  assetCount?: number;
  status: 'active' | 'draft' | 'deprecated';
}

const DEMO_CLASSIFICATIONS: ClassificationNode[] = [
  { id: 'c1', name: '数据资产', domain: 'data', level: 0, assetCount: 12, status: 'active' },
  { id: 'c1-1', name: '物理表', domain: 'data', level: 1, parentId: 'c1', assetCount: 8, status: 'active' },
  { id: 'c1-2', name: '视图', domain: 'data', level: 1, parentId: 'c1', assetCount: 4, status: 'active' },
  { id: 'c2', name: '安全策略', domain: 'security', level: 0, assetCount: 6, status: 'active' },
  { id: 'c2-1', name: '访问控制', domain: 'security', level: 1, parentId: 'c2', assetCount: 3, status: 'active' },
  { id: 'c2-2', name: '脱敏规则', domain: 'security', level: 1, parentId: 'c2', assetCount: 3, status: 'active' },
  { id: 'c3', name: '本体对象', domain: 'ontology', level: 0, assetCount: 5, status: 'active' },
  { id: 'c3-1', name: '业务对象', domain: 'ontology', level: 1, parentId: 'c3', assetCount: 3, status: 'active' },
  { id: 'c3-2', name: '算子动作', domain: 'ontology', level: 1, parentId: 'c3', assetCount: 2, status: 'draft' },
];

export default function ClassificationTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [classifications, setClassifications] = useState<ClassificationNode[]>(DEMO_CLASSIFICATIONS);
  const [isLoading, setIsLoading] = useState(false);
  const [classifyingAssetId, setClassifyingAssetId] = useState('');
  const [classifyResult, setClassifyResult] = useState<any>(null);
  const [isClassifying, setIsClassifying] = useState(false);

  const handleAutoClassify = async () => {
    if (!classifyingAssetId.trim()) return;
    setIsClassifying(true);
    try {
      const result = await knowledgeApi.classifyAsset(classifyingAssetId);
      setClassifyResult(result);
    } catch {
      setClassifyResult({ error: true, message: locale === 'zh' ? '分类失败，后端未响应' : 'Classify failed' });
    } finally {
      setIsClassifying(false);
    }
  };

  const rootNodes = classifications.filter(c => c.level === 0);
  const getChildren = (parentId: string) => classifications.filter(c => c.parentId === parentId);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between border-b border-slate-200 pb-3">
        <div className="space-y-1">
          <h2 className="text-sm font-black text-slate-800 flex items-center gap-2">
            <Tag size={16} className="text-purple-600" />
            {locale === 'zh' ? '分类体系 (Classification Taxonomy)' : 'Classification Taxonomy'}
          </h2>
          <p className="text-xs text-slate-500">
            {locale === 'zh' ? '构建资产分类层级，支持自动分类与人工标注' : 'Build asset classification hierarchy with auto-classify and manual tagging'}
          </p>
        </div>
        <button
          onClick={() => { setIsLoading(true); setTimeout(() => setIsLoading(false), 500); }}
          className="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs"
        >
          {isLoading ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
          {locale === 'zh' ? '刷新' : 'Refresh'}
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-3">
          {rootNodes.map(root => (
            <div key={root.id} className="bg-white border border-slate-200 rounded-xl shadow-xs overflow-hidden">
              <div className="px-4 py-3 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className={`w-2.5 h-2.5 rounded-full ${
                    root.domain === 'data' ? 'bg-blue-500' :
                    root.domain === 'security' ? 'bg-rose-500' : 'bg-indigo-500'
                  }`} />
                  <span className="font-black text-xs text-slate-800">{root.name}</span>
                  <span className="text-[9px] text-slate-400 font-mono">{root.assetCount} assets</span>
                </div>
                <span className={`px-1.5 py-0.5 text-[8px] font-bold rounded ${
                  root.status === 'active' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'
                }`}>{root.status}</span>
              </div>
              <div className="divide-y divide-slate-100">
                {getChildren(root.id).map(child => (
                  <div key={child.id} className="px-4 py-2.5 flex items-center justify-between hover:bg-slate-50/50 transition">
                    <div className="flex items-center gap-2 pl-4">
                      <span className="w-1.5 h-1.5 rounded-full bg-slate-300" />
                      <span className="font-bold text-xs text-slate-700">{child.name}</span>
                      <span className="text-[9px] text-slate-400 font-mono">{child.assetCount} assets</span>
                    </div>
                    <span className={`px-1.5 py-0.5 text-[8px] font-bold rounded ${
                      child.status === 'active' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'
                    }`}>{child.status}</span>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>

        <div className="space-y-4">
          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-3">
            <h3 className="font-bold text-xs text-slate-800 flex items-center gap-1.5">
              <Sparkles size={13} className="text-purple-600" />
              {locale === 'zh' ? '自动分类' : 'Auto Classify'}
            </h3>
            <p className="text-[10px] text-slate-500">
              {locale === 'zh' ? '输入资产ID，系统自动推荐分类归属' : 'Enter asset ID for auto-classification'}
            </p>
            <input
              value={classifyingAssetId}
              onChange={e => setClassifyingAssetId(e.target.value)}
              placeholder={locale === 'zh' ? '资产ID (如: ds_flights_clean)' : 'Asset ID'}
              className="w-full px-3 py-1.5 border border-slate-200 rounded-lg text-xs"
            />
            <button
              onClick={handleAutoClassify}
              disabled={isClassifying}
              className="w-full py-1.5 bg-purple-600 hover:bg-purple-700 text-white font-bold rounded-lg text-xs cursor-pointer flex items-center justify-center gap-1.5 disabled:opacity-60"
            >
              {isClassifying ? <Loader2 size={12} className="animate-spin" /> : <Sparkles size={12} />}
              {locale === 'zh' ? '执行分类' : 'Classify'}
            </button>
            {classifyResult && (
              <div className={`p-3 rounded-lg text-[10px] ${
                classifyResult.error ? 'bg-rose-50 text-rose-700' : 'bg-emerald-50 text-emerald-700'
              }`}>
                {classifyResult.error
                  ? classifyResult.message
                  : JSON.stringify(classifyResult, null, 2)
                }
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
