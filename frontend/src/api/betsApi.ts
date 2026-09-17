import apiClient from './apiClient';
import type {
  BetResponse,
  PlaceBetRequest,
  ComboBetRequest,
  UpdateBetRequest,
  UpdateComboBetRequest,
  CorrectScoreBetRequest,
  UpdateCorrectScoreBetRequest,
  HandicapBetRequest,
  UpdateHandicapBetRequest,
  SeasonBetStatusResponse,
  ChampionOddsOption,
  TopScorerOddsOption,
  ChampionBetRequest,
  TopScorerBetRequest,
  UpdateChampionBetRequest,
  UpdateTopScorerBetRequest,
} from '../types';

export const placeBet = (request: PlaceBetRequest) =>
  apiClient.post<BetResponse>('/bets', request);

export const placeCombo = (request: ComboBetRequest) =>
  apiClient.post<BetResponse>('/bets/combo', request);

export const placeCorrectScoreBet = (request: CorrectScoreBetRequest) =>
  apiClient.post<BetResponse>('/bets/correct-score', request);

export const editBet = (betId: number, request: UpdateBetRequest) =>
  apiClient.put<BetResponse>(`/bets/${betId}`, request);

export const editCombo = (betId: number, request: UpdateComboBetRequest) =>
  apiClient.put<BetResponse>(`/bets/${betId}/combo`, request);

export const editCorrectScoreBet = (betId: number, request: UpdateCorrectScoreBetRequest) =>
  apiClient.put<BetResponse>(`/bets/${betId}/correct-score`, request);

export const placeHandicapBet = (request: HandicapBetRequest) =>
  apiClient.post<BetResponse>('/bets/handicap', request);

export const editHandicapBet = (betId: number, request: UpdateHandicapBetRequest) =>
  apiClient.put<BetResponse>(`/bets/${betId}/handicap`, request);

export const cancelBet = (betId: number) =>
  apiClient.post<BetResponse>(`/bets/${betId}/cancel`);

export const getMyBets = () =>
  apiClient.get<BetResponse[]>('/bets/my');

export const getMyOpenBets = () =>
  apiClient.get<BetResponse[]>('/bets/my/open');

export const getMyBetHistory = () =>
  apiClient.get<BetResponse[]>('/bets/my/history');

// ─── Season bets ──────────────────────────────────────────────────────────────

export const getSeasonBetStatus = () =>
  apiClient.get<SeasonBetStatusResponse>('/bets/season/status');

export const getChampionOdds = () =>
  apiClient.get<ChampionOddsOption[]>('/bets/season/champion/odds');

export const getTopScorerOdds = (limit = 50) =>
  apiClient.get<TopScorerOddsOption[]>(`/bets/season/top-scorer/odds?limit=${limit}`);

export const placeChampionBet = (request: ChampionBetRequest) =>
  apiClient.post<BetResponse>('/bets/season/champion', request);

export const placeTopScorerBet = (request: TopScorerBetRequest) =>
  apiClient.post<BetResponse>('/bets/season/top-scorer', request);

export const editChampionBet = (betId: number, request: UpdateChampionBetRequest) =>
  apiClient.put<BetResponse>(`/bets/${betId}/season/champion`, request);

export const editTopScorerBet = (betId: number, request: UpdateTopScorerBetRequest) =>
  apiClient.put<BetResponse>(`/bets/${betId}/season/top-scorer`, request);
