import { useEffect, useState } from 'react';
import type { MatchLineupsResponse, MatchResponse, PlayerResponse, ReplacedPlayerInfo } from '../types';
import { getMatchLineups } from '../api/matchesApi';
import Loading from './Loading';
import ErrorMessage from './ErrorMessage';
import TeamCrest from './TeamCrest';
import Icon, { weatherIconName } from './Icon';

interface Props {
  match: MatchResponse;
  onClose: () => void;
}

function unavailabilityLabel(p: PlayerResponse): string {
  if (p.status === 'INJURED') {
    const desc = p.injuryDescription ? `${p.injuryDescription} — ` : '';
    return `Injured — ${desc}${p.injuryMatchesRemaining} match${p.injuryMatchesRemaining === 1 ? '' : 'es'} remaining`;
  }
  if (p.status === 'SUSPENDED') {
    return `Suspended — ${p.suspensionMatchesRemaining} match${p.suspensionMatchesRemaining === 1 ? '' : 'es'} remaining`;
  }
  return p.status;
}

function statsLabel(p: PlayerResponse): string | null {
  const parts: string[] = [];
  if (p.goals > 0) parts.push(`${p.goals} goal${p.goals === 1 ? '' : 's'}`);
  if (p.assists > 0) parts.push(`${p.assists} assist${p.assists === 1 ? '' : 's'}`);
  if (p.redCards > 0) parts.push(`${p.redCards} red card${p.redCards === 1 ? '' : 's'}`);
  return parts.length > 0 ? parts.join(' · ') : null;
}

function PlayerList({ title, players, testIdPrefix, unavailable = false }: { title: string; players: PlayerResponse[]; testIdPrefix: string; unavailable?: boolean }) {
  return (
    <div className="lineup-group" data-testid={testIdPrefix}>
      <h4>{title}</h4>
      {players.length === 0 ? (
        <p className="muted">{unavailable ? 'No unavailable players' : 'None'}</p>
      ) : (
        <ul className="lineup-player-list">
          {players.map((p) => (
            <li key={p.id} data-testid={`${testIdPrefix}-player-${p.id}`}>
              <span className="lineup-player-name">
                {p.jerseyNumber != null ? `#${p.jerseyNumber} ` : ''}{p.fullName}
              </span>
              <span className="lineup-player-meta">{p.position} · rating {p.rating}</span>
              {statsLabel(p) && (
                <span className="lineup-player-stats" data-testid={`${testIdPrefix}-player-${p.id}-stats`}>
                  {statsLabel(p)}
                </span>
              )}
              {unavailable ? (
                <span className={`status-badge ${p.status.toLowerCase()}`}>
                  {unavailabilityLabel(p)}
                </span>
              ) : (
                p.status !== 'FIT' && (
                  <span className={`status-badge ${p.status.toLowerCase()}`}>
                    {unavailabilityLabel(p)}
                  </span>
                )
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function LineupAdjustments({
  replacedPlayers,
  warnings,
  testIdPrefix,
}: {
  replacedPlayers: ReplacedPlayerInfo[];
  warnings: string[];
  testIdPrefix: string;
}) {
  if (replacedPlayers.length === 0 && warnings.length === 0) {
    return null;
  }
  return (
    <div className="lineup-group" data-testid={testIdPrefix}>
      <h4>Lineup adjustments</h4>
      <ul className="lineup-adjustments-list">
        {replacedPlayers.map((r, i) => (
          <li key={i}>
            {r.originalPlayerName} unavailable ({r.reason}); replaced by {r.replacementPlayerName}
          </li>
        ))}
        {warnings.map((w, i) => (
          <li key={`warning-${i}`} className="muted">{w}</li>
        ))}
      </ul>
    </div>
  );
}

function TeamLineup({
  teamName,
  formation,
  starters,
  substitutes,
  unavailablePlayers,
  replacedPlayers,
  lineupWarnings,
  side,
}: {
  teamName: string;
  formation: string;
  starters: PlayerResponse[];
  substitutes: PlayerResponse[];
  unavailablePlayers: PlayerResponse[];
  replacedPlayers: ReplacedPlayerInfo[];
  lineupWarnings: string[];
  side: 'home' | 'away';
}) {
  return (
    <div className="lineup-team" data-testid={`lineup-${side}-team`}>
      <h3>
        <TeamCrest name={teamName} size="sm" className="lineup-team-crest" />
        {teamName} <span className="lineup-formation">({formation})</span>
      </h3>
      <PlayerList title="Starters" players={starters} testIdPrefix={`lineup-${side}-starters`} />
      <PlayerList title="Substitutes" players={substitutes} testIdPrefix={`lineup-${side}-substitutes`} />
      <PlayerList title="Unavailable" players={unavailablePlayers} testIdPrefix={`lineup-${side}-unavailable`} unavailable />
      <LineupAdjustments
        replacedPlayers={replacedPlayers}
        warnings={lineupWarnings}
        testIdPrefix={`lineup-${side}-adjustments`}
      />
    </div>
  );
}

export default function LineupsModal({ match, onClose }: Props) {
  const [lineups, setLineups] = useState<MatchLineupsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getMatchLineups(match.id)
      .then((res) => setLineups(res.data))
      .catch(() => setError('Failed to load lineups.'))
      .finally(() => setLoading(false));
  }, [match.id]);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal modal-wide" data-testid="lineups-modal" onClick={(e) => e.stopPropagation()}>
        <h2>Lineups</h2>
        <div className="modal-match-teams">
          <TeamCrest name={match.homeTeamName} size="sm" />
          <span className="modal-match-vs">vs</span>
          <TeamCrest name={match.awayTeamName} size="sm" />
        </div>
        <p className="modal-match">{match.homeTeamName} vs {match.awayTeamName}</p>

        {loading && <Loading message="Loading lineups..." />}
        {error && <ErrorMessage message={error} />}

        {lineups && (
          <>
            {lineups.weatherCondition && (
              <p className="lineup-weather" data-testid="lineups-weather">
                <Icon name={weatherIconName(lineups.weatherCondition)} size={13} /> Weather: <strong>{lineups.weatherCondition}</strong>
              </p>
            )}

            <div className="lineups-grid">
              <TeamLineup
                side="home"
                teamName={lineups.homeTeamName}
                formation={lineups.homeFormation}
                starters={lineups.homeStarters}
                substitutes={lineups.homeSubstitutes}
                unavailablePlayers={lineups.homeUnavailablePlayers}
                replacedPlayers={lineups.homeReplacedPlayers}
                lineupWarnings={lineups.homeLineupWarnings}
              />
              <TeamLineup
                side="away"
                teamName={lineups.awayTeamName}
                formation={lineups.awayFormation}
                starters={lineups.awayStarters}
                substitutes={lineups.awaySubstitutes}
                unavailablePlayers={lineups.awayUnavailablePlayers}
                replacedPlayers={lineups.awayReplacedPlayers}
                lineupWarnings={lineups.awayLineupWarnings}
              />
            </div>
          </>
        )}

        <div className="modal-actions">
          <button type="button" onClick={onClose} className="btn-secondary" data-testid="close-lineups-btn">Close</button>
        </div>
      </div>
    </div>
  );
}
