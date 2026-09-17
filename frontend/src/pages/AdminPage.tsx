import { useEffect, useState } from 'react';
import { getRounds } from '../api/matchesApi';
import { getMatchesByRound } from '../api/matchesApi';
import * as adminApi from '../api/adminApi';
import { useAuth } from '../context/AuthContext';
import type { RoundResponse, MatchResponse, DataValidationResponse, DataImportResponse, TeamSquadSummary, AdminOverviewResponse, SeasonResetResponse } from '../types';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import SeasonResetModal from '../components/SeasonResetModal';
import TeamCrest from '../components/TeamCrest';
import Icon from '../components/Icon';

function squadSummaryWarnings(s: TeamSquadSummary): string[] {
  const warnings: string[] = [];
  if (s.status === 'MISSING') return ['squad is missing'];
  if (!s.hasExactly11Starters) warnings.push(`${s.startersCount} starters`);
  if (!s.hasExactlyOneStartingGoalkeeper) warnings.push('no single starting GK');
  if (!s.hasAtLeast7Substitutes) warnings.push(`${s.substitutesCount} substitutes`);
  if (s.invalidStarterSubstituteOverlap) warnings.push('starter/substitute overlap');
  if (s.duplicateJerseyNumbers.length > 0) warnings.push(`duplicate jersey numbers: ${s.duplicateJerseyNumbers.join(', ')}`);
  if (s.duplicateLineupOrders.length > 0) warnings.push(`duplicate lineup orders: ${s.duplicateLineupOrders.join(', ')}`);
  if (s.missingPositions.length > 0) warnings.push(`missing positions: ${s.missingPositions.join(', ')}`);
  return warnings;
}

