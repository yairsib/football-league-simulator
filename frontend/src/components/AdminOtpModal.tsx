import { useState, FormEvent } from 'react';
import { useAuth } from '../context/AuthContext';
import Icon from './Icon';

interface Props {
  adminVerificationToken: string;
  onSuccess: () => void;
  onCancel: () => void;
}

export default function AdminOtpModal({ adminVerificationToken, onSuccess, onCancel }: Props) {
  const { completeAdminLogin } = useAuth();
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await completeAdminLogin(adminVerificationToken, code);
      onSuccess();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message ?? 'Invalid code. Please try again.');
      } else {
        setError('Invalid code. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" data-testid="admin-otp-modal">
      <div className="modal otp-modal">
        <div className="otp-modal-icon"><Icon name="lock" size={22} /></div>
        <h2 style={{ textAlign: 'center' }}>Admin Verification</h2>
        <p className="muted" style={{ textAlign: 'center', marginBottom: '1.25rem' }}>
          A verification code has been sent to your email. Enter it below to complete login.
        </p>
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Verification Code</label>
            <input
              type="text"
              className="otp-code-input"
              data-testid="admin-otp-input"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              maxLength={6}
              placeholder="••••••"
              autoFocus
              required
            />
          </div>
          {error && <div className="error-message" data-testid="admin-otp-error">{error}</div>}
          <div className="modal-actions">
            <button type="submit" data-testid="admin-otp-submit-btn" disabled={loading} className="btn-primary">
              {loading ? 'Verifying...' : 'Verify'}
            </button>
            <button type="button" onClick={onCancel} className="btn-secondary" disabled={loading}>
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
