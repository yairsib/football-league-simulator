import { useEffect, useState } from 'react';
import type { HandicapOddsOption, HandicapSelection, MatchResponse } from '../types';
import { getHandicapOdds } from '../api/matchesApi';
import { placeHandicapBet } from '../api/betsApi';
import { useAuth } from '../context/AuthContext';
import TeamCrest from './TeamCrest';

interface Props {
  match: MatchResponse;
  onClose: () => void;
  onSuccess: () => void;
}

export default function HandicapBetModal({ match, onClose, onSuccess }: Props) {
  const { refreshUser } = useAuth();
  const [options, setOptions] = useState<HandicapOddsOption[]>([]);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [selected, setSelected] = useState<HandicapOddsOption | null>(null);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    getHandicapOdds(match.id)
      .then((res) => setOptions(res.data))
      .catch(() => setError('Failed to load handicap odds.'))
      .finally(() => setLoadingOptions(false));
  }, [match.id]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!selected) {
      setError('Select a handicap outcome.');
      return;
    }
    const amountNum = parseFloat(amount);
    if (!amount || isNaN(amountNum) || amountNum <= 0) {
      setError('Enter a valid amount greater than 0.');
      return;
    }
    setSubmitting(true);
    try {
      await placeHandicapBet({
        matchId: match.id,
        selection: selected.selection as HandicapSelection,
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
    ? (amountNum * Number(selected.odds)).toFixed(2)
    : '—';

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" data-testid="handicap-bet-modal" onClick={(e) => e.stopPropagation()}>
        <h2>Handicap Bet (Home -1)</h2>
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
            <div className="prediction-options" data-testid="handicap-options">
              {options.map((opt) => (
                <label
                  key={opt.selection}
                  className={`prediction-option ${selected?.selection === opt.selection ? 'selected' : ''}`}
                  data-testid={`handicap-option-${opt.selection}`}
                >
                  <input
                    type="radio"
                    name="handicap-selection"
                    value={opt.selection}
                    checked={selected?.selection === opt.selection}
                    onChange={() => setSelected(opt)}
                  />
                  <span className="pred-label">{opt.label} @ {Number(opt.odds).toFixed(2)}</span>
                </label>
              ))}
            </div>
          )}

          <div className="form-group">
            <label>Amount</label>
            <input
              type="number"
              data-testid="handicap-amount-input"
              min="0.01"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="Enter amount"
              required
            />
          </div>

          <p className="muted" data-testid="handicap-possible-win">Possible win: ${possibleWin}</p>

          {error && <div className="error-message">{error}</div>}

          <div className="modal-actions">
            <button type="button" onClick={onClose} className="btn-secondary">Cancel</button>
            <button type="submit" data-testid="confirm-handicap-btn" disabled={submitting} className="btn-primary">
              {submitting ? 'Placing...' : 'Place Bet'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
