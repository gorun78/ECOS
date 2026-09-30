/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Shared Icon helper + domain types for AiGuardrailsView (H6-T4 split).

import React from 'react';
import * as Icons from 'lucide-react';

export const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

export interface Proposal {
  id: string;
  actionId: string;
  actionName: string;
  agentId: string;
  agentName: string;
  payload: Record<string, string>;
  proposedBy: string;
  proposedAt: string;
  status: 'pending' | 'approved' | 'rejected';
  validated: boolean;
  validationErrors: string[];
  rbacRoleRequired: string;
}

export interface PhysicalFlight {
  flight_id: string;
  flight_num: string;
  dep_airport: string;
  arr_airport: string;
  scheduled_departure: string;
  actual_departure: string;
  pilot_id: string;
  pilot_name: string;
  status: string;
  delay_minutes: number;
}

export interface PhysicalPilot {
  pilot_id: string;
  pilot_name: string;
  ssn_number: string;
  base_salary: number;
  hours_flown: number;
  licence_rating: string;
}
