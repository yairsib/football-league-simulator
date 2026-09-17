# Team Squad Entry Template

This template shows exactly how to fill in **one team's** entry in
`ligat-haal-2025-2026.json` with a real squad. It complements
`ligat-haal-2025-2026.schema.md` (the full format/validation reference) with a
copy-paste-ready, annotated example.

> All player names below are placeholders and are clearly marked
> `DO_NOT_IMPORT` in `dataSource` so they are never mistaken for real data and
> are skipped by the loader (see "dataSource rules").
> **Never copy these names into the real data file. Never invent players.**

## 1. Team object

```json
{
  "name": "Example FC",
  "displayName": "Example FC",
  "skillLevel": 70,
  "morale": 6,
  "defaultFormation": "4-3-3",
  "players": [ /* player objects — see section 2 */ ]
}
```

- `name` must be unique and non-blank — match the existing entry already in
  `ligat-haal-2025-2026.json`; do not rename teams.
- `skillLevel` (40–100) and `morale` (0–10) are **internal simulation
  ratings**, not official ratings — leave the team's existing values unless you
  have a documented reason to adjust them.
- `defaultFormation` should reflect the team's real, sourced formation
  (e.g. from 365Scores lineups), e.g. `"4-3-3"`, `"4-2-3-1"`, `"3-5-2"`.

## 2. Player object

```json
{
  "fullName": "PLACEHOLDER Player One — DO_NOT_IMPORT",
  "position": "GK",
  "jerseyNumber": 1,
  "rating": 75,
  "status": "FIT",
  "starter": true,
  "substitute": false,
  "lineupOrder": 1,
  "dataSource": "EXAMPLE_DO_NOT_IMPORT"
}
```

- `fullName`, `position`, `rating`, `starter`, `substitute` are required.
- `jerseyNumber`, `lineupOrder`, `dataSource` are optional but strongly
  recommended for real data.
- `position` must be one of: `GK, CB, LB, RB, DM, CM, AM, LW, RW, ST`.

## 3. Rating rules

- `rating` is an **internal simulation rating** used by `PlayerService`/match
  simulation — it is **not** an official rating from Transfermarkt, FIFA,
  EA Sports, or any other source.
- Pick a rating consistent with the team's `skillLevel` and the player's role
  (e.g. starters and key players generally rate higher than fringe squad
  players). Document your reasoning via `dataSource` if you derive it from a
  real source's market value or role description — but do not present it as an
  "official" number.

## 4. Starter / substitute rules

- A player is **either** a starter, a substitute, or neither (extra squad
  player) — **never both** (`starter && substitute` is a hard error).
- A team's starters should number **exactly 11**, including **exactly one**
  goalkeeper (`position: "GK"`).
- `lineupOrder` (1–11) should be set **only on starters** and must be **unique
  within the team** — use it to encode the real lineup order/shirt order from
  your source (e.g. a 365Scores lineup graphic).
- Substitutes should number **at least 7** for a complete squad report.
- Extra squad players (neither starter nor substitute) are allowed and counted
  toward `playersCount`.

## 5. Status rules

- Always set `status: "FIT"` for real players.
- **Never** enter real-world injury/suspension status
  (`INJURED`/`SUSPENDED`, `injuryDescription`, `injuredUntilRound`,
  `injuryMatchesRemaining`, `injuryMatchesTotal`) — these fields are **ignored
  on import** and injuries/suspensions/goals/assists/red cards are
  **app-generated only**, produced exclusively by `SimulationService` as
  matches are simulated (see "Injuries are simulation-only" in
  `ligat-haal-2025-2026.schema.md`).
- An `INJURED`/`SUSPENDED` player can never be a `starter` or `substitute`
  (hard error) — since real players always import as `FIT`, simply never set
  those statuses for real squad entries.

## 6. `dataSource` rules

- Set `dataSource` to where you found the player's data, e.g.:
  - `"Transfermarkt"` — squad list, positions, jersey numbers, market-value tier
  - `"365Scores"` — recommended starting lineup / substitutes for a given match
  - `"official"` — the club's own website or matchday programme
  - `"Israeli FA"` — cross-checking name spelling / squad numbers
  - Combine sources where useful, e.g. `"Transfermarkt + 365Scores lineup reference"`
- Values starting with `EXAMPLE` (e.g. `EXAMPLE_DO_NOT_IMPORT`,
  `EXAMPLE_TEMPLATE`) mark placeholder data that the loader **always skips** —
  use this prefix for any draft/example entries you leave in the file while
  working, and remove them before the team is considered ready.

## 7. Full example (placeholder squad — DO NOT copy into real data)

