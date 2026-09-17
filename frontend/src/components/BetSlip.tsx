import { useState } from 'react';
import type { MatchResponse, Prediction } from '../types';
import { placeCombo } from '../api/betsApi';
import { useAuth } from '../context/AuthContext';
import TeamCrest from './TeamCrest';

export interface SlipSelection {
  match: MatchResponse;
  prediction: Prediction;
}

interface Props {
  selections: SlipSelection[];
  onRemove: (matchId: number) => void;
  onClear: () => void;
  onPlaced: () => void;
}

export const oddsForSelection = (match: MatchResponse, prediction: Prediction): number | null => {
  if (prediction === 'HOME_WIN') return match.homeOdds;
  if (prediction === 'DRAW') return match.drawOdds;
  return match.awayOdds;
};

export const predictionLabel = (match: MatchResponse, prediction: Prediction): string => {
  if (prediction === 'HOME_WIN') return match.homeTeamName;
  if (prediction === 'AWAY_WIN') return match.awayTeamName;
  return 'Draw';
};

export default function BetSlip({ selections, onRemove, onClear, onPlaced }: Props) {
  const { refreshUser } = useAuth();
  const [amount, setAmount] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (selections.length === 0) return null;

  const totalOdds = selections.reduce((acc, s) => acc * (oddsForSelection(s.match, s.prediction) ?? 1), 1);
  const amountNum = parseFloat(amount);
  const hasValidAmount = !!amount && !isNaN(amountNum) && amountNum > 0;
  const possibleWin = hasValidAmount ? amountNum * totalOdds : 0;

  const handlePlaceCombo = async () => {
    setError('');
    if (selections.length < 2) {
      setError('Add at least 2 selections to place a combo bet.');
      return;
    }
    if (!hasValidAmount) {
      setError('Enter a valid stake amount greater than 0.');
      return;
    }
    setSubmitting(true);
    try {
      await placeCombo({
        amount: amountNum,
        selections: selections.map((s) => ({ matchId: s.match.id, prediction: s.prediction })),
      });
      await refreshUser();
      setAmount('');
      onClear();
      onPlaced();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message ?? 'Failed to place combo bet.');
      } else {
        setError('Failed to place combo bet.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="bet-slip" data-testid="bet-slip">
      <h3>Bet Slip ({selections.length})</h3>

      <ul className="bet-slip-list">
        {selections.map((s) => (
          <li key={s.match.id} className="bet-slip-item" data-testid={`slip-item-${s.match.id}`}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <TeamCrest name={s.match.homeTeamName} size="sm" />
              <span>
                {s.match.homeTeamName} vs {s.match.awayTeamName} — {predictionLabel(s.match, s.prediction)} @{' '}
                {oddsForSelection(s.match, s.prediction)?.toFixed(2) ?? '—'}
              </span>
            </span>
            <button type="button" className="btn-link" onClick={() => onRemove(s.match.id)} data-testid={`slip-remove-${s.match.id}`}>
              Remove
            </button>
          </li>
        ))}
      </ul>

      <div className="form-group">
        <label>Stake Amount</label>
        <input
          type="number"
          data-testid="combo-amount-input"
          min="0.01"
          step="0.01"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          placeholder="Enter stake amount"
        />
      </div>

      <div className="bet-slip-summary">
        <span>Total Odds: <strong>{totalOdds.toFixed(2)}</strong></span>
        <span>Possible Win: <strong>${possibleWin.toFixed(2)}</strong></span>
      </div>

      {selections.length < 2 && (
        <p className="muted bet-slip-hint">Add at least one more selection to place a combo bet.</p>
      )}

      {error && <div className="error-message">{error}</div>}

      <div className="modal-actions">
        <button type="button" className="btn-secondary" onClick={onClear}>Clear Slip</button>
        <button
          type="button"
          className="btn-primary"
          data-testid="place-combo-btn"
          disabled={selections.length < 2 || submitting}
          onClick={handlePlaceCombo}
        >
          {submitting ? 'Placing...' : 'Place Combo Bet'}
        </button>
      </div>
    </div>
  );
}
