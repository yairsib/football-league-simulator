# API Plan — Football League Simulator

Base path: `/api`
All protected endpoints require `Authorization: Bearer <token>` header.

---

## Auth

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /api/auth/register | Public | Register a new user. Returns JWT. |
| POST | /api/auth/login | Public | Login. Returns JWT. |

### POST /api/auth/register
Request body:
```json
{ "username": "string", "email": "string", "password": "Secret123" }
```
Password rules (enforced server-side): minimum 8 characters, at least one uppercase letter,
one lowercase letter, and one digit.

During registration, if the submitted email matches the configured admin email
(`app.admin.email` in `application.properties`, case-insensitive), the account is created
with role `ADMIN`; otherwise role `USER`.

Response (201):
```json
{ "token": "string", "user": { "id": 1, "username": "string", "email": "string", "balance": 1000.00, "role": "USER" } }
```

### POST /api/auth/login
Request body:
```json
{ "email": "string", "password": "string" }
```
If the account's email matches the configured admin email but the stored role is not yet
`ADMIN` (e.g. the admin email was configured after the account was created), the role is
promoted to `ADMIN` on login to avoid lockout.

Response (200):
```json
{ "token": "string", "user": { "id": 1, "username": "string", "email": "string", "balance": 1000.00, "role": "USER" } }
```

---

## Users

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/users/me | User | Get current user profile and balance. |
| PUT | /api/users/me | User | Update current user's username/email. Returns a fresh JWT. |

### GET /api/users/me
Response (200):
```json
{ "id": 1, "username": "string", "email": "string", "balance": 850.00, "role": "USER" }
```

### PUT /api/users/me
Request body:
```json
{ "username": "newUsername", "email": "newEmail@example.com" }
```
Rules:
- `username` and `email` are required; `email` must be a valid address.
- `username` must be unique (excluding the current user).
- `email` must be unique (excluding the current user).
- Password and balance cannot be changed via this endpoint.
- Since the JWT subject is the user's email, changing the email invalidates the old token —
  the response always contains a fresh token.

Response (200) — same shape as auth responses, so the frontend can replace its stored token directly:
```json
{
  "token": "string",
  "user": { "id": 1, "username": "string", "email": "string", "balance": 1000.00, "role": "USER" }
}
```

---

## Teams

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/teams | User | Get all teams. |
| GET | /api/teams/{id} | User | Get team by id. |
| GET | /api/teams/{teamId}/squad | User | Get a team's full squad (formation + all players). |
| GET | /api/teams/{teamId}/lineup | User | Get a team's lineup split into starters / substitutes / unavailable players. |

### GET /api/teams/{teamId}/squad
Response (200):
```json
{
  "teamId": 1,
  "teamName": "string",
  "formation": "4-3-3",
  "players": [
    {
      "id": 1,
      "fullName": "string",
      "position": "ST",
      "jerseyNumber": 9,
      "rating": 78,
      "status": "FIT",
      "injuryDescription": null,
      "injuredUntilRound": null,
      "injuryMatchesRemaining": 0,
      "injuryMatchesTotal": 0,
      "starter": true,
      "substitute": false,
      "lineupOrder": 1,
      "goals": 0,
      "assists": 0,
      "redCards": 0,
      "suspensionMatchesRemaining": 0
    }
  ]
}
```
`goals`/`assists`/`redCards`/`suspensionMatchesRemaining` are simulation-generated only —
imported real players always start at zero (see "Match Events & Player Stats" below).

