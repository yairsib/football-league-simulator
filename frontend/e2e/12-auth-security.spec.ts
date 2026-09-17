import { test, expect, request } from '@playwright/test';
import {
  ADMIN_EMAIL,
  ADMIN_PASSWORD,
  loginAsAdmin,
  registerUser,
  uniqueEmail,
} from './helpers';

async function getLatestMail(toEmail: string) {
  const ctx = await request.newContext({ baseURL: 'http://localhost:8080' });
  const res = await ctx.get('/api/test/mail');
  const emails = (await res.json()) as Array<{ to: string; code: string; type: string }>;
  return [...emails].reverse().find((e) => e.to === toEmail) ?? null;
}

test.describe('Admin OTP Login', () => {
  test('admin login shows OTP modal and does not redirect until code submitted', async ({ page }) => {
    await page.goto('/login');
    await page.getByTestId('login-email-input').fill(ADMIN_EMAIL);
    await page.getByTestId('login-password-input').fill(ADMIN_PASSWORD);
    await page.getByTestId('login-submit-btn').click();

    await expect(page.getByTestId('admin-otp-modal')).toBeVisible();
    // Should still be on /login (not yet navigated to dashboard)
    expect(page.url()).toContain('/login');
  });

  test('admin login with wrong OTP code shows error', async ({ page }) => {
    await page.goto('/login');
    await page.getByTestId('login-email-input').fill(ADMIN_EMAIL);
    await page.getByTestId('login-password-input').fill(ADMIN_PASSWORD);
    await page.getByTestId('login-submit-btn').click();

    await page.getByTestId('admin-otp-modal').waitFor({ state: 'visible' });
    await page.getByTestId('admin-otp-input').fill('000000');
    await page.getByTestId('admin-otp-submit-btn').click();

    await expect(page.getByTestId('admin-otp-error')).toBeVisible();
    expect(page.url()).toContain('/login');
  });

  test('full admin OTP flow completes and lands on dashboard', async ({ page }) => {
    await loginAsAdmin(page);
    await expect(page).toHaveURL('/');
  });

  test('admin OTP code is delivered to test mail endpoint', async ({ page }) => {
    await page.goto('/login');
    await page.getByTestId('login-email-input').fill(ADMIN_EMAIL);
    await page.getByTestId('login-password-input').fill(ADMIN_PASSWORD);
    await page.getByTestId('login-submit-btn').click();

    await page.getByTestId('admin-otp-modal').waitFor({ state: 'visible' });

    const mail = await getLatestMail(ADMIN_EMAIL);
    expect(mail).not.toBeNull();
    expect(mail!.type).toBe('ADMIN_OTP');
    expect(mail!.code).toMatch(/^\d{6}$/);
  });
});

test.describe('Forgot Password Flow', () => {
  test('forgot password link visible on login page', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByTestId('forgot-password-link')).toBeVisible();
  });

  test('non-existent email shows generic success message', async ({ page }) => {
    await page.goto('/forgot-password');
    await page.getByTestId('forgot-email-input').fill('nobody-ever-existed@example.com');
    await page.getByTestId('forgot-email-submit-btn').click();

    await expect(page.getByTestId('forgot-info-message')).toBeVisible();
    const msg = await page.getByTestId('forgot-info-message').textContent();
    expect(msg).toContain('If an account exists');
  });

  test('existing user can reset password with correct code', async ({ page }) => {
    const user = await registerUser(page, 'resettest');
    await page.goto('/login');
    await page.getByTestId('login-email-input').fill(user.email);
    await page.getByTestId('login-password-input').fill(user.password);
    await page.getByTestId('login-submit-btn').click();
    await page.waitForURL('/');

    // Log out
    await page.evaluate(() => localStorage.removeItem('jwt_token'));
    await page.goto('/forgot-password');

    await page.getByTestId('forgot-email-input').fill(user.email);
    await page.getByTestId('forgot-email-submit-btn').click();

    await page.getByTestId('forgot-info-message').waitFor({ state: 'visible' });

    const mail = await getLatestMail(user.email);
    expect(mail).not.toBeNull();

    await page.getByTestId('reset-code-input').fill(mail!.code);
    await page.getByTestId('reset-new-password-input').fill('NewSecret123!');
    await page.getByTestId('reset-confirm-password-input').fill('NewSecret123!');
    await page.getByTestId('reset-submit-btn').click();

    await page.waitForURL('/login');

    // Verify new password works
    await page.getByTestId('login-email-input').fill(user.email);
    await page.getByTestId('login-password-input').fill('NewSecret123!');
    await page.getByTestId('login-submit-btn').click();
    await page.waitForURL('/');
  });

  test('wrong reset code shows error', async ({ page }) => {
    const user = await registerUser(page, 'resetwrong');

    await page.goto('/forgot-password');
    await page.getByTestId('forgot-email-input').fill(user.email);
    await page.getByTestId('forgot-email-submit-btn').click();

    await page.getByTestId('forgot-info-message').waitFor({ state: 'visible' });

    await page.getByTestId('reset-code-input').fill('000000');
    await page.getByTestId('reset-new-password-input').fill('NewSecret123!');
    await page.getByTestId('reset-confirm-password-input').fill('NewSecret123!');
    await page.getByTestId('reset-submit-btn').click();

    await expect(page.getByTestId('reset-error')).toBeVisible();
  });

  test('admin email registration is blocked', async ({ page }) => {
    await page.goto('/register');
    await page.getByTestId('register-username-input').fill('hackerattempt');
    await page.getByTestId('register-email-input').fill(ADMIN_EMAIL);
    await page.getByTestId('register-password-input').fill('Secret123');
    await page.getByTestId('register-submit-btn').click();

    await expect(page.getByTestId('register-error')).toBeVisible();
    expect(page.url()).toContain('/register');
  });
});
