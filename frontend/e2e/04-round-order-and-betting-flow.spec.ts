import { test, expect, Page } from '@playwright/test';
import { loginAsAdmin, registerUser, getToken } from './helpers';

/**
 * Selects a round in the Admin round dropdown and waits for the selection to
 * actually stick. selectOption can race with the page's own async state
 * updates (rounds/matches still loading), so retry until the control reflects
 * the chosen value before relying on the derived UI (blocked messages, button
 * enablement, matches list) that depends on it.
 */
async function selectRound(page: Page, roundNumber: number): Promise<void> {
  const select = page.getByTestId('round-select');
  await expect(async () => {
    // Wait for rounds to load from the API — options only appear after getRounds() resolves.
    // Without this, selectOption can race ahead while rounds state is still empty.
    await expect(select.locator(`option[value="${roundNumber}"]`)).toBeAttached();
    await select.selectOption(String(roundNumber));
    await expect(select).toHaveValue(String(roundNumber));
    await expect(page.getByRole('heading', { name: `Round ${roundNumber} Matches` })).toBeVisible();
  }).toPass({ timeout: 15000 });
}

test.describe('Round order enforcement and end-to-end betting flow', () => {
  test('round 2 is blocked until round 1 finishes, and a bet settles correctly', async ({ browser }) => {
    const adminContext = await browser.newContext();
    const userContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    const userPage = await userContext.newPage();

    await loginAsAdmin(adminPage);
    await adminPage.goto('/admin');
    await expect(adminPage.getByTestId('admin-page')).toBeVisible();

    await test.step('Round 2 cannot be opened or simulated before Round 1 finishes', async () => {
      await selectRound(adminPage, 2);

      const openBtn = adminPage.getByTestId('open-betting-btn');
      const simulateBtn = adminPage.getByTestId('simulate-round-btn');

      // The UI itself prevents the action: buttons are disabled while a
      // "previous rounds must be finished" message is shown.
      await expect(adminPage.getByText('Previous rounds must be finished before starting this round.')).toBeVisible();
      await expect(openBtn).toBeDisabled();
      await expect(simulateBtn).toBeDisabled();

      // Confirm the backend itself rejects the action (not just the UI), with a clear 409 error.
      const adminToken = await getToken(adminPage);
      const openRes = await adminPage.request.post('/api/admin/rounds/2/open-betting', {
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      expect(openRes.status()).toBe(409);
      expect((await openRes.json()).message).toContain('Previous rounds must be finished');

      const simulateRes = await adminPage.request.post('/api/admin/rounds/2/simulate', {
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      expect(simulateRes.status()).toBe(409);
    });

    let homeTeam = '';
    let awayTeam = '';

    await test.step('Round 1 betting opens successfully', async () => {
      await selectRound(adminPage, 1);
      await expect(adminPage.getByTestId('open-betting-btn')).toBeEnabled();
      await adminPage.getByTestId('open-betting-btn').click();
      await expect(adminPage.getByTestId('admin-message')).toContainText('Betting opened for Round 1');
      await expect(adminPage.getByTestId('round-select')).toContainText('Round 1 — OPEN_FOR_BETS');
    });

    let regularUser: { email: string; password: string };

    await test.step('Regular user places a bet and balance decreases', async () => {
      regularUser = await registerUser(userPage, 'bettor');
      await expect(userPage.getByTestId('navbar-balance')).toContainText('1000.00');

      await userPage.goto('/matches');
      await userPage.getByTestId('round-tab-1').click();

      const placeBetBtn = userPage.locator('[data-testid^="place-bet-btn-"]').first();
      await expect(placeBetBtn).toBeVisible();
      await placeBetBtn.click();

      const modal = userPage.getByTestId('place-bet-modal');
      await expect(modal).toBeVisible();
      const matchLabel = await modal.locator('.modal-match').innerText();
      [homeTeam, awayTeam] = matchLabel.split(' vs ').map((s) => s.trim());

      await modal.getByTestId('bet-amount-input').fill('100');
      await modal.getByTestId('confirm-bet-btn').click();
      await expect(modal).toBeHidden();

      await expect(userPage.getByTestId('navbar-balance')).toContainText('900.00');
    });

    let betId = '';

    await test.step('Bet appears as OPEN in My Bets', async () => {
      await userPage.goto('/bets');
      const openRow = userPage.locator('[data-testid^="open-bet-row-"]').filter({ hasText: homeTeam }).filter({ hasText: awayTeam });
      await expect(openRow).toBeVisible();
      const testId = await openRow.getAttribute('data-testid');
      betId = testId!.replace('open-bet-row-', '');
    });

    await test.step('Admin simulates Round 1', async () => {
      await selectRound(adminPage, 1);
      await expect(adminPage.getByTestId('simulate-round-btn')).toBeEnabled();
      await adminPage.getByTestId('simulate-round-btn').click();
      await expect(adminPage.getByTestId('admin-message')).toContainText('Round 1 simulated');
      await expect(adminPage.getByTestId('round-select')).toContainText('Round 1 — FINISHED');
    });

    await test.step('Finished match cards render goal/red-card events with stable format when present', async () => {
      await userPage.goto('/matches');
      await userPage.getByTestId('round-tab-1').click();

      const finishedCards = userPage.locator('[data-testid^="match-card-"].status-finished');
      // Wait for the round's cards to render before counting (count() does not auto-wait).
      await expect(finishedCards.first()).toBeVisible();
      const count = await finishedCards.count();
      expect(count).toBeGreaterThan(0);

      for (let i = 0; i < count; i++) {
        const card = finishedCards.nth(i);
        const cardTestId = await card.getAttribute('data-testid');
        const matchId = cardTestId!.replace('match-card-', '');
        const events = userPage.getByTestId(`match-events-${matchId}`);

        // Events are random — a finished match may have zero, some, or no events section at all.
        // What must hold: IF the section renders, goal/red-card lines follow "<minute>' <name>[ (assist: <name>)]".
        if (await events.count() > 0) {
          const lines = await events.locator('li').allInnerTexts();
          for (const line of lines) {
            expect(line).toMatch(/^\d+' .+( \(assist: .+\))?$/);
          }
        }
      }
    });

    await test.step('Bet settles to WON or LOST and balance updates logically', async () => {
      await userPage.goto('/bets');
      const settledRow = userPage.getByTestId(`settled-bet-row-${betId}`);
      await expect(settledRow).toBeVisible();

      const status = await userPage.getByTestId(`bet-status-${betId}`).innerText();
      expect(['WON', 'LOST']).toContain(status);
      await expect(userPage.getByTestId(`bet-profit-${betId}`)).not.toHaveText('—');
      await expect(settledRow.locator('td').last()).not.toHaveText('—'); // settledAt rendered

      await userPage.reload();
      const balanceText = await userPage.getByTestId('navbar-balance').innerText();
      const balance = Number(balanceText.replace(/[^0-9.]/g, ''));
      if (status === 'LOST') {
        expect(balance).toBe(900);
      } else {
        expect(balance).toBeGreaterThan(900);
      }
    });

    await test.step('League table updates: all teams have played 1 match', async () => {
      await userPage.goto('/league');
      const rows = userPage.locator('table.league-table tbody tr');
      await expect(rows).toHaveCount(14);
      const playedValues = await rows.locator('td:nth-child(3)').allInnerTexts();
      expect(playedValues.every((v) => v.trim() === '1')).toBe(true);
    });

    await test.step('Matches page shows round results AND the league standings on the same screen', async () => {
      await userPage.goto('/matches');
      await userPage.getByTestId('round-tab-1').click();

      // (a) finished results with scores
      const finishedCards = userPage.locator('[data-testid^="match-card-"].status-finished');
      await expect(finishedCards.first()).toBeVisible();
      await expect(finishedCards.first().locator('.match-score')).toHaveText(/\d+ – \d+/);

      // (b) the standings table, rendered beside them (not a link)
      const standings = userPage.getByTestId('matches-standings');
      await expect(standings).toBeVisible();
      const standingRows = standings.locator('table.league-table tbody tr');
      await expect(standingRows).toHaveCount(14);
      const played = await standingRows.locator('td:nth-child(3)').allInnerTexts();
      expect(played.every((v) => v.trim() === '1')).toBe(true);
      await expect(standings.locator('thead')).toContainText('Pts');
      await expect(userPage.getByTestId('matches-standings-full-link')).toBeVisible();
    });

    await test.step('Round 2 betting can now be opened', async () => {
      await selectRound(adminPage, 2);
      await expect(adminPage.getByTestId('open-betting-btn')).toBeEnabled();
      await adminPage.getByTestId('open-betting-btn').click();
      await expect(adminPage.getByTestId('admin-message')).toContainText('Betting opened for Round 2');
      await expect(adminPage.getByTestId('round-select')).toContainText('Round 2 — OPEN_FOR_BETS');
    });

    await adminContext.close();
    await userContext.close();
  });
});
