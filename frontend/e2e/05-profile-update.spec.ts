import { test, expect } from '@playwright/test';
import { registerUser, uniqueEmail, uniqueUsername, getToken } from './helpers';

test.describe('Profile update', () => {
  test('username and email can be updated and the session keeps working afterwards', async ({ page }) => {
    await registerUser(page, 'profile');

    const newUsername = uniqueUsername('renamed');
    const newEmail = uniqueEmail('renamed');

    const card = page.getByTestId('edit-profile-card');
    await expect(card).toBeVisible();

    const tokenBefore = await getToken(page);

    await card.getByTestId('profile-username-input').fill(newUsername);
    await card.getByTestId('profile-email-input').fill(newEmail);
    await card.getByTestId('profile-save-btn').click();

    await expect(card.getByTestId('profile-update-success')).toBeVisible();

    // Navbar reflects the new username immediately.
    await expect(page.getByTestId('navbar-username')).toHaveText(newUsername);

    // A fresh token was issued after the email change (login identity changed).
    const tokenAfter = await getToken(page);
    expect(tokenAfter).not.toBe(tokenBefore);
    expect(tokenAfter).toBeTruthy();

    // The app keeps working with the refreshed token: reload and navigate around.
    await page.reload();
    await expect(page.getByTestId('navbar-username')).toHaveText(newUsername);
    await expect(page).not.toHaveURL(/\/login/);

    await page.goto('/bets');
    await expect(page.getByRole('heading', { name: 'My Bets' })).toBeVisible();

    await page.goto('/');
    await expect(card.getByTestId('profile-username-input')).toHaveValue(newUsername);
    await expect(card.getByTestId('profile-email-input')).toHaveValue(newEmail);
  });
});
