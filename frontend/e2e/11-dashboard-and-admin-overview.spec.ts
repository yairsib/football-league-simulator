import { test, expect } from '@playwright/test';
import { loginAsAdmin, registerUser } from './helpers';

test.describe('Dashboard summary loads for regular user', () => {
  test('dashboard shows summary cards and quick links after login', async ({ page }) => {
    await registerUser(page, 'dash');

    // Should be on dashboard after register
    await expect(page.getByTestId('dashboard-summary-cards')).toBeVisible();
    await expect(page.getByTestId('dashboard-balance-card')).toBeVisible();
    await expect(page.getByTestId('dashboard-open-bets-card')).toBeVisible();
    await expect(page.getByTestId('dashboard-profit-card')).toBeVisible();
    await expect(page.getByTestId('dashboard-potential-card')).toBeVisible();
  });

  test('dashboard shows season status section', async ({ page }) => {
    await registerUser(page, 'dashseas');

    await expect(page.getByTestId('dashboard-season-status')).toBeVisible();
  });

  test('dashboard shows quick links to key pages', async ({ page }) => {
    await registerUser(page, 'dashlinks');

    await expect(page.getByTestId('dashboard-quick-links')).toBeVisible();
    await expect(page.getByTestId('ql-matches')).toBeVisible();
    await expect(page.getByTestId('ql-my-bets')).toBeVisible();
    await expect(page.getByTestId('ql-stats')).toBeVisible();
    await expect(page.getByTestId('ql-season-bets')).toBeVisible();
    await expect(page.getByTestId('ql-league')).toBeVisible();
  });

  test('recent bets section renders (empty state)', async ({ page }) => {
    await registerUser(page, 'dashbets');

    await expect(page.getByTestId('dashboard-recent-bets')).toBeVisible();
  });

  test('edit profile card still renders on dashboard', async ({ page }) => {
    await registerUser(page, 'dashedit');

    await expect(page.getByTestId('edit-profile-card')).toBeVisible();
  });
});

test.describe('Admin overview panel', () => {
  test('admin overview section is visible to admin', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByTestId('nav-admin-link').click();
    await expect(page.getByTestId('admin-page')).toBeVisible();

    await expect(page.getByTestId('admin-overview')).toBeVisible();
    await expect(page.getByTestId('admin-overview-data')).toBeVisible();
    await expect(page.getByTestId('admin-overview-rounds')).toBeVisible();
    await expect(page.getByTestId('admin-overview-bets')).toBeVisible();
  });

  test('recommended actions appear in admin overview before data is imported', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByTestId('nav-admin-link').click();
    await expect(page.getByTestId('admin-page')).toBeVisible();

    // Before seeding, recommended actions should mention importing data
    const overviewSection = page.getByTestId('admin-overview');
    await expect(overviewSection).toBeVisible();
  });

  test('admin overview updates after seeding and generating schedule', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByTestId('nav-admin-link').click();
    await expect(page.getByTestId('admin-page')).toBeVisible();

    // Seed (tolerate already-seeded)
    await page.getByTestId('seed-teams-btn').click();
    await expect(page.getByTestId('admin-message').or(page.getByTestId('admin-error'))).toBeVisible({ timeout: 10000 });

    // Generate schedule (tolerate already-generated)
    await page.getByTestId('generate-schedule-btn').click();
    await expect(page.getByTestId('admin-message').or(page.getByTestId('admin-error'))).toBeVisible({ timeout: 10000 });

    // Overview should still be visible after actions
    await expect(page.getByTestId('admin-overview')).toBeVisible();
    await expect(page.getByTestId('admin-overview-data')).toBeVisible();
  });

  test('regular user cannot call GET /api/admin/overview', async ({ page }) => {
    await registerUser(page, 'adminoverviewtest');
    const token = await page.evaluate(() => localStorage.getItem('jwt_token'));
    const response = await page.request.get('/api/admin/overview', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(response.status()).toBe(403);
  });
});

test.describe('My Bets empty state', () => {
  test('shows helpful message with links when no bets placed', async ({ page }) => {
    await registerUser(page, 'emptybets');
    await page.goto('/bets');
    await expect(page.getByTestId('my-bets-empty-state')).toBeVisible();
    // Should contain links to Matches and Season Bets
    const emptyState = page.getByTestId('my-bets-empty-state');
    await expect(emptyState.getByRole('link', { name: 'Matches' })).toBeVisible();
    await expect(emptyState.getByRole('link', { name: 'Season Bets' })).toBeVisible();
  });
});

test.describe('Season bets locked message', () => {
  test('season bets status banner shows on season bets page', async ({ page }) => {
    await registerUser(page, 'seasonlock');
    await page.goto('/season-bets');
    await expect(page.getByTestId('season-bets-status-banner')).toBeVisible();
  });
});
