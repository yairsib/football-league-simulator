import { useEffect, useState } from 'react';
import type { MatchEventResponse, MatchLineupsResponse, MatchResponse } from '../types';
import { getMatchEvents, getMatchLineups } from '../api/matchesApi';
import Loading from './Loading';
import ErrorMessage from './ErrorMessage';
import TeamCrest from './TeamCrest';
import Icon, { type IconName, weatherIconName } from './Icon';

interface Props {
  match: MatchResponse;
  onClose: () => void;
  onViewLineups: () => void;
}

function eventIconName(type: string): IconName {
  if (type === 'GOAL') return 'goal';
  if (type === 'RED_CARD') return 'redCard';
  return 'substitution';
}

function eventClass(type: string): string {
  if (type === 'GOAL') return 'goal';
  if (type === 'RED_CARD') return 'red_card';
  return 'substitution';
}

function timelineContent(e: MatchEventResponse): React.ReactNode {
  if (e.eventType === 'GOAL') {
    return (
      <>
        <strong>{e.playerName}</strong>
        {e.assistPlayerName && <> <span style={{ color: 'var(--muted)' }}>assist: {e.assistPlayerName}</span></>}
        {e.teamName && <> · <span style={{ color: 'var(--muted)', fontSize: '0.78rem' }}>{e.teamName}</span></>}
      </>
    );
  }
  if (e.eventType === 'SUBSTITUTION') {
    return (
      <>
        <span style={{ color: 'var(--muted)', fontSize: '0.78rem' }}>{e.teamName}</span>
        {' · '}
        <span style={{ color: 'var(--danger)', fontSize: '0.82rem' }}>↓ {e.playerOutName}</span>
        {' '}
        <span style={{ color: 'var(--primary)', fontSize: '0.82rem' }}>↑ {e.playerInName}</span>
      </>
    );
  }
  return (
    <>
      <strong>{e.playerName}</strong>
      {e.teamName && <> · <span style={{ color: 'var(--muted)', fontSize: '0.78rem' }}>{e.teamName}</span></>}
    </>
  );
}

function timelineLabel(e: MatchEventResponse): string {
  if (e.eventType === 'GOAL') {
    const assist = e.assistPlayerName ? `, assist: ${e.assistPlayerName}` : '';
    return `${e.minute}' GOAL — ${e.playerName}${assist}`;
  }
  if (e.eventType === 'SUBSTITUTION') {
    return `${e.minute}' Substitution — ${e.teamName}: ${e.playerOutName} out, ${e.playerInName} in`;
  }
  return `${e.minute}' RED CARD — ${e.playerName}`;
}