### GET /api/teams/{teamId}/lineup
Response (200): an **effective lineup** generated dynamically by `LineupService` on every request
— the persisted `starter`/`substitute`/`lineupOrder` flags are the baseline squad roles and are
never rewritten. `starters`/`substitutes` never contain a player who is `SUSPENDED`
(`suspensionMatchesRemaining > 0`), or `INJURED` with `injuryMatchesRemaining > 0` — such players
always appear under `unavailablePlayers` instead, with `fullName`, `position`, `status`,
`injuryDescription`, `injuryMatchesRemaining`, `injuryMatchesTotal`, and
`suspensionMatchesRemaining` so the UI can show e.g. "Injured — out for 3 matches" /
"Suspended — 1 match remaining". When an original starter is unavailable, the service swaps in the
best available replacement by position compatibility and rating (see `docs/architecture.md` →
"Effective lineups: dynamic auto substitutions"), keeps 11 starters whenever possible, and refills
the bench toward 7 substitutes. Injuries/suspensions are generated only by the match simulation —
real-world status is never imported, so every imported real player starts `FIT`
(see "Real League Data" / `ligat-haal-2025-2026.schema.md`).
```json
{
  "teamId": 1,
  "teamName": "string",
  "formation": "4-3-3",
  "starters": [ /* PlayerResponse[] */ ],
  "substitutes": [ /* PlayerResponse[] */ ],
  "unavailablePlayers": [ /* PlayerResponse[] */ ],
  "effectiveLineupGenerated": false,
  "lineupWarnings": [ /* string[], e.g. "Only 9 available players — team cannot field a full 11-player lineup." */ ],
  "replacedPlayers": [
    { "originalPlayerName": "string", "replacementPlayerName": "string", "reason": "Injured | Suspended | Injured + Suspended", "position": "CB" }
  ]
}
```
`effectiveLineupGenerated`/`lineupWarnings`/`replacedPlayers` are **additive, optional** fields —
existing clients that ignore them keep working unchanged. `GET /api/matches/{matchId}/lineups`
returns the same three fields per side (`home*`/`away*` prefixed) alongside the existing
`home`/`away` starters/substitutes/unavailable lists and weather.

### GET /api/teams
Response (200): array of team objects
```json
[
  {
    "id": 1,
    "name": "string",
    "skillLevel": 80,
    "morale": 70,
    "injuries": 5,
    "wins": 0,
    "draws": 0,
    "losses": 0,
    "goalsFor": 0,
    "goalsAgainst": 0,
    "points": 0
  }
]
```

---

## Rounds

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/rounds | User | Get all rounds. |
| GET | /api/rounds/{id} | User | Get round by id with its matches. |

---

## Matches

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/matches | User | Get all matches (optional ?roundId= filter). |
| GET | /api/matches/{id} | User | Get single match with odds. |
| GET | /api/matches/{matchId}/lineups | User | Get both teams' lineups, formations, unavailable players, and match weather. |
| GET | /api/matches/{matchId}/events | User | Get goal/red-card events for a match, ordered by minute (empty until the match is simulated). |
| GET | /api/matches/{matchId}/correct-score-odds | User | Get odds for the 25 "popular" correct-score outcomes (0-4 home goals × 0-4 away goals). Powers the Correct Score betting grid. |
| GET | /api/matches/{matchId}/handicap-odds | User | Get the 3 European Handicap -1 (home) outcomes with labels and odds. Powers the Handicap betting modal. |

### GET /api/matches/{id}
Response (200):
```json
{
  "id": 1,
  "roundNumber": 1,
  "homeTeamId": 1,
  "homeTeamName": "string",
  "awayTeamId": 2,
  "awayTeamName": "string",
  "homeGoals": null,
  "awayGoals": null,
  "status": "SCHEDULED",
  "bettingOpen": false,
  "homeOdds": null,
  "drawOdds": null,
  "awayOdds": null,
  "matchDate": null,
  "weatherCondition": "RAIN",
  "weatherImpact": -1
}
```
`weatherCondition`/`weatherImpact` are assigned for every match when the schedule is generated
(see Weather below), so they are visible to users before betting opens or the match is simulated.

