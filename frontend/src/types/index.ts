export type MatchStatus = 'SCHEDULED' | 'BETTING_OPEN' | 'IN_PROGRESS' | 'FINISHED';
export type RoundStatus = 'NOT_STARTED' | 'OPEN_FOR_BETS' | 'FINISHED';
export type BetStatus = 'OPEN' | 'WON' | 'LOST' | 'CANCELLED';
export type BetType = 'SINGLE' | 'COMBO';
export type BetMarket = 'MATCH_RESULT' | 'CORRECT_SCORE' | 'HANDICAP' | 'CHAMPION' | 'TOP_SCORER';
export type HandicapSelection = 'HOME_MINUS_ONE' | 'HANDICAP_DRAW' | 'AWAY_PLUS_ONE';
export type Prediction = 'HOME_WIN' | 'DRAW' | 'AWAY_WIN';
export type Role = 'USER' | 'ADMIN';
export type Position = 'GK' | 'CB' | 'LB' | 'RB' | 'DM' | 'CM' | 'AM' | 'LW' | 'RW' | 'ST';
export type PlayerStatus = 'FIT' | 'INJURED' | 'SUSPENDED';
export type WeatherCondition = 'CLEAR' | 'RAIN' | 'WIND' | 'HOT' | 'COLD' | 'STORM';
export type MatchEventType = 'GOAL' | 'RED_CARD' | 'SUBSTITUTION';

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  balance: number;
  role: Role;
}

export interface AuthResponse {
  token?: string;
  user?: UserResponse;
  adminVerificationRequired?: boolean;
  adminVerificationToken?: string;
}

export interface TeamResponse {
  id: number;
  name: string;
  skillLevel: number;
  morale: number;
  /** Persisted starting values for this season (set at import, restored by Season Reset). */
  baselineSkillLevel: number | null;
  baselineMorale: number | null;
  injuries: number;
  played: number;
  wins: number;
  draws: number;
  losses: number;
  goalsFor: number;
  goalsAgainst: number;
  goalDifference: number;
  points: number;
}

