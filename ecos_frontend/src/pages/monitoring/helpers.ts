/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  Cpu,
  Server,
  Zap,
  Database,
  Activity,
  Radio,
  BarChart3,
  Settings,
  Layers,
  Box,
} from "lucide-react";

export const ICON_MAP: Record<string, React.ComponentType<any>> = {
  Cpu, Server, Zap, Database, Activity, Radio, BarChart3,
};

// ── Device type icons ──────────────────────────────────────
export const DEVICE_TYPE_ICON: Record<string, React.ComponentType<any>> = {
  pump: Activity,
  motor: Zap,
  valve: Settings,
  sensor: Radio,
  conveyor: Layers,
  default: Box,
};