### GET /api/matches/{matchId}/lineups
Response (200):
```json
{
  "matchId": 1,
  "homeTeamName": "string",
  "awayTeamName": "string",
  "homeFormation": "4-3-3",
  "awayFormation": "4-2-3-1",
  "homeStarters": [ /* PlayerResponse[] */ ],
  "homeSubstitutes": [ /* PlayerResponse[] */ ],
  "homeUnavailablePlayers": [ /* PlayerResponse[] */ ],
  "awayStarters": [ /* PlayerResponse[] */ ],
  "awaySubstitutes": [ /* PlayerResponse[] */ ],
  "awayUnavailablePlayers": [ /* PlayerResponse[] */ ],
  "weatherCondition": "RAIN",
  "weatherImpact": -1
}
```

### GET /api/matches/{matchId}/events
Response (200): goal and red-card events for the match, ordered by minute (then id). Empty
array `[]` until the match is simulated, and may stay empty for a finished match (e.g. a
0-0 draw, or a match between two teams with no imported squad — events are skipped rather
than invented when no eligible player exists).
```json
[
  {
    "id": 1,
    "matchId": 1,
    "teamId": 2,
    "teamName": "Maccabi Tel Aviv",
    "playerId": 5,
    "playerName": "Sagiv Jehezkel",
    "assistPlayerId": 24,
    "assistPlayerName": "Denny Gropper",
    "eventType": "GOAL",
    "minute": 13,
    "description": "Sagiv Jehezkel scored, assisted by Denny Gropper"
  },
  {
    "id": 2,
    "matchId": 1,
    "teamId": 5,
    "teamName": "Hapoel Tel Aviv",
    "playerId": 41,
    "playerName": "Tyrese Asante",
    "assistPlayerId": null,
    "assistPlayerName": null,
    "eventType": "RED_CARD",
    "minute": 53,
    "description": "Tyrese Asante received a red card"
  }
]
```

### GET /api/matches/{matchId}/correct-score-odds
Response (200): the 25 combinations of 0-4 home goals × 0-4 away goals, each with its
current odds (Poisson goal model — see [docs/architecture.md](architecture.md) →
"Correct Score Odds"). Ordered by home goals then away goals.
```json
[
  { "homeGoals": 0, "awayGoals": 0, "odds": 9.50 },
  { "homeGoals": 0, "awayGoals": 1, "odds": 14.20 },
  ...
  { "homeGoals": 1, "awayGoals": 0, "odds": 6.80 },
  ...
  { "homeGoals": 4, "awayGoals": 4, "odds": 80.00 }
]
```
`POST /api/bets/correct-score` accepts any scoreline with each side 0-6 (computed the same
way), not just these 25 "popular" combinations.

### GET /api/matches/{matchId}/handicap-odds
Returns the three European Handicap -1 (home) outcomes with their labels and current odds.
Odds are derived from the same Poisson expected-goals model used by correct-score; probabilities
are bucketed by `homeGoals - 1 - awayGoals` sign and a bookmaker margin of 1.10 is applied
(`odds = 1 / (probability * 1.10)`). Bounds: [1.20, 25.00].
```json
[
  { "selection": "HOME_MINUS_ONE", "label": "Home -1",     "odds": 2.10 },
  { "selection": "HANDICAP_DRAW",  "label": "Handicap Draw", "odds": 3.60 },
  { "selection": "AWAY_PLUS_ONE",  "label": "Away +1",     "odds": 2.85 }
]
```

---

## Players

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/players/{playerId} | User | Get a single player's profile and simulation-generated stats. |
| GET | /api/teams/{teamId}/players/stats | User | Get every player on a team (full squad — starters, substitutes, additional squad players) with their stats (goals/assists/red cards/suspension). Powers the **Stats page → Teams → View Squad** modal. |
| GET | /api/players/leaders?stat={goals\|assists\|redCards}&limit={n} | User | League leaders for one stat, returned as `PlayerStatsResponse[]` (includes `teamId`/`teamName`). `stat` must be one of `goals`, `assists`, `redCards` — anything else returns `400 Bad Request`. `limit` defaults to 20. Powers the **Stats page** (Top Scorers / Top Assists / Red Cards tables). |

