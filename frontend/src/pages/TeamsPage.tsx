import { useEffect, useState } from 'react';
import { getTeams } from '../api/teamsApi';
import type { TeamResponse } from '../types';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import TeamSquadModal from '../components/TeamSquadModal';
import TeamCrest from '../components/TeamCrest';

export default function TeamsPage() {
  const [teams, setTeams] = useState<TeamResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [squadTeam, setSquadTeam] = useState<TeamResponse | null>(null);

  useEffect(() => {
    getTeams()
      .then((res) => setTeams(res.data))
      .catch(() => setError('Failed to load teams.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <div className="page page-teams">
      <h1>Teams</h1>
      {teams.length === 0 ? (
        <p className="muted">No teams found. Use the Admin page to seed teams.</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Skill</th>
              <th>Morale</th>
              <th>Injuries</th>
              <th>P</th>
              <th>W</th>
              <th>D</th>
              <th>L</th>
              <th>GF</th>
              <th>GA</th>
              <th>GD</th>
              <th>Pts</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {teams.map((t) => (
              <tr key={t.id}>
                <td>
                  <span style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                    <TeamCrest name={t.name} size="sm" />
                    {t.name}
                  </span>
                </td>
                <td>{t.skillLevel}</td>
                <td>{t.morale}</td>
                <td>{t.injuries}</td>
                <td>{t.played}</td>
                <td>{t.wins}</td>
                <td>{t.draws}</td>
                <td>{t.losses}</td>
                <td>{t.goalsFor}</td>
                <td>{t.goalsAgainst}</td>
                <td>{t.goalDifference}</td>
                <td><strong>{t.points}</strong></td>
                <td>
                  <button
                    type="button"
                    className="btn-secondary"
                    data-testid={`view-squad-btn-${t.id}`}
                    onClick={() => setSquadTeam(t)}
                  >
                    View Squad
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {squadTeam && (
        <TeamSquadModal team={squadTeam} onClose={() => setSquadTeam(null)} />
      )}
    </div>
  );
}
