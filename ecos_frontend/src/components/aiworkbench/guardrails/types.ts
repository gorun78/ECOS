/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import type { AIPGuardrail } from '../../../types/aiworkbench';

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