`GET /api/players/{playerId}` and `GET /api/teams/{teamId}/players/stats` return `PlayerResponse`
(single object / array respectively) — the same shape as in `GET /api/teams/{teamId}/squad`,
including `goals`, `assists`, `redCards`, and `suspensionMatchesRemaining`.

### Leaders sorting (GET /api/players/leaders)

- `stat=goals`: `goals desc, assists desc, rating desc, fullName asc`
- `stat=assists`: `assists desc, goals desc, rating desc, fullName asc`
- `stat=redCards`: `redCards desc, fullName asc`

Sorting is computed in-memory in `PlayerService.getLeaders` (small dataset, ~375 players across
14 teams) — no extra repository methods needed. At season start, when every player is at 0,
the same comparators still produce a stable, consistent order (by rating/name).

---

## Match Events & Player Stats

Goals, assists, and red cards are generated **only** by `SimulationService` while simulating
a match — never imported, never invented outside of simulation:

- **Goalscorer**: chosen by weighted random selection favoring attacking positions, higher
  ratings, and starters, from players who are `FIT`/available (excluding anyone just sent off
  in the same match); falls back to substitutes, and the event is skipped (no player, no
  stats) if a team has no eligible players at all (e.g. an empty imported squad).
- **Assist**: 75% chance per goal, attributed to a different team-mate via the same weighted
  mechanism with assist-favoring weights; omitted from the description when it doesn't occur.
- **Red card**: 8% chance per team per match (max one per team), preferring starters; reduces
  that team's win/draw chances by 15–25% for **this match's** score generation (mostly
  transferred to the opponent), increments `redCards`, and immediately suspends the player
  (`status = SUSPENDED`, `suspensionMatchesRemaining = 1`).
