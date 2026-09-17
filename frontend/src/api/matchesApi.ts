import apiClient from './apiClient';
import type { CorrectScoreOddsOption, HandicapOddsOption, MatchEventResponse, MatchLineupsResponse, MatchResponse, RoundResponse } from '../types';

export const getRounds = () =>
  apiClient.get<RoundResponse[]>('/rounds');

export const getMatchesByRound = (roundNumber: number) =>
  apiClient.get<MatchResponse[]>(`/matches/round/${roundNumber}`);

export const getAllMatches = () =>
  apiClient.get<MatchResponse[]>('/matches');

export const getMatchLineups = (matchId: number) =>
  apiClient.get<MatchLineupsResponse>(`/matches/${matchId}/lineups`);

export const getMatchEvents = (matchId: number) =>
  apiClient.get<MatchEventResponse[]>(`/matches/${matchId}/events`);

export const getCorrectScoreOdds = (matchId: number) =>
  apiClient.get<CorrectScoreOddsOption[]>(`/matches/${matchId}/correct-score-odds`);

export const getHandicapOdds = (matchId: number) =>
  apiClient.get<HandicapOddsOption[]>(`/matches/${matchId}/handicap-odds`);
