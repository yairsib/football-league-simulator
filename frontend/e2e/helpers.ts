import { request, type Page } from '@playwright/test';

/**
 * Must match application-test.properties (app.admin.email / app.admin.initial-password).
 * Admin account is created by AdminBootstrapService at startup — never by registration.
 */
export const ADMIN_EMAIL = 'admin@test.com';
export const ADMIN_PASSWORD = 'Admin123!';
export const ADMIN_USERNAME = 'admin';

export const STRONG_PASSWORD = 'Secret123';

export function uniqueEmail(prefix: string): string {
  return `${prefix}+${Date.now()}_${Math.floor(Math.random() * 100000)}@example.com`;
}

export function uniqueUsername(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.floor(Math.random() * 100000)}`;
}

export interface RegisteredUser {
  username: string;
  email: string;
  password: string;
}

/** Registers a brand new regular user via the UI and lands on the dashboard. */
export async function registerUser(page: Page, prefix = 'user'): Promise<RegisteredUser> {
  const user: RegisteredUser = {
    username: uniqueUsername(prefix),
    email: uniqueEmail(prefix),
    password: STRONG_PASSWORD,
  };

  await page.goto('/register');
  await page.getByTestId('register-username-input').fill(user.username);
  await page.getByTestId('register-email-input').fill(user.email);
  await page.getByTestId('register-password-input').fill(user.password);
  await page.getByTestId('register-submit-btn').click();
  await page.waitForURL('/');

  return user;
}

/**
 * Fetches the most recent OTP code sent to a given email address from the
 * FakeEmailService via the test-only GET /api/test/mail endpoint.
 * Only available when SPRING_PROFILES_ACTIVE=test and app.otp.test-mode=true.
 */
async function getLatestOtpCode(toEmail: string): Promise<string> {
  const apiContext = await request.newContext({ baseURL: 'http://localhost:8080' });
  const res = await apiContext.get('/api/test/mail');
  const emails = await res.json() as Array<{ to: string; code: string; type: string }>;
  const match = [...emails].reverse().find((e) => e.to === toEmail);
  if (!match) throw new Error(`No OTP email found for ${toEmail}`);
  return match.code;
}

/**
 * Logs in as the bootstrapped admin account.
 * Flow: UI login → OTP modal → fetch code from test mail endpoint → fill + submit.
 */
export async function loginAsAdmin(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('login-email-input').fill(ADMIN_EMAIL);
  await page.getByTestId('login-password-input').fill(ADMIN_PASSWORD);
  await page.getByTestId('login-submit-btn').click();

  // Wait for the OTP modal to appear
  await page.getByTestId('admin-otp-modal').waitFor({ state: 'visible' });

  // Retrieve the OTP code from the backend test mail endpoint
  const code = await getLatestOtpCode(ADMIN_EMAIL);

  await page.getByTestId('admin-otp-input').fill(code);
  await page.getByTestId('admin-otp-submit-btn').click();
  await page.waitForURL('/');
}

export function getToken(page: Page): Promise<string | null> {
  return page.evaluate(() => localStorage.getItem('jwt_token'));
}
