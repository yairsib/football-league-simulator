import { test, expect } from '@playwright/test';
import { loginAsAdmin, registerUser, getToken } from './helpers';

/**
 * Season completion sanity spec.
 *
 * Uses the test-only POST /api/test/simulate-all endpoint to fast-forward the
 * full 26-round season without clicking through each round in the Admin UI.
 * That endpoint is only available when SPRING_PROFILES_ACTIVE=test and
 * app.otp.test-mode=true (same conditions as the fake-email endpoint).
 */
test.describe('Season completion — champion/top-scorer bets settle and UI reflects done state', () => {
  test('full season flow: seed → season bets → simulate all → verify settled', async ({ browser }) => {
    const adminContext = await browser.newContext();
    const userContext  = await browser.newContext();
    const adminPage    = await adminContext.newPage();
    const userPage     = await userContext.newPage();

    // ── 1. Admin: seed teams + generate schedule ──────────────────────────────
    await loginAsAdmin(adminPage);
    const adminToken = await getToken(adminPage);

    const seedRes = await adminPage.request.post('/api/admin/seed', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect([200, 409]).toContain(seedRes.status());

    const scheduleRes = await adminPage.request.post('/api/admin/generate-schedule', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect([200, 409]).toContain(scheduleRes.status());

    // ── 2. User: register ─────────────────────────────────────────────────────
    await registerUser(userPage, 'seasondone');
    const userToken = await getToken(userPage);

    // ── 3. User: verify season betting is open, fetch champion odds ──────────
    const statusRes = await userPage.request.get('/api/bets/season/status', {
      headers: { Authorization: `Bearer ${userToken}` },
    });
    expect(statusRes.status()).toBe(200);
    const status = await statusRes.json();
    expect(status.open).toBe(true);

    const champOddsRes = await userPage.request.get('/api/bets/season/champion/odds', {
      headers: { Authorization: `Bearer ${userToken}` },
    });
    expect(champOddsRes.status()).toBe(200);
    const champOdds = await champOddsRes.json() as Array<{ teamId: number; odds: number }>;
    expect(champOdds.length).toBeGreaterThan(0);

    const topScorerOddsRes = await userPage.request.get('/api/bets/season/top-scorer/odds?limit=5', {
      headers: { Authorization: `Bearer ${userToken}` },
    });
    expect(topScorerOddsRes.status()).toBe(200);
    const topScorerOdds = await topScorerOddsRes.json() as Array<{ playerId: number; odds: number }>;
    expect(topScorerOdds.length).toBeGreaterThan(0);

    // ── 4. User: place a champion bet + a top-scorer bet ─────────────────────
    const champBetRes = await userPage.request.post('/api/bets/season/champion', {
      headers: { Authorization: `Bearer ${userToken}`, 'Content-Type': 'application/json' },
      data: { teamId: champOdds[0].teamId, amount: 50 },
    });
    expect([200, 201]).toContain(champBetRes.status());

    const topScorerBetRes = await userPage.request.post('/api/bets/season/top-scorer', {
      headers: { Authorization: `Bearer ${userToken}`, 'Content-Type': 'application/json' },
      data: { playerId: topScorerOdds[0].playerId, amount: 30 },
    });
    expect([200, 201]).toContain(topScorerBetRes.status());

    // ── 5. Simulate the full remaining season via the test endpoint ───────────
    const simRes = await adminPage.request.post('/api/test/simulate-all');
    expect(simRes.status()).toBe(200);
    expect(await simRes.text()).toContain('Simulated');

    // ── 6. Verify all rounds are FINISHED ─────────────────────────────────────
    const roundsRes = await adminPage.request.get('/api/rounds', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(roundsRes.status()).toBe(200);
    const rounds = await roundsRes.json() as Array<{ status: string }>;
    expect(rounds.length).toBeGreaterThan(0);
    const allFinished = rounds.every((r) => r.status === 'FINISHED');
    expect(allFinished).toBe(true);

    // ── 7. Verify the user's season bets are now settled (WON or LOST) ───────
    const myBetsRes = await userPage.request.get('/api/bets/my', {
      headers: { Authorization: `Bearer ${userToken}` },
    });
    expect(myBetsRes.status()).toBe(200);
    const myBets = await myBetsRes.json() as Array<{ market: string; status: string }>;

    const champBet = myBets.find((b) => b.market === 'CHAMPION');
    const topBet   = myBets.find((b) => b.market === 'TOP_SCORER');
    expect(champBet).toBeDefined();
    expect(topBet).toBeDefined();
    expect(['WON', 'LOST']).toContain(champBet!.status);
    expect(['WON', 'LOST']).toContain(topBet!.status);

    // ── 8. Verify Dashboard shows "Season Complete!" banner and label ─────────
    await userPage.goto('/');
    await expect(userPage.getByTestId('dashboard-next-action')).toContainText('Season complete');
    await expect(userPage.getByTestId('season-complete-label')).toBeVisible();

    // ── 9. Season status panel shows all rounds finished ─────────────────────
    const seasonStatus = userPage.getByTestId('dashboard-season-status');
    await expect(seasonStatus).toContainText('rounds finished', { ignoreCase: true });
    await expect(seasonStatus).toContainText(String(rounds.length));

    // ── 10. League table is visible with all teams having played ─────────────
    await userPage.goto('/league');
    const rows = userPage.locator('.data-table tbody tr');
    await expect(rows).toHaveCount(14);

    // Every team should have played at least one match
    const firstPlayedCell = rows.first().locator('td').nth(2);
    await expect(firstPlayedCell).not.toHaveText('0');

    await adminContext.close();
    await userContext.close();
  });
});
