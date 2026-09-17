import type { LeagueTableEntry } from '../types';
import TeamCrest from './TeamCrest';

interface Props {
  rows: LeagueTableEntry[];
  /** Compact mode (Matches page side panel): #, Team, P, W, D, L, GD, Pts. Full mode adds GF/GA. */
  compact?: boolean;
  /** When set, each row gets data-testid="<prefix>-<teamId>". */
  rowTestIdPrefix?: string;
}

/**
 * The single league-standings renderer, shared by the League Table page (full) and the
 * Matches page (compact, side-by-side with the round's results). It only displays what the
 * backend computed — position, sorting and points come from GET /api/league/table.
 */
export default function LeagueStandingsTable({ rows, compact = false, rowTestIdPrefix }: Props) {
  const gd = (v: number) => (v > 0 ? `+${v}` : `${v}`);

  return (
    <div className="table-wrap">
      <table className={`data-table league-table${compact ? ' league-table-compact' : ''}`}>
        <thead>
          <tr>
            <th>#</th>
            <th>Team</th>
            <th>P</th>
            <th>W</th>
            <th>D</th>
            <th>L</th>
            {!compact && <th>GF</th>}
            {!compact && <th>GA</th>}
            <th>GD</th>
            <th>Pts</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row.teamId}
              className={row.position === 1 ? 'leader' : ''}
              data-testid={rowTestIdPrefix ? `${rowTestIdPrefix}-${row.teamId}` : undefined}
            >
              <td>{row.position}</td>
              <td>
                <span style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                  <TeamCrest name={row.teamName} size="sm" />
                  <strong>{row.teamName}</strong>
                </span>
              </td>
              <td>{row.played}</td>
              <td>{row.wins}</td>
              <td>{row.draws}</td>
              <td>{row.losses}</td>
              {!compact && <td>{row.goalsFor}</td>}
              {!compact && <td>{row.goalsAgainst}</td>}
              <td>{gd(row.goalDifference)}</td>
              <td><strong>{row.points}</strong></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