export interface RoundResponse {
  id: number;
  roundNumber: number;
  status: RoundStatus;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface MatchResponse {
  id: number;
  roundNumber: number;
  homeTeamId: number;
  homeTeamName: string;
  awayTeamId: number;
  awayTeamName: string;
  homeGoals: number | null;
  awayGoals: number | null;
  status: MatchStatus;
  bettingOpen: boolean;
  homeOdds: number | null;
  drawOdds: number | null;
  awayOdds: number | null;
  /** European Handicap -1 market, frozen together with the 1X2 odds when betting opens. */
  handicapHomeMinusOneOdds: number | null;
  handicapDrawOdds: number | null;
  handicapAwayPlusOneOdds: number | null;
  matchDate: string | null;
  weatherCondition: WeatherCondition | null;
  weatherImpact: number | null;
}

export interface PlayerResponse {
  id: number;
  fullName: string;
  position: Position;
  jerseyNumber: number | null;
  rating: number;
  status: PlayerStatus;
  injuryDescription: string | null;
  injuredUntilRound: number | null;
  injuryMatchesRemaining: number;
  injuryMatchesTotal: number | null;
  starter: boolean;
  substitute: boolean;
  lineupOrder: number | null;
  goals: number;
  assists: number;
  redCards: number;
  suspensionMatchesRemaining: number;
}

export interface PlayerStatsResponse {
  playerId: number;
  fullName: string;
  teamId: number;
  teamName: string;
  position: Position;
  jerseyNumber: number | null;
  rating: number;
  goals: number;
  assists: number;
  redCards: number;
  status: PlayerStatus;
  injuryMatchesRemaining: number;
  suspensionMatchesRemaining: number;
}

export interface MatchEventResponse {
  id: number;
  matchId: number;
  teamId: number;
  teamName: string;
  playerId: number | null;
  playerName: string | null;
  assistPlayerId: number | null;
  assistPlayerName: string | null;
  playerInId: number | null;
  playerInName: string | null;
  playerOutId: number | null;
  playerOutName: string | null;
  eventType: MatchEventType;
  minute: number;
  description: string;
}

export interface TeamSquadResponse {
  teamId: number;
  teamName: string;
  formation: string;
  players: PlayerResponse[];
}

export interface ReplacedPlayerInfo {
  originalPlayerName: string;
  replacementPlayerName: string;
  reason: string;
  position: Position;
}

export interface TeamLineupResponse {
  teamId: number;
  teamName: string;
  formation: string;
  starters: PlayerResponse[];
  substitutes: PlayerResponse[];
  unavailablePlayers: PlayerResponse[];
  effectiveLineupGenerated: boolean;
  lineupWarnings: string[];
  replacedPlayers: ReplacedPlayerInfo[];
}

export interface MatchLineupsResponse {
  matchId: number;
  homeTeamName: string;
  awayTeamName: string;
  homeFormation: string;
  awayFormation: string;
  homeStarters: PlayerResponse[];
  homeSubstitutes: PlayerResponse[];
  homeUnavailablePlayers: PlayerResponse[];
  awayStarters: PlayerResponse[];
  awaySubstitutes: PlayerResponse[];
  awayUnavailablePlayers: PlayerResponse[];
  homeEffectiveLineupGenerated: boolean;
  homeLineupWarnings: string[];
  homeReplacedPlayers: ReplacedPlayerInfo[];
  awayEffectiveLineupGenerated: boolean;
  awayLineupWarnings: string[];
  awayReplacedPlayers: ReplacedPlayerInfo[];
  weatherCondition: WeatherCondition | null;
  weatherImpact: number | null;
}

export interface TeamSquadSummary {
  teamName: string;
  playersCount: number;
  startersCount: number;
  substitutesCount: number;
  unavailableCount: number;
  hasExactlyOneStartingGoalkeeper: boolean;
  hasAtLeast7Substitutes: boolean;
  hasExactly11Starters: boolean;
  duplicateJerseyNumbers: number[];
  duplicateLineupOrders: number[];
  invalidStarterSubstituteOverlap: boolean;
  missingPositions: string[];
  status: 'COMPLETE' | 'PARTIAL' | 'MISSING';
}

export interface DataValidationResponse {
  valid: boolean;
  errors: string[];
  warnings: string[];
  squadSummaries: TeamSquadSummary[];
}

export interface DataImportResponse {
  teamsProcessed: number;
  playersProcessed: number;
  warnings: string[];
  errors: string[];
}

export interface LeagueTableEntry {
  position: number;
  teamId: number;
  teamName: string;
  played: number;
  wins: number;
  draws: number;
  losses: number;
  goalsFor: number;
  goalsAgainst: number;
  goalDifference: number;
  points: number;
}

export interface BetSelectionResponse {
  matchId: number;
  homeTeamName: string;
  awayTeamName: string;
  prediction: Prediction;
  odds: number;
}

export interface HandicapOddsOption {
  selection: HandicapSelection;
  label: string;
  odds: number;
}

export interface HandicapBetRequest {
  matchId: number;
  selection: HandicapSelection;
  amount: number;
}

export interface UpdateHandicapBetRequest {
  amount: number;
  selection: HandicapSelection;
}

export interface BetResponse {
  id: number;
  betType: BetType;
  matchId: number | null;
  homeTeamName: string | null;
  awayTeamName: string | null;
  prediction: Prediction | null;
  market: BetMarket | null;
  predictedHomeGoals: number | null;
  predictedAwayGoals: number | null;
  handicapSelection: HandicapSelection | null;
  displayLabel: string | null;
  amount: number;
  odds: number | null;
  totalOdds: number | null;
  possibleWin: number;
  status: BetStatus;
  profit: number | null;
  createdAt: string;
  settledAt: string | null;
  selections: BetSelectionResponse[];
  selectedTeamId: number | null;
  selectedTeamName: string | null;
  selectedPlayerId: number | null;
  selectedPlayerName: string | null;
}

export interface PlaceBetRequest {
  matchId: number;
  prediction: Prediction;
  amount: number;
}

export interface BetSelectionRequest {
  matchId: number;
  prediction: Prediction;
}

export interface ComboBetRequest {
  amount: number;
  selections: BetSelectionRequest[];
}

export interface UpdateBetRequest {
  amount: number;
  prediction: Prediction;
}

export interface UpdateComboBetRequest {
  amount: number;
  selections: BetSelectionRequest[];
}

export interface CorrectScoreOddsOption {
  homeGoals: number;
  awayGoals: number;
  odds: number;
}

export interface CorrectScoreBetRequest {
  matchId: number;
  homeGoals: number;
  awayGoals: number;
  amount: number;
}

export interface UpdateCorrectScoreBetRequest {
  amount: number;
  homeGoals: number;
  awayGoals: number;
}

export interface SeasonBetStatusResponse {
  open: boolean;
  reason: string;
}

export interface ChampionOddsOption {
  teamId: number;
  teamName: string;
  skillLevel: number;
  odds: number;
}

export interface TopScorerOddsOption {
  playerId: number;
  fullName: string;
  teamId: number;
  teamName: string;
  position: Position;
  rating: number;
  odds: number;
}

export interface ChampionBetRequest {
  teamId: number;
  amount: number;
}

export interface TopScorerBetRequest {
  playerId: number;
  amount: number;
}

export interface UpdateChampionBetRequest {
  teamId: number;
  amount: number;
}

export interface UpdateTopScorerBetRequest {
  playerId: number;
  amount: number;
}

export interface DashboardSummaryResponse {
  // User/betting summary
  balance: number;
  openBetsCount: number;
  settledBetsCount: number;
  wonBetsCount: number;
  lostBetsCount: number;
  cancelledBetsCount: number;
  totalStakedOpen: number;
  totalProfitSettled: number;
  potentialWinningsOpen: number;
  recentBets: BetResponse[];

