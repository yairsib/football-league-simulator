import { test, expect } from '@playwright/test';
import { loginAsAdmin, registerUser } from './helpers';

test.describe('Player stats pages', () => {
  test('Stats nav link, stats page sections, and Teams "View Squad" all render', async ({ browser }) => {
    const adminContext = await browser.newContext();
    const userContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    const userPage = await userContext.newPage();

    // Ensure teams + real squads exist (idempotent — tolerate "already seeded/exists").
    await loginAsAdmin(adminPage);
    await adminPage.goto('/admin');
    await adminPage.getByTestId('seed-teams-btn').click();
    await expect(adminPage.getByTestId('admin-message').or(adminPage.getByTestId('admin-error'))).toBeVisible();
    // Import is only allowed pre-season (Milestone 41): once a round has been played the button is
    // disabled and the API answers 409. Squads already exist from the seed, so only import when allowed.
    await expect(adminPage.getByTestId('admin-overview')).toBeVisible();
    const importBtn = adminPage.getByTestId('import-league-data-btn');
    if (await importBtn.isEnabled()) {
      await importBtn.click();
      await expect(adminPage.getByTestId('league-data-import-result')).toBeVisible();
    }
    await adminPage.getByTestId('generate-schedule-btn').click();
    await expect(adminPage.getByTestId('admin-message').or(adminPage.getByTestId('admin-error'))).toBeVisible();

    await registerUser(userPage, 'statsviewer');

    await test.step('Stats nav link appears for a logged-in user', async () => {
      const navLink = userPage.getByTestId('nav-stats-link');
      await expect(navLink).toBeVisible();
      await navLink.click();
      await userPage.waitForURL('/stats');
    });

    await test.step('Stats page loads with Top Scorers, Top Assists, and Red Cards sections', async () => {
      await expect(userPage.getByRole('heading', { name: 'Player Stats' })).toBeVisible();

      await expect(userPage.getByTestId('stats-tab-button-goals')).toBeVisible();
      await expect(userPage.getByTestId('stats-tab-button-assists')).toBeVisible();
      await expect(userPage.getByTestId('stats-tab-button-redCards')).toBeVisible();

      await expect(userPage.getByTestId('stats-tab-goals')).toBeVisible();
      const goalsTable = userPage.getByTestId('stats-tab-goals').locator('table');
      await expect(goalsTable.or(userPage.getByText('No player stats available yet.'))).toBeVisible();

      await userPage.getByTestId('stats-tab-button-assists').click();
      await expect(userPage.getByTestId('stats-tab-assists')).toBeVisible();

      await userPage.getByTestId('stats-tab-button-redCards').click();
      await expect(userPage.getByTestId('stats-tab-redCards')).toBeVisible();
    });

    await test.step('Teams page has a "View Squad" button that shows player rows', async () => {
      await userPage.goto('/teams');
      const viewSquadBtn = userPage.locator('[data-testid^="view-squad-btn-"]').first();
      await expect(viewSquadBtn).toBeVisible();
      await viewSquadBtn.click();

      const modal = userPage.getByTestId('team-squad-modal');
      await expect(modal).toBeVisible();

      const squadTable = modal.getByTestId('team-squad-table');
      await expect(squadTable.or(modal.getByText('No squad data available for this team.'))).toBeVisible();

      const rows = modal.locator('[data-testid^="team-squad-row-"]');
      const rowCount = await rows.count();
      if (rowCount > 0) {
        await rows.first().click();
        const detailsModal = userPage.getByTestId('player-details-modal');
        await expect(detailsModal).toBeVisible();
        await detailsModal.getByTestId('close-player-details-btn').click();
        await expect(detailsModal).toBeHidden();
      }

      await modal.getByTestId('close-team-squad-btn').click();
      await expect(modal).toBeHidden();
    });

    await adminContext.close();
    await userContext.close();
  });
});
