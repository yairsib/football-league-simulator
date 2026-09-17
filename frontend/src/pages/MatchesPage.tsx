import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getRounds, getMatchesByRound } from '../api/matchesApi';
import { getLeagueTable } from '../api/leagueApi';
import type { RoundResponse, MatchResponse, Prediction, LeagueTableEntry } from '../types';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import PlaceBetModal from '../components/PlaceBetModal';
import CorrectScoreBetModal from '../components/CorrectScoreBetModal';
import HandicapBetModal from '../components/HandicapBetModal';
import LineupsModal from '../components/LineupsModal';
import MatchEvents from '../components/MatchEvents';
import MatchDetailsModal from '../components/MatchDetailsModal';
import BetSlip, { predictionLabel, type SlipSelection } from '../components/BetSlip';
import TeamCrest from '../components/TeamCrest';
import Icon, { weatherIconName } from '../components/Icon';
import LeagueStandingsTable from '../components/LeagueStandingsTable';

export default function MatchesPage() {
  const [rounds, setRounds] = useState<RoundResponse[]>([]);
  const [selectedRound, setSelectedRound] = useState<number | null>(null);
  const [matches, setMatches] = useState<MatchResponse[]>([]);
  const [loadingRounds, setLoadingRounds] = useState(true);
  const [loadingMatches, setLoadingMatches] = useState(false);
  const [error, setError] = useState('');
  const [betMatch, setBetMatch] = useState<MatchResponse | null>(null);
  const [correctScoreMatch, setCorrectScoreMatch] = useState<MatchResponse | null>(null);
  const [handicapMatch, setHandicapMatch] = useState<MatchResponse | null>(null);
  const [lineupsMatch, setLineupsMatch] = useState<MatchResponse | null>(null);
  const [detailsMatch, setDetailsMatch] = useState<MatchResponse | null>(null);
  const [slipSelections, setSlipSelections] = useState<SlipSelection[]>([]);
  // Standings are shown next to the round's results (same screen) — display only, computed by the backend.
  const [standings, setStandings] = useState<LeagueTableEntry[]>([]);

  const loadStandings = () => {
    getLeagueTable()
      .then((res) => setStandings(res.data))
      .catch(() => {});
  };

  const addToSlip = (match: MatchResponse, prediction: Prediction) => {
    setSlipSelections((prev) => {
      if (prev.some((s) => s.match.id === match.id)) return prev;
      return [...prev, { match, prediction }];
    });
  };

  const removeFromSlip = (matchId: number) => {
    setSlipSelections((prev) => prev.filter((s) => s.match.id !== matchId));
  };

  const isInSlip = (matchId: number) => slipSelections.some((s) => s.match.id === matchId);

  useEffect(() => {
    getRounds()
      .then((res) => {
        setRounds(res.data);
        if (res.data.length > 0) setSelectedRound(res.data[0].roundNumber);
      })
      .catch(() => setError('Failed to load rounds.'))
      .finally(() => setLoadingRounds(false));
    loadStandings();
  }, []);

  useEffect(() => {
    if (selectedRound === null) return;
    setLoadingMatches(true);
    setError('');
    getMatchesByRound(selectedRound)
      .then((res) => setMatches(res.data))
      .catch(() => setError('Failed to load matches.'))
      .finally(() => setLoadingMatches(false));
    loadStandings();
  }, [selectedRound]);

  const refreshMatches = () => {
    if (selectedRound === null) return;
    getMatchesByRound(selectedRound).then((res) => setMatches(res.data));
    loadStandings();
  };

  if (loadingRounds) return <Loading />;

  return (
    <div className="page page-matches">
      <h1>Fixtures</h1>

      {error && <ErrorMessage message={error} />}

      {rounds.length === 0 ? (
        <div className="empty-state">
          <p>The fixture list is empty.</p>
          <p>Head to the <a href="/admin">Admin page</a> to generate the schedule and get the season moving.</p>
        </div>
      ) : (
        <div className="matches-layout">
          <div className="matches-main">
          <div className="round-selector">
            {rounds.map((r) => (
              <button
                key={r.id}
                data-testid={`round-tab-${r.roundNumber}`}
                onClick={() => setSelectedRound(r.roundNumber)}
                className={`round-btn ${selectedRound === r.roundNumber ? 'active' : ''}`}
              >
                Rd {r.roundNumber}
                <span className="round-status">{r.status.replace('_', ' ')}</span>
              </button>
            ))}
          </div>

          {!loadingMatches && matches.length > 0 && !matches.some((m) => m.status === 'BETTING_OPEN') && (
            <p className="muted" data-testid="no-open-matches-message" style={{ marginBottom: '1rem' }}>
              Markets are closed for this round — check back once betting opens.
            </p>
          )}

          {loadingMatches ? (
            <Loading message="Loading matches..." />
          ) : (
            <div className="matches-list">
              {matches.map((m) => {
                const isBettingOpen = m.status === 'BETTING_OPEN' && m.bettingOpen;
                const isFinished = m.status === 'FINISHED';
                const inSlip = isInSlip(m.id);

                return (
                  <div key={m.id} data-testid={`match-card-${m.id}`} className={`match-card status-${m.status.toLowerCase()}`}>
                    {/* Main column */}
                    <div className="match-card-main">
                      <div className="match-teams">
                        <span className="match-team-side">
                          <TeamCrest name={m.homeTeamName} size="sm" />
                          <span className="team home">{m.homeTeamName}</span>
                        </span>
                        {isFinished ? (
                          <span className="match-score">{m.homeGoals} – {m.awayGoals}</span>
                        ) : (
                          <span className="match-score vs-label">vs</span>
                        )}
                        <span className="match-team-side">
                          <TeamCrest name={m.awayTeamName} size="sm" />
                          <span className="team away">{m.awayTeamName}</span>
                        </span>
                      </div>

                      <div className="match-meta">
                        <span className={`status-badge ${m.status.toLowerCase()}`}>{m.status.replace('_', ' ')}</span>
                        {m.weatherCondition && (
                          <span
                            className={`weather-badge weather-${m.weatherCondition.toLowerCase()}`}
                            data-testid={`match-weather-${m.id}`}
                          >
                            <Icon name={weatherIconName(m.weatherCondition)} size={13} />
                            {m.weatherCondition}
                          </span>
                        )}
                      </div>

                      {m.homeOdds !== null && (
                        <div className="match-odds">
                          <span className="odds-chip">1 {Number(m.homeOdds).toFixed(2)}</span>
                          <span className="odds-chip">X {Number(m.drawOdds).toFixed(2)}</span>
                          <span className="odds-chip">2 {Number(m.awayOdds).toFixed(2)}</span>
                        </div>
                      )}

                      {isFinished && <MatchEvents matchId={m.id} />}

                      {/* Bet slip add row */}
                      {isBettingOpen && (
                        <div className="bet-slip-add" data-testid={`bet-slip-add-${m.id}`}>
                          {inSlip ? (
                            <span className="muted" style={{ fontSize: '0.82rem' }}>
                              In slip —{' '}
                              <button type="button" className="btn-link" onClick={() => removeFromSlip(m.id)} data-testid={`slip-remove-from-card-${m.id}`}>
                                Remove
                              </button>
                            </span>
                          ) : (
                            <>
                              <span className="muted" style={{ fontSize: '0.78rem' }}>Add to slip:</span>
                              {(['HOME_WIN', 'DRAW', 'AWAY_WIN'] as Prediction[]).map((p) => (
                                <button
                                  key={p}
                                  type="button"
                                  className="btn-slip-add"
                                  data-testid={`add-to-slip-${m.id}-${p}`}
                                  onClick={() => addToSlip(m, p)}
                                >
                                  {predictionLabel(m, p)} {Number(p === 'HOME_WIN' ? m.homeOdds : p === 'DRAW' ? m.drawOdds : m.awayOdds).toFixed(2)}
                                </button>
                              ))}
                            </>
                          )}
                        </div>
                      )}
                    </div>

                    {/* Side column — actions */}
                    <div className="match-card-side">
                      <div className="match-actions">
                        <button
                          className="btn-ghost btn-sm"
                          data-testid={`view-lineups-btn-${m.id}`}
                          onClick={() => setLineupsMatch(m)}
                        >
                          Lineups
                        </button>
                        <button
                          className="btn-secondary btn-sm"
                          data-testid={`view-details-btn-${m.id}`}
                          onClick={() => setDetailsMatch(m)}
                        >
                          Details
                        </button>
                      </div>

                      {isBettingOpen && (
                        <div className="match-actions" style={{ marginTop: '0.25rem' }}>
                          <button
                            className="btn-primary btn-sm btn-bet"
                            data-testid={`place-bet-btn-${m.id}`}
                            onClick={() => setBetMatch(m)}
                          >
                            Bet
                          </button>
                          <button
                            className="btn-primary btn-sm btn-bet"
                            data-testid={`correct-score-btn-${m.id}`}
                            onClick={() => setCorrectScoreMatch(m)}
                          >
                            Score
                          </button>
                          <button
                            className="btn-primary btn-sm btn-bet"
                            data-testid={`handicap-btn-${m.id}`}
                            onClick={() => setHandicapMatch(m)}
                          >
                            Hcp
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
          </div>

          {/* League standings — rendered on the same screen as the round's results */}
          <aside className="matches-standings card" data-testid="matches-standings" aria-label="League standings">
            <div className="matches-standings-header">
              <h2>Standings</h2>
              <Link to="/league" className="btn-link" data-testid="matches-standings-full-link">Full table</Link>
            </div>
            {standings.length === 0 ? (
              <p className="muted">The table fills in as soon as teams are seeded.</p>
            ) : (
              <LeagueStandingsTable rows={standings} compact rowTestIdPrefix="matches-standings-row" />
            )}
          </aside>
        </div>
      )}

      {betMatch && (
        <PlaceBetModal match={betMatch} onClose={() => setBetMatch(null)} onSuccess={refreshMatches} />
      )}
      {correctScoreMatch && (
        <CorrectScoreBetModal match={correctScoreMatch} onClose={() => setCorrectScoreMatch(null)} onSuccess={refreshMatches} />
      )}
      {handicapMatch && (
        <HandicapBetModal match={handicapMatch} onClose={() => setHandicapMatch(null)} onSuccess={refreshMatches} />
      )}
      {lineupsMatch && (
        <LineupsModal match={lineupsMatch} onClose={() => setLineupsMatch(null)} />
      )}
      {detailsMatch && (
        <MatchDetailsModal
          match={detailsMatch}
          onClose={() => setDetailsMatch(null)}
          onViewLineups={() => { setLineupsMatch(detailsMatch); setDetailsMatch(null); }}
        />
      )}

      <BetSlip
        selections={slipSelections}
        onRemove={removeFromSlip}
        onClear={() => setSlipSelections([])}
        onPlaced={refreshMatches}
      />
    </div>
  );
}
