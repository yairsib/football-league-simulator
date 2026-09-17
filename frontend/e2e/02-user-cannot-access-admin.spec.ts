import { test, expect } from '@playwright/test';
import { registerUser, getToken } from './helpers';

test.describe('Regular user cannot access admin', () => {
  test('admin link is hidden, /admin is blocked, and /api/admin/** returns 403', async ({ page }) => {
    await registerUser(page, 'regular');

    // Dashboard loaded and Admin link is not visible.
    await expect(page.getByTestId('navbar-username')).toBeVisible();
    await expect(page.getByTestId('nav-admin-link')).toHaveCount(0);

    // Direct navigation to /admin shows access-denied, not the admin tools.
    await page.goto('/admin');
    await expect(page.getByTestId('admin-access-denied')).toBeVisible();
    await expect(page.getByTestId('admin-page')).toHaveCount(0);

    // Direct API call to an admin endpoint is forbidden for a regular user.
    const token = await getToken(page);
    const response = await page.request.post('/api/admin/seed', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(response.status()).toBe(403);
  });
});
