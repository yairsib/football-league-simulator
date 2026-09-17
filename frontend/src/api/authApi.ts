import apiClient from './apiClient';
import type { AuthResponse, UserResponse } from '../types';

export const register = (username: string, email: string, password: string) =>
  apiClient.post<AuthResponse>('/auth/register', { username, email, password });

export const login = (email: string, password: string) =>
  apiClient.post<AuthResponse>('/auth/login', { email, password });

export const verifyAdminCode = (adminVerificationToken: string, code: string) =>
  apiClient.post<AuthResponse>('/auth/verify-admin-code', { adminVerificationToken, code });

export const forgotPassword = (email: string) =>
  apiClient.post<{ message: string; resetToken: string }>('/auth/forgot-password', { email });

export const resetPassword = (
  resetToken: string,
  code: string,
  newPassword: string,
  confirmPassword: string,
) =>
  apiClient.post<{ message: string }>('/auth/reset-password', {
    resetToken,
    code,
    newPassword,
    confirmPassword,
  });

export const getMe = () =>
  apiClient.get<UserResponse>('/users/me');

export const updateProfile = (username: string, email: string) =>
  apiClient.put<AuthResponse>('/users/me', { username, email });
