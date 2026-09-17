import { useEffect, useState } from 'react';
import type { PlayerResponse, TeamResponse } from '../types';
import { getTeamPlayerStats } from '../api/playersApi';
import Loading from './Loading';
import ErrorMessage from './ErrorMessage';
import PlayerDetailsModal from './PlayerDetailsModal';
import TeamCrest from './TeamCrest';

interface Props {
  team: TeamResponse;
  onClose: () => void;
}

function roleLabel(p: PlayerResponse): string {
  if (p.starter) return 'Starter';
  if (p.substitute) return 'Substitute';
  return 'Squad';
}

function statusLabel(p: PlayerResponse): string {
  if (p.status === 'INJURED') {
    return `Injured — ${p.injuryMatchesRemaining} match${p.injuryMatchesRemaining === 1 ? '' : 'es'} remaining`;
  }
  if (p.status === 'SUSPENDED') {
    return `Suspended — ${p.suspensionMatchesRemaining} match${p.suspensionMatchesRemaining === 1 ? '' : 'es'} remaining`;
  }
  return 'Fit';
}

export default function TeamSquadModal({ team, onClose }: Props) {
  const [players, setPlayers] = useState<PlayerResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedPlayer, setSelectedPlayer] = useState<PlayerResponse | null>(null);

  useEffect(() => {
    getTeamPlayerStats(team.id)
      .then((res) => setPlayers(res.data))
      .catch(() => setError('Failed to load squad.'))
      .finally(() => setLoading(false));
  }, [team.id]);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal modal-wide" data-testid="team-squad-modal" onClick={(e) => e.stopPropagation()}>
        <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
          <TeamCrest name={team.name} size="md" />
          {team.name} — Squad
        </h2>

        {loading && <Loading message="Loading squad..." />}
        {error && <ErrorMessage message={error} />}

        {!loading && !error && (
          players.length === 0 ? (
            <p className="muted">No squad data available for this team.</p>
          ) : (
            <table className="data-table" data-testid="team-squad-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Name</th>
                  <th>Position</th>
                  <th>Role</th>
                  <th>Rating</th>
                  <th>Goals</th>
                  <th>Assists</th>
                  <th>Red Cards</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {players.map((p) => (
                  <tr
                    key={p.id}
                    className="clickable-row"
                    data-testid={`team-squad-row-${p.id}`}
                    onClick={() => setSelectedPlayer(p)}
                  >
                    <td>{p.jerseyNumber ?? '—'}</td>
                    <td>{p.fullName}</td>
                    <td>{p.position}</td>
                    <td>{roleLabel(p)}</td>
                    <td>{p.rating}</td>
                    <td>{p.goals}</td>
                    <td>{p.assists}</td>
                    <td>{p.redCards}</td>
                    <td>{statusLabel(p)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )
        )}

        <div className="modal-actions">
          <button type="button" onClick={onClose} className="btn-secondary" data-testid="close-team-squad-btn">Close</button>
        </div>
      </div>

      {selectedPlayer && (
        <PlayerDetailsModal
          player={selectedPlayer}
          teamName={team.name}
          onClose={() => setSelectedPlayer(null)}
        />
      )}
    </div>
  );
}
