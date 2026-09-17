import { test, expect } from '@playwright/test';
import { uniqueEmail, uniqueUsername, STRONG_PASSWORD } from './helpers';

test.describe('Password validation', () => {
  test('weak password is rejected and a strong password is accepted', async ({ page }) => {
    await page.goto('/register');

    // Weak password: fails the frontend's minLength constraint, so submit a
    // borderline-weak-but-long-enough password to force a backend rejection
    // (no uppercase/digit) and assert on the readable error message.
    await page.getByTestId('register-username-input').fill(uniqueUsername('weakpw'));
    await page.getByTestId('register-email-input').fill(uniqueEmail('weakpw'));
    await page.getByTestId('register-password-input').fill('alllowercase');
    await page.getByTestId('register-submit-btn').click();

    await expect(page.getByTestId('register-error')).toBeVisible();
    await expect(page).toHaveURL(/\/register/);

    // Now retry with a strong password — registration should succeed and land on the dashboard.
    await page.getByTestId('register-username-input').fill(uniqueUsername('strongpw'));
    await page.getByTestId('register-email-input').fill(uniqueEmail('strongpw'));
    await page.getByTestId('register-password-input').fill(STRONG_PASSWORD);
    await page.getByTestId('register-submit-btn').click();

    await page.waitForURL('/');
    await expect(page.getByTestId('navbar-balance')).toContainText('1000');
  });
});
