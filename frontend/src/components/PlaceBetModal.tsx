import { useState } from 'react';
import type { MatchResponse, Prediction } from '../types';
import { placeBet } from '../api/betsApi';
import { useAuth } from '../context/AuthContext';
import TeamCrest from './TeamCrest';

interface Props {
  match: MatchResponse;
  onClose: () => void;
  onSuccess: () => void;
}

export default function PlaceBetModal({ match, onClose, onSuccess }: Props) {
  const { refreshUser } = useAuth();
  const [prediction, setPrediction] = useState<Prediction>('HOME_WIN');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const amountNum = parseFloat(amount);
    if (!amount || isNaN(amountNum) || amountNum <= 0) {
      setError('Enter a valid amount greater than 0.');
      return;
    }
    setSubmitting(true);
    try {
      await placeBet({ matchId: match.id, prediction, amount: amountNum });
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

  const oddsFor = (p: Prediction) => {
    if (p === 'HOME_WIN') return match.homeOdds;
    if (p === 'DRAW') return match.drawOdds;
    return match.awayOdds;
  };

  const amountNum = parseFloat(amount);
  const selectedOdds = oddsFor(prediction);
  const hasValidPreview = !!selectedOdds && !isNaN(amountNum) && amountNum > 0;
  const potentialReturn = hasValidPreview ? amountNum * selectedOdds! : 0;
  const netProfit = hasValidPreview ? amountNum * (selectedOdds! - 1) : 0;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" data-testid="place-bet-modal" onClick={(e) => e.stopPropagation()}>
        <h2>Place Bet</h2>
        <div className="modal-match-teams">
          <TeamCrest name={match.homeTeamName} size="sm" />
          <span className="modal-match-vs">vs</span>
          <TeamCrest name={match.awayTeamName} size="sm" />
        </div>
        <p className="modal-match">{match.homeTeamName} vs {match.awayTeamName}</p>

        <form onSubmit={handleSubmit}>
          <div className="prediction-options">
            {(['HOME_WIN', 'DRAW', 'AWAY_WIN'] as Prediction[]).map((p) => (
              <label key={p} className={`prediction-option ${prediction === p ? 'selected' : ''}`}>
                <input
                  type="radio"
                  name="prediction"
                  value={p}
                  checked={prediction === p}
                  onChange={() => setPrediction(p)}
                />
                <span className="pred-label">
                  {p === 'HOME_WIN' ? match.homeTeamName : p === 'AWAY_WIN' ? match.awayTeamName : 'Draw'}
                </span>
                <span className="pred-odds">{oddsFor(p)?.toFixed(2) ?? '—'}</span>
              </label>
            ))}
          </div>

          <div className="form-group">
            <label>Amount</label>
            <input
              type="number"
              data-testid="bet-amount-input"
              min="0.01"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="Enter amount"
              required
            />
          </div>

          <div className="bet-slip-summary" data-testid="place-bet-payout-preview">
            <span>Potential return: <strong data-testid="place-bet-potential-return">${potentialReturn.toFixed(2)}</strong></span>
            <span>Net profit: <strong data-testid="place-bet-net-profit">${netProfit.toFixed(2)}</strong></span>
          </div>

          {error && <div className="error-message">{error}</div>}

          <div className="modal-actions">
            <button type="button" onClick={onClose} className="btn-secondary">Cancel</button>
            <button type="submit" data-testid="confirm-bet-btn" disabled={submitting} className="btn-primary">
              {submitting ? 'Placing...' : 'Place Bet'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
