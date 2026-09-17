# Ligat Ha'Al 2025/2026 — Real Data Format

This document describes the JSON format used to seed/import real Israeli
Premier League (Ligat Ha'Al) 2025/2026 data: teams, squads, lineups,
substitutes, unavailable players, and formations.

The actual data file is `ligat-haal-2025-2026.json` in this same folder.
A structural template with a clearly-marked example team is in
`ligat-haal-2025-2026.template.json`.

## Top-level object

| Field        | Type     | Required | Notes                                           |
|--------------|----------|----------|-------------------------------------------------|
| `season`     | string   | yes      | e.g. `"2025/2026"`                              |
| `league`     | string   | yes      | e.g. `"Israeli Premier League"`                 |
| `sourceNotes`| string[] | no       | Free-text notes about where data came from      |
| `teams`      | array    | yes      | Must contain **exactly 14** team objects        |

## Team object

| Field             | Type    | Required | Validation                                              |
|-------------------|---------|----------|---------------------------------------------------------|
| `name`            | string  | yes      | Non-blank, **unique** across all teams                  |
| `displayName`     | string  | no       | Falls back to `name` if blank/omitted                   |
| `skillLevel`      | int     | yes      | **40–100**                                               |
| `morale`          | int     | yes      | **0–10**                                                 |
| `defaultFormation`| string  | yes      | Non-blank, e.g. `"4-3-3"`, `"4-2-3-1"`, `"5-3-2"`        |
| `players`         | array   | yes      | May be **empty** — see "Empty squads" below              |

## Player object

| Field               | Type     | Required | Validation                                                      |
|---------------------|----------|----------|------------------------------------------------------------------|
| `fullName`          | string   | yes      | Non-blank                                                        |
| `position`          | string   | yes      | One of the `Position` enum values (see below)                    |
| `jerseyNumber`      | int      | no       | —                                                                |
| `rating`            | int      | yes      | —                                                                |
| `status`            | string   | yes      | One of the `PlayerStatus` enum values (see below). **Ignored on import — every imported player is set to `FIT`** (see "Injuries are simulation-only" below) |
| `injuryDescription` | string   | no       | **Ignored on import.** Free text; only meaningful for app-simulation test fixtures, never for real-world data |
| `injuredUntilRound` | int      | no       | **Ignored on import.** Legacy field, superseded by `injuryMatchesRemaining`/`injuryMatchesTotal` |
| `injuryMatchesRemaining` | int | no       | **Ignored on import.** Number of matches the player will miss; set only by the simulation |
| `injuryMatchesTotal`| int      | no       | **Ignored on import.** Total duration of the current injury; set only by the simulation |
| `starter`           | boolean  | yes      | A player cannot be both `starter` and `substitute`               |
| `substitute`        | boolean  | yes      | A player cannot be both `starter` and `substitute`               |
| `lineupOrder`       | int      | no       | Required to be **unique among starters** of the same team        |
| `dataSource`        | string   | no       | Where the player data came from, e.g. `"365Scores"`, `"official"`, `"Transfermarkt"`, `"manual"`. Values starting with `EXAMPLE` mark placeholder/example data that the loader will **never** import. |

### `Position` enum values
`GK, CB, LB, RB, DM, CM, AM, LW, RW, ST`

### `PlayerStatus` enum values
`FIT, INJURED, SUSPENDED`

## Validation rules

The loader (`LeagueDataImportService`) enforces the following rules. Rows
marked **error** block the import; rows marked **warning** do not.

| Rule                                                                          | Severity |
|-------------------------------------------------------------------------------|----------|
| There must be exactly 14 teams                                                | error    |
| Team names must be unique                                                      | error    |
| `skillLevel` must be between 40 and 100                                       | error    |
| `morale` must be between 0 and 10                                             | error    |
| `defaultFormation` must not be blank                                          | error    |
| Player `fullName` must not be blank                                           | error    |
| Player `position` must be a valid `Position` enum value                       | error    |
| Player `status` must be a valid `PlayerStatus` enum value                     | error    |
| A player cannot be both `starter` and `substitute`                            | error    |
| Injured or suspended players cannot be `starter` or `substitute`              | error    |
| If a team has players: starters count should be exactly 11                    | warning  |
| If a team has players: starters should include exactly one `GK`               | warning  |
| If a team has players: substitutes should be at least 7 (full squad expected) | warning  |
| `lineupOrder` should be unique among a team's starters                        | warning  |
| If a team's `players` array is empty, the squad is reported as missing        | warning  |

> Empty squads never fail validation or import — they are reported as
> warnings ("squad is missing") so the rest of the league can still be
> imported while real squad data is collected.

## Squad-completeness summary (`squadSummaries`)

`GET /api/admin/data/validate` also returns a `squadSummaries` array — one
entry per team — to make it easy to see, at a glance, which teams still need
real squad data before bulk insertion. Each entry reports:

| Field                             | Meaning                                                              |
|-----------------------------------|----------------------------------------------------------------------|
| `teamName`                         | The team's name                                                      |
| `playersCount`                     | Total non-example players in `players[]`                            |
| `startersCount` / `substitutesCount` / `unavailableCount` | Counts by role (unavailable = `INJURED`/`SUSPENDED`) |
| `hasExactlyOneStartingGoalkeeper`  | Exactly one starter with `position: "GK"`                           |
| `hasAtLeast7Substitutes`           | At least 7 substitutes                                              |
| `hasExactly11Starters`             | Exactly 11 starters                                                 |
| `duplicateJerseyNumbers`           | Jersey numbers shared by more than one player (if any)              |
| `duplicateLineupOrders`            | `lineupOrder` values shared by more than one starter (if any)       |
| `invalidStarterSubstituteOverlap`  | `true` if any player is marked both starter and substitute          |
| `missingPositions`                 | `Position` enum values with zero players in the squad               |
| `status`                           | `COMPLETE` / `PARTIAL` / `MISSING` — see rules below                |

### `status` rules

- **`MISSING`** — `players[]` is empty.
- **`COMPLETE`** — at least 18 players, exactly 11 starters (incl. exactly one
  starting GK), at least 7 substitutes, and no hard errors for that team.
- **`PARTIAL`** — has some players but doesn't yet meet the `COMPLETE` bar.

This report never blocks validation or import — it is purely informational, so
real squads can be filled in incrementally, team by team, with clear visibility
into what's still missing.

## Injuries are simulation-only

Injuries in this app are generated **exclusively** by the match simulation —
never copied from real life. Real-world injury status from 365Scores,
Transfermarkt, club sites, or any other source must **not** be treated as an
actual injury in this app.

For that reason, `LeagueDataImportService` always imports players with
`status = FIT`, `injuryDescription = null`, `injuredUntilRound = null`,
`injuryMatchesRemaining = 0`, and `injuryMatchesTotal = 0`, regardless of what
the source JSON contains for those fields. A player only becomes `INJURED`
(with a description and a match-count duration) or `SUSPENDED` if the running
simulation puts them in that state.

## How to fill in real data (bulk squad insertion workflow)

A copy-paste-ready, fully annotated example of one team's entry — covering the
team object, player object, rating rules, starter/substitute rules, status
rules, and `dataSource` rules — lives in
[`team-squad-entry-template.md`](team-squad-entry-template.md).

1. **Collect verified data from reliable sources, per team:**
   - **Transfermarkt** — squad list, positions, jersey numbers, and
     market-value tier (useful as a rough guide when picking an internal
     `rating`, alongside the team's `skillLevel`).
   - **365Scores lineups** — recommended starting lineup and substitutes for a
     given matchday, when available (use this to set `starter`/`substitute`
     and `lineupOrder`).
   - **Official club pages or the Israeli Football Association** — for
     cross-checking name spelling, squad numbers, and positions.
2. Add `Player` objects to a team's `players` array using the format above
   (see the template for a full worked example). Set `dataSource` to where
   each player's data came from, e.g. `"Transfermarkt"`, `"365Scores"`,
   `"official"`, or a combination like `"Transfermarkt + 365Scores lineup reference"`.
3. **Never invent player names, ratings, or injuries.** Only enter data that
   is explicitly documented in your source. If you can only verify part of a
   squad (e.g. the starting XI but not the bench), enter only what you can
   verify and leave the rest out — a `PARTIAL` report is expected and fine;
   never pad the squad to reach `COMPLETE`. Do not enter real-world injury
   status either — it is ignored on import (see "Injuries are simulation-only"
   above) and would be misleading anyway. Remember: `skillLevel`/`morale`/
   `rating` are **internal simulation ratings**, not official ratings — don't
   present them as such.
4. Run `GET /api/admin/data/validate` (ADMIN only, also surfaced in the Admin
   page's **Validate League Data** button) to check the file before
   importing. Besides `errors`/`warnings`, the response includes a
   `squadSummaries` report — one row per team — showing exactly which teams
   are `COMPLETE`, `PARTIAL`, or still `MISSING` squad data, plus counts and
   any duplicate-jersey/lineup-order issues (see "Squad-completeness summary"
   above).
5. Run `POST /api/admin/data/import` (ADMIN only, also surfaced via **Import
   League Data**) to seed/update teams and players. The import is an upsert:
   existing teams are matched by `name` and updated; existing players are
   matched by `(team, fullName)` and updated. Nothing is deleted, and nothing
   is invented for teams that still have an empty `players` array.
6. Repeat team by team — missing/partial squads are expected and safe to
   leave as-is between imports; the loader never deletes or invents data.
