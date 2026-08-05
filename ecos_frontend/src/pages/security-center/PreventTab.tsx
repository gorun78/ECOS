/**
 * PreventTab — 事前预防（用户管理 / 角色管理 / 权限定义）
 * 直接复用 UserManagement 组件
 * @license Apache-2.0
 */

import React from 'react';
import UserManagement from '../UserManagement';

export default function PreventTab() {
  return <UserManagement />;
}
