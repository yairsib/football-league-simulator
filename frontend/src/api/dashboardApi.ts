import apiClient from './apiClient';
import type { DashboardSummaryResponse } from '../types';

export const getDashboardSummary = () =>
  apiClient.get<DashboardSummaryResponse>('/dashboard/summary');
