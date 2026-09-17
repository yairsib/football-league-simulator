import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Icon from './Icon';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav className="navbar">
      <div className="navbar-brand">
        <span className="navbar-brand-mark"><Icon name="shield" size={17} /></span>
        <span className="navbar-brand-text">Ligat Ha'al</span>
      </div>
      <div className="navbar-links">
        <NavLink to="/">Dashboard</NavLink>
        <NavLink to="/teams">Teams</NavLink>
        <NavLink to="/stats" data-testid="nav-stats-link">Stats</NavLink>
        <NavLink to="/matches">Matches</NavLink>
        <NavLink to="/league">League Table</NavLink>
        <NavLink to="/bets">My Bets</NavLink>
        <NavLink to="/season-bets">Season Bets</NavLink>
        {user?.role === 'ADMIN' && <NavLink to="/admin" data-testid="nav-admin-link">Admin</NavLink>}
      </div>
      <div className="navbar-user">
        {user && (
          <>
            <span className="balance" data-testid="navbar-balance">Balance: ${Number(user.balance).toFixed(2)}</span>
            <span className="username" data-testid="navbar-username">{user.username}</span>
            <button onClick={handleLogout} className="btn-logout">Logout</button>
          </>
        )}
      </div>
    </nav>
  );
}
