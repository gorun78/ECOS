/**
 * MetadataTab — 对象元数据配置 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Layers } from 'lucide-react';
import type { MetadataTabProps } from './types';

export default function MetadataTab({
  objectType,
  handleMetaChange,
  domains,
  interfaces,
}: MetadataTabProps) {
  return (
    <div className="space-y-6 max-w-2xl">
      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-slate-700">对象显示名称 (Display Name)</label>
          <input
            type="text"
            value={objectType.displayName}
            onChange={e => handleMetaChange('displayName', e.target.value)}
            className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
          />
        </div>
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-slate-700">API 标识名 (API Name)</label>
          <input
            type="text"
            value={objectType.apiName}
            onChange={e => handleMetaChange('apiName', e.target.value)}
            className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
          />
        </div>
      </div>

      <div className="space-y-1.5">
        <label className="text-xs font-semibold text-slate-700">对象描述信息 (Description)</label>
        <textarea
          value={objectType.description}
          onChange={e => handleMetaChange('description', e.target.value)}
          className="w-full h-20 px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
          placeholder="为此实体对象输入详细的业务背景和使用建议。"
        />
      </div>

      <div className="space-y-1.5 border-t border-gray-100 pt-4">
        <label className="text-xs font-semibold text-slate-700">划分业务域 (Ontology Domain Hierarchy)</label>
        <select
          value={objectType.domainId || ''}
          onChange={e => handleMetaChange('domainId', e.target.value || undefined)}
          className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
        >
          <option value="">-- 未分类 (不属于任何业务域) --</option>
          {domains.map(d => (
            <option key={d.id} value={d.id}>{d.displayName}</option>
          ))}
        </select>
        <p className="text-[10px] text-slate-400">选择该实体所属的顶级业务大类。可前往"本体全景与总览"页面创建和维护更多业务域分级。</p>
      </div>

      <div className="grid grid-cols-2 gap-4 border-t border-gray-100 pt-4">
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-slate-700">标题展示属性 (Title Property)</label>
          <select
            value={objectType.titleProperty}
            onChange={e => handleMetaChange('titleProperty', e.target.value)}
            className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
          >
            {objectType.properties.map(p => (
              <option key={p.id} value={p.id}>{p.displayName} ({p.apiName})</option>
            ))}
          </select>
          <p className="text-[10px] text-slate-400">用于在图谱、搜索结果和关系列表里展示此对象的默认文本标题。</p>
        </div>

        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-slate-700">运营状态 (Status)</label>
          <select
            value={objectType.status}
            onChange={e => handleMetaChange('status', e.target.value)}
            className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
          >
            <option value="DRAFT">草稿 (DRAFT)</option>
            <option value="ACTIVE">启用 (ACTIVE)</option>
            <option value="DEPRECATED">弃用 (DEPRECATED)</option>
          </select>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-slate-700">界面显示图标 (Lucide Icon)</label>
          <select
            value={objectType.icon}
            onChange={e => handleMetaChange('icon', e.target.value)}
            className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
          >
            <option value="Plane">Plane (飞机)</option>
            <option value="Building2">Building2 (机场/楼宇)</option>
            <option value="Navigation">Navigation (导航/指针)</option>
            <option value="UserSquare2">UserSquare2 (飞行员/人员)</option>
            <option value="Database">Database (数据源)</option>
            <option value="ShieldAlert">ShieldAlert (安全性)</option>
            <option value="FileText">FileText (文档)</option>
            <option value="Heart">Heart (健康度)</option>
          </select>
        </div>

        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-slate-700">视觉主题颜色 (Color Theme)</label>
          <select
            value={objectType.color}
            onChange={e => handleMetaChange('color', e.target.value)}
            className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
          >
            <option value="border-blue-500 bg-blue-50 text-blue-700">皇家蓝 (Blue)</option>
            <option value="border-emerald-500 bg-emerald-50 text-emerald-700">活力绿 (Emerald)</option>
            <option value="border-purple-500 bg-purple-50 text-purple-700">星空紫 (Purple)</option>
            <option value="border-orange-500 bg-orange-50 text-orange-700">温暖橘 (Orange)</option>
            <option value="border-red-500 bg-red-50 text-red-700">警戒红 (Red)</option>
            <option value="border-slate-500 bg-slate-50 text-slate-700">中性灰 (Slate)</option>
          </select>
        </div>
      </div>

      {/* Implements Interfaces */}
      <div className="space-y-2 border-t border-gray-100 pt-4">
        <label className="text-xs font-semibold text-slate-700 block">实现的接口 (Implements Interfaces)</label>
        <div className="flex flex-wrap gap-2">
          {interfaces.map(intf => {
            const isChecked = (objectType.interfaces || []).includes(intf.id);
            return (
              <label key={intf.id} className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs cursor-pointer select-none transition-colors ${
                isChecked ? 'bg-blue-50 border-blue-300 text-blue-700' : 'bg-white border-gray-200 text-slate-600 hover:bg-gray-50'
              }`}>
                <input
                  type="checkbox"
                  checked={isChecked}
                  onChange={(e) => {
                    const current = objectType.interfaces || [];
                    const updated = e.target.checked
                      ? [...current, intf.id]
                      : current.filter(id => id !== intf.id);
                    handleMetaChange('interfaces', updated);
                  }}
                  className="sr-only"
                />
                <Layers size={13} />
                <span>{intf.displayName}</span>
              </label>
            );
          })}
        </div>
        <p className="text-[10px] text-slate-400">对象类型通过实现特定接口，将继承该接口规范的一系列属性和行为逻辑。</p>
      </div>
    </div>
  );
}
