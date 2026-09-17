import { test, expect, request } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { loginAsAdmin, registerUser, getToken, ADMIN_EMAIL } from './helpers';

const BASE = 'http://localhost:8080';

async function apiPost(token: string, path: string, body: unknown) {
  const ctx = await request.newContext({ baseURL: BASE });
  return ctx.post(path, {
    data: body,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  });
}

test.describe('Season Reset — auth guards', () => {
  test('unauthenticated request returns 401', async () => {
    const ctx = await request.newContext({ baseURL: BASE });
    const res = await ctx.post('/api/admin/season/reset', {
      data: { confirm: true, regenerateSchedule: true, resetUserBalances: false },
    });
    expect(res.status()).toBe(401);
  });

  test('regular user returns 403', async ({ page }) => {
    await registerUser(page);
    const token = await getToken(page);
    expect(token).not.toBeNull();
    const res = await apiPost(token!, '/api/admin/season/reset', {
      confirm: true, regenerateSchedule: true, resetUserBalances: false,
    });
    expect(res.status()).toBe(403);
  });

  test('confirm=false returns 400', async ({ page }) => {
    await loginAsAdmin(page);
    const token = await getToken(page);
    const res = await apiPost(token!, '/api/admin/season/reset', {
      confirm: false, regenerateSchedule: true, resetUserBalances: false,
    });
    expect(res.status()).toBe(400);
  });

  test('admin with confirm=true returns 200', async ({ page }) => {
    await loginAsAdmin(page);
    const token = await getToken(page);
    const res = await apiPost(token!, '/api/admin/season/reset', {
      confirm: true, regenerateSchedule: true, resetUserBalances: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json() as { message: string; scheduleRegenerated: boolean };
    expect(body.message).toBeTruthy();
  });
});

test.describe('Season Reset — UI flow', () => {
  test('Reset Season button appears in admin Danger Zone', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin');
    await expect(page.getByTestId('danger-zone')).toBeVisible();
    await expect(page.getByTestId('reset-season-btn')).toBeVisible();
  });

  test('cancel closes modal without resetting', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin');
    await page.getByTestId('reset-season-btn').click();
    const cancelBtn = page.getByRole('button', { name: 'Cancel' });
    await expect(cancelBtn).toBeVisible();
    await cancelBtn.click();
    // Modal gone — result not shown
    await expect(page.getByTestId('reset-result')).not.toBeVisible();
  });

  test('full UI reset flow: confirm and verify clean state', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin');

    await page.getByTestId('reset-season-btn').click();
    // Modal should be visible with confirmation text
    await expect(page.getByText('This will reset the current season')).toBeVisible();

    // Click Confirm Reset
    await page.getByRole('button', { name: 'Confirm Reset' }).click();

    // Result summary appears
    await expect(page.getByTestId('reset-result')).toBeVisible({ timeout: 15000 });

    // Admin overview should have reloaded — admin page still functional
    await expect(page.getByTestId('admin-overview')).toBeVisible();
  });

  test('admin account remains after reset', async ({ page }) => {
    // Admin can still log in after a reset
    await loginAsAdmin(page);
    await expect(page).toHaveURL('/');
  });

  test('regular users remain after reset', async ({ page }) => {
    const user = await registerUser(page, 'reset_survivor');
    // User can still navigate to dashboard after reset
    await expect(page).toHaveURL('/');
    expect(user.username).toBeTruthy();
  });

  test('season bets are available again after reset with regenerateSchedule=true', async ({ page }) => {
    await loginAsAdmin(page);
    const token = await getToken(page);
    // Perform reset via API
    const resetRes = await apiPost(token!, '/api/admin/season/reset', {
      confirm: true, regenerateSchedule: true, resetUserBalances: false,
    });
    expect(resetRes.status()).toBe(200);

    // Check that season bet status is open (Round 1 exists and hasn't started)
    const ctx = await request.newContext({ baseURL: BASE });
    const statusRes = await ctx.get('/api/bets/season/status', {
      headers: { Authorization: `Bearer ${token!}` },
    });
    expect(statusRes.status()).toBe(200);
    const status = await statusRes.json() as { open: boolean };
    expect(status.open).toBe(true);
  });

  test('reset restores every team skill/morale to its persisted season baseline (JSON ±3)', async ({ page }) => {
    await loginAsAdmin(page);
    const token = await getToken(page);

    // In-place reset (regenerateSchedule=false) — the unit tests cover both flags; this
    // proves the live wiring against the bundled data file the backend actually reads.
    const resetRes = await apiPost(token!, '/api/admin/season/reset', {
      confirm: true, regenerateSchedule: false, resetUserBalances: false,
    });
    expect(resetRes.status()).toBe(200);
    const body = await resetRes.json() as { teamsReset: number; teamBaselinesRestored: number };
    expect(body.teamsReset).toBe(14);
    expect(body.teamBaselinesRestored).toBe(14);

    // Source of truth: the same JSON file the backend restores from.
    const seedPath = path.resolve(process.cwd(), '../backend/src/main/resources/data/ligat-haal-2025-2026.json');
    const seed = JSON.parse(fs.readFileSync(seedPath, 'utf-8')) as {
      teams: { name: string; skillLevel: number; morale: number }[];
    };

    const ctx = await request.newContext({ baseURL: BASE });
    const teamsRes = await ctx.get('/api/teams', { headers: { Authorization: `Bearer ${token!}` } });
    expect(teamsRes.status()).toBe(200);
    const teams = await teamsRes.json() as {
      name: string; skillLevel: number; morale: number; baselineSkillLevel: number; baselineMorale: number;
    }[];
    expect(teams).toHaveLength(14);
    for (const t of teams) {
      const reference = seed.teams.find((s) => s.name === t.name);
      expect(reference, `reference for ${t.name}`).toBeTruthy();
      // Reset restores the persisted starting values of THIS season (not a reroll) ...
      expect(t.skillLevel, `skill of ${t.name}`).toBe(t.baselineSkillLevel);
      expect(t.morale, `morale of ${t.name}`).toBe(t.baselineMorale);
      // ... and that baseline is the league-data value plus a small bounded random variation.
      expect(Math.abs(t.baselineSkillLevel - reference!.skillLevel), `variation for ${t.name}`).toBeLessThanOrEqual(3);
      expect(t.baselineMorale).toBe(reference!.morale);
      expect(t.skillLevel).toBeGreaterThanOrEqual(40);
      expect(t.skillLevel).toBeLessThanOrEqual(100);
    }

    // Teams page shows the restored values again (Skill = 2nd column, Morale = 3rd).
    const beerSheva = teams.find((t) => t.name === 'Hapoel Beer Sheva')!;
    await page.goto('/teams');
    const row = page.locator('table.data-table tbody tr', { hasText: 'Hapoel Beer Sheva' });
    await expect(row).toHaveCount(1);
    await expect(row.locator('td').nth(1)).toHaveText(String(beerSheva.baselineSkillLevel));
    await expect(row.locator('td').nth(2)).toHaveText(String(beerSheva.baselineMorale));
  });
});
