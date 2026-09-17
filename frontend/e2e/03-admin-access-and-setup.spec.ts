import { test, expect } from '@playwright/test';
import { loginAsAdmin, registerUser } from './helpers';

test.describe('Admin user can access admin', () => {
  test('admin link is visible, admin page loads, and league setup actions work', async ({ page }) => {
    await loginAsAdmin(page);

    await expect(page.getByTestId('navbar-username')).toBeVisible();
    await expect(page.getByTestId('nav-admin-link')).toBeVisible();

    await page.getByTestId('nav-admin-link').click();
    await expect(page.getByTestId('admin-page')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Admin Panel' })).toBeVisible();

    // Seed teams — tolerate "already seeded" so the suite is re-runnable
    // against a backend that wasn't restarted between runs.
    await page.getByTestId('seed-teams-btn').click();
    await expect(page.getByTestId('admin-message').or(page.getByTestId('admin-error'))).toBeVisible();

    // Generate schedule — same idempotency tolerance.
    await page.getByTestId('generate-schedule-btn').click();
    await expect(page.getByTestId('admin-message').or(page.getByTestId('admin-error'))).toBeVisible();

    // Round actions are now visible (the schedule produced rounds).
    await expect(page.getByTestId('round-select')).toBeVisible();
    await expect(page.getByTestId('open-betting-btn')).toBeVisible();
    await expect(page.getByTestId('simulate-round-btn')).toBeVisible();
  });
});

test.describe('Admin can validate and import real league data', () => {
  test('Validate/Import League Data buttons work and produce 14 complete teams', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByTestId('nav-admin-link').click();
    await expect(page.getByTestId('admin-page')).toBeVisible();

    // Buttons are visible to ADMIN only.
    await expect(page.getByTestId('validate-league-data-btn')).toBeVisible();
    await expect(page.getByTestId('import-league-data-btn')).toBeVisible();

    // Validate the bundled real-data file.
    await page.getByTestId('validate-league-data-btn').click();
    await expect(page.getByTestId('league-data-validation-result')).toBeVisible();
    // All 14 squads are now real and complete, so there are no warnings to show.
    await expect(page.getByTestId('league-data-validation-warnings')).toHaveCount(0);

    // Squad-completeness summary table: all 14 teams have real, complete squads.
    const summaryTable = page.getByTestId('squad-summary-table');
    await expect(summaryTable).toBeVisible();
    const completeStatuses = page.locator('[data-testid^="squad-status-"]', { hasText: 'COMPLETE' });
    await expect(completeStatuses).toHaveCount(14);

    // Import the bundled real-data file.
    await page.getByTestId('import-league-data-btn').click();
    const importResult = page.getByTestId('league-data-import-result');
    await expect(importResult).toBeVisible();
    await expect(importResult).toContainText('14 teams');
    await expect(page.getByTestId('league-data-import-warnings')).toHaveCount(0);

    // Teams page reflects the 14 imported teams.
    await page.goto('/teams');
    await expect(page.locator('table.data-table tbody tr')).toHaveCount(14);
  });
});

test.describe('Regular user still cannot access admin after league-data feature is added', () => {
  test('admin link hidden and admin page shows access-denied for a regular user', async ({ page }) => {
    await registerUser(page, 'regularld');

    await expect(page.getByTestId('navbar-username')).toBeVisible();
    await expect(page.getByTestId('nav-admin-link')).toHaveCount(0);

    await page.goto('/admin');
    await expect(page.getByTestId('admin-access-denied')).toBeVisible();
    await expect(page.getByTestId('validate-league-data-btn')).toHaveCount(0);
    await expect(page.getByTestId('import-league-data-btn')).toHaveCount(0);
  });
});
