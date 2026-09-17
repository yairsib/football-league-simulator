import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyBets, cancelBet } from '../api/betsApi';
import type { BetResponse } from '../types';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import EditBetModal from '../components/EditBetModal';
import { useAuth } from '../context/AuthContext';
import TeamCrest from '../components/TeamCrest';

const describeBet = (b: BetResponse): string => {
  if (b.betType === 'COMBO') {
    return b.selections
      .map((s) => `${s.homeTeamName} vs ${s.awayTeamName} (${s.prediction === 'HOME_WIN' ? s.homeTeamName : s.prediction === 'AWAY_WIN' ? s.awayTeamName : 'Draw'})`)
      .join(' + ');
  }
  if (b.market === 'CHAMPION') return 'Season Champion';
  if (b.market === 'TOP_SCORER') return 'Top Scorer';
  return `${b.homeTeamName} vs ${b.awayTeamName}`;
};

const marketDisplay = (b: BetResponse): string => {
  if (b.betType === 'COMBO') return 'Match Result';
  if (b.market === 'CORRECT_SCORE') return 'Correct Score';
  if (b.market === 'HANDICAP') return 'Handicap';
  if (b.market === 'CHAMPION') return 'Champion';
  if (b.market === 'TOP_SCORER') return 'Top Scorer';
  return 'Match Result';
};

const predictionDisplay = (b: BetResponse): string => {
  if (b.betType === 'COMBO') return `${b.selections.length} selections`;
  if (b.market === 'CORRECT_SCORE') return `${b.predictedHomeGoals}-${b.predictedAwayGoals}`;
  if (b.market === 'HANDICAP') return b.displayLabel ?? '—';
  if (b.market === 'CHAMPION') return b.selectedTeamName ?? '—';
  if (b.market === 'TOP_SCORER') return `${b.selectedPlayerName ?? '—'} (${b.selectedTeamName ?? '—'})`;
  if (!b.prediction) return '—';
  if (b.prediction === 'HOME_WIN') return b.homeTeamName ?? 'Home';
  if (b.prediction === 'AWAY_WIN') return b.awayTeamName ?? 'Away';
  return 'Draw';
};

function MatchCell({ bet }: { bet: BetResponse }) {
  if (bet.betType === 'COMBO' || bet.market === 'CHAMPION' || bet.market === 'TOP_SCORER') {
    return <>{describeBet(bet)}</>;
  }
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
      <TeamCrest name={bet.homeTeamName ?? ''} size="sm" />
      <span>{bet.homeTeamName} vs {bet.awayTeamName}</span>
    </span>
  );
}

const oddsDisplay = (b: BetResponse): string => {
  const value = b.betType === 'COMBO' ? b.totalOdds : b.odds;
  return value !== null && value !== undefined ? Number(value).toFixed(2) : '—';
};