- **Suspension countdown**: decremented the same way as injuries — but only starting from the
  player's team's *next* match (the red card just issued is not decremented in the same
  match it's created in). A suspended-and-injured player stays `INJURED` until the injury
  also clears (suspension takes priority for the `status` field while both are active).

Imported real players always start with `goals = 0`, `assists = 0`, `redCards = 0`,
`suspensionMatchesRemaining = 0` regardless of source data.

---

## Weather

- Each match is assigned a `weatherCondition` (`CLEAR`, `RAIN`, `WIND`, `HOT`, `COLD`, `STORM`)
  and a small signed `weatherImpact` when the schedule is generated (`POST /api/admin/generate-schedule`),
  so weather is visible to users on the Matches page before betting opens or the match is simulated.
- Weather nudges odds and simulation outcomes without overpowering team skill — see
  [docs/architecture.md](architecture.md) for the exact effect of each condition.

---

## League Table

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/league-table | User | Get full league table sorted by rules. |

### GET /api/league-table
Response (200): ordered array
```json
[
  {
    "position": 1,
    "team": { "id": 1, "name": "string" },
    "played": 5,
    "wins": 4,
    "draws": 0,
    "losses": 1,
    "goalsFor": 12,
    "goalsAgainst": 4,
    "goalDifference": 8,
    "points": 12
  }
]
```

---

## Bets

Every bet has a `betType` (`SINGLE` | `COMBO`) and, independently, a `market`
(`MATCH_RESULT` | `CORRECT_SCORE` | `HANDICAP`). Combo bets are always `market: MATCH_RESULT`.

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /api/bets | User | Place a single MATCH_RESULT (1X2) bet. |
| POST | /api/bets/correct-score | User | Place a single CORRECT_SCORE bet on an exact scoreline. |
| POST | /api/bets/handicap | User | Place a single HANDICAP bet (European Handicap -1, home). |
| POST | /api/bets/combo | User | Place a combo (accumulator) bet — 2+ MATCH_RESULT selections, one stake. |
| PUT | /api/bets/{betId} | User | Edit an OPEN single MATCH_RESULT bet (amount and/or prediction). |
| PUT | /api/bets/{betId}/correct-score | User | Edit an OPEN single CORRECT_SCORE bet (amount and/or predicted score). |
| PUT | /api/bets/{betId}/handicap | User | Edit an OPEN single HANDICAP bet (amount and/or selection). |
| PUT | /api/bets/{betId}/combo | User | Edit an OPEN combo bet (amount and/or selections). |
| POST | /api/bets/{betId}/cancel | User | Cancel an OPEN bet (single or combo, any market) and refund the stake. |
| GET | /api/bets/my | User | Get current user's bets (single + combo, all statuses). |
| GET | /api/bets/my/open | User | Get current user's OPEN bets. |
| GET | /api/bets/my/history | User | Get current user's settled/cancelled bets. |

### POST /api/bets
Request body:
```json
{ "matchId": 1, "prediction": "HOME_WIN", "amount": 50.00 }
```
Response (201) — `BetResponse`:
```json
{
  "id": 1,
  "betType": "SINGLE",
  "matchId": 1,
  "homeTeamName": "string",
  "awayTeamName": "string",
  "prediction": "HOME_WIN",
  "market": "MATCH_RESULT",
  "predictedHomeGoals": null,
  "predictedAwayGoals": null,
  "displayLabel": "Match Result",
  "amount": 50.00,
  "odds": 1.85,
  "totalOdds": null,
  "possibleWin": 92.50,
  "status": "OPEN",
  "profit": null,
  "createdAt": "2026-01-01T12:00:00",
  "settledAt": null,
  "selections": [
    { "matchId": 1, "homeTeamName": "string", "awayTeamName": "string", "prediction": "HOME_WIN", "odds": 1.85 }
  ]
}
```
For SINGLE bets, `selections` always contains exactly one entry mirroring the top-level
match/prediction/odds fields, so the frontend can render single and combo bets with the
same selections-list UI. `market`/`predictedHomeGoals`/`predictedAwayGoals`/`displayLabel`
are additive fields — `predictedHomeGoals`/`predictedAwayGoals` are `null`
and `prediction` is set for `MATCH_RESULT` bets; for `CORRECT_SCORE` bets it's the reverse
(`prediction: null`, `predictedHomeGoals`/`predictedAwayGoals` set, `displayLabel` e.g.
`"Correct Score 1-0"`). Combo bets always have `market: "MATCH_RESULT"`,
`predictedHomeGoals`/`predictedAwayGoals: null`.

Errors:
- 400: Match betting not open, amount <= 0, insufficient balance.
- 409 (via `IllegalStateException`): user already has an open bet on this match.

### POST /api/bets/correct-score
Place a single bet on an exact final scoreline. Request body:
```json
{ "matchId": 1, "homeGoals": 1, "awayGoals": 0, "amount": 20.00 }
```
`homeGoals`/`awayGoals` must each be between 0 and 6. Odds are computed via
`computeCorrectScoreOdds` (Poisson goal model — see [docs/architecture.md](architecture.md) →
"Correct Score Odds") and snapshotted at placement.

Response (201) — `BetResponse` with `betType: "SINGLE"`, `market: "CORRECT_SCORE"`,
`prediction: null`, `predictedHomeGoals: 1`, `predictedAwayGoals: 0`,
`displayLabel: "Correct Score 1-0"`.

Errors:
- 400: `homeGoals`/`awayGoals` out of range (0-6), amount <= 0, insufficient balance.
- 409 (via `IllegalStateException`): match betting not open, or user already has an open bet
  on this match (the same one-open-bet-per-match rule as `POST /api/bets`, regardless of market).

### POST /api/bets/handicap
Place a single European Handicap -1 (home) bet. Request body:
```json
{ "matchId": 1, "selection": "HOME_MINUS_ONE", "amount": 100.00 }
```
`selection` must be one of `HOME_MINUS_ONE`, `HANDICAP_DRAW`, `AWAY_PLUS_ONE`.
Odds are computed via `computeHandicapOdds` (Poisson Poisson bucket model — see
[docs/architecture.md](architecture.md) → "Handicap Odds") and snapshotted at placement.

Settlement rules (European Handicap, no pushes):
- `HOME_MINUS_ONE`: WON when `homeGoals - 1 > awayGoals` (home wins by 2+)
- `HANDICAP_DRAW`: WON when `homeGoals - 1 == awayGoals` (home wins by exactly 1)
- `AWAY_PLUS_ONE`: WON when `homeGoals - 1 < awayGoals` (draw, away win, or home fails to cover)

Response (201) — `BetResponse` with `betType: "SINGLE"`, `market: "HANDICAP"`,
`prediction: null`, `predictedHomeGoals: null`, `predictedAwayGoals: null`,
`handicapSelection: "HOME_MINUS_ONE"`, `displayLabel: "Home -1"`.

Errors:
- 400: amount <= 0, insufficient balance.
- 404: match not found.
- 409 (via `IllegalStateException`): match betting not open, or user already has an open bet
  on this match (same one-open-bet-per-match rule as other markets).

### POST /api/bets/combo
Request body — at least 2 selections, no duplicate match, one stake for the whole ticket:
```json
{
  "amount": 100.00,
  "selections": [
    { "matchId": 1, "prediction": "HOME_WIN" },
    { "matchId": 2, "prediction": "DRAW" }
  ]
}
```
Response (201) — `BetResponse` with `betType: "COMBO"`, `matchId`/`homeTeamName`/`prediction`/`odds`
all `null`, `totalOdds` = product of each selection's odds snapshot, `possibleWin = amount * totalOdds`,
and `selections` listing each `BetSelectionResponse` (`matchId`, team names, `prediction`, `odds` snapshot).

Errors:
- 400: fewer than 2 selections, duplicate match within the combo, amount <= 0, insufficient balance.
- 409 (via `IllegalStateException`): a selected match's betting is not open, or the user already
  has another open bet/selection on one of the selected matches.

### PUT /api/bets/{betId}
Edit an OPEN single bet — only while the match is still `BETTING_OPEN`:
```json
{ "amount": 150.00, "prediction": "AWAY_WIN" }
```
Behavior: if `amount` increases, the difference is deducted from the balance (rejected with 400
if insufficient); if it decreases, the difference is refunded. If `prediction` changes, odds are
re-snapshotted at the match's *current* odds and `possibleWin` is recalculated. Returns the
updated `BetResponse` (200).

Errors: 404 bet not found / not owned, 400 not a `MATCH_RESULT` bet or insufficient balance,
409 bet not OPEN or match betting no longer open.

### PUT /api/bets/{betId}/correct-score
Edit an OPEN single `CORRECT_SCORE` bet — only while the match is still `BETTING_OPEN`:
```json
{ "amount": 30.00, "homeGoals": 2, "awayGoals": 1 }
```
Behavior: amount changes apply the same balance-delta logic as `PUT /api/bets/{betId}`
(increase deducts the difference, rejected with 400 if insufficient; decrease refunds it).
If `homeGoals`/`awayGoals` differ from the bet's current prediction, odds are re-snapshotted
via `computeCorrectScoreOdds` at the new score's current price and `possibleWin` is
recalculated. Returns the updated `BetResponse` (200).

Errors: 404 bet not found / not owned, 400 not a `CORRECT_SCORE` bet / `homeGoals`/`awayGoals`
out of range (0-6) / insufficient balance, 409 bet not OPEN or match betting no longer open.

### PUT /api/bets/{betId}/handicap
Edit an OPEN single `HANDICAP` bet — only while the match is still `BETTING_OPEN`:
```json
{ "amount": 120.00, "selection": "AWAY_PLUS_ONE" }
```
Behavior: amount changes apply the same balance-delta logic as other edit endpoints.
If `selection` differs from the bet's current selection, odds are re-snapshotted via
`computeHandicapOdds` at the new selection's current price and `possibleWin` is recalculated.
If `selection` is unchanged, the stored odds are kept. Returns the updated `BetResponse` (200).

Errors: 404 bet not found / not owned, 400 not a `HANDICAP` bet / insufficient balance,
409 bet not OPEN or match betting no longer open.

### PUT /api/bets/{betId}/combo
Edit an OPEN combo bet — amount and/or the full selections list (replaces all selections):
```json
{
  "amount": 150.00,
  "selections": [
    { "matchId": 1, "prediction": "HOME_WIN" },
    { "matchId": 2, "prediction": "AWAY_WIN" }
  ]
}
```
Behavior: requires every *currently held* selection's match to still be `BETTING_OPEN` before
allowing any change; the new selections list is validated exactly like placement (2+ selections,
no duplicate match, each match `BETTING_OPEN`), odds are re-snapshotted, `totalOdds`/`possibleWin`
recalculated, and the amount delta is applied to the balance the same way as single-bet edits.
Returns the updated `BetResponse` (200).

Errors: 404 bet not found / not owned, 400 not a combo bet / fewer than 2 selections / duplicate
match / insufficient balance, 409 bet not OPEN or any involved match no longer `BETTING_OPEN`.

### POST /api/bets/{betId}/cancel
Cancels an OPEN bet (single or combo): status → `CANCELLED`, the full stake (`amount`) is
refunded to the user's balance, `settledAt` (used as the cancellation timestamp) is set, and the
bet is permanently excluded from settlement (idempotent — cannot be cancelled, edited, or settled
again). Allowed only while every involved match is still `BETTING_OPEN`. Returns the updated
`BetResponse` (200).

Errors: 404 bet not found / not owned, 409 bet not OPEN or an involved match is no longer open
for betting.

### GET /api/bets/my, /api/bets/my/open, /api/bets/my/history
Response (200): array of `BetResponse` (single and combo bets together, newest first), each with
`betType`, `status` (OPEN / WON / LOST / CANCELLED), `amount`, `odds`/`totalOdds`, `possibleWin`,
`profit`, and `selections`.

---

## Admin / Simulation

All `/api/admin/**` endpoints require role `ADMIN` (`ROLE_ADMIN`). Regular `USER` accounts
receive `403 Forbidden`.

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /api/admin/seed | Admin | Seed teams — prefers importing the real Israeli Premier League 2025/2026 data file when present, falls back to a random 8-team demo seed otherwise. |
| POST | /api/admin/generate-schedule | Admin | Generate the round-robin schedule (every team plays every other team home and away). |
| POST | /api/admin/rounds/{roundNumber}/open-betting | Admin | Compute odds and open betting for a round. |
| POST | /api/admin/matches/{matchId}/simulate | Admin | Simulate a single match and settle its bets. |
| POST | /api/admin/rounds/{roundNumber}/simulate | Admin | Simulate all unfinished matches in a round and settle bets. |
| GET | /api/admin/data/validate | Admin | Validate the bundled real-data JSON file without importing it. |
| POST | /api/admin/data/import | Admin | Validate and import/update teams and players from the bundled real-data JSON file. |

### GET /api/admin/data/validate
Validates `ligat-haal-2025-2026.json` (read from the backend's classpath) against the
documented schema and business rules, without writing anything to the database.

Response `200 OK`:
```json
{
  "valid": true,
  "errors": [],
  "warnings": [
    "Team 'Hapoel Beer Sheva': squad is missing (players[] is empty)."
  ]
}
```
- `valid` is `true` when there are no errors (warnings never block validity).
- `errors`: hard problems that would block import (e.g. wrong team count, duplicate
  names, out-of-range skill/morale, invalid enum values, starter+substitute conflicts).
- `warnings`: non-blocking issues (e.g. missing squads, wrong starter/substitute counts,
  duplicate lineup orders) — these are reported but never fail validation or import.

### POST /api/admin/data/import
Validates the same file, then — if there are no errors — imports it as an **upsert**:
existing teams are matched by `name` and updated (skill level, morale, formation,
display name); existing players are matched by `(team, fullName)` and updated. Nothing
is deleted, and nothing is invented for teams whose `players[]` is still empty. Entries
whose `dataSource` starts with `EXAMPLE` (template/placeholder data) are always skipped.

Response `200 OK`:
```json
{
  "teamsProcessed": 14,
  "playersProcessed": 0,
  "warnings": [
    "Team 'Hapoel Beer Sheva': squad is missing (players[] is empty)."
  ],
  "errors": []
}
```
If validation finds errors, the import is skipped and the response reports
`teamsProcessed: 0`, `playersProcessed: 0` with the `errors` populated.

### Round order enforcement (409 Conflict)
- A round's betting cannot be opened, and a round (or any of its matches) cannot be simulated,
  unless rounds `1..N-1` are all `FINISHED`. Round 1 is always allowed.
- A round must be `OPEN_FOR_BETS` before it (or its matches) can be simulated.
- An already-`FINISHED` match can never be simulated again.

Violations return `409 Conflict`:
```json
{ "error": "Conflict", "message": "Previous rounds must be finished before starting this round.", "status": 409 }
```

---

## Dashboard

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/dashboard/summary | Any authenticated user | Returns user's betting summary, season/match state, league highlights, and quick-action flags. |

### GET /api/dashboard/summary
Returns a flat `DashboardSummaryResponse` with:
- **User/betting**: `balance`, `openBetsCount`, `wonBetsCount`, `lostBetsCount`, `cancelledBetsCount`, `settledBetsCount`, `totalStakedOpen`, `totalProfitSettled`, `potentialWinningsOpen`, `recentBets` (up to 5 most recent)
- **Season/match**: `totalRounds`, `finishedRoundsCount`, `currentRoundNumber`, `nextRoundNumberToPlay`, `nextOpenRoundNumber`, `totalMatches`, `finishedMatchesCount`, `seasonBetsOpen`, `seasonBetsStatusReason`
- **League highlights** (only set when stats > 0): `leaderTeamName`, `leaderTeamId`, `leaderPoints`, `topScorerName`/`Team`/`Goals`, `topAssisterName`/`Team`/`Assists`, `redCardLeaderName`/`Team`/`Cards`
- **Quick actions**: `canPlaceSeasonBets`, `canPlaceMatchBets`, `hasOpenBets`, `hasFinishedMatches`, `adminCanGenerateSchedule`, `adminCanOpenNextRound`, `adminCanSimulateNextRound` (admin flags only set to `true` when the caller has ADMIN role)

---

## Admin Overview

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/admin/overview | ROLE_ADMIN | Returns a full system overview for the admin panel. Regular users get 403 Forbidden. |

### GET /api/admin/overview
Returns `AdminOverviewResponse` with:
- **Data state**: `teamsCount`, `playersCount`, `completeSquadsCount` (≥18 players), `missingSquadsCount` (0 players), `scheduleGenerated`, `totalRounds`, `totalMatches`
- **Round state**: `currentRound`, `nextRoundToOpen`, `nextRoundToSimulate`, `roundsByStatus` (Map<String, Long>)
- **Betting state**: `openBetsCount`, `settledBetsCount`, `cancelledBetsCount`, `seasonBetsOpen`, `seasonBetsStatusReason`
- **Recommended actions**: `List<String>` describing the next recommended admin action — e.g. "Import league data", "Generate schedule", "Open betting for Round 1", "Simulate Round 1", "Season complete"

---

## Error Response Format

All errors follow:
```json
{ "error": "string", "message": "string", "status": 400 }
```
