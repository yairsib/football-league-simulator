import { useState, FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as authApi from '../api/authApi';

type Step = 'email' | 'reset';

export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>('email');
  const [email, setEmail] = useState('');
  const [resetToken, setResetToken] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleEmailSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await authApi.forgotPassword(email);
      setResetToken(res.data.resetToken);
      setMessage(res.data.message);
      setStep('reset');
    } catch {
      // Always show generic message — no enumeration
      setMessage('If an account exists for this email, a reset code was sent.');
      setStep('reset');
    } finally {
      setLoading(false);
    }
  };

  const handleResetSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    setLoading(true);
    try {
      await authApi.resetPassword(resetToken, code, newPassword, confirmPassword);
      navigate('/login', { state: { message: 'Password reset successfully. Please log in.' } });
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message ?? 'Reset failed. The code may be invalid or expired.');
      } else {
        setError('Reset failed. The code may be invalid or expired.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page auth-page-forgot">
      <div className="auth-card">
        <h1>Football League Simulator</h1>
        <h2>Forgot Password</h2>

        {step === 'email' && (
          <form onSubmit={handleEmailSubmit}>
            <p>Enter your email address and we will send you a reset code.</p>
            <div className="form-group">
              <label>Email</label>
              <input
                type="email"
                data-testid="forgot-email-input"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoFocus
              />
            </div>
            {error && <div className="error-message">{error}</div>}
            <button type="submit" data-testid="forgot-email-submit-btn" disabled={loading} className="btn-primary btn-full">
              {loading ? 'Sending...' : 'Send Reset Code'}
            </button>
          </form>
        )}

        {step === 'reset' && (
          <form onSubmit={handleResetSubmit}>
            {message && <p className="info-message" data-testid="forgot-info-message">{message}</p>}
            <div className="form-group">
              <label>Reset Code</label>
              <input
                type="text"
                data-testid="reset-code-input"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                maxLength={6}
                placeholder="6-digit code"
                required
                autoFocus
              />
            </div>
            <div className="form-group">
              <label>New Password</label>
              <input
                type="password"
                data-testid="reset-new-password-input"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
              />
            </div>
            <div className="form-group">
              <label>Confirm New Password</label>
              <input
                type="password"
                data-testid="reset-confirm-password-input"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
              />
            </div>
            {error && <div className="error-message" data-testid="reset-error">{error}</div>}
            <button type="submit" data-testid="reset-submit-btn" disabled={loading} className="btn-primary btn-full">
              {loading ? 'Resetting...' : 'Reset Password'}
            </button>
          </form>
        )}

        <p className="auth-link">
          <Link to="/login">Back to login</Link>
        </p>
      </div>
    </div>
  );
}
