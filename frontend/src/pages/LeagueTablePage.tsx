import { useEffect, useState } from 'react';
import { getLeagueTable } from '../api/leagueApi';
import type { LeagueTableEntry } from '../types';
import Loading from '../components/Loading';
import ErrorMessage from '../components/ErrorMessage';
import LeagueStandingsTable from '../components/LeagueStandingsTable';

export default function LeagueTablePage() {
  const [table, setTable] = useState<LeagueTableEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getLeagueTable()
      .then((res) => setTable(res.data))
      .catch(() => setError('Failed to load league table.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <div className="page page-league">
      <h1>League Table</h1>
      {table.length === 0 ? (
        <p className="muted">The table is empty — simulate a round to see the standings take shape.</p>
      ) : (
        <LeagueStandingsTable rows={table} />
      )}
    </div>
  );
}
