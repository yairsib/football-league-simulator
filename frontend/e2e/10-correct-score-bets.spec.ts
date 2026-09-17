import { test, expect, Page } from '@playwright/test';
import { loginAsAdmin, registerUser } from './helpers';

/**
 * Finds a round that is currently open for betting (or opens betting for the
 * next eligible one) and returns its round number. Reuses the admin page that
 * `loginAsAdmin` already navigated to `/admin`.
 */
async function ensureOpenBettingRound(adminPage: Page): Promise<number> {
  const select = adminPage.getByTestId('round-select');
  await expect(select).toBeVisible();

  const optionTexts = await select.locator('option').allInnerTexts();
  const parsed = optionTexts.map((text) => {
    const match = text.match(/Round (\d+) — (\w+)/);
    return { roundNumber: Number(match![1]), status: match![2] };
  });

  const alreadyOpen = parsed.find((r) => r.status === 'OPEN_FOR_BETS');
  if (alreadyOpen) return alreadyOpen.roundNumber;

  const eligible = parsed.find((r) => r.status === 'NOT_STARTED');
  if (!eligible) throw new Error('No round available to open betting for');

  await expect(async () => {
    await select.selectOption(String(eligible.roundNumber));
    await expect(select).toHaveValue(String(eligible.roundNumber));
  }).toPass({ timeout: 15000 });

  await adminPage.getByTestId('open-betting-btn').click();
  await expect(adminPage.getByTestId('admin-message')).toContainText(`Betting opened for Round ${eligible.roundNumber}`);
  return eligible.roundNumber;
}

test.describe('Correct score bets (Milestone 24)', () => {
  test('user can place, view, edit, and cancel a correct-score bet', async ({ browser }) => {
    const adminContext = await browser.newContext();
    const userContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    const userPage = await userContext.newPage();

    await loginAsAdmin(adminPage);
    await adminPage.goto('/admin');
    await expect(adminPage.getByTestId('admin-page')).toBeVisible();

    // Defensive setup mirroring earlier specs: seeding/schedule generation are
    // safe to call even if already done.
    await adminPage.getByTestId('seed-teams-btn').click();
    await expect(adminPage.getByTestId('admin-message').or(adminPage.getByTestId('admin-error'))).toBeVisible();
    await adminPage.getByTestId('generate-schedule-btn').click();
    await expect(adminPage.getByTestId('admin-message').or(adminPage.getByTestId('admin-error'))).toBeVisible();

    const openRound = await ensureOpenBettingRound(adminPage);

    await registerUser(userPage, 'csbettor');
    await expect(userPage.getByTestId('navbar-balance')).toContainText('1000.00');

    await userPage.goto('/matches');
    await userPage.getByTestId(`round-tab-${openRound}`).click();

    const openCards = userPage.locator('[data-testid^="bet-slip-add-"]');
    await expect(openCards.first()).toBeVisible();
    const firstTestId = await openCards.first().getAttribute('data-testid');
    const matchId = Number(firstTestId!.replace('bet-slip-add-', ''));

    let selectedOdds = 0;
    let betId = '';

    await test.step('Correct Score button opens the modal with a score grid', async () => {
      await expect(userPage.getByTestId(`correct-score-btn-${matchId}`)).toBeVisible();
      await userPage.getByTestId(`correct-score-btn-${matchId}`).click();

      const modal = userPage.getByTestId('correct-score-bet-modal');
      await expect(modal).toBeVisible();
      await expect(modal.getByTestId('correct-score-options')).toBeVisible();
    });

    await test.step('Select a scoreline and place the bet', async () => {
      const modal = userPage.getByTestId('correct-score-bet-modal');
      const oneNilBtn = modal.getByTestId('correct-score-1-0');
      await expect(oneNilBtn).toBeEnabled();

      selectedOdds = Number(await oneNilBtn.innerText());
      await oneNilBtn.click();
      await expect(modal.getByTestId('correct-score-selected')).toContainText('Selected: 1-0');

      await modal.getByTestId('correct-score-amount-input').fill('20');
      const expectedPossibleWin = (20 * selectedOdds).toFixed(2);
      await expect(modal.getByTestId('correct-score-possible-win')).toContainText(`$${expectedPossibleWin}`);

      await modal.getByTestId('confirm-correct-score-btn').click();
      await expect(modal).toBeHidden();

      // Stake is deducted from balance immediately, regardless of odds.
      await expect(userPage.getByTestId('navbar-balance')).toContainText('980.00');
    });

    await test.step('My Bets shows the Correct Score market and predicted scoreline', async () => {
      await userPage.goto('/bets');

      const row = userPage.locator('[data-testid^="open-bet-row-"]').filter({ hasText: 'Correct Score' });
      await expect(row).toBeVisible();
      betId = (await row.getAttribute('data-testid'))!.replace('open-bet-row-', '');

      await expect(userPage.getByTestId(`bet-market-${betId}`)).toHaveText('Correct Score');
      await expect(row).toContainText('1-0');
      await expect(row).toContainText('$20.00');
    });

    await test.step('Editing the amount adjusts the balance and keeps the predicted score', async () => {
      await userPage.getByTestId(`edit-bet-btn-${betId}`).click();
      const modal = userPage.getByTestId('edit-bet-modal');
      await expect(modal).toBeVisible();

      await expect(modal.getByTestId('edit-correct-score-home-input')).toHaveValue('1');
      await expect(modal.getByTestId('edit-correct-score-away-input')).toHaveValue('0');

      await modal.getByTestId('edit-bet-amount-input').fill('30');
      await modal.getByTestId('confirm-edit-bet-btn').click();
      await expect(modal).toBeHidden();

      // 980 - (30 - 20) = 970
      await expect(userPage.getByTestId('navbar-balance')).toContainText('970.00');
      await expect(userPage.getByTestId(`open-bet-row-${betId}`)).toContainText('$30.00');
    });

    await test.step('Editing the predicted score re-snapshots the displayed scoreline', async () => {
      await userPage.getByTestId(`edit-bet-btn-${betId}`).click();
      const modal = userPage.getByTestId('edit-bet-modal');
      await expect(modal).toBeVisible();

      await modal.getByTestId('edit-correct-score-home-input').fill('2');
      await modal.getByTestId('edit-correct-score-away-input').fill('1');
      await modal.getByTestId('confirm-edit-bet-btn').click();
      await expect(modal).toBeHidden();

      await expect(userPage.getByTestId(`open-bet-row-${betId}`)).toContainText('2-1');
    });

    await test.step('Cancel the open correct-score bet: full stake refunded', async () => {
      userPage.once('dialog', (dialog) => dialog.accept());
      await userPage.getByTestId(`cancel-bet-btn-${betId}`).click();

      // 970 + 30 = 1000 (back to the starting balance)
      await expect(userPage.getByTestId('navbar-balance')).toContainText('1000.00');

      const settledRow = userPage.getByTestId(`settled-bet-row-${betId}`);
      await expect(settledRow).toBeVisible();
      await expect(userPage.getByTestId(`bet-status-${betId}`)).toHaveText('CANCELLED');
      await expect(settledRow.getByTestId(`bet-market-${betId}`)).toHaveText('Correct Score');
      await expect(userPage.locator(`[data-testid="open-bet-row-${betId}"]`)).toHaveCount(0);
    });

    await adminContext.close();
    await userContext.close();
  });
});
