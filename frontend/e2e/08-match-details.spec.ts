import { test, expect } from '@playwright/test';
import { loginAsAdmin, registerUser } from './helpers';

test.describe('Match details modal (Milestone 22)', () => {
  test('View Details opens a modal with score/status/weather/odds, an events timeline, and an unavailable-players summary', async ({ browser }) => {
    const adminContext = await browser.newContext();
    const userContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    const userPage = await userContext.newPage();

    // Ensure teams + schedule exist (idempotent — tolerate "already seeded/exists").
    await loginAsAdmin(adminPage);
    await adminPage.goto('/admin');
    await adminPage.getByTestId('seed-teams-btn').click();
    await expect(adminPage.getByTestId('admin-message').or(adminPage.getByTestId('admin-error'))).toBeVisible();
    await adminPage.getByTestId('generate-schedule-btn').click();
    await expect(adminPage.getByTestId('admin-message').or(adminPage.getByTestId('admin-error'))).toBeVisible();

    await registerUser(userPage, 'detailsviewer');
    await userPage.goto('/matches');
    await userPage.getByTestId('round-tab-1').click();

    const firstCard = userPage.locator('[data-testid^="match-card-"]').first();
    await expect(firstCard).toBeVisible();
    const cardTestId = await firstCard.getAttribute('data-testid');
    const matchId = cardTestId!.replace('match-card-', '');

    await test.step('View Details button opens the match details modal', async () => {
      const viewDetailsBtn = userPage.getByTestId(`view-details-btn-${matchId}`);
      await expect(viewDetailsBtn).toBeVisible();
      await viewDetailsBtn.click();

      const modal = userPage.getByTestId('match-details-modal');
      await expect(modal).toBeVisible();

      // Status/score line always renders with a status badge.
      await expect(modal.getByTestId('match-details-status')).toBeVisible();
      await expect(modal.getByTestId('match-details-status')).toContainText(/SCHEDULED|BETTING_OPEN|IN_PROGRESS|FINISHED/);

      // Weather is always assigned at schedule generation, so it should be visible.
      await expect(modal.getByTestId('match-details-weather')).toBeVisible();
      await expect(modal.getByTestId('match-details-weather')).toContainText(/CLEAR|RAIN|WIND|HOT|COLD|STORM/);

      // Events timeline section always renders — either rows or an empty state.
      const eventsSection = modal.getByTestId('match-details-events');
      await expect(eventsSection).toBeVisible();
      const emptyState = modal.getByTestId('match-details-events-empty');
      if (await emptyState.count() > 0) {
        await expect(emptyState).toContainText(/No events yet|No recorded events/);
      } else {
        const rows = modal.locator('[data-testid^="match-details-event-"]');
        const rowCount = await rows.count();
        expect(rowCount).toBeGreaterThan(0);
        for (let i = 0; i < rowCount; i++) {
          const label = await rows.nth(i).getAttribute('aria-label');
          expect(label).toMatch(/^\d+' (GOAL|RED CARD|Substitution) — .+/);
        }
      }

      // Unavailable-players summary (per side) renders once lineups are loaded.
      const unavailableSection = modal.getByTestId('match-details-unavailable');
      await expect(unavailableSection).toBeVisible();
      await expect(modal.getByTestId('match-details-home-unavailable')).toContainText(/\d+ unavailable/);
      await expect(modal.getByTestId('match-details-away-unavailable')).toContainText(/\d+ unavailable/);

      await test.step('View Lineups from within the details modal still works', async () => {
        await modal.getByTestId('match-details-view-lineups-btn').click();
        const lineupsModal = userPage.getByTestId('lineups-modal');
        await expect(lineupsModal).toBeVisible();
        await lineupsModal.getByTestId('close-lineups-btn').click();
        await expect(lineupsModal).toBeHidden();
      });
    });

    await adminContext.close();
    await userContext.close();
  });
});
