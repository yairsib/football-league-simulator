import apiClient from './apiClient';
import type { TeamResponse } from '../types';

export const getTeams = () =>
  apiClient.get<TeamResponse[]>('/teams');

export const getTeamById = (id: number) =>
  apiClient.get<TeamResponse>(`/teams/${id}`);
