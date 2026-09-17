import { useState } from 'react';
import type { BetResponse, HandicapSelection, Prediction } from '../types';
import { editBet, editCombo, editCorrectScoreBet, editHandicapBet, editChampionBet, editTopScorerBet } from '../api/betsApi';
import { useAuth } from '../context/AuthContext';
import TeamCrest from './TeamCrest';

interface Props {
  bet: BetResponse;
  onClose: () => void;
  onSuccess: () => void;
}

const labelFor = (bet: BetResponse, prediction: Prediction): string => {
  if (prediction === 'HOME_WIN') return bet.homeTeamName ?? 'Home';
  if (prediction === 'AWAY_WIN') return bet.awayTeamName ?? 'Away';
  return 'Draw';
};

const HANDICAP_LABELS: Record<HandicapSelection, string> = {
  HOME_MINUS_ONE: 'Home -1',
  HANDICAP_DRAW: 'Handicap Draw',
  AWAY_PLUS_ONE: 'Away +1',
};

export default function EditBetModal({ bet, onClose, onSuccess }: Props) {
  const { refreshUser } = useAuth();
  const isCombo = bet.betType === 'COMBO';
  const isCorrectScore = bet.market === 'CORRECT_SCORE';
  const isHandicap = bet.market === 'HANDICAP';
  const isChampion = bet.market === 'CHAMPION';
  const isTopScorer = bet.market === 'TOP_SCORER';
  const [amount, setAmount] = useState(String(bet.amount));
  const [prediction, setPrediction] = useState<Prediction>(bet.prediction ?? 'HOME_WIN');
  const [homeGoals, setHomeGoals] = useState(String(bet.predictedHomeGoals ?? 0));
  const [awayGoals, setAwayGoals] = useState(String(bet.predictedAwayGoals ?? 0));
  const [handicapSelection, setHandicapSelection] = useState<HandicapSelection>(bet.handicapSelection ?? 'HOME_MINUS_ONE');
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
    if (isCorrectScore) {
      const homeGoalsNum = parseInt(homeGoals, 10);
      const awayGoalsNum = parseInt(awayGoals, 10);
      if (isNaN(homeGoalsNum) || isNaN(awayGoalsNum) || homeGoalsNum < 0 || homeGoalsNum > 6 || awayGoalsNum < 0 || awayGoalsNum > 6) {
        setError('Enter valid scores between 0 and 6.');
        return;
      }
    }
    setSubmitting(true);
    try {
      if (isCombo) {
        await editCombo(bet.id, {
          amount: amountNum,
          selections: bet.selections.map((s) => ({ matchId: s.matchId, prediction: s.prediction })),
        });
      } else if (isCorrectScore) {
        await editCorrectScoreBet(bet.id, {
          amount: amountNum,
          homeGoals: parseInt(homeGoals, 10),
          awayGoals: parseInt(awayGoals, 10),
        });
      } else if (isHandicap) {
        await editHandicapBet(bet.id, { amount: amountNum, selection: handicapSelection });
      } else if (isChampion) {
        await editChampionBet(bet.id, { teamId: bet.selectedTeamId!, amount: amountNum });
      } else if (isTopScorer) {
        await editTopScorerBet(bet.id, { playerId: bet.selectedPlayerId!, amount: amountNum });
      } else {
        await editBet(bet.id, { amount: amountNum, prediction });
      }
      await refreshUser();
      onSuccess();
      onClose();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message ?? 'Failed to update bet.');
      } else {
        setError('Failed to update bet.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" data-testid="edit-bet-modal" onClick={(e) => e.stopPropagation()}>
        <h2>Edit Bet</h2>

        <form onSubmit={handleSubmit}>
          {isCombo ? (
            <>
              <p className="modal-match">Combo Bet — {bet.selections.length} selections</p>
              <ul className="bet-slip-list">
                {bet.selections.map((s) => (
                  <li key={s.matchId} className="bet-slip-item">
                    <span>
                      {s.homeTeamName} vs {s.awayTeamName} — {labelFor({ ...bet, homeTeamName: s.homeTeamName, awayTeamName: s.awayTeamName }, s.prediction)} @{' '}
                      {Number(s.odds).toFixed(2)}
                    </span>
                  </li>
                ))}
              </ul>
              <p className="muted">Selections cannot be changed here — only the stake amount.</p>
            </>
          ) : isCorrectScore ? (
            <>
              <div className="modal-match-teams">
                <TeamCrest name={bet.homeTeamName ?? ''} size="sm" />
                <span className="modal-match-vs">vs</span>
                <TeamCrest name={bet.awayTeamName ?? ''} size="sm" />
              </div>
              <p className="modal-match">{bet.homeTeamName} vs {bet.awayTeamName}</p>
              <div className="form-group">
                <label>Predicted Score</label>
                <div className="correct-score-edit-inputs">
                  <input
                    type="number"
                    data-testid="edit-correct-score-home-input"
                    min="0"
                    max="6"
                    value={homeGoals}
                    onChange={(e) => setHomeGoals(e.target.value)}
                    required
                  />
                  <span>-</span>
                  <input
                    type="number"
                    data-testid="edit-correct-score-away-input"
                    min="0"
                    max="6"
                    value={awayGoals}
                    onChange={(e) => setAwayGoals(e.target.value)}
                    required
                  />
                </div>
              </div>
              <p className="muted">Changing the predicted score re-snapshots odds at the match's current odds.</p>
            </>
          ) : isHandicap ? (
            <>
              <div className="modal-match-teams">
                <TeamCrest name={bet.homeTeamName ?? ''} size="sm" />
                <span className="modal-match-vs">vs</span>
                <TeamCrest name={bet.awayTeamName ?? ''} size="sm" />
              </div>
              <p className="modal-match">{bet.homeTeamName} vs {bet.awayTeamName}</p>
              <div className="prediction-options">
                {(['HOME_MINUS_ONE', 'HANDICAP_DRAW', 'AWAY_PLUS_ONE'] as HandicapSelection[]).map((sel) => (
                  <label key={sel} className={`prediction-option ${handicapSelection === sel ? 'selected' : ''}`}>
                    <input
                      type="radio"
                      name="edit-handicap-selection"
                      value={sel}
                      checked={handicapSelection === sel}
                      onChange={() => setHandicapSelection(sel)}
                    />
                    <span className="pred-label">{HANDICAP_LABELS[sel]}</span>
                  </label>
                ))}
              </div>
              <p className="muted">Changing the selection re-snapshots odds at the match's current odds.</p>
            </>
          ) : isChampion ? (
            <>
              <p className="modal-match">Season Champion Bet</p>
              <p>Selected team: <strong>{bet.selectedTeamName}</strong></p>
              <p className="muted">You can only change the stake amount. The team selection is locked at placement.</p>
            </>
          ) : isTopScorer ? (
            <>
              <p className="modal-match">Top Scorer Bet</p>
              <p>Selected player: <strong>{bet.selectedPlayerName}</strong> ({bet.selectedTeamName})</p>
              <p className="muted">You can only change the stake amount. The player selection is locked at placement.</p>
            </>
          ) : (
            <>
              <div className="modal-match-teams">
                <TeamCrest name={bet.homeTeamName ?? ''} size="sm" />
                <span className="modal-match-vs">vs</span>
                <TeamCrest name={bet.awayTeamName ?? ''} size="sm" />
              </div>
              <p className="modal-match">{bet.homeTeamName} vs {bet.awayTeamName}</p>
              <div className="prediction-options">
                {(['HOME_WIN', 'DRAW', 'AWAY_WIN'] as Prediction[]).map((p) => (
                  <label key={p} className={`prediction-option ${prediction === p ? 'selected' : ''}`}>
                    <input
                      type="radio"
                      name="edit-prediction"
                      value={p}
                      checked={prediction === p}
                      onChange={() => setPrediction(p)}
                    />
                    <span className="pred-label">{labelFor(bet, p)}</span>
                  </label>
                ))}
              </div>
              <p className="muted">Changing the prediction re-snapshots odds at the match's current odds.</p>
            </>
          )}

          <div className="form-group">
            <label>Amount</label>
            <input
              type="number"
              data-testid="edit-bet-amount-input"
              min="0.01"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              required
            />
          </div>

          {error && <div className="error-message">{error}</div>}

          <div className="modal-actions">
            <button type="button" onClick={onClose} className="btn-secondary">Cancel</button>
            <button type="submit" data-testid="confirm-edit-bet-btn" disabled={submitting} className="btn-primary">
              {submitting ? 'Saving...' : 'Save Changes'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
