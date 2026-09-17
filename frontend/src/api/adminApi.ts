import apiClient from './apiClient';
import type { AdminOverviewResponse, DataImportResponse, DataValidationResponse, MatchResponse, RoundResponse, SeasonResetRequest, SeasonResetResponse } from '../types';

export const seedTeams = () =>
  apiClient.post<{ message: string }>('/admin/seed');

export const generateSchedule = () =>
  apiClient.post<{ message: string }>('/admin/generate-schedule');

export const openBettingForRound = (roundNumber: number) =>
  apiClient.post<MatchResponse[]>(`/admin/rounds/${roundNumber}/open-betting`);

export const simulateMatch = (matchId: number) =>
  apiClient.post<MatchResponse>(`/admin/matches/${matchId}/simulate`);

export const simulateRound = (roundNumber: number) =>
  apiClient.post<RoundResponse>(`/admin/rounds/${roundNumber}/simulate`);

export const getAdminOverview = () =>
  apiClient.get<AdminOverviewResponse>('/admin/overview');

export const validateLeagueData = () =>
  apiClient.get<DataValidationResponse>('/admin/data/validate');

export const importLeagueData = () =>
  apiClient.post<DataImportResponse>('/admin/data/import');

export const resetSeason = (req: SeasonResetRequest) =>
  apiClient.post<SeasonResetResponse>('/admin/season/reset', req);