export default function MatchDetailsModal({ match, onClose, onViewLineups }: Props) {
  const [events, setEvents] = useState<MatchEventResponse[]>([]);
  const [lineups, setLineups] = useState<MatchLineupsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    Promise.all([getMatchEvents(match.id), getMatchLineups(match.id)])
      .then(([eventsRes, lineupsRes]) => {
        setEvents(eventsRes.data);
        setLineups(lineupsRes.data);
      })
      .catch(() => setError('Failed to load match details.'))
      .finally(() => setLoading(false));
  }, [match.id]);

  const emptyEventsMessage = match.status === 'FINISHED' ? 'No recorded events' : 'No events yet';
  const isFinished = match.status === 'FINISHED';

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal modal-wide" data-testid="match-details-modal" onClick={(e) => e.stopPropagation()}>

        {/* ── Match Center Header ─────────────────────────── */}
        <div className="match-center-header">
          <div className="match-center-teams">
            <div className="match-center-team">
              <TeamCrest name={match.homeTeamName} size="lg" />
              <div className="match-center-team-name">{match.homeTeamName}</div>
            </div>
            {isFinished ? (
              <div className="match-center-score">
                {match.homeGoals} — {match.awayGoals}
              </div>
            ) : (
              <div className="match-center-score vs">vs</div>
            )}
            <div className="match-center-team">
              <TeamCrest name={match.awayTeamName} size="lg" />
              <div className="match-center-team-name">{match.awayTeamName}</div>
            </div>
          </div>
          <div className="match-center-meta">
            <span className={`status-badge ${match.status.toLowerCase()}`} data-testid="match-details-status">{match.status.replace('_', ' ')}</span>
            {match.weatherCondition && (
              <span
                className={`weather-badge weather-${match.weatherCondition.toLowerCase()}`}
                data-testid="match-details-weather"
              >
                <Icon name={weatherIconName(match.weatherCondition)} size={13} />
                {match.weatherCondition}
              </span>
            )}
          </div>
        </div>

        {/* ── Odds ───────────────────────────────────────── */}
        {match.homeOdds !== null && (
          <div style={{ marginBottom: '1rem' }}>
            <div style={{ fontSize: '0.72rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--muted)', marginBottom: '0.4rem' }}>
              Match Odds
            </div>
            <div className="match-odds" data-testid="match-details-odds">
              <span className="odds-chip">Home {Number(match.homeOdds).toFixed(2)}</span>
              <span className="odds-chip">Draw {Number(match.drawOdds).toFixed(2)}</span>
              <span className="odds-chip">Away {Number(match.awayOdds).toFixed(2)}</span>
            </div>
          </div>
        )}

        {loading && <Loading message="Loading match details..." />}
        {error && <ErrorMessage message={error} />}

        {!loading && !error && (
          <>
            <hr className="section-divider" />

            {/* ── Events Timeline ─────────────────────────── */}
            <div data-testid="match-details-events">
              <div style={{ fontSize: '0.72rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--muted)', marginBottom: '0.6rem' }}>
                Match Timeline
              </div>
              {events.length === 0 ? (
                <p className="muted" data-testid="match-details-events-empty">{emptyEventsMessage}</p>
              ) : (
                <div className="timeline">
                  {events.map((e) => (
                    <div
                      key={e.id}
                      className={`timeline-event ${eventClass(e.eventType)}`}
                      data-testid={`match-details-event-${e.id}`}
                      aria-label={timelineLabel(e)}
                    >
                      <span className="timeline-minute">{e.minute}'</span>
                      <span className="timeline-icon"><Icon name={eventIconName(e.eventType)} size={15} /></span>
                      <span className="timeline-text">{timelineContent(e)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* ── Unavailable Players ─────────────────────── */}
            {lineups && (
              <>
                <hr className="section-divider" />
                <div data-testid="match-details-unavailable">
                  <div style={{ fontSize: '0.72rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--muted)', marginBottom: '0.5rem' }}>
                    Availability
                  </div>
                  <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>
                    <span style={{ fontSize: '0.85rem', color: 'var(--text-2)' }} data-testid="match-details-home-unavailable">
                      {lineups.homeTeamName}: <strong style={{ color: lineups.homeUnavailablePlayers.length > 0 ? 'var(--danger)' : 'var(--muted)' }}>
                        {lineups.homeUnavailablePlayers.length} unavailable
                      </strong>
                    </span>
                    <span style={{ fontSize: '0.85rem', color: 'var(--text-2)' }} data-testid="match-details-away-unavailable">
                      {lineups.awayTeamName}: <strong style={{ color: lineups.awayUnavailablePlayers.length > 0 ? 'var(--danger)' : 'var(--muted)' }}>
                        {lineups.awayUnavailablePlayers.length} unavailable
                      </strong>
                    </span>
                  </div>
                </div>
              </>
            )}
          </>
        )}

        <div className="modal-actions" style={{ marginTop: '1.5rem' }}>
          <button type="button" onClick={onViewLineups} className="btn-secondary" data-testid="match-details-view-lineups-btn">
            View Lineups
          </button>
          <button type="button" onClick={onClose} className="btn-secondary" data-testid="close-match-details-btn">Close</button>
        </div>
      </div>
    </div>
  );
}
