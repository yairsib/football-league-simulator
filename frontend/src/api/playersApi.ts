import apiClient from './apiClient';
import type { PlayerResponse, PlayerStatsResponse } from '../types';

export const getPlayer = (playerId: number) =>
  apiClient.get<PlayerResponse>(`/players/${playerId}`);

export const getTeamPlayerStats = (teamId: number) =>
  apiClient.get<PlayerResponse[]>(`/teams/${teamId}/players/stats`);

export const getLeaders = (stat: 'goals' | 'assists' | 'redCards', limit = 20) =>
  apiClient.get<PlayerStatsResponse[]>('/players/leaders', { params: { stat, limit } });
