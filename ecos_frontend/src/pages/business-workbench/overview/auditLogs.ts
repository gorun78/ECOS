/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Mock enterprise life cycle log, extracted verbatim from OverviewView.tsx.

export const auditLogs = [
  { id: '1', time: '10分钟前', user: 'guorongxiao@gmail.com', action: '发布了本体版本 v1.2.4', detail: '同步了 飞行员 (Pilot) 接口绑定以及新增了多对多资质表关联映射。', type: 'publish' },
  { id: '2', time: '1小时前', user: 'guorongxiao@gmail.com', action: '更新对象属性', detail: '为 航班 (Flight) 对象新增了「计划起飞时间」和「计划到达时间」高精度时间戳。', type: 'edit' },
  { id: '3', time: '5小时前', user: 'guorongxiao@gmail.com', action: '创建操作类型', detail: '完成了「安排飞机适航维护 (scheduleMaintenanceCheck)」后台原子副作用函数定义。', type: 'create' },
  { id: '4', time: '昨天', user: 'System Agent', action: '智能数据源检查', detail: '确认原始数据集 ds_airport_geolocations 格式契合地理定位 (locatable) 接口契约。', type: 'check' }
];
