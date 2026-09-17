import { test, expect } from '@playwright/test';
import { loginAsAdmin, registerUser, getToken } from './helpers';

test.describe('Visible weather and lineups infrastructure', () => {
  test('matches show weather, lineups can be viewed, and unavailable players render when present', async ({ browser }) => {
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

    await registerUser(userPage, 'lineupviewer');
    await userPage.goto('/matches');
    await userPage.getByTestId('round-tab-1').click();

    const firstCard = userPage.locator('[data-testid^="match-card-"]').first();
    await expect(firstCard).toBeVisible();
    const cardTestId = await firstCard.getAttribute('data-testid');
    const matchId = cardTestId!.replace('match-card-', '');

    await test.step('Weather is visible on the match card', async () => {
      const weatherBadge = userPage.getByTestId(`match-weather-${matchId}`);
      await expect(weatherBadge).toBeVisible();
      await expect(weatherBadge).toContainText(/CLEAR|RAIN|WIND|HOT|COLD|STORM/);
    });

    await test.step('View Lineups button opens the lineups modal with backend data', async () => {
      const viewLineupsBtn = userPage.getByTestId(`view-lineups-btn-${matchId}`);
      await expect(viewLineupsBtn).toBeVisible();
      await viewLineupsBtn.click();

      const modal = userPage.getByTestId('lineups-modal');
      await expect(modal).toBeVisible();
      await expect(modal.getByTestId('lineups-weather')).toContainText(/CLEAR|RAIN|WIND|HOT|COLD|STORM/);

      // Squads are infrastructure-only at this milestone (no seeded players yet),
      // so the lists render empty — but the unavailable-players section must
      // still be present and would display players if/when squads are seeded.
      await expect(modal.getByTestId('lineup-home-unavailable')).toBeVisible();
      await expect(modal.getByTestId('lineup-away-unavailable')).toBeVisible();

      // Per-player stats (goals/assists/red cards) are simulation-generated and only
      // render when non-zero — only assert the format is stable when present.
      const statBadges = modal.locator('[data-testid$="-stats"]');
      const statCount = await statBadges.count();
      for (let i = 0; i < statCount; i++) {
        const text = await statBadges.nth(i).innerText();
        expect(text).toMatch(/^(\d+ goals? ?·? ?|\d+ assists? ?·? ?|\d+ red cards? ?·? ?)+$/);
      }

      await modal.getByTestId('close-lineups-btn').click();
      await expect(modal).toBeHidden();
    });

    await test.step('Lineups endpoint returns the expected shape directly', async () => {
      const userToken = await getToken(userPage);
      const res = await userPage.request.get(`/api/matches/${matchId}/lineups`, {
        headers: { Authorization: `Bearer ${userToken}` },
      });
      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body).toHaveProperty('homeTeamName');
      expect(body).toHaveProperty('awayTeamName');
      expect(body).toHaveProperty('weatherCondition');
      expect(body).toHaveProperty('homeUnavailablePlayers');
      expect(body).toHaveProperty('awayUnavailablePlayers');
    });

    await adminContext.close();
    await userContext.close();
  });
});
