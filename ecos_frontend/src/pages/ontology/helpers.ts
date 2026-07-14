/**
 * OverviewView 共享工具函数与常量
 * @license Apache-2.0
 */

/** 域颜色 Tailwind 类映射 */
export function getDomainColorClasses(color: string) {
  const map: Record<string, { bg: string; text: string; border: string; activeBg: string; dot: string; hoverBg: string; focusRing: string; leftBorder: string }> = {
    blue:      { bg: 'bg-blue-50/70', text: 'text-blue-700', border: 'border-blue-200', activeBg: 'bg-blue-600', dot: 'bg-blue-500', hoverBg: 'hover:bg-blue-50', focusRing: 'focus:ring-blue-400', leftBorder: 'border-l-4 border-l-blue-500' },
    emerald:   { bg: 'bg-emerald-50/70', text: 'text-emerald-700', border: 'border-emerald-200', activeBg: 'bg-emerald-600', dot: 'bg-emerald-500', hoverBg: 'hover:bg-emerald-50', focusRing: 'focus:ring-emerald-400', leftBorder: 'border-l-4 border-l-emerald-500' },
    amber:     { bg: 'bg-amber-50/70', text: 'text-amber-700', border: 'border-amber-200', activeBg: 'bg-amber-600', dot: 'bg-amber-500', hoverBg: 'hover:bg-amber-50', focusRing: 'focus:ring-amber-400', leftBorder: 'border-l-4 border-l-amber-500' },
    purple:    { bg: 'bg-purple-50/70', text: 'text-purple-700', border: 'border-purple-200', activeBg: 'bg-purple-600', dot: 'bg-purple-500', hoverBg: 'hover:bg-purple-50', focusRing: 'focus:ring-purple-400', leftBorder: 'border-l-4 border-l-purple-500' },
    rose:      { bg: 'bg-rose-50/70', text: 'text-rose-700', border: 'border-rose-200', activeBg: 'bg-rose-600', dot: 'bg-rose-500', hoverBg: 'hover:bg-rose-50', focusRing: 'focus:ring-rose-400', leftBorder: 'border-l-4 border-l-rose-500' },
    indigo:    { bg: 'bg-indigo-50/70', text: 'text-indigo-700', border: 'border-indigo-200', activeBg: 'bg-indigo-600', dot: 'bg-indigo-500', hoverBg: 'hover:bg-indigo-50', focusRing: 'focus:ring-indigo-400', leftBorder: 'border-l-4 border-l-indigo-500' },
    slate:     { bg: 'bg-slate-50', text: 'text-slate-700', border: 'border-slate-300', activeBg: 'bg-slate-700', dot: 'bg-slate-500', hoverBg: 'hover:bg-slate-100/50', focusRing: 'focus:ring-slate-400', leftBorder: 'border-l-4 border-l-slate-400' },
  };
  return map[color] || map.slate;
}

/** 模拟审计日志 */
export const auditLogs = [
  { id: '1', time: '10分钟前', user: 'guorongxiao@gmail.com', action: '发布了本体版本 v1.2.4', detail: '同步了 飞行员 (Pilot) 接口绑定以及新增了多对多资质表关联映射。', type: 'publish' },
  { id: '2', time: '1小时前', user: 'guorongxiao@gmail.com', action: '更新对象属性', detail: '为 航班 (Flight) 对象新增了「计划起飞时间」和「计划到达时间」高精度时间戳。', type: 'edit' },
  { id: '3', time: '5小时前', user: 'guorongxiao@gmail.com', action: '创建操作类型', detail: '完成了「安排飞机适航维护 (scheduleMaintenanceCheck)」后台原子副作用函数定义。', type: 'create' },
  { id: '4', time: '昨天', user: 'System Agent', action: '智能数据源检查', detail: '确认原始数据集 ds_airport_geolocations 格式契合地理定位 (locatable) 接口契约。', type: 'check' }
];
