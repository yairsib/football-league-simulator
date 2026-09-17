import { useEffect, useState, FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getDashboardSummary } from '../api/dashboardApi';
import type { DashboardSummaryResponse, BetResponse } from '../types';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import TeamCrest from '../components/TeamCrest';
import Icon from '../components/Icon';

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'response' in err) {
    const axiosErr = err as { response?: { data?: { message?: string } } };
    return axiosErr.response?.data?.message ?? fallback;
  }
  return fallback;
}

function RecentBetRow({ bet }: { bet: BetResponse }) {
  const marketLabel = (() => {
    if (bet.betType === 'COMBO') return 'Combo';
    if (bet.market === 'CORRECT_SCORE') return 'Correct Score';
    if (bet.market === 'HANDICAP') return 'Handicap';
    if (bet.market === 'CHAMPION') return 'Champion';
    if (bet.market === 'TOP_SCORER') return 'Top Scorer';
    return 'Match Result';
  })();

  const selectionLabel = (() => {
    if (bet.betType === 'COMBO') return `${bet.selections.length} selections`;
    if (bet.market === 'CORRECT_SCORE') return `${bet.predictedHomeGoals}-${bet.predictedAwayGoals}`;
    if (bet.market === 'HANDICAP') return bet.displayLabel ?? '—';
    if (bet.market === 'CHAMPION') return bet.selectedTeamName ?? '—';
    if (bet.market === 'TOP_SCORER') return bet.selectedPlayerName ?? '—';
    if (bet.prediction === 'HOME_WIN') return bet.homeTeamName ?? 'Home';
    if (bet.prediction === 'AWAY_WIN') return bet.awayTeamName ?? 'Away';
    if (bet.prediction === 'DRAW') return 'Draw';
    return '—';
  })();

  return (
    <tr>
      <td>{marketLabel}</td>
      <td>{selectionLabel}</td>
      <td><span className="odds-chip">{Number(bet.betType === 'COMBO' ? bet.totalOdds : bet.odds).toFixed(2)}</span></td>
      <td>${Number(bet.amount).toFixed(2)}</td>
      <td><span className={`status-badge ${bet.status.toLowerCase()}`}>{bet.status}</span></td>
    </tr>
  );
}

function nextActionMessage(s: DashboardSummaryResponse): string | null {
  if (s.seasonComplete) {
    return 'Season complete! Explore the final standings and top performers.';
  }
  if (s.canPlaceSeasonBets && s.totalRounds > 0 && s.finishedRoundsCount === 0) {
    return 'Season markets are open — lock in your Champion and Top Scorer picks before kickoff.';
  }
  if (s.nextOpenRoundNumber !== null && s.openBetsCount === 0) {
    return `Round ${s.nextOpenRoundNumber} markets are live — build your slip.`;
  }
  if (s.hasOpenBets) {
    return `Tracking ${s.openBetsCount} open bet${s.openBetsCount !== 1 ? 's' : ''} — good luck.`;
  }
  if (!s.seasonBetsOpen && s.totalRounds > 0) {
    return 'Season markets are locked — Round 1 is already underway.';
  }
  if (s.totalRounds === 0) {
    return 'No schedule yet — the admin needs to kick things off.';
  }
  return null;
}

