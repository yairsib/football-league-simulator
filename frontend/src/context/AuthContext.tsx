import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import type { UserResponse } from '../types';
import * as authApi from '../api/authApi';

type LoginResult =
  | { requiresOtp: false }
  | { requiresOtp: true; adminVerificationToken: string };

interface AuthContextValue {
  user: UserResponse | null;
  token: string | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<LoginResult>;
  completeAdminLogin: (adminVerificationToken: string, code: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
  updateProfile: (username: string, email: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [token, setToken] = useState<string | null>(localStorage.getItem('jwt_token'));
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const storedToken = localStorage.getItem('jwt_token');
    if (storedToken) {
      authApi.getMe()
        .then((res) => setUser(res.data))
        .catch(() => {
          localStorage.removeItem('jwt_token');
          setToken(null);
        })
        .finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
  }, []);

  const login = async (email: string, password: string): Promise<LoginResult> => {
    const res = await authApi.login(email, password);
    if (res.data.adminVerificationRequired) {
      return { requiresOtp: true, adminVerificationToken: res.data.adminVerificationToken! };
    }
    localStorage.setItem('jwt_token', res.data.token!);
    setToken(res.data.token!);
    setUser(res.data.user!);
    return { requiresOtp: false };
  };

  const completeAdminLogin = async (adminVerificationToken: string, code: string) => {
    const res = await authApi.verifyAdminCode(adminVerificationToken, code);
    localStorage.setItem('jwt_token', res.data.token!);
    setToken(res.data.token!);
    setUser(res.data.user!);
  };

  const register = async (username: string, email: string, password: string) => {
    const res = await authApi.register(username, email, password);
    localStorage.setItem('jwt_token', res.data.token!);
    setToken(res.data.token!);
    setUser(res.data.user!);
  };

  const logout = () => {
    localStorage.removeItem('jwt_token');
    setToken(null);
    setUser(null);
  };

  const refreshUser = async () => {
    const res = await authApi.getMe();
    setUser(res.data);
  };

  const updateProfile = async (username: string, email: string) => {
    const res = await authApi.updateProfile(username, email);
    localStorage.setItem('jwt_token', res.data.token!);
    setToken(res.data.token!);
    setUser(res.data.user!);
  };

  return (
    <AuthContext.Provider value={{ user, token, isLoading, login, completeAdminLogin, register, logout, refreshUser, updateProfile }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
