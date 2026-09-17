import { useState, FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import AdminOtpModal from '../components/AdminOtpModal';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [otpToken, setOtpToken] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const result = await login(email, password);
      if (result.requiresOtp) {
        setOtpToken(result.adminVerificationToken);
      } else {
        navigate('/');
      }
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } };
        setError(axiosErr.response?.data?.message ?? 'Login failed.');
      } else {
        setError('Login failed.');
      }
    } finally {
      setLoading(false);
    }
  };

  if (otpToken) {
    // Rendered without the auth-card wrapper: AdminOtpModal already provides its own
    // full-viewport overlay, and nesting it inside an animated (transformed) ancestor
    // would make that ancestor the containing block for its position:fixed overlay.
    return (
      <AdminOtpModal
        adminVerificationToken={otpToken}
        onSuccess={() => navigate('/')}
        onCancel={() => setOtpToken(null)}
      />
    );
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Football League Simulator</h1>
        <h2>Login</h2>
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Email</label>
            <input
              type="email"
              data-testid="login-email-input"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoFocus
            />
          </div>
          <div className="form-group">
            <label>Password</label>
            <input
              type="password"
              data-testid="login-password-input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          {error && <div className="error-message" data-testid="login-error">{error}</div>}
          <button type="submit" data-testid="login-submit-btn" disabled={loading} className="btn-primary btn-full">
            {loading ? 'Logging in...' : 'Login'}
          </button>
        </form>
        <p className="auth-link">
          <Link to="/forgot-password" data-testid="forgot-password-link">Forgot password?</Link>
        </p>
        <p className="auth-link">
          No account? <Link to="/register">Register</Link>
        </p>
      </div>
    </div>
  );
}