function EditProfileCard() {
  const { user, updateProfile } = useAuth();
  const [username, setUsername] = useState(user?.username ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setSaving(true);
    try {
      await updateProfile(username, email);
      setSuccess('Profile updated successfully.');
    } catch (err: unknown) {
      setError(extractErrorMessage(err, 'Profile update failed.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card" data-testid="edit-profile-card">
      <h2>Edit Profile</h2>
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label>Username</label>
          <input
            type="text"
            data-testid="profile-username-input"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </div>
        <div className="form-group">
          <label>Email</label>
          <input
            type="email"
            data-testid="profile-email-input"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        {error && <ErrorMessage message={error} />}
        {success && <p className="muted" data-testid="profile-update-success">{success}</p>}
        <button type="submit" data-testid="profile-save-btn" disabled={saving} className="btn-primary btn-full">
          {saving ? 'Saving...' : 'Save Changes'}
        </button>
      </form>
    </div>
  );
}

export default function DashboardPage() {
  const { user, refreshUser } = useAuth();
  const [summary, setSummary] = useState<DashboardSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    // Balance is always read from the shared AuthContext user (single source of truth —
    // same value the Navbar displays), so refresh it here too: visiting the Dashboard
    // should never show a balance that's out of sync with the rest of the app (e.g. after
    // an admin simulation settled this user's own bets while they were on another page).
    Promise.all([getDashboardSummary(), refreshUser()])
      .then(([res]) => setSummary(res.data))
      .catch(() => setError('Failed to load dashboard data.'))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (loading) return <Loading />;

  const s = summary;

  const heroClass = s?.seasonComplete ? 'hero-card season-complete' : 'hero-card';

  return (
    <div className="page page-dashboard">
      {error && <ErrorMessage message={error} />}

      {/* ── Hero / Season Status ─────────────────────────────── */}
      <div className={heroClass} data-testid="dashboard-season-status">
        <div className="hero-league">
          <Icon name="shield" size={13} /> Israeli Premier League 2025/26
        </div>
        <div className="hero-title">
          {s?.seasonComplete
            ? 'Season Complete'
            : s?.totalRounds === 0
            ? 'Pre-Season'
            : s?.currentRoundNumber
            ? `Round ${s.currentRoundNumber} in progress`
            : `Round ${s?.nextRoundNumberToPlay ?? '—'} upcoming`}
        </div>
        <div className="hero-tagline">Your matchday control center — form, markets, and results in one place.</div>

        <div className="hero-meta">
          {s && s.totalRounds > 0 && (
            <>
              <span className={`status-badge ${s.seasonBetsOpen ? 'betting_open' : 'finished'}`}>
                Season Bets: {s.seasonBetsOpen ? 'Open' : 'Locked'}
              </span>
              <span className="muted">·</span>
              <span>
                {s.finishedRoundsCount} / {s.totalRounds} rounds finished
              </span>
              <span className="muted">·</span>
              <span>
                {s.finishedMatchesCount} / {s.totalMatches} matches played
              </span>
            </>
          )}
          {s?.totalRounds === 0 && (
            <span className="muted">No schedule generated yet.</span>
          )}
        </div>

        {s?.seasonComplete && (
          <p className="text-won" data-testid="season-complete-label" style={{ marginBottom: '0.75rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
            <Icon name="trophy" size={18} /> All {s.totalRounds} rounds finished — final standings are in!
          </p>
        )}

        {s && (() => {
          const msg = nextActionMessage(s);
          return msg ? (
            <div className="dashboard-action-banner" data-testid="dashboard-next-action" style={{ marginBottom: '1rem' }}>
              {msg}
            </div>
          ) : null;
        })()}

        <div className="hero-cta">
          <Link to="/matches" className="btn-primary">View Matches</Link>
          <Link to="/season-bets" className="btn-secondary">Season Bets</Link>
          <Link to="/league" className="btn-secondary">League Table</Link>
        </div>
      </div>

      {/* ── Stat cards ─────────────────────────────────────── */}
      <div className="dashboard-grid" data-testid="dashboard-summary-cards">
        <div className="stat-card" data-testid="dashboard-balance-card">
          <div className="stat-card-label">Balance</div>
          {/* Reads the shared AuthContext user, same source as the Navbar — never a
              second, independently-fetched balance that could drift out of sync. */}
          <div className="balance-large">${Number(user?.balance ?? 0).toFixed(2)}</div>
        </div>

        <div className="stat-card" data-testid="dashboard-open-bets-card">
          <div className="stat-card-label">Open Bets</div>
          <div className="stat-card-value" style={{ color: 'var(--primary)' }}>{s?.openBetsCount ?? 0}</div>
          {s && s.openBetsCount > 0 && (
            <div className="stat-card-sub">
              Staked: ${Number(s.totalStakedOpen).toFixed(2)} &nbsp;·&nbsp; To Win: ${Number(s.potentialWinningsOpen).toFixed(2)}
            </div>
          )}
        </div>

        <div className="stat-card" data-testid="dashboard-profit-card">
          <div className="stat-card-label">Profit / Loss</div>
          <div className={`stat-card-value ${s && Number(s.totalProfitSettled) >= 0 ? 'text-won' : 'text-lost'}`}>
            {s ? (Number(s.totalProfitSettled) >= 0 ? '+' : '') + '$' + Math.abs(Number(s.totalProfitSettled)).toFixed(2) : '$0.00'}
          </div>
          {s && s.settledBetsCount > 0 && (
            <div className="stat-card-sub">{s.wonBetsCount}W / {s.lostBetsCount}L ({s.settledBetsCount} settled)</div>
          )}
        </div>

        <div className="stat-card" data-testid="dashboard-potential-card">
          <div className="stat-card-label">Potential Return</div>
          <div className="stat-card-value" style={{ color: 'var(--accent)' }}>
            ${Number(s?.potentialWinningsOpen ?? 0).toFixed(2)}
          </div>
          {s && s.openBetsCount > 0 && (
            <div className="stat-card-sub">from {s.openBetsCount} open bet{s.openBetsCount !== 1 ? 's' : ''}</div>
          )}
        </div>
      </div>

      <div className="dashboard-lower">
        {/* B. League Highlights */}
        {s && (s.leaderTeamName || s.topScorerName || s.topAssisterName || s.redCardLeaderName) && (
          <div className="card" data-testid="dashboard-highlights">
            <div className="section-header">
              <span className="section-header-icon"><Icon name="crown" size={16} /></span>
              <h2>League Highlights</h2>
            </div>
            <ul className="dashboard-info-list">
              {s.leaderTeamName && (
                <li style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <TeamCrest name={s.leaderTeamName} size="sm" />
                  League leader: <strong>{s.leaderTeamName}</strong> — {s.leaderPoints} pts
                </li>
              )}
              {s.topScorerName && (
                <li style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Icon name="goal" size={15} className="icon-muted" />
                  Top scorer: <strong>{s.topScorerName}</strong> ({s.topScorerTeam}) — {s.topScorerGoals} goals
                </li>
              )}
              {s.topAssisterName && (
                <li style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Icon name="target" size={15} className="icon-muted" />
                  Top assist: <strong>{s.topAssisterName}</strong> ({s.topAssisterTeam}) — {s.topAssisterAssists}
                </li>
              )}
              {s.redCardLeaderName && (
                <li style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Icon name="redCard" size={13} style={{ color: 'var(--danger)' }} />
                  Most red cards: <strong>{s.redCardLeaderName}</strong> ({s.redCardLeaderTeam}) — {s.redCardLeaderCards}
                </li>
              )}
            </ul>
          </div>
        )}

        {/* C. Quick links */}
        <div className="card" data-testid="dashboard-quick-links">
          <div className="section-header">
            <span className="section-header-icon"><Icon name="bolt" size={16} /></span>
            <h2>Quick Links</h2>
          </div>
          <div className="quick-links-grid">
            <Link to="/matches" className="btn-secondary" data-testid="ql-matches">Matches</Link>
            <Link to="/bets" className="btn-secondary" data-testid="ql-my-bets">My Bets</Link>
            <Link to="/stats" className="btn-secondary" data-testid="ql-stats">Stats</Link>
            <Link to="/season-bets" className="btn-secondary" data-testid="ql-season-bets">Season Bets</Link>
            <Link to="/league" className="btn-secondary" data-testid="ql-league">League Table</Link>
          </div>
        </div>

        {/* D. Recent bets */}
        <div className="card" data-testid="dashboard-recent-bets" style={{ gridColumn: '1 / -1' }}>
          <h2>Recent Bets</h2>
          {s && s.recentBets.length > 0 ? (
            <>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Market</th>
                      <th>Selection</th>
                      <th>Odds</th>
                      <th>Amount</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {s.recentBets.map((b) => (
                      <RecentBetRow key={b.id} bet={b} />
                    ))}
                  </tbody>
                </table>
              </div>
              <Link to="/bets" className="link-more">View all bets →</Link>
            </>
          ) : (
            <p className="muted">No bets yet — <Link to="/matches">pick your first market</Link>.</p>
          )}
        </div>
      </div>

      <div className="dashboard-grid" style={{ marginTop: '1.5rem' }}>
        <EditProfileCard />
      </div>
    </div>
  );
}
