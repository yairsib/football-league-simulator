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

async function getBalance(page: Page): Promise<number> {
  const text = await page.getByTestId('navbar-balance').innerText();
  return Number(text.replace(/[^0-9.]/g, ''));
}

test.describe('Combo bets, bet slip, and edit/cancel of open bets (Milestone 23)', () => {
  test('user can place a single bet and a combo, then edit/cancel both with correct balance refunds', async ({ browser }) => {
    const adminContext = await browser.newContext();
    const userContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    const userPage = await userContext.newPage();

    await loginAsAdmin(adminPage);
    await adminPage.goto('/admin');
    await expect(adminPage.getByTestId('admin-page')).toBeVisible();

    // Defensive setup mirroring specs 06-08: seeding/schedule generation are
    // safe to call even if already done (backend reports a message or error
    // either way), so this spec doesn't strictly depend on running after 03/04.
    await adminPage.getByTestId('seed-teams-btn').click();
    await expect(adminPage.getByTestId('admin-message').or(adminPage.getByTestId('admin-error'))).toBeVisible();
    await adminPage.getByTestId('generate-schedule-btn').click();
    await expect(adminPage.getByTestId('admin-message').or(adminPage.getByTestId('admin-error'))).toBeVisible();

    const openRound = await ensureOpenBettingRound(adminPage);

    await registerUser(userPage, 'combobettor');
    await expect(userPage.getByTestId('navbar-balance')).toContainText('1000.00');

    await userPage.goto('/matches');
    await userPage.getByTestId(`round-tab-${openRound}`).click();

    const openCards = userPage.locator('[data-testid^="bet-slip-add-"]');
    await expect(openCards.first()).toBeVisible();
    const cardCount = await openCards.count();
    expect(cardCount).toBeGreaterThanOrEqual(3);

    const matchIds: number[] = [];
    for (let i = 0; i < cardCount; i++) {
      const testId = await openCards.nth(i).getAttribute('data-testid');
      matchIds.push(Number(testId!.replace('bet-slip-add-', '')));
    }

    let singleHomeTeam = '';
    let singleAwayTeam = '';

    await test.step('Place a single bet on the first open match (existing flow, untouched)', async () => {
      await userPage.getByTestId(`place-bet-btn-${matchIds[0]}`).click();
      const modal = userPage.getByTestId('place-bet-modal');
      await expect(modal).toBeVisible();
      const matchLabel = await modal.locator('.modal-match').innerText();
      [singleHomeTeam, singleAwayTeam] = matchLabel.split(' vs ').map((s) => s.trim());

      await modal.getByTestId('bet-amount-input').fill('50');
      await modal.getByTestId('confirm-bet-btn').click();
      await expect(modal).toBeHidden();
      await expect(userPage.getByTestId('navbar-balance')).toContainText('950.00');
    });

    await test.step('Add 2 different matches to the Bet Slip and place a combo bet', async () => {
      await userPage.getByTestId(`add-to-slip-${matchIds[1]}-HOME_WIN`).click();
      await userPage.getByTestId(`add-to-slip-${matchIds[2]}-AWAY_WIN`).click();

      const slip = userPage.getByTestId('bet-slip');
      await expect(slip).toBeVisible();
      await expect(slip.getByTestId(`slip-item-${matchIds[1]}`)).toBeVisible();
      await expect(slip.getByTestId(`slip-item-${matchIds[2]}`)).toBeVisible();

      const placeComboBtn = slip.getByTestId('place-combo-btn');
      await expect(placeComboBtn).toBeEnabled();

      await slip.getByTestId('combo-amount-input').fill('40');
      await placeComboBtn.click();

      await expect(slip).toBeHidden();
      await expect(userPage.getByTestId('navbar-balance')).toContainText('910.00');
    });

    let singleBetId = '';
    let comboBetId = '';

    await test.step('My Bets shows both the single bet and the combo with its selections', async () => {
      await userPage.goto('/bets');

      const singleRow = userPage.locator('[data-testid^="open-bet-row-"]').filter({ hasText: singleHomeTeam }).filter({ hasText: singleAwayTeam }).filter({ hasText: 'SINGLE' });
      await expect(singleRow).toBeVisible();
      singleBetId = (await singleRow.getAttribute('data-testid'))!.replace('open-bet-row-', '');

      const comboRow = userPage.locator('[data-testid^="open-bet-row-"]').filter({ hasText: 'COMBO' });
      await expect(comboRow).toBeVisible();
      comboBetId = (await comboRow.getAttribute('data-testid'))!.replace('open-bet-row-', '');

      await expect(comboRow).toContainText('2 selections');
      await expect(comboRow).toContainText('$40.00');

      // Edit/Cancel are visible on OPEN rows for both single and combo bets.
      await expect(userPage.getByTestId(`edit-bet-btn-${singleBetId}`)).toBeVisible();
      await expect(userPage.getByTestId(`cancel-bet-btn-${singleBetId}`)).toBeVisible();
      await expect(userPage.getByTestId(`edit-bet-btn-${comboBetId}`)).toBeVisible();
      await expect(userPage.getByTestId(`cancel-bet-btn-${comboBetId}`)).toBeVisible();
    });

    await test.step('Edit the open single bet amount: balance reflects the deducted difference', async () => {
      await userPage.getByTestId(`edit-bet-btn-${singleBetId}`).click();
      const modal = userPage.getByTestId('edit-bet-modal');
      await expect(modal).toBeVisible();

      await modal.getByTestId('edit-bet-amount-input').fill('70');
      await modal.getByTestId('confirm-edit-bet-btn').click();
      await expect(modal).toBeHidden();

      // 910 - (70 - 50) = 890
      await expect(userPage.getByTestId('navbar-balance')).toContainText('890.00');
      await expect(userPage.getByTestId(`open-bet-row-${singleBetId}`)).toContainText('$70.00');
    });

    await test.step('Cancel the open single bet: full stake refunded and it moves to settled as CANCELLED', async () => {
      userPage.once('dialog', (dialog) => dialog.accept());
      await userPage.getByTestId(`cancel-bet-btn-${singleBetId}`).click();

      // 890 + 70 = 960
      await expect(userPage.getByTestId('navbar-balance')).toContainText('960.00');

      const settledRow = userPage.getByTestId(`settled-bet-row-${singleBetId}`);
      await expect(settledRow).toBeVisible();
      await expect(userPage.getByTestId(`bet-status-${singleBetId}`)).toHaveText('CANCELLED');
      await expect(userPage.locator(`[data-testid="open-bet-row-${singleBetId}"]`)).toHaveCount(0);

      // Cancelled bets must not offer Edit/Cancel actions.
      await expect(settledRow.getByTestId(`edit-bet-btn-${singleBetId}`)).toHaveCount(0);
      await expect(settledRow.getByTestId(`cancel-bet-btn-${singleBetId}`)).toHaveCount(0);
    });

    await test.step('Cancel the open combo bet: full stake refunded back to the original balance', async () => {
      userPage.once('dialog', (dialog) => dialog.accept());
      await userPage.getByTestId(`cancel-bet-btn-${comboBetId}`).click();

      // 960 + 40 = 1000 (back to the starting balance — nothing else open)
      await expect(userPage.getByTestId('navbar-balance')).toContainText('1000.00');

      const settledRow = userPage.getByTestId(`settled-bet-row-${comboBetId}`);
      await expect(settledRow).toBeVisible();
      await expect(userPage.getByTestId(`bet-status-${comboBetId}`)).toHaveText('CANCELLED');
      await expect(settledRow).toContainText('COMBO');
      await expect(settledRow).toContainText('2 selections');
      await expect(userPage.locator(`[data-testid="open-bet-row-${comboBetId}"]`)).toHaveCount(0);
    });

    await adminContext.close();
    await userContext.close();
  });
});
