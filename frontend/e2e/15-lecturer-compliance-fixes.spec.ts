import { test, expect, request } from '@playwright/test';
import { loginAsAdmin, registerUser, getToken } from './helpers';

const BASE = 'http://localhost:8080';

async function api(token: string) {
  const ctx = await request.newContext({ baseURL: BASE });
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
  return {
    post: (path: string, body?: unknown) => ctx.post(path, { data: body, headers }),
    get: (path: string) => ctx.get(path, { headers }),
  };
}

test.describe.serial('Milestone 41 — lecturer compliance fixes', () => {
  test('league data import is allowed pre-season, then blocked (409 + disabled button) once betting opens', async ({ page }) => {
    await loginAsAdmin(page);
    const admin = await api((await getToken(page))!);

    // Back to a clean pre-season state (fixtures kept, results/bets/events wiped).
    const reset = await admin.post('/api/admin/season/reset', { confirm: true, regenerateSchedule: false, resetUserBalances: false });
    expect(reset.status()).toBe(200);

    // Pre-season: import allowed.
    const preSeasonImport = await admin.post('/api/admin/data/import');
    expect(preSeasonImport.status()).toBe(200);

    // Betting opens for Round 1 -> season has started.
    expect((await admin.post('/api/admin/rounds/1/open-betting')).status()).toBe(200);

    const blocked = await admin.post('/api/admin/data/import');
    expect(blocked.status()).toBe(409);
    const body = await blocked.json() as { message: string };
    expect(body.message).toContain('before the season starts');

    // Admin UI reflects the backend rule (backend remains the authority).
    await page.goto('/admin');
    await expect(page.getByTestId('import-league-data-btn')).toBeDisabled();
    await expect(page.getByTestId('import-locked-message')).toBeVisible();
    // Validation stays available (read-only).
    await expect(page.getByTestId('validate-league-data-btn')).toBeEnabled();
  });

  test('handicap odds are frozen: repeated GETs are identical and the booked bet uses the displayed price', async ({ page }) => {
    await registerUser(page, 'hcp');
    const user = await api((await getToken(page))!);

    const matches = await (await user.get('/api/matches/round/1')).json() as { id: number; status: string; homeOdds: number }[];
    const open = matches.filter((m) => m.status === 'BETTING_OPEN');
    expect(open.length).toBeGreaterThan(0);
    const m = open[0];

    const first = await (await user.get(`/api/matches/${m.id}/handicap-odds`)).json() as { selection: string; odds: number }[];
    for (let i = 0; i < 8; i++) {
      const again = await (await user.get(`/api/matches/${m.id}/handicap-odds`)).json() as { selection: string; odds: number }[];
      expect(again).toEqual(first);
    }
    const homeMinusOne = first.find((o) => o.selection === 'HOME_MINUS_ONE')!;
    expect(Number(homeMinusOne.odds)).toBeGreaterThan(Number(m.homeOdds));

    const placed = await user.post('/api/bets/handicap', { matchId: m.id, selection: 'HOME_MINUS_ONE', amount: 10 });
    expect(placed.status()).toBe(201);
    const bet = await placed.json() as { odds: number };
    expect(Number(bet.odds)).toBe(Number(homeMinusOne.odds));
  });

  test('My Bets converges after settlement without a manual reload or a Dashboard visit', async ({ page }) => {
    const bettor = await registerUser(page, 'live');
    expect(bettor.username).toBeTruthy();
    const user = await api((await getToken(page))!);

    const matches = await (await user.get('/api/matches/round/1')).json() as { id: number; status: string }[];
    const open = matches.filter((m) => m.status === 'BETTING_OPEN');
    // Bet on every open Round 1 match so at least one settlement is very likely to change the balance.
    const betIds: number[] = [];
    for (const m of open) {
      const res = await user.post('/api/bets', { matchId: m.id, prediction: 'HOME_WIN', amount: 10 });
      expect(res.status()).toBe(201);
      betIds.push((await res.json() as { id: number }).id);
    }

    await page.goto('/bets');
    await expect(page.getByTestId('navbar-balance')).toContainText((1000 - 10 * open.length).toFixed(2));
    await expect(page.getByTestId(`open-bet-row-${betIds[0]}`)).toBeVisible();

    // Admin settles the round via the API while the user stays on My Bets.
    const adminPage = await page.context().browser()!.newPage();
    await loginAsAdmin(adminPage);
    const admin = await api((await getToken(adminPage))!);
    expect((await admin.post('/api/admin/rounds/1/simulate')).status()).toBe(200);
    await adminPage.close();

    // Without navigating or reloading: settled rows appear and the navbar balance equals the server balance.
    await expect(page.getByTestId(`settled-bet-row-${betIds[0]}`)).toBeVisible({ timeout: 25000 });
    const me = await (await user.get('/api/users/me')).json() as { balance: number };
    await expect(page.getByTestId('navbar-balance')).toContainText(Number(me.balance).toFixed(2), { timeout: 25000 });
    expect(page.url()).toContain('/bets');
  });

  test('Reset Season with "reset balances" refreshes the admin navbar balance immediately', async ({ page }) => {
    await loginAsAdmin(page);
    const admin = await api((await getToken(page))!);

    // Give the admin a non-1000 balance: open Round 2 and place a bet.
    expect((await admin.post('/api/admin/rounds/2/open-betting')).status()).toBe(200);
    const r2 = await (await admin.get('/api/matches/round/2')).json() as { id: number; status: string }[];
    const open = r2.find((m) => m.status === 'BETTING_OPEN')!;
    expect((await admin.post('/api/bets', { matchId: open.id, prediction: 'DRAW', amount: 50 })).status()).toBe(201);

    await page.goto('/admin');
    await expect(page.getByTestId('navbar-balance')).toContainText('950.00');

    await page.getByTestId('reset-season-btn').click();
    const modal = page.getByTestId('season-reset-modal');
    await expect(modal).toBeVisible();
    await modal.locator('input[type="checkbox"]').nth(1).check();   // "Reset all user balances to 1000"
    await page.getByRole('button', { name: 'Confirm Reset' }).click();
    await expect(page.getByTestId('reset-result')).toBeVisible({ timeout: 15000 });

    // No reload, no Dashboard visit: the shared user state was refreshed after the reset.
    await expect(page.getByTestId('navbar-balance')).toContainText('1000.00');
    expect(page.url()).toContain('/admin');
  });
});
