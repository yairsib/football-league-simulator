import apiClient from './apiClient';
import type { LeagueTableEntry } from '../types';

export const getLeagueTable = () =>
  apiClient.get<LeagueTableEntry[]>('/league/table');