export default function AdminPage() {
  const { refreshUser } = useAuth();
  const [rounds, setRounds] = useState<RoundResponse[]>([]);
  const [selectedRound, setSelectedRound] = useState<number>(1);
  const [matches, setMatches] = useState<MatchResponse[]>([]);
  const [loadingRounds, setLoadingRounds] = useState(true);
  const [loadingMatches, setLoadingMatches] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [validationResult, setValidationResult] = useState<DataValidationResponse | null>(null);
  const [importResult, setImportResult] = useState<DataImportResponse | null>(null);
  const [overview, setOverview] = useState<AdminOverviewResponse | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(true);
  const [showResetModal, setShowResetModal] = useState(false);
  const [resetResult, setResetResult] = useState<SeasonResetResponse | null>(null);

  const loadOverview = () => {
    setOverviewLoading(true);
    adminApi.getAdminOverview()
      .then((res) => setOverview(res.data))
      .catch(() => { setOverview(null); })
      .finally(() => setOverviewLoading(false));
  };

  useEffect(() => {
    getRounds()
      .then((res) => {
        setRounds(res.data);
        if (res.data.length > 0) setSelectedRound(res.data[0].roundNumber);
      })
      .catch(() => setError('Failed to load rounds.'))
      .finally(() => setLoadingRounds(false));
    loadOverview();
  }, []);

  useEffect(() => {
    if (!selectedRound) return;
    setLoadingMatches(true);
    getMatchesByRound(selectedRound)
      .then((res) => setMatches(res.data))
      .catch(() => {})
      .finally(() => setLoadingMatches(false));
  }, [selectedRound]);

  const run = async (action: () => Promise<string>) => {
    setError('');
    setMessage('');
    setBusy(true);
    try {
      const msg = await action();
      setMessage(msg);
      const [roundsRes, matchesRes] = await Promise.all([
        getRounds(),
        getMatchesByRound(selectedRound),
      ]);
      setRounds(roundsRes.data);
      setMatches(matchesRes.data);
      loadOverview();
      // Simulating a round/match can settle the logged-in admin's own bets (if they
      // have any) — refresh the shared balance so the Navbar never goes stale here.
      refreshUser().catch(() => {});
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message ?? 'Action failed.');
      } else {
        setError('Action failed.');
      }
    } finally {
      setBusy(false);
    }
  };

  const handleValidateLeagueData = async () => {
    setError('');
    setMessage('');
    setImportResult(null);
    setValidationResult(null);
    setBusy(true);
    try {
      const res = await adminApi.validateLeagueData();
      setValidationResult(res.data);
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message ?? 'Validation failed.');
      } else {
        setError('Validation failed.');
      }
    } finally {
      setBusy(false);
    }
  };

  const handleImportLeagueData = async () => {
    setError('');
    setMessage('');
    setValidationResult(null);
    setImportResult(null);
    setBusy(true);
    try {
      const res = await adminApi.importLeagueData();
      setImportResult(res.data);
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message ?? 'Import failed.');
      } else {
        setError('Import failed.');
      }
    } finally {
      setBusy(false);
    }
  };

  if (loadingRounds) return <Loading />;

  const previousRoundsFinished = (roundNumber: number) =>
    rounds.filter((r) => r.roundNumber < roundNumber).every((r) => r.status === 'FINISHED');

  const selectedRoundData = rounds.find((r) => r.roundNumber === selectedRound);
  const blockedReason = !previousRoundsFinished(selectedRound)
    ? 'Previous rounds must be finished before starting this round.'
    : null;

  const canOpenBetting = !blockedReason && selectedRoundData?.status === 'NOT_STARTED';
  const canSimulateRound = !blockedReason && selectedRoundData?.status === 'OPEN_FOR_BETS';

  return (
    <div className="page page-admin" data-testid="admin-page">
      <h1>Admin Panel</h1>

      {message && <div className="success-message" data-testid="admin-message">{message}</div>}
      {error && <div data-testid="admin-error"><ErrorMessage message={error} /></div>}

      <section className="admin-section" data-testid="admin-overview">
        <div className="section-header">
          <span className="section-header-icon"><Icon name="shield" size={16} /></span>
          <h2>System Overview</h2>
        </div>

        {overviewLoading ? (
          <Loading message="Loading overview..." />
        ) : overview !== null ? (
          <>
            {overview.recommendedActions.length > 0 && (
              <div className="admin-recommended-actions" data-testid="admin-recommended-actions">
                <strong>Recommended next action{overview.recommendedActions.length > 1 ? 's' : ''}:</strong>
                <ul>
                  {overview.recommendedActions.map((a, i) => <li key={i}>{a}</li>)}
                </ul>
              </div>
            )}

            <div className="admin-overview-grid">
              <div className="admin-overview-card" data-testid="admin-overview-data">
                <h3>Data State</h3>
                <ul className="dashboard-info-list">
                  <li>Teams: <strong>{overview.teamsCount}</strong></li>
                  <li>Players: <strong>{overview.playersCount}</strong></li>
                  <li>Complete squads: <strong>{overview.completeSquadsCount}</strong></li>
                  <li>Missing squads: <strong>{overview.missingSquadsCount}</strong></li>
                  <li>Schedule: <strong>{overview.scheduleGenerated ? `${overview.totalRounds} rounds / ${overview.totalMatches} matches` : 'Not generated'}</strong></li>
                </ul>
              </div>

              <div className="admin-overview-card" data-testid="admin-overview-rounds">
                <h3>Round State</h3>
                <ul className="dashboard-info-list">
                  {overview.currentRound !== null && (
                    <li>Current round: <strong>Round {overview.currentRound}</strong></li>
                  )}
                  {overview.nextRoundToOpen !== null && (
                    <li>Next to open: <strong>Round {overview.nextRoundToOpen}</strong></li>
                  )}
                  {overview.nextRoundToSimulate !== null && (
                    <li>Next to simulate: <strong>Round {overview.nextRoundToSimulate}</strong></li>
                  )}
                  {Object.entries(overview.roundsByStatus).map(([status, count]) => (
                    <li key={status}>{status}: <strong>{count}</strong></li>
                  ))}
                  {Object.keys(overview.roundsByStatus).length === 0 && (
                    <li className="muted">No rounds yet</li>
                  )}
                </ul>
              </div>

              <div className="admin-overview-card" data-testid="admin-overview-bets">
                <h3>Betting State</h3>
                <ul className="dashboard-info-list">
                  <li>Open bets: <strong>{overview.openBetsCount}</strong></li>
                  <li>Settled bets: <strong>{overview.settledBetsCount}</strong></li>
                  <li>Cancelled bets: <strong>{overview.cancelledBetsCount}</strong></li>
                  <li>
                    Season bets:{' '}
                    <strong className={overview.seasonBetsOpen ? 'text-won' : 'text-lost'}>
                      {overview.seasonBetsOpen ? 'Open' : 'Locked'}
                    </strong>
                    {' '}— {overview.seasonBetsStatusReason}
                  </li>
                </ul>
              </div>
            </div>
          </>
        ) : (
          <p className="muted">Overview unavailable.</p>
        )}
      </section>

      <section className="admin-section">
        <h2>Setup</h2>
        <div className="admin-buttons">
          <button
            className="btn-primary"
            data-testid="seed-teams-btn"
            disabled={busy}
            onClick={() => run(async () => {
              const res = await adminApi.seedTeams();
              return res.data.message;
            })}
          >
            Seed Teams
          </button>
          <button
            className="btn-primary"
            data-testid="generate-schedule-btn"
            disabled={busy}
            onClick={() => run(async () => {
              const res = await adminApi.generateSchedule();
              return res.data.message;
            })}
          >
            Generate Schedule
          </button>
        </div>
      </section>

      <section className="admin-section">
        <h2>Real League Data (Israeli Premier League 2025/2026)</h2>
        <p className="muted">
          Validates and imports <code>ligat-haal-2025-2026.json</code> from the backend's bundled
          data file. Import upserts teams and players — it never invents player data.
        </p>
        <div className="admin-buttons">
          <button
            className="btn-primary"
            data-testid="validate-league-data-btn"
            disabled={busy}
            onClick={handleValidateLeagueData}
          >
            Validate League Data
          </button>
          <button
            className="btn-primary"
            data-testid="import-league-data-btn"
            disabled={busy || (overview !== null && !overview.leagueDataImportAllowed)}
            title={overview && !overview.leagueDataImportAllowed
              ? 'Import is only allowed before the season starts — reset the season first'
              : undefined}
            onClick={handleImportLeagueData}
          >
            Import League Data
          </button>
        </div>
        {overview && !overview.leagueDataImportAllowed && (
          <p className="muted" data-testid="import-locked-message" style={{ marginTop: '0.5rem' }}>
            League data import is locked because the season has started (betting opened, results, events, or bets exist).
            Use Reset Season to return to a pre-season state before importing again.
          </p>
        )}

        {validationResult && (
          <div data-testid="league-data-validation-result" className="data-result">
            <p>
              Validation result:{' '}
              <strong>{validationResult.valid ? 'Valid' : 'Invalid'}</strong>
            </p>
            {validationResult.errors.length > 0 && (
              <div data-testid="league-data-validation-errors">
                <h3>Errors</h3>
                <ul>
                  {validationResult.errors.map((e, i) => <li key={i}>{e}</li>)}
                </ul>
              </div>
            )}
            {validationResult.warnings.length > 0 && (
              <div data-testid="league-data-validation-warnings">
                <h3>Warnings</h3>
                <ul>
                  {validationResult.warnings.map((w, i) => <li key={i}>{w}</li>)}
                </ul>
              </div>
            )}

            {validationResult.squadSummaries.length > 0 && (
              <div data-testid="squad-summary-table">
                <h3>Squad Completeness Summary</h3>
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Team</th>
                      <th>Players</th>
                      <th>Starters</th>
                      <th>Subs</th>
                      <th>Status</th>
                      <th>Warnings</th>
                    </tr>
                  </thead>
                  <tbody>
                    {validationResult.squadSummaries.map((s) => (
                      <tr key={s.teamName} data-testid={`squad-summary-row-${s.teamName}`}>
                        <td>{s.teamName}</td>
                        <td>{s.playersCount}</td>
                        <td>{s.startersCount}</td>
                        <td>{s.substitutesCount}</td>
                        <td>
                          <span className={`status-badge ${s.status.toLowerCase()}`} data-testid={`squad-status-${s.teamName}`}>
                            {s.status}
                          </span>
                        </td>
                        <td>{squadSummaryWarnings(s).join('; ') || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {importResult && (
          <div data-testid="league-data-import-result" className="data-result">
            <p>
              Import summary: <strong>{importResult.teamsProcessed} teams</strong> and{' '}
              <strong>{importResult.playersProcessed} players</strong> processed.
            </p>
            {importResult.errors.length > 0 && (
              <div data-testid="league-data-import-errors">
                <h3>Errors</h3>
                <ul>
                  {importResult.errors.map((e, i) => <li key={i}>{e}</li>)}
                </ul>
              </div>
            )}
            {importResult.warnings.length > 0 && (
              <div data-testid="league-data-import-warnings">
                <h3>Warnings</h3>
                <ul>
                  {importResult.warnings.map((w, i) => <li key={i}>{w}</li>)}
                </ul>
              </div>
            )}
          </div>
        )}
      </section>

      <section className="admin-section">
        <h2>Round Actions</h2>
        <div className="form-group">
          <label>Select Round</label>
          <select
            data-testid="round-select"
            value={selectedRound}
            onChange={(e) => setSelectedRound(Number(e.target.value))}
          >
            {rounds.map((r) => (
              <option key={r.id} value={r.roundNumber}>
                Round {r.roundNumber} — {r.status}
              </option>
            ))}
          </select>
        </div>

        {blockedReason && <ErrorMessage message={blockedReason} />}

        <div className="admin-buttons">
          <button
            className="btn-primary"
            data-testid="open-betting-btn"
            disabled={busy || !canOpenBetting}
            title={!canOpenBetting ? (blockedReason ?? `Round ${selectedRound} betting cannot be opened (status: ${selectedRoundData?.status}).`) : undefined}
            onClick={() => run(async () => {
              await adminApi.openBettingForRound(selectedRound);
              return `Betting opened for Round ${selectedRound}.`;
            })}
          >
            Open Betting for Round {selectedRound}
          </button>
          <button
            className="btn-danger"
            data-testid="simulate-round-btn"
            disabled={busy || !canSimulateRound}
            title={!canSimulateRound ? (blockedReason ?? `Round ${selectedRound} cannot be simulated (status: ${selectedRoundData?.status}).`) : undefined}
            onClick={() => run(async () => {
              await adminApi.simulateRound(selectedRound);
              return `Round ${selectedRound} simulated.`;
            })}
          >
            Simulate Round {selectedRound}
          </button>
        </div>
      </section>

      <section className="admin-section danger-zone" data-testid="danger-zone">
        <h2><Icon name="lock" size={16} /> Danger Zone</h2>
        <p className="muted">
          These actions affect the entire season and cannot be undone.
          Admin and user accounts are never deleted or modified.
        </p>

        {resetResult && (
          <div className="success-message" data-testid="reset-result">
            <strong>Season reset complete.</strong>
            <ul style={{ marginTop: '0.5rem', paddingLeft: '1.5rem' }}>
              <li>{resetResult.betsDeleted} bet(s) deleted</li>
              {resetResult.openBetsRefunded > 0 && <li>{resetResult.openBetsRefunded} open bet(s) refunded</li>}
              <li>{resetResult.matchEventsDeleted} match event(s) deleted</li>
              <li>{resetResult.teamsReset} team(s) reset</li>
              <li>{resetResult.playersReset} player(s) reset</li>
              <li>{resetResult.teamBaselinesRestored} team skill/morale baseline(s) restored</li>
              {resetResult.scheduleRegenerated
                ? <li>Schedule regenerated ({resetResult.roundsReset} old round(s) removed)</li>
                : <li>{resetResult.matchesReset} match(es) and {resetResult.roundsReset} round(s) reset in place</li>}
              {resetResult.usersBalanceReset > 0 && <li>{resetResult.usersBalanceReset} user balance(s) reset to 1000</li>}
            </ul>
          </div>
        )}

        <div className="admin-buttons">
          <button
            className="btn-danger"
            data-testid="reset-season-btn"
            onClick={() => { setResetResult(null); setShowResetModal(true); }}
          >
            Reset Season
          </button>
        </div>
      </section>

      <section className="admin-section">
        <h2>Round {selectedRound} Matches</h2>
        {loadingMatches ? (
          <Loading message="Loading matches..." />
        ) : matches.length === 0 ? (
          <p className="muted">No matches for this round.</p>
        ) : (
          <div className="matches-list">
            {matches.map((m) => (
              <div key={m.id} className={`match-card status-${m.status.toLowerCase()}`}>
                <div className="match-teams">
                  <span className="match-team-side">
                    <TeamCrest name={m.homeTeamName} size="sm" />
                    <span className="team home">{m.homeTeamName}</span>
                  </span>
                  <span className="match-score">
                    {m.status === 'FINISHED' ? `${m.homeGoals} - ${m.awayGoals}` : 'vs'}
                  </span>
                  <span className="match-team-side">
                    <TeamCrest name={m.awayTeamName} size="sm" />
                    <span className="team away">{m.awayTeamName}</span>
                  </span>
                </div>
                <span className={`status-badge ${m.status.toLowerCase()}`}>{m.status}</span>
                {m.status !== 'FINISHED' && m.status !== 'SCHEDULED' && (
                  <button
                    className="btn-danger btn-sm"
                    disabled={busy}
                    onClick={() => run(async () => {
                      await adminApi.simulateMatch(m.id);
                      return `Match ${m.homeTeamName} vs ${m.awayTeamName} simulated.`;
                    })}
                  >
                    Simulate
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </section>
      {showResetModal && (
        <SeasonResetModal
          onCancel={() => setShowResetModal(false)}
          onSuccess={(result) => {
            setShowResetModal(false);
            setResetResult(result);
            // Reload rounds and overview to reflect clean state
            setMessage('');
            setError('');
            getRounds()
              .then((res) => {
                setRounds(res.data);
                if (res.data.length > 0) setSelectedRound(res.data[0].roundNumber);
              })
              .catch(() => {});
            loadOverview();
            // A reset may change the logged-in admin's own balance (refund / reset-to-1000):
            // refresh the shared user state so the Navbar never goes stale.
            refreshUser().catch(() => {});
          }}
        />
      )}
    </div>
  );
}