export default function MyBetsPage() {
  const { refreshUser } = useAuth();
  const [bets, setBets] = useState<BetResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  // Non-blocking notice for a failed *background* refresh (poll/focus). The page keeps showing
  // the last known bets; the notice clears itself on the next successful refresh.
  const [refreshWarning, setRefreshWarning] = useState('');
  const [actionError, setActionError] = useState('');
  const [editingBet, setEditingBet] = useState<BetResponse | null>(null);
  const [cancellingId, setCancellingId] = useState<number | null>(null);

  /**
   * Loads the user's bets. The initial page load may show the blocking error state; a
   * background refresh (poll, focus, after an action) must never replace the page — it only
   * surfaces a transient notice, and any error is cleared as soon as a refresh succeeds.
   */
  const loadBets = (background = false) => {
    getMyBets()
      .then((res) => {
        setBets(res.data);
        setError('');
        setRefreshWarning('');
      })
      .catch(() => {
        if (background) {
          setRefreshWarning('Could not refresh your bets just now — showing the last known state, retrying automatically.');
        } else {
          setError('Failed to load bets.');
        }
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadBets(false);
  }, []);

  // Settlement happens server-side when the admin simulates a round. While this page is open,
  // poll lightly (only when the tab is visible) and refresh on focus so settled rows and the
  // shared navbar balance converge without a manual reload or a Dashboard visit.
  useEffect(() => {
    const sync = () => {
      if (document.visibilityState !== 'visible') return;
      loadBets(true);
      refreshUser().catch(() => {});
    };
    const interval = window.setInterval(sync, 10000);
    const onVisibility = () => { if (document.visibilityState === 'visible') sync(); };
    window.addEventListener('focus', sync);
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      window.clearInterval(interval);
      window.removeEventListener('focus', sync);
      document.removeEventListener('visibilitychange', onVisibility);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleCancel = async (bet: BetResponse) => {
    setActionError('');
    if (!window.confirm('Cancel this bet? Your stake will be refunded in full.')) {
      return;
    }
    setCancellingId(bet.id);
    try {
      await cancelBet(bet.id);
      await refreshUser();
      loadBets(true);
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setActionError(axiosErr.response?.data?.message ?? 'Failed to cancel bet.');
      } else {
        setActionError('Failed to cancel bet.');
      }
    } finally {
      setCancellingId(null);
    }
  };

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  const open = bets.filter((b) => b.status === 'OPEN');
  const settled = bets.filter((b) => b.status !== 'OPEN');

  return (
    <div className="page page-mybets">
      <h1>My Bets</h1>

      {actionError && <ErrorMessage message={actionError} />}
      {refreshWarning && (
        <p className="muted" role="status" data-testid="my-bets-refresh-warning" style={{ marginBottom: '0.75rem' }}>
          {refreshWarning}
        </p>
      )}

      {bets.length === 0 && (
        <div className="empty-state" data-testid="my-bets-empty-state">
          <p>No bets yet — pick your first market.</p>
          <p>
            Head to <Link to="/matches">Matches</Link> or <Link to="/season-bets">Season Bets</Link> to get started.
          </p>
        </div>
      )}

      <section>
        <h2>Open Bets ({open.length})</h2>
        {open.length === 0 ? (
          <p className="muted">No open bets.</p>
        ) : (
          <div className="table-wrap"><table className="data-table">
            <thead>
              <tr>
                <th>Type</th>
                <th>Market</th>
                <th>Match / Selections</th>
                <th>Prediction</th>
                <th>Amount</th>
                <th>Odds</th>
                <th>Possible Win</th>
                <th>Placed</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {open.map((b) => (
                <tr key={b.id} data-testid={`open-bet-row-${b.id}`}>
                  <td><span className={`status-badge ${b.betType.toLowerCase()}`} data-testid={`bet-type-${b.id}`}>{b.betType}</span></td>
                  <td data-testid={`bet-market-${b.id}`}>{marketDisplay(b)}</td>
                  <td><MatchCell bet={b} /></td>
                  <td>{predictionDisplay(b)}</td>
                  <td>${Number(b.amount).toFixed(2)}</td>
                  <td><span className="odds-chip">{oddsDisplay(b)}</span></td>
                  <td>${Number(b.possibleWin).toFixed(2)}</td>
                  <td>{new Date(b.createdAt).toLocaleDateString()}</td>
                  <td>
                    <div className="bet-row-actions">
                      <button
                        type="button"
                        className="btn-secondary btn-sm"
                        data-testid={`edit-bet-btn-${b.id}`}
                        onClick={() => setEditingBet(b)}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="btn-danger btn-sm"
                        data-testid={`cancel-bet-btn-${b.id}`}
                        disabled={cancellingId === b.id}
                        onClick={() => handleCancel(b)}
                      >
                        {cancellingId === b.id ? 'Cancelling...' : 'Cancel'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table></div>
        )}
      </section>

      <section className="section-gap">
        <h2>Settled Bets ({settled.length})</h2>
        {settled.length === 0 ? (
          <p className="muted">No settled bets yet.</p>
        ) : (
          <div className="table-wrap"><table className="data-table">
            <thead>
              <tr>
                <th>Type</th>
                <th>Market</th>
                <th>Match / Selections</th>
                <th>Prediction</th>
                <th>Amount</th>
                <th>Odds</th>
                <th>Possible Win</th>
                <th>Result</th>
                <th>Profit</th>
                <th>Settled</th>
              </tr>
            </thead>
            <tbody>
              {settled.map((b) => (
                <tr key={b.id} data-testid={`settled-bet-row-${b.id}`} className={b.status === 'WON' ? 'row-won' : 'row-lost'}>
                  <td><span className={`status-badge ${b.betType.toLowerCase()}`} data-testid={`bet-type-${b.id}`}>{b.betType}</span></td>
                  <td data-testid={`bet-market-${b.id}`}>{marketDisplay(b)}</td>
                  <td><MatchCell bet={b} /></td>
                  <td>{predictionDisplay(b)}</td>
                  <td>${Number(b.amount).toFixed(2)}</td>
                  <td><span className="odds-chip">{oddsDisplay(b)}</span></td>
                  <td>${Number(b.possibleWin).toFixed(2)}</td>
                  <td><span className={`status-badge ${b.status.toLowerCase()}`} data-testid={`bet-status-${b.id}`}>{b.status}</span></td>
                  <td data-testid={`bet-profit-${b.id}`}>{b.profit !== null ? `$${Number(b.profit).toFixed(2)}` : '—'}</td>
                  <td>{b.settledAt ? new Date(b.settledAt).toLocaleDateString() : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table></div>
        )}
      </section>

      {editingBet && (
        <EditBetModal
          bet={editingBet}
          onClose={() => setEditingBet(null)}
          onSuccess={loadBets}
        />
      )}
    </div>
  );
}
