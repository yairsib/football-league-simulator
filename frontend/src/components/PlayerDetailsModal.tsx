import type { PlayerResponse, PlayerStatsResponse } from '../types';
import TeamCrest from './TeamCrest';

interface Props {
  player: PlayerResponse | PlayerStatsResponse;
  teamName: string;
  onClose: () => void;
}

function name(p: PlayerResponse | PlayerStatsResponse): string {
  return p.fullName;
}

function statusLabel(p: PlayerResponse | PlayerStatsResponse): string {
  if (p.status === 'INJURED') {
    return `Injured — ${p.injuryMatchesRemaining} match${p.injuryMatchesRemaining === 1 ? '' : 'es'} remaining`;
  }
  if (p.status === 'SUSPENDED') {
    return `Suspended — ${p.suspensionMatchesRemaining} match${p.suspensionMatchesRemaining === 1 ? '' : 'es'} remaining`;
  }
  return 'Fit';
}

export default function PlayerDetailsModal({ player, teamName, onClose }: Props) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" data-testid="player-details-modal" onClick={(e) => e.stopPropagation()}>
        <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
          <TeamCrest name={teamName} size="md" />
          {name(player)}
        </h2>
        <table className="data-table">
          <tbody>
            <tr><th>Team</th><td>{teamName}</td></tr>
            <tr><th>Position</th><td>{player.position}</td></tr>
            <tr><th>Jersey Number</th><td>{player.jerseyNumber ?? '—'}</td></tr>
            <tr><th>Rating</th><td>{player.rating}</td></tr>
            <tr><th>Goals</th><td>{player.goals}</td></tr>
            <tr><th>Assists</th><td>{player.assists}</td></tr>
            <tr><th>Red Cards</th><td>{player.redCards}</td></tr>
            <tr><th>Status</th><td>{statusLabel(player)}</td></tr>
          </tbody>
        </table>
        <div className="modal-actions">
          <button type="button" onClick={onClose} className="btn-secondary" data-testid="close-player-details-btn">Close</button>
        </div>
      </div>
    </div>
  );
}
