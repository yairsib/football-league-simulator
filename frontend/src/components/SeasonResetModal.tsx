import { useState } from 'react';
import { resetSeason } from '../api/adminApi';
import type { SeasonResetResponse } from '../types';

interface Props {
  onSuccess: (result: SeasonResetResponse) => void;
  onCancel: () => void;
}

export default function SeasonResetModal({ onSuccess, onCancel }: Props) {
  const [regenerateSchedule, setRegenerateSchedule] = useState(true);
  const [resetUserBalances, setResetUserBalances] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleConfirm = async () => {
    setError('');
    setLoading(true);
    try {
      const res = await resetSeason({ confirm: true, regenerateSchedule, resetUserBalances });
      onSuccess(res.data);
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message ?? 'Reset failed. Please try again.');
      } else {
        setError('Reset failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" data-testid="season-reset-modal" onClick={(e) => e.stopPropagation()}>
        <h2 style={{ color: 'var(--danger)' }}>Reset Season</h2>

        <p style={{ marginBottom: '1rem', lineHeight: '1.6', color: 'var(--text-2)', fontSize: '0.9rem' }}>
          This will reset the current season, remove all match results, events, bets, and
          standings, and return the league to before Round 1.
        </p>

        <div className="danger-modal-warning">
          Admin and user accounts will remain. This action cannot be undone.
        </div>

        <div className="checkbox-group">
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={regenerateSchedule}
              onChange={e => setRegenerateSchedule(e.target.checked)}
              disabled={loading}
            />
            Regenerate schedule (recommended — creates fresh fixtures with new weather)
          </label>

          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={resetUserBalances}
              onChange={e => setResetUserBalances(e.target.checked)}
              disabled={loading}
            />
            Reset all user balances to 1000{' '}
            <span className="muted">(unchecked = preserve balances, refund open bets)</span>
          </label>
        </div>

        {error && <div className="error-message">{error}</div>}

        <div className="modal-actions">
          <button
            type="button"
            onClick={onCancel}
            disabled={loading}
            className="btn-secondary"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={loading}
            className="btn-danger"
            data-testid="confirm-reset-btn"
          >
            {loading ? 'Resetting...' : 'Confirm Reset'}
          </button>
        </div>
      </div>
    </div>
  );
}
