import { useEffect, useState } from 'react';
import type { CorrectScoreOddsOption, MatchResponse } from '../types';
import { getCorrectScoreOdds } from '../api/matchesApi';
import { placeCorrectScoreBet } from '../api/betsApi';
import { useAuth } from '../context/AuthContext';
import TeamCrest from './TeamCrest';

interface Props {
  match: MatchResponse;
  onClose: () => void;
  onSuccess: () => void;
}

export default function CorrectScoreBetModal({ match, onClose, onSuccess }: Props) {
  const { refreshUser } = useAuth();
  const [options, setOptions] = useState<CorrectScoreOddsOption[]>([]);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [selected, setSelected] = useState<CorrectScoreOddsOption | null>(null);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    getCorrectScoreOdds(match.id)
      .then((res) => setOptions(res.data))
      .catch(() => setError('Failed to load correct score odds.'))
      .finally(() => setLoadingOptions(false));
  }, [match.id]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!selected) {
      setError('Select a scoreline.');
      return;
    }
    const amountNum = parseFloat(amount);
    if (!amount || isNaN(amountNum) || amountNum <= 0) {
      setError('Enter a valid amount greater than 0.');
      return;
    }
    setSubmitting(true);
    try {
      await placeCorrectScoreBet({
        matchId: match.id,
        homeGoals: selected.homeGoals,
        awayGoals: selected.awayGoals,
        amount: amountNum,
      });
      await refreshUser();
      onSuccess();
      onClose();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message ?? 'Failed to place bet.');
      } else {
        setError('Failed to place bet.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const amountNum = parseFloat(amount);
  const possibleWin = selected && !isNaN(amountNum) && amountNum > 0
    ? (amountNum * selected.odds).toFixed(2)
    : '—';

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" data-testid="correct-score-bet-modal" onClick={(e) => e.stopPropagation()}>
        <h2>Correct Score</h2>
        <div className="modal-match-teams">
          <TeamCrest name={match.homeTeamName} size="sm" />
          <span className="modal-match-vs">vs</span>
          <TeamCrest name={match.awayTeamName} size="sm" />
        </div>
        <p className="modal-match">{match.homeTeamName} vs {match.awayTeamName}</p>

        <form onSubmit={handleSubmit}>
          {loadingOptions ? (
            <p className="muted">Loading odds...</p>
          ) : (
            <table className="correct-score-grid" data-testid="correct-score-options">
              <thead>
                <tr>
                  <th>{match.homeTeamName} \ {match.awayTeamName}</th>
                  {[0, 1, 2, 3, 4].map((away) => (
                    <th key={away}>{away}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[0, 1, 2, 3, 4].map((home) => (
                  <tr key={home}>
                    <th>{home}</th>
                    {[0, 1, 2, 3, 4].map((away) => {
                      const option = options.find((o) => o.homeGoals === home && o.awayGoals === away);
                      const isSelected = selected?.homeGoals === home && selected?.awayGoals === away;
                      return (
                        <td key={away}>
                          <button
                            type="button"
                            className={`correct-score-option ${isSelected ? 'selected' : ''}`}
                            data-testid={`correct-score-${home}-${away}`}
                            onClick={() => option && setSelected(option)}
                            disabled={!option}
                          >
                            {option ? Number(option.odds).toFixed(2) : '—'}
                          </button>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {selected && (
            <p className="modal-match" data-testid="correct-score-selected">
              Selected: {selected.homeGoals}-{selected.awayGoals} @ {Number(selected.odds).toFixed(2)}
            </p>
          )}

          <div className="form-group">
            <label>Amount</label>
            <input
              type="number"
              data-testid="correct-score-amount-input"
              min="0.01"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="Enter amount"
              required
            />
          </div>

          <p className="muted" data-testid="correct-score-possible-win">Possible win: ${possibleWin}</p>

          {error && <div className="error-message">{error}</div>}

          <div className="modal-actions">
            <button type="button" onClick={onClose} className="btn-secondary">Cancel</button>
            <button type="submit" data-testid="confirm-correct-score-btn" disabled={submitting} className="btn-primary">
              {submitting ? 'Placing...' : 'Place Bet'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
