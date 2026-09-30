/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ActionType } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';

interface ValidationTabProps {
  actionType: ActionType;
  newValName: string;
  setNewValName: (value: string) => void;
  newValExpression: string;
  setNewValExpression: (value: string) => void;
  newValError: string;
  setNewValError: (value: string) => void;
  handleAddValidation: () => void;
  handleRemoveValidation: (valId: string) => void;
}

export default function ValidationTab({
  actionType,
  newValName,
  setNewValName,
  newValExpression,
  setNewValExpression,
  newValError,
  setNewValError,
  handleAddValidation,
  handleRemoveValidation
}: ValidationTabProps) {
  const { styles } = useTheme();

  return (
    <div className="space-y-6">
      <p className={`text-xs ${styles.cardTextMuted}`}>
        定义执行操作前的拦截限制条件。只有当验证表达式 (Validation Expression) 计算结果为真 (True) 时，该操作才允许被提交到本体。
      </p>

      <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4`}>
        <h4 className={`text-xs font-semibold ${styles.cardText}`}>新建验证安全规则</h4>
        <div className="grid grid-cols-3 gap-4">
          <div className="space-y-1">
            <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>验证项名称</label>
            <input
              type="text"
              placeholder="如：状态合法性校验"
              value={newValName}
              onChange={e => setNewValName(e.target.value)}
              className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:outline-hidden`}
            />
          </div>
          <div className="space-y-1 col-span-2">
            <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>验证公式/表达式 (Logic Expression)</label>
            <input
              type="text"
              placeholder="如：parameter.new_status_param IN [&quot;ON_TIME&quot;, &quot;DELAYED&quot;]"
              value={newValExpression}
              onChange={e => setNewValExpression(e.target.value)}
              className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} font-mono focus:outline-hidden`}
            />
          </div>
        </div>
        <div className="space-y-1">
          <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>验证不通过时的报错警告信息 (Error Message)</label>
          <input
            type="text"
            placeholder="如：状态代码错误，航班状态必须设定为合法选项。"
            value={newValError}
            onChange={e => setNewValError(e.target.value)}
            className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:outline-hidden`}
          />
        </div>
        <button
          onClick={handleAddValidation}
          className="bg-blue-600 hover:bg-blue-700 text-white text-xs px-4 py-1.5 rounded font-medium transition-colors flex items-center gap-1.5"
        >
          <LucideIcon name="Shield" size={13} />
          添加拦截验证规则
        </button>
      </div>

      <div className="space-y-4">
        <h4 className={`text-xs font-semibold ${styles.cardText}`}>已生效的验证列表 ({actionType.validationRules.length})</h4>
        {actionType.validationRules.length === 0 ? (
          <div className={`text-center py-8 border border-dashed ${styles.sidebarBorder} rounded-lg ${styles.cardTextMuted} text-xs`}>
            暂无拦截验证规则，该操作在调用时无入参安全性限制。
          </div>
        ) : (
          <div className="space-y-3">
            {actionType.validationRules.map(val => (
              <div key={val.id} className={`p-4 border ${styles.cardBorder} rounded-lg ${styles.cardBg} shadow-3xs flex items-start justify-between`}>
                <div className="space-y-2">
                  <div className="flex items-center gap-2">
                    <span className="p-1 rounded-full bg-emerald-50 text-emerald-600">
                      <LucideIcon name="ShieldCheck" size={14} />
                    </span>
                    <span className={`text-xs font-semibold ${styles.cardText}`}>{val.displayName}</span>
                  </div>
                  <div className={`font-mono text-[10px] ${styles.appBg} ${styles.cardTextMuted} px-2 py-1 rounded border ${styles.divider}`}>
                    {val.expression}
                  </div>
                  <div className="text-[10px] text-red-500 font-medium">
                    <strong>警告文案:</strong> {val.errorMessage}
                  </div>
                </div>
                <button
                  onClick={() => handleRemoveValidation(val.id)}
                  className={`p-1 ${styles.cardTextMuted} opacity-70 hover:opacity-100 hover:text-red-500`}
                  title="删除规则"
                >
                  <LucideIcon name="Trash2" size={14} />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
