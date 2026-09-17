import { useEffect, useState } from 'react';
import {
  getSeasonBetStatus,
  getChampionOdds,
  getTopScorerOdds,
  placeChampionBet,
  placeTopScorerBet,
} from '../api/betsApi';
import type { SeasonBetStatusResponse, ChampionOddsOption, TopScorerOddsOption } from '../types';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import { useAuth } from '../context/AuthContext';
import TeamCrest from '../components/TeamCrest';
import Icon from '../components/Icon';

export default function SeasonBetsPage() {
  const { refreshUser } = useAuth();
  const [status, setStatus] = useState<SeasonBetStatusResponse | null>(null);
  const [championOdds, setChampionOdds] = useState<ChampionOddsOption[]>([]);
  const [topScorerOdds, setTopScorerOdds] = useState<TopScorerOddsOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [championTeamId, setChampionTeamId] = useState<number | null>(null);
  const [championAmount, setChampionAmount] = useState('');
  const [championError, setChampionError] = useState('');
  const [championSuccess, setChampionSuccess] = useState('');
  const [championSubmitting, setChampionSubmitting] = useState(false);

  const [topScorerPlayerId, setTopScorerPlayerId] = useState<number | null>(null);
  const [topScorerAmount, setTopScorerAmount] = useState('');
  const [topScorerError, setTopScorerError] = useState('');
  const [topScorerSuccess, setTopScorerSuccess] = useState('');
  const [topScorerSubmitting, setTopScorerSubmitting] = useState(false);

  useEffect(() => {
    Promise.all([
      getSeasonBetStatus(),
      getChampionOdds(),
      getTopScorerOdds(50),
    ])
      .then(([s, c, t]) => {
        setStatus(s.data);
        setChampionOdds(c.data);
        setTopScorerOdds(t.data);
      })
      .catch(() => setError('Failed to load season bet data.'))
      .finally(() => setLoading(false));
  }, []);

  const handleChampionBet = async (e: React.FormEvent) => {
    e.preventDefault();
    setChampionError('');
    setChampionSuccess('');
    const amt = parseFloat(championAmount);
    if (!championTeamId) { setChampionError('Select a team.'); return; }
    if (!championAmount || isNaN(amt) || amt <= 0) { setChampionError('Enter a valid amount.'); return; }
    setChampionSubmitting(true);
    try {
      await placeChampionBet({ teamId: championTeamId, amount: amt });
      await refreshUser();
      const teamName = championOdds.find(o => o.teamId === championTeamId)?.teamName ?? '';
      setChampionSuccess(`Champion bet placed on ${teamName}!`);
      setChampionTeamId(null);
      setChampionAmount('');
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setChampionError(axiosErr.response?.data?.message ?? 'Failed to place bet.');
      } else {
        setChampionError('Failed to place bet.');
      }
    } finally {
      setChampionSubmitting(false);
    }
  };

  const handleTopScorerBet = async (e: React.FormEvent) => {
    e.preventDefault();
    setTopScorerError('');
    setTopScorerSuccess('');
    const amt = parseFloat(topScorerAmount);
    if (!topScorerPlayerId) { setTopScorerError('Select a player.'); return; }
    if (!topScorerAmount || isNaN(amt) || amt <= 0) { setTopScorerError('Enter a valid amount.'); return; }
    setTopScorerSubmitting(true);
    try {
      await placeTopScorerBet({ playerId: topScorerPlayerId, amount: amt });
      await refreshUser();
      const player = topScorerOdds.find(o => o.playerId === topScorerPlayerId);
      setTopScorerSuccess(`Top scorer bet placed on ${player?.fullName ?? ''}!`);
      setTopScorerPlayerId(null);
      setTopScorerAmount('');
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setTopScorerError(axiosErr.response?.data?.message ?? 'Failed to place bet.');
      } else {
        setTopScorerError('Failed to place bet.');
      }
    } finally {
      setTopScorerSubmitting(false);
    }
  };

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  const bettingOpen = status?.open ?? false;

  return (
    <div className="page page-seasonbets">
      <h1>Season Bets</h1>

      <div
        className={`season-bet-status ${bettingOpen ? 'open' : 'closed'}`}
        data-testid="season-bets-status-banner"
      >
        {bettingOpen
          ? 'Markets are open — lock in your Champion and Top Scorer picks before kickoff!'
          : (
            <>
              <strong>Season markets are locked.</strong>{' '}
              <span data-testid="season-bets-lock-reason">{status?.reason ?? 'Round 1 has started.'}</span>
            </>
          )}
      </div>

      <div className="season-bets-grid">
        {/* Champion */}
        <section className="season-bet-section">
          <div className="section-header">
            <span className="section-header-icon"><Icon name="trophy" size={16} /></span>
            <h2>Champion Winner</h2>
          </div>
          <p className="muted">Back the club you think lifts the title this season.</p>

          <div className="table-wrap"><table className="data-table">
            <thead>
              <tr>
                <th>Team</th>
                <th>Skill Level</th>
                <th>Odds</th>
                {bettingOpen && <th>Bet</th>}
              </tr>
            </thead>
            <tbody>
              {championOdds.map((opt) => (
                <tr
                  key={opt.teamId}
                  className={championTeamId === opt.teamId ? 'row-selected' : ''}
                  onClick={() => bettingOpen && setChampionTeamId(opt.teamId)}
                  style={bettingOpen ? { cursor: 'pointer' } : undefined}
                >
                  <td>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <TeamCrest name={opt.teamName} size="sm" />
                      {opt.teamName}
                    </span>
                  </td>
                  <td>{opt.skillLevel}</td>
                  <td><span className="odds-chip">{Number(opt.odds).toFixed(2)}</span></td>
                  {bettingOpen && (
                    <td>
                      <input
                        type="radio"
                        name="champion-team"
                        checked={championTeamId === opt.teamId}
                        onChange={() => setChampionTeamId(opt.teamId)}
                        data-testid={`champion-select-${opt.teamId}`}
                      />
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table></div>

          {bettingOpen && (
            <form className="season-bet-form" onSubmit={handleChampionBet}>
              {championSuccess && <div className="success-message">{championSuccess}</div>}
              {championError && <div className="error-message">{championError}</div>}
              <div className="form-row">
                <label>
                  Stake ($)
                  <input
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={championAmount}
                    onChange={(e) => setChampionAmount(e.target.value)}
                    data-testid="champion-amount-input"
                    placeholder="0.00"
                  />
                </label>
                {championTeamId && championAmount && !isNaN(parseFloat(championAmount)) && (
                  <span className="possible-win">
                    Win: ${(parseFloat(championAmount) * (championOdds.find(o => o.teamId === championTeamId)?.odds ?? 1)).toFixed(2)}
                  </span>
                )}
                <button
                  type="submit"
                  className="btn-primary"
                  disabled={championSubmitting}
                  data-testid="place-champion-bet-btn"
                >
                  {championSubmitting ? 'Placing...' : 'Place Champion Bet'}
                </button>
              </div>
            </form>
          )}
        </section>

        {/* Top Scorer */}
        <section className="season-bet-section">
          <div className="section-header">
            <span className="section-header-icon"><Icon name="target" size={16} /></span>
            <h2>Top Scorer</h2>
          </div>
          <p className="muted">Call the league's top marksman before a ball is kicked.</p>

          <div className="table-wrap"><table className="data-table">
            <thead>
              <tr>
                <th>Player</th>
                <th>Team</th>
                <th>Position</th>
                <th>Rating</th>
                <th>Odds</th>
                {bettingOpen && <th>Bet</th>}
              </tr>
            </thead>
            <tbody>
              {topScorerOdds.map((opt) => (
                <tr
                  key={opt.playerId}
                  className={topScorerPlayerId === opt.playerId ? 'row-selected' : ''}
                  onClick={() => bettingOpen && setTopScorerPlayerId(opt.playerId)}
                  style={bettingOpen ? { cursor: 'pointer' } : undefined}
                >
                  <td>{opt.fullName}</td>
                  <td>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <TeamCrest name={opt.teamName} size="sm" />
                      {opt.teamName}
                    </span>
                  </td>
                  <td><span className={`pos-badge ${opt.position.toLowerCase()}`}>{opt.position}</span></td>
                  <td>{opt.rating}</td>
                  <td><span className="odds-chip">{Number(opt.odds).toFixed(2)}</span></td>
                  {bettingOpen && (
                    <td>
                      <input
                        type="radio"
                        name="top-scorer-player"
                        checked={topScorerPlayerId === opt.playerId}
                        onChange={() => setTopScorerPlayerId(opt.playerId)}
                        data-testid={`top-scorer-select-${opt.playerId}`}
                      />
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table></div>

          {bettingOpen && (
            <form className="season-bet-form" onSubmit={handleTopScorerBet}>
              {topScorerSuccess && <div className="success-message">{topScorerSuccess}</div>}
              {topScorerError && <div className="error-message">{topScorerError}</div>}
              <div className="form-row">
                <label>
                  Stake ($)
                  <input
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={topScorerAmount}
                    onChange={(e) => setTopScorerAmount(e.target.value)}
                    data-testid="top-scorer-amount-input"
                    placeholder="0.00"
                  />
                </label>
                {topScorerPlayerId && topScorerAmount && !isNaN(parseFloat(topScorerAmount)) && (
                  <span className="possible-win">
                    Win: ${(parseFloat(topScorerAmount) * (topScorerOdds.find(o => o.playerId === topScorerPlayerId)?.odds ?? 1)).toFixed(2)}
                  </span>
                )}
                <button
                  type="submit"
                  className="btn-primary"
                  disabled={topScorerSubmitting}
                  data-testid="place-top-scorer-bet-btn"
                >
                  {topScorerSubmitting ? 'Placing...' : 'Place Top Scorer Bet'}
                </button>
              </div>
            </form>
          )}
        </section>
      </div>
    </div>
  );
}