```json
{
  "name": "Example FC",
  "displayName": "Example FC",
  "skillLevel": 70,
  "morale": 6,
  "defaultFormation": "4-3-3",
  "players": [
    { "fullName": "PLACEHOLDER GK One — DO_NOT_IMPORT",  "position": "GK", "jerseyNumber": 1,  "rating": 75, "status": "FIT", "starter": true,  "substitute": false, "lineupOrder": 1,  "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER RB One — DO_NOT_IMPORT",  "position": "RB", "jerseyNumber": 2,  "rating": 72, "status": "FIT", "starter": true,  "substitute": false, "lineupOrder": 2,  "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER CB One — DO_NOT_IMPORT",  "position": "CB", "jerseyNumber": 3,  "rating": 73, "status": "FIT", "starter": true,  "substitute": false, "lineupOrder": 3,  "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER CB Two — DO_NOT_IMPORT",  "position": "CB", "jerseyNumber": 4,  "rating": 73, "status": "FIT", "starter": true,  "substitute": false, "lineupOrder": 4,  "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER LB One — DO_NOT_IMPORT",  "position": "LB", "jerseyNumber": 5,  "rating": 71, "status": "FIT", "starter": true,  "substitute": false, "lineupOrder": 5,  "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER DM One — DO_NOT_IMPORT",  "position": "DM", "jerseyNumber": 6,  "rating": 74, "status": "FIT", "starter": true,  "substitute": false, "lineupOrder": 6,  "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER CM One — DO_NOT_IMPORT",  "position": "CM", "jerseyNumber": 8,  "rating": 76, "status": "FIT", "starter": true,  "substitute": false, "lineupOrder": 7,  "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER AM One — DO_NOT_IMPORT",  "position": "AM", "jerseyNumber": 10, "rating": 78, "status": "FIT", "starter": true,  "substitute": false, "lineupOrder": 8,  "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER LW One — DO_NOT_IMPORT",  "position": "LW", "jerseyNumber": 11, "rating": 75, "status": "FIT", "starter": true,  "substitute": false, "lineupOrder": 9,  "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER RW One — DO_NOT_IMPORT",  "position": "RW", "jerseyNumber": 7,  "rating": 75, "status": "FIT", "starter": true,  "substitute": false, "lineupOrder": 10, "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER ST One — DO_NOT_IMPORT",  "position": "ST", "jerseyNumber": 9,  "rating": 79, "status": "FIT", "starter": true,  "substitute": false, "lineupOrder": 11, "dataSource": "EXAMPLE_DO_NOT_IMPORT" },

    { "fullName": "PLACEHOLDER GK Two — DO_NOT_IMPORT",  "position": "GK", "jerseyNumber": 31, "rating": 68, "status": "FIT", "starter": false, "substitute": true, "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER CB Three — DO_NOT_IMPORT", "position": "CB", "jerseyNumber": 15, "rating": 67, "status": "FIT", "starter": false, "substitute": true, "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER RB Two — DO_NOT_IMPORT",  "position": "RB", "jerseyNumber": 16, "rating": 66, "status": "FIT", "starter": false, "substitute": true, "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER DM Two — DO_NOT_IMPORT",  "position": "DM", "jerseyNumber": 17, "rating": 68, "status": "FIT", "starter": false, "substitute": true, "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER CM Two — DO_NOT_IMPORT",  "position": "CM", "jerseyNumber": 18, "rating": 69, "status": "FIT", "starter": false, "substitute": true, "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER AM Two — DO_NOT_IMPORT",  "position": "AM", "jerseyNumber": 19, "rating": 70, "status": "FIT", "starter": false, "substitute": true, "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER LW Two — DO_NOT_IMPORT",  "position": "LW", "jerseyNumber": 20, "rating": 67, "status": "FIT", "starter": false, "substitute": true, "dataSource": "EXAMPLE_DO_NOT_IMPORT" },
    { "fullName": "PLACEHOLDER ST Two — DO_NOT_IMPORT",  "position": "ST", "jerseyNumber": 21, "rating": 70, "status": "FIT", "starter": false, "substitute": true, "dataSource": "EXAMPLE_DO_NOT_IMPORT" }
  ]
}
```

This example yields: 19 players, 11 starters (1 GK, `lineupOrder` 1–11, no
duplicates), 8 substitutes — which the squad-completeness report
(`GET /api/admin/data/validate`) would mark `COMPLETE` if it were real,
sourced data.

## 8. Status rules for the squad-completeness report

`GET /api/admin/data/validate` reports each team's status as:

- `MISSING` — `players[]` is empty.
- `PARTIAL` — has some players but does not yet meet the `COMPLETE` bar.
- `COMPLETE` — at least 18 players, exactly 11 starters (with exactly 1
  starting GK), at least 7 substitutes, and no hard errors for the team.

If you only have partial, verified information (e.g. starters but not the full
bench), enter only what you can verify and leave the rest out — `PARTIAL` is
expected and fine. **Never invent players to reach `COMPLETE`.**
