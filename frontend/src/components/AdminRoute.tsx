import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Loading from './Loading';
import type { ReactNode } from 'react';

export default function AdminRoute({ children }: { children: ReactNode }) {
  const { user, isLoading } = useAuth();

  if (isLoading) return <Loading />;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'ADMIN') {
    return (
      <div className="page" data-testid="admin-access-denied">
        <h1>Admin Panel</h1>
        <p>Access denied. This page is only available to administrators.</p>
      </div>
    );
  }

  return <>{children}</>;
}