  // Season/match summary
  totalRounds: number;
  currentRoundNumber: number | null;
  nextRoundNumberToPlay: number | null;
  finishedRoundsCount: number;
  totalMatches: number;
  finishedMatchesCount: number;
  nextOpenRoundNumber: number | null;
  seasonBetsOpen: boolean;
  seasonBetsStatusReason: string;

  // League highlights
  leaderTeamName: string | null;
  leaderTeamId: number | null;
  leaderPoints: number | null;
  topScorerName: string | null;
  topScorerTeam: string | null;
  topScorerGoals: number | null;
  topAssisterName: string | null;
  topAssisterTeam: string | null;
  topAssisterAssists: number | null;
  redCardLeaderName: string | null;
  redCardLeaderTeam: string | null;
  redCardLeaderCards: number | null;

  // Season completion
  seasonComplete: boolean;

  // Quick actions
  canPlaceSeasonBets: boolean;
  canPlaceMatchBets: boolean;
  hasOpenBets: boolean;
  hasFinishedMatches: boolean;
  adminCanGenerateSchedule: boolean;
  adminCanOpenNextRound: boolean;
  adminCanSimulateNextRound: boolean;
}

export interface SeasonResetRequest {
  confirm: boolean;
  regenerateSchedule: boolean;
  resetUserBalances: boolean;
}

export interface SeasonResetResponse {
  matchEventsDeleted: number;
  betsDeleted: number;
  openBetsRefunded: number;
  matchesReset: number;
  roundsReset: number;
  teamsReset: number;
  playersReset: number;
  teamBaselinesRestored: number;
  usersBalanceReset: number;
  scheduleRegenerated: boolean;
  message: string;
}

export interface AdminOverviewResponse {
  // Data state
  teamsCount: number;
  playersCount: number;
  completeSquadsCount: number;
  missingSquadsCount: number;
  scheduleGenerated: boolean;
  /** True only in a clean pre-season state; league data import is rejected (409) otherwise. */
  leagueDataImportAllowed: boolean;
  totalRounds: number;
  totalMatches: number;

  // Round state
  currentRound: number | null;
  nextRoundToOpen: number | null;
  nextRoundToSimulate: number | null;
  roundsByStatus: Record<string, number>;

  // Betting state
  openBetsCount: number;
  settledBetsCount: number;
  cancelledBetsCount: number;
  seasonBetsOpen: boolean;
  seasonBetsStatusReason: string;

  // Recommended actions
  recommendedActions: string[];
}
