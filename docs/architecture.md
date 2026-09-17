# Architecture — Football League Simulator

---

## Layer Responsibilities

### Frontend (React + TypeScript + Vite)
- Renders UI only: pages, forms, tables, navigation.
- Calls backend REST API for all data.
- Stores JWT token in memory or localStorage; attaches it to every protected request.
- **Never** calculates odds, match results, league standings, or bet settlement.
- **Never** modifies balance locally — always re-fetches from backend.

### Backend (Java 17 + Spring Boot)
- Owns all business logic.
- Validates all inputs.
- Calculates match simulation, odds, and bet settlement.
- Manages user balance with transactional guarantees.
- Issues and validates JWT tokens.
- Returns DTOs — never raw JPA entities.

### Database (H2 / JPA)
- Stores persistent state: users, teams, rounds, matches, bets.
- No stored procedures or business logic in the schema.
- Hibernate manages DDL in dev mode; schema can be promoted to a migration tool for production.

---

## Entity Relationships

```
User
 └── Bet (many bets per user)

Team
 ├── homeMatches (Match.homeTeam)
 └── awayMatches (Match.awayTeam)

Round
 └── Match (many matches per round)

Team
 └── Player (many players per team — squad)

Match
 ├── homeTeam → Team
 ├── awayTeam → Team
 ├── weatherCondition / weatherImpact (visible before simulation)
 └── Bet (many bets per match)

Player
 ├── team → Team
 ├── position: GK | CB | LB | RB | DM | CM | AM | LW | RW | ST
 ├── status: FIT | INJURED | SUSPENDED
 └── starter / substitute / lineupOrder (lineup placement)

Bet
 ├── user → User
 ├── betType: SINGLE | COMBO
 ├── market: MATCH_RESULT | CORRECT_SCORE | HANDICAP
 ├── match → Match            (SINGLE only; null for COMBO)
 ├── prediction: HOME_WIN | DRAW | AWAY_WIN   (SINGLE MATCH_RESULT only; null for COMBO/CORRECT_SCORE/HANDICAP)
 ├── predictedHomeGoals / predictedAwayGoals (SINGLE CORRECT_SCORE only; null otherwise)
 ├── handicapSelection: HOME_MINUS_ONE | HANDICAP_DRAW | AWAY_PLUS_ONE (SINGLE HANDICAP only; null otherwise)
 ├── odds (snapshot, SINGLE only) / totalOdds (snapshot product, COMBO only)
 ├── amount (BigDecimal — single stake for the whole bet, single or combo)
 ├── possibleWin (amount * odds, or amount * totalOdds)
 ├── status: OPEN | WON | LOST | CANCELLED
 ├── profit, createdAt, settledAt/cancelledAt
 └── selections → BetSelection[]   (COMBO only; empty for SINGLE)

BetSelection
 ├── bet → Bet
 ├── match → Match
 ├── prediction: HOME_WIN | DRAW | AWAY_WIN
 └── oddsSnapshot (BigDecimal — snapshot at placement/edit time, never recalculated retroactively)
```

---

## Full User Flow

1. User visits the app → sees login / register page (no JWT required).
2. User registers → backend creates account with balance 1000, returns JWT.
3. User logs in → backend validates credentials, returns JWT.
4. JWT is stored in the client and sent with every subsequent request.
5. User browses teams → GET /api/teams (authenticated).
6. User browses matches by round → GET /api/rounds, GET /api/matches?roundId=X.
7. Each match card shows current odds (fetched from backend).
8. User places a bet → POST /api/bets (amount, matchId, outcome).
   - Backend validates: match is SCHEDULED, user has balance, no duplicate bet, amount > 0.
   - Backend deducts amount from balance, saves bet with odds snapshot.
9. User views balance → GET /api/users/me (balance field).
10. User views bet history → GET /api/bets/my.
11. User views league table → GET /api/league-table.

---

## Roles & Authorization

- Two roles: `USER` (default) and `ADMIN`.
- The admin account is identified by a configurable email (`app.admin.email` in
  `application.properties`). During registration, if the email matches (case-insensitive),
  the account is assigned `ADMIN`; otherwise `USER`. On login, an existing account whose email
  matches the configured admin email is promoted to `ADMIN` if it isn't already, preventing lockout
  if the configured email changes after the account was created.
- `CustomUserDetailsService` grants `ROLE_USER` or `ROLE_ADMIN` based on the persisted role, and
  `SecurityConfig` restricts `/api/admin/**` to `ROLE_ADMIN` (regular users get `403 Forbidden`).
- `UserResponse` / `AuthResponse` include the role so the frontend can show/hide the Admin nav link
  and page — but the backend remains the sole source of truth for authorization.

---

## Admin / Simulation Flow

1. Admin logs in with a user that has ROLE_ADMIN.
2. Admin calls POST /api/admin/simulate/match/{id} to simulate a single match.
   - Backend computes homePower and awayPower.
   - Backend derives outcome probabilities and picks a winner.
   - Backend generates a realistic score.
   - Backend updates Match (score, status = FINISHED).
   - Backend settles all open bets on that match.
   - Backend updates user balances for winners.
   - Backend updates team stats (wins, losses, draws, goals for/against, points).
   - Backend updates team form: winner gets skillLevel +1 / morale +1, loser gets
     skillLevel -1 / morale -1, draws leave both unchanged. skillLevel is clamped to
     [40, 100] and morale to [0, 10].
3. Admin can simulate a full round: POST /api/admin/simulate/round/{id}
   - Simulates all SCHEDULED matches in that round sequentially.
4. Admin can simulate the full season: POST /api/admin/simulate/all

### Round Order Enforcement

A league season must progress in order, so the backend enforces:

- Round N's betting cannot be opened, and round N (or any of its matches) cannot be simulated,
  unless rounds `1..N-1` are all `FINISHED`. Round 1 has no previous rounds, so it is always allowed.
- A round must be `OPEN_FOR_BETS` before it (or its matches) can be simulated — a round that is
  still `NOT_STARTED` cannot be simulated.
- A match that is already `FINISHED` can never be simulated again.
- Any violation throws `IllegalStateException`, mapped by `GlobalExceptionHandler` to
  `409 Conflict` with a message such as "Previous rounds must be finished before starting this round."
- The frontend `AdminPage` mirrors this by disabling the relevant buttons and showing an inline
  message, but the backend check is authoritative.

---

## Squads, Lineups & Player Status

- `Player` belongs to a `Team` (squad) and carries `position`, `rating`, `status`
  (`FIT` | `INJURED` | `SUSPENDED`), injury details expressed in **match counts**
  (`injuryDescription`, `injuryMatchesRemaining`, `injuryMatchesTotal` — the legacy
  `injuredUntilRound` field is kept only for backward compatibility and is not used by the
  injury system), and lineup placement (`starter`, `substitute`, `lineupOrder`).
- A player is never both a starter and a substitute.
- A player is **unavailable** (excluded from `starters`/`substitutes` and surfaced separately
  as `unavailablePlayers`) when `status = SUSPENDED`, or `status = INJURED` with
  `injuryMatchesRemaining > 0`. `unavailablePlayers` entries include `fullName`, `position`,
  `status`, `injuryDescription`, `injuryMatchesRemaining`, and `injuryMatchesTotal` so the
  frontend can show e.g. "Injured — out for 3 matches" / "Injured — 3 matches remaining".
- `Team.defaultFormation` (e.g. `"4-3-3"`) is shown alongside the squad/lineup.

### Injuries are simulation-generated only

- Injuries are **never** copied from real-world sources (365Scores, Transfermarkt, club sites,
  etc.). `LeagueDataImportService` always imports real players as `FIT` — any injury-related
  fields present in the source JSON are ignored — so real-world injury status is never treated
  as an actual in-app injury.
- Injuries are produced solely by `SimulationService` while simulating matches:
  - After each match, for each of the two teams there's roughly a **10% chance** of generating
    **0 or 1** new injury, drawn from the team's currently-`FIT` players (preferring those in
    the lineup, falling back to any `FIT` squad player if lineup data is incomplete).
  - A new injury gets a random description (`Muscle injury`, `Ankle injury`, `Knee injury`,
    `Hamstring injury`, `Knock`) and a random duration of **1–5 matches**
    (`injuryMatchesRemaining` = `injuryMatchesTotal`).
  - Before rolling for a new injury, existing injuries for that team are decremented by one
    (since the team just completed a match); a freshly-created injury is **not** decremented in
    the same match. When `injuryMatchesRemaining` reaches zero the player returns to `FIT`
    (`injuryDescription` cleared, counts reset to zero) and becomes selectable for lineups again.
  - `Team.injuries` is kept synchronized with the count of players currently `INJURED` with
    `injuryMatchesRemaining > 0` (a team-level summary used by `OddsService`'s power
    calculation; it never conflicts with individual player status).
- `PlayerService` builds `TeamSquadResponse`, `TeamLineupResponse`, and `MatchLineupsResponse`
  (combining both teams' lineups with the match's weather) — exposed via
  `GET /api/teams/{teamId}/squad`, `GET /api/teams/{teamId}/lineup`, and
  `GET /api/matches/{matchId}/lineups`.
- Squads/lineups remain empty until real Israeli Premier League 2025/2026 squad data is
  added to the real-data file and imported (see "Real League Data Import" below).

### Effective lineups: dynamic auto substitutions for unavailable players

- The persisted `starter` / `substitute` / `lineupOrder` flags on `Player` are the **baseline
  squad roles** — imported once from real data and never rewritten by the lineup logic. Every
  lineup request instead computes an **effective lineup** on the fly via the new
  `LineupService.generateEffectiveLineup(List<Player>)`, so unavailable players never appear in
  `starters`/`substitutes` and a fielded XI stays logical even mid-season.
- Algorithm (pure, stateless, easily unit-testable):
  1. Partition the squad into `unavailable` (suspended w/ matches remaining, or injured w/
     matches remaining) and the rest.
  2. Keep every available original starter; collect the *missing* starter slots left by
     unavailable original starters.
  3. For each missing slot, `findBestReplacement(missingPosition, candidates)` scores every
     not-yet-used available non-starter by: same position (highest), then a documented
     compatible-position fallback chain (see table below), then higher rating, with "was an
     original substitute" as a tiebreak over additional squad players; a goalkeeper slot is
     only ever filled by a goalkeeper (and vice versa). The chosen player is recorded as a
     `Replacement(originalPlayerName, replacementPlayerName, reason, position)`.
  4. If still short of 11 (e.g. not enough suitable substitutes), tops up from any remaining
     available player by rating. Starters are capped at 11; if fewer than 11 players are
     available in total, returns as many as possible and appends a `lineupWarnings` entry.
  5. Builds the bench from the remaining available players — original substitutes first, then
     additional squad players, sorted by rating — aiming for at least 7.
- **Compatible-position fallback chain** (in priority order, excluding the position itself):
  `GK`→ none; `CB`→ `RB, LB, DM`; `RB`→ `CB, LB, DM`; `LB`→ `CB, RB, DM`; `DM`→ `CM, CB`;
  `CM`→ `DM, AM`; `AM`→ `CM, LW, RW`; `LW`→ `RW, AM, ST`; `RW`→ `LW, AM, ST`; `ST`→ `LW, RW, AM`.
- `TeamLineupResponse` (and `MatchLineupsResponse`, per side) gains three **additive, optional**
  fields so the response shape stays backward compatible: `effectiveLineupGenerated` (boolean —
  true once at least one starter had to be swapped), `lineupWarnings` (`List<String>`), and
  `replacedPlayers` (`List<ReplacedPlayerInfo>` — `originalPlayerName`, `replacementPlayerName`,
  `reason` [`"Injured"` / `"Suspended"` / `"Injured + Suspended"`], `position`).
- A new injury or red-card suspension only ever changes the *next* effective-lineup computation —
  it never mutates or removes `MatchEvent` records of matches that already finished.
- `LineupsModal` renders a small **"Lineup adjustments"** section per team (only when
  `replacedPlayers`/`lineupWarnings` are non-empty), e.g. *"Omer Nir'on unavailable (Injured);
  replaced by Tomer Tzarfati"*. No other UI/styling changed — design polish is deferred.

---

## Match Events & Player Stats (Goals, Assists, Red Cards)

- `Player` carries simulation-generated career counters: `goals`, `assists`, `redCards`
  (all `int`, default 0), and `suspensionMatchesRemaining` (`int`, default 0, counted down
  the same way as `injuryMatchesRemaining`). `LeagueDataImportService` always imports real
  players with all four reset to zero — exactly like injuries, these are never copied from
  real-world sources.
- `MatchEvent` (immutable, no `updatedAt`) records a single goal or red card: `match`, `team`,
  `player` (nullable — null only if no eligible player exists), `assistPlayer` (nullable,
  GOAL events only), `eventType` (`MatchEventType`: `GOAL` | `RED_CARD`), `minute` (1-90),
  `description`, and `createdAt`. Exposed read-only via `MatchEventResponse` and
  `GET /api/matches/{matchId}/events` (`MatchEventRepository.findByMatch_IdOrderByMinuteAscIdAsc`).
- `GET /api/players/{playerId}` and `GET /api/teams/{teamId}/players/stats` (new thin
  `PlayerController`) expose individual/team player stats (goals/assists/red cards/suspension)
  via the existing `PlayerResponse` DTO.

### Simulation order in `SimulationService.finishMatch`

Event generation must both *influence* and *follow* the match result, and a fresh red-card
suspension must not be decremented in the same match it's issued. This is achieved by a
specific ordering:

1. Compute each team's `availablePlayers` (FIT, not injured, not suspended) from the lineup.
2. **Decide** (without mutating anything yet) whether each team suffers a red card —
   `maybeSelectRedCardPlayer` rolls **8% per team per match** (capped at one per team),
   preferring starters from the available pool.
3. Compute base win/draw/loss probabilities, then `applyRedCardAdjustment` reduces a
   carded team's chances by **15–25%** (`RED_CARD_MIN_REDUCTION` + random×`RED_CARD_REDUCTION_RANGE`),
   transferring ~70% of that to the opponent's win probability and ~30% to the draw
   probability (clamped ≥ 0.01 and renormalized) — so the card affects *this* match's score.
4. Generate the score from the adjusted probabilities and mark the match `FINISHED`.
5. Apply team-level stats and `progressTeamInjuries` (which now also decrements
   **pre-existing** `suspensionMatchesRemaining`, recomputing status via
   `recomputeStatusAfterProgression` — suspension takes priority over injury, then injury,
   then `FIT`). Because the new red card hasn't been applied to the DB yet, this step never
   touches it.
6. **Apply** the red card decided in step 2 (`applyRedCard`): increments `redCards`, sets
   `status = SUSPENDED` and `suspensionMatchesRemaining = 1`, removes the player from the
   available pool, and saves a `RED_CARD` `MatchEvent` (e.g. *"Tyrese Asante received a red
   card"*) at a random minute.
7. **Generate goal events** (`generateGoalEvents`) for each goal in the final score:
   - The **scorer** is chosen via weighted random selection (`selectWeighted` + `scorerWeight`)
     from available players — starters preferred, weights favor attacking positions
     (ST > LW/RW > AM > CM > DM > defenders > GK) scaled by `rating/70` and a 1.5× starter
     multiplier — and falls back to all available players (including subs) if the starter
     pool is empty, or skips the event entirely if no players are available (e.g. a team
     with no imported squad). `scorer.goals` is incremented.
   - With a **75% chance** (`ASSIST_PROBABILITY`), an **assist** is attributed to a different
     team-mate via the same weighted-selection mechanism with assist-favoring weights
     (AM > LW/RW > CM/RB/LB > ST/DM > CB > GK); `assistPlayer.assists` is incremented.
   - A `GOAL` `MatchEvent` is saved with a description like *"Dor Peretz scored, assisted by
     Kervin Andrade"* or *"Dor Peretz scored"* (no assist).
8. Bets are settled last, exactly as before.

### Suspensions

- A red card immediately sets `status = SUSPENDED`, `suspensionMatchesRemaining = 1`.
- `progressTeamInjuries` decrements `suspensionMatchesRemaining` for `SUSPENDED` players the
  same way it decrements injuries — but only for suspensions that existed *before* the
  current match's events are generated (see ordering above), so a freshly-issued suspension
  survives to the player's actual next match. When it reaches zero, `recomputeStatusAfterProgression`
  returns the player to `FIT` (or keeps them `INJURED` if they also happen to be injured —
  defensive handling; the generation rules mean this combination cannot currently arise,
  since suspended/injured players are excluded from both the new-injury and red-card pools).
- `PlayerService.isUnavailable` now also excludes `suspensionMatchesRemaining > 0` players
  from `starters`/`substitutes`, surfacing them in `unavailablePlayers` with a
  "Suspended — N matches remaining" label.

### Frontend

- `MatchesPage` shows a **Match Events** section under each `FINISHED` match card listing
  goals (`12' Dor Peretz (assist: Kervin Andrade)`) and red cards (`53' Tyrese Asante`),
  fetched via `getMatchEvents`. The section is omitted entirely when a match has no events
  (e.g. a 0-0 draw, or a match between two teams with no imported squads).
- `LineupsModal` shows each player's `goals`/`assists`/`redCards` (only when non-zero) and
  extends the unavailability label to "Suspended — N matches remaining".

### Match details modal

- Every match card on `MatchesPage` gained a **View Details** button opening a new
  `MatchDetailsModal` that consolidates everything about a match in one place: title
  (home vs away), score/status badge, weather (condition + impact), odds (home/draw/away,
  when available), a chronological **events timeline** built from `GET /api/matches/{matchId}/events`
  (already returned sorted by `(minute asc, id asc)` — e.g. `12' GOAL — Dor Peretz, assist:
  Kervin Andrade`, `53' RED CARD — Tyrese Asante`), and a short **unavailable players summary**
  per side (`"<team> unavailable: N"`) derived from `GET /api/matches/{matchId}/lineups`'
  existing `home/awayUnavailablePlayers` arrays. A "View Lineups" button inside the modal opens
  the existing `LineupsModal`.
- Empty states for the timeline: **"No events yet"** while the match isn't `FINISHED`,
  **"No recorded events"** for a finished match with zero events (0-0 draw, or a match between
  squad-less teams).
- **No backend changes were needed.** `MatchResponse` already carries score/status/`bettingOpen`/
  odds/weather, and the existing `GET /api/matches/{matchId}/events` /
  `GET /api/matches/{matchId}/lineups` endpoints already return sorted events and per-side
  unavailable-player lists — `MatchDetailsModal` simply fetches both in parallel and renders
  them alongside the `MatchResponse` it already has, per the project's "reuse existing
  endpoints, don't create duplicates" rule. This keeps controllers thin (unchanged) and avoids
  exposing any new DTOs/entities.
- Minimal additive CSS only (`.match-events-group-list`, reusing `.modal`, `.modal-wide`,
  `.lineup-group`, `.lineup-weather`, `.match-odds`, `.status-badge`) — no visual redesign;
  final UI/UX polish for the whole app remains a separate later phase.

### Stats pages

- New `/stats` route + Navbar **Stats** link. `StatsPage` has three tabs — **Top Scorers**,
  **Top Assists**, **Red Cards** — each rendering a table (Rank, Player, Team, Position,
  stat value, Rating) fetched from `GET /api/players/leaders?stat=...&limit=20`
  (`PlayerStatsResponse[]`, includes `teamId`/`teamName`).
- `TeamsPage` gained a **View Squad** button per team opening `TeamSquadModal`
  (styled like `LineupsModal`), which lists the *full* squad (starters, substitutes, and
  additional squad players — not just the effective lineup) via the existing
  `GET /api/teams/{teamId}/players/stats`, with each player's role, rating,
  goals/assists/redCards, and status.
- Clicking a player row in either the leaders tables or the squad modal opens
  `PlayerDetailsModal` — a small read-only card (team, position, jersey #, rating,
  goals, assists, red cards, status/availability).
- All of the above is purely a thin presentation layer over existing/new read-only
  endpoints — no odds/results/balance/standings calculations happen on the frontend,
  consistent with the project's "frontend handles UI only" rule. Minimal functional
  styling only; final visual design is a separate later phase.

---

## Real League Data Import

Real Israeli Premier League 2025/2026 data flows through a structured JSON file rather
than hand-written code, so it can be filled in incrementally from verified sources
without further schema or backend changes:

1. **File**: `backend/src/main/resources/data/ligat-haal-2025-2026.json` holds the
   season's teams (and, once collected, squads/lineups). Its format is documented in
   the sibling `ligat-haal-2025-2026.schema.md`, with a structural example in
   `ligat-haal-2025-2026.template.json` (whose example team/players are marked with a
   `dataSource` of `EXAMPLE_TEMPLATE` and are never imported).
2. **DTOs**: `LeagueSeedData` → `TeamSeedData` → `PlayerSeedData` deserialize the file
   via Jackson (`ObjectMapper` + `ClassPathResource`), independent of the JPA entities.
3. **`LeagueDataImportService`**:
   - `validate()` checks the file against the documented rules (team count, unique
     names, skill/morale ranges, formation, enum values, starter/substitute conflicts,
     injured/suspended exclusions, squad composition) and returns errors (blocking) and
     warnings (non-blocking, e.g. "squad is missing").
   - `importData()` re-validates, then — if there are no errors — **upserts**: teams
     are matched by `name`, players by `(team, fullName)`. Nothing is ever deleted and
     nothing is invented; teams whose `players[]` stays empty are simply reported as
     warnings so the rest of the league can be imported while squads are collected.
4. **Endpoints** (`AdminController`, `ROLE_ADMIN` only, thin — delegate to the service):
   - `GET /api/admin/data/validate` — validation-only, returns errors/warnings.
   - `POST /api/admin/data/import` — validates then imports, returns a summary
     (`teamsProcessed`, `playersProcessed`, `warnings`, `errors`).
5. **`POST /api/admin/seed`** prefers this real-data import when the file is present and
   valid (so the league reflects real 2025/2026 teams by default), falling back to the
   original random 8-team demo seed otherwise — existing behavior for environments
   without the file is preserved.
6. **Frontend**: `AdminPage` exposes **Validate League Data** and **Import League Data**
   buttons (ADMIN only), rendering the validation result / import summary including
   warnings and errors.

---

## Weather

- Each `Match` carries a visible `weatherCondition` (`CLEAR`, `RAIN`, `WIND`, `HOT`, `COLD`,
  `STORM`) and a small signed `weatherImpact`, assigned for every match when the schedule is
  generated (`AdminService.generateSchedule`) — so weather is visible to users on the Matches
  page before betting opens or the match is simulated, not just at simulation time.
- `weatherImpact` is derived from the condition (e.g. `CLEAR` → 0, `RAIN` → -1/-2,
  `STORM` → -2/-3, `WIND`/`COLD` → small ±1 jitter) and stays small relative to skill-driven
  power so it nudges — but never overrides — the outcome.
- `OddsService` folds `weatherImpact` into both teams' power equally (same conditions for both
  sides), then applies a simple, condition-specific adjustment on top:
  - `CLEAR` — neutral, no extra adjustment.
  - `RAIN` — negative impact reduces scoring power (fewer goals); draw probability gets a
    small bonus (higher draw chance).
  - `WIND` — adds extra randomness (probability jitter) without shifting power.
  - `HOT` — negative impact represents slightly reduced tempo.
  - `COLD` — small random impact plus a touch of extra randomness.
  - `STORM` — negative impact (fewer goals) combined with both a draw-probability bonus and
    extra randomness.
- The resulting effects are intentionally small and bounded so weather influences but never
  overpowers the skill/morale/injury/home-advantage power calculation below.

---

## Simulation Factors & Odds Formula

`OddsService` generates 1X2 odds from an **internal probability model** — they are not real
bookmaker odds. Each team's "power" is computed first, then the power difference is converted
into realistic win/draw/win probabilities, and finally a bookmaker margin is applied:

```
teamPower = skillLevel + morale*1.5 + homeAdvantage(home only)
            - unavailablePenalty + lineupStrengthAdjustment + weatherImpact

diff       = homePower - awayPower
drawProb   = clamp(0.28 - |diff| * 0.002, 0.16, 0.30)      // + small bonus in RAIN/STORM
homeShare  = logistic(diff / 16)
homeWinProb = (1 - drawProb) * homeShare
awayWinProb = (1 - drawProb) * (1 - homeShare)
// clamp favorite ≤ 0.76, underdog ≥ 0.04, draw ∈ [0.16, 0.30], then normalize so the three sum to 1.0

odds = round(1 / (probability * 1.07), 2)   // 1.07 = ~7% bookmaker margin/overround
odds bounded to [1.20, 15.00]
```

- **skillLevel / morale** — persistent team attributes updated after each match (skillLevel is
  the dominant factor; morale contributes a smaller weight).
- **homeAdvantage** — fixed `+4` power bonus applied only to the home team.
- **unavailablePenalty** — `OddsService` queries `PlayerRepository` for each team's current
  squad/lineup: missing **starters** (injured with `injuryMatchesRemaining > 0` or suspended)
  cost more power than missing substitutes; teams without imported squad data fall back to the
  legacy `team.injuries` counter. The penalty is capped so a team's power is never destroyed.
- **lineupStrengthAdjustment** — a small, bounded adjustment derived from how the available
  starters' average `rating` compares to a typical squad rating (skipped when no squad data
  exists, so teams without real squads are unaffected).
- **weatherImpact** — the match's persisted, visible weather impact (see Weather above),
  applied identically to both teams since they play under the same conditions; on top of the
  power adjustment, `OddsService` adds a small RAIN/STORM draw-probability bump and bounded
  WIND/STORM/COLD randomness, exactly as described above.
- **logistic curve (`diff / 16`)** — converts the power gap into a home-win *share* of the
  non-draw probability; this produces a realistic spread (heavy favorites price near the 0.76
  cap, evenly matched teams price close to 50/50) instead of the old, nearly-flat linear
  power-share model.
- **bookmaker margin (1.07)** — converts "true" probabilities into odds that are slightly less
  generous than fair value (implied probabilities sum to ~1.07), matching how real bookmakers
  price markets; this is the only place a margin is applied — `SimulationService` uses the
  *unmargined* probabilities (via `computeProbabilities`) to decide match outcomes, so odds
  pricing and simulated results stay coherent with the same underlying strength model.

`SimulationService` uses `OddsService.computeProbabilities` (the pre-margin probabilities) to
generate the match score, so the odds shown to users and the simulated outcomes are always
driven by the same team-strength model.

### Correct Score Odds

Correct-score odds reuse the same team-power difference as 1X2, but feed it into a Poisson
goal model instead of a win/draw/win probability split:

```
diff = homePower - awayPower   // same calculateTeamPower as 1X2

expectedGoals[home] = clamp(1.35 * exp( diff / 28), 0.45, 3.2)
expectedGoals[away] = clamp(1.35 * exp(-diff / 28), 0.45, 3.2)

prob(h, a) = poissonPmf(h, expectedGoals[home]) * poissonPmf(a, expectedGoals[away])

odds(h, a) = round(1 / (prob(h, a) * 1.15), 2)
odds bounded to [4.00, 80.00]
```

- A stronger team's `expectedGoals` rises (and the weaker side's falls) by the same `diff`
  that drives 1X2 home/away win shares, so favorite-leaning scorelines (e.g. a strong home
  team winning 2-0) price lower than the mirrored underdog scoreline (0-2).
- The correct-score margin (1.15, ~15%) is larger than the 1X2 margin (1.07) because a
  correct-score market has many more possible outcomes.
- `GET /api/matches/{matchId}/correct-score-odds` (`MatchService.getCorrectScoreOdds` →
  `OddsService.getPopularCorrectScores`) returns all 25 combinations of 0-4 home goals ×
  0-4 away goals as `CorrectScoreOddsOption[]` (`homeGoals`, `awayGoals`, `odds`), used to
  populate the betting grid. `POST /api/bets/correct-score` accepts any scoreline with each
  side 0-6 (`computeCorrectScoreOdds`), not just the 25 shown in the grid.

### Handicap Odds

Handicap odds reuse the same Poisson `expectedGoals` as correct-score odds, but sum the probability mass into three outcome buckets based on the European Handicap -1 rule (home team concedes a 1-goal head-start):

```
// same expectedGoals[home/away] = clamp(1.35 * exp(±diff/28), 0.45, 3.2) as Correct Score

for h in 0..6, a in 0..6:
    p = poissonPmf(h, expectedGoals[home]) * poissonPmf(a, expectedGoals[away])
    diff = h - 1 - a
    if diff > 0  → HOME_MINUS_ONE bucket
    if diff == 0 → HANDICAP_DRAW bucket
    if diff < 0  → AWAY_PLUS_ONE bucket

// normalize the three bucket probabilities (defensive, since 0..6 covers >99% of mass)

odds = round(1 / (probability * 1.10), 2)   // 1.10 = ~10% bookmaker margin
odds bounded to [1.20, 25.00]
```

- The **same** `calculateTeamPower` → `expectedGoals` chain as correct-score pricing drives the handicap buckets, so team strength, home advantage, injuries, and weather are all reflected.
- The margin (1.10, ~10%) sits between the 1X2 margin (1.07) and the correct-score margin (1.15), fitting a 3-way market with many fewer outcomes than correct-score.
- European Handicap has **no pushes**: every bet settles either WON or LOST, regardless of the exact score.
- `GET /api/matches/{matchId}/handicap-odds` → `List<HandicapOddsOption>` (3 entries with `selection`, `label`, `odds`).

---

## Correct Score Bets

- New `BetMarket` enum (`MATCH_RESULT` | `CORRECT_SCORE`) on `Bet`, independent of `betType`
  (`SINGLE` | `COMBO`). Combo bets remain `MATCH_RESULT`-only — correct-score is a
  single-bet market for now.
- **Placement** — `POST /api/bets/correct-score` (`CorrectScoreBetRequest`: `matchId`,
  `homeGoals`/`awayGoals` each 0-6, `amount`): same guardrails as a single 1X2 bet (match
  must be `BETTING_OPEN`, user must not already have an open bet on the match, `0 < amount
  <= balance`); odds come from `computeCorrectScoreOdds` and are snapshotted at placement,
  the stake is deducted immediately, and the saved bet has `market = CORRECT_SCORE`,
  `prediction = null`, `predictedHomeGoals`/`predictedAwayGoals` set.
- **Settlement** — `settleBetsForMatch` branches on `market`: a `CORRECT_SCORE` bet is `WON`
  iff the match's final score exactly equals `predictedHomeGoals`/`predictedAwayGoals`,
  otherwise `LOST`; `MATCH_RESULT` settlement (comparing `prediction` to the 1X2 outcome) is
  unchanged. Winners receive `amount * odds` (the snapshotted odds), exactly like 1X2 bets.
- **Edit** — `PUT /api/bets/{betId}/correct-score` (`UpdateCorrectScoreBetRequest`: `amount`,
  `homeGoals`/`awayGoals`), only for an owned `OPEN` `CORRECT_SCORE` bet whose match is still
  `BETTING_OPEN`. Amount changes apply the same balance-delta logic as `editBet`. Changing the
  predicted scoreline re-snapshots odds via `computeCorrectScoreOdds` at the new score's
  current price and recomputes `possibleWin`. The two edit endpoints are now market-specific:
  `PUT /api/bets/{betId}` rejects `CORRECT_SCORE` bets and `PUT /api/bets/{betId}/correct-score`
  rejects `MATCH_RESULT` bets.
- **Cancel** — the existing `POST /api/bets/{betId}/cancel` works unchanged for
  `CORRECT_SCORE` bets (full stake refund, `status = CANCELLED`).
- `BetResponse` gains additive fields `market`, `predictedHomeGoals`, `predictedAwayGoals`,
  and `displayLabel` (`"Correct Score 1-0"` vs `"Match Result"`) — existing `MATCH_RESULT`
  bets/clients are unaffected.
- **Frontend**: `MatchesPage` match cards gain a **Correct Score** button opening
  `CorrectScoreBetModal`, which fetches `GET /api/matches/{matchId}/correct-score-odds` and
  renders a 5x5 grid (home goals 0-4 rows × away goals 0-4 columns) of clickable odds
  buttons; selecting one shows "Selected: H-A @ odds" and a live possible-win calculation.
  `EditBetModal` shows two 0-6 number inputs for the predicted score when editing a
  `CORRECT_SCORE` bet. `MyBetsPage` shows the market (`Correct Score` / `Match Result`) and
  the predicted scoreline per row.
- No bet types beyond `MATCH_RESULT`/`CORRECT_SCORE` were added; combo bets remain
  `MATCH_RESULT`-only.

---

## Handicap Bets

- New `BetMarket` value `HANDICAP` alongside `MATCH_RESULT` and `CORRECT_SCORE`. Combo bets remain `MATCH_RESULT`-only; handicap is a single-bet market only.
- **European Handicap -1 for the home team**: three outcomes — `HOME_MINUS_ONE` (home wins by 2+), `HANDICAP_DRAW` (home wins by exactly 1), `AWAY_PLUS_ONE` (draw or away win). No pushes — every bet settles WON or LOST.
- **Placement** — `POST /api/bets/handicap` (`HandicapBetRequest`: `matchId`, `selection`, `amount`): same guardrails as other single bets (match must be `BETTING_OPEN`, user must not already have an open bet on the match, `0 < amount <= balance`); odds come from `computeHandicapOdds(match, selection)` and are snapshotted at placement; stake deducted immediately; saved bet has `market = HANDICAP`, `handicapSelection` set, `prediction = null`, `predictedHomeGoals/predictedAwayGoals = null`.
- **Settlement** — `settleBetsForMatch` branches on `market`: a `HANDICAP` bet applies `diff = homeGoals - 1 - awayGoals`; `HOME_MINUS_ONE` wins if `diff > 0`, `HANDICAP_DRAW` wins if `diff == 0`, `AWAY_PLUS_ONE` wins if `diff < 0`. Winners receive `amount * odds` (snapshotted odds). Idempotent — only acts on OPEN bets.
- **Edit** — `PUT /api/bets/{betId}/handicap` (`UpdateHandicapBetRequest`: `amount`, `selection`): only for an owned `OPEN` `HANDICAP` bet whose match is still `BETTING_OPEN`. Amount changes apply the balance-delta logic (`applyAmountDelta`). Changing the selection re-snapshots odds via `computeHandicapOdds` at the new selection's current price and recomputes `possibleWin`.
- **Cancel** — the existing `POST /api/bets/{betId}/cancel` works unchanged for `HANDICAP` bets (full stake refund, `status = CANCELLED`).
- `BetResponse` gains `handicapSelection` field; `displayLabel` is set to `"Home -1"` / `"Handicap Draw"` / `"Away +1"` for handicap bets.
- **Frontend**: `MatchesPage` match cards gain a **Handicap** button (same `BETTING_OPEN` condition as existing buttons) opening `HandicapBetModal`, which fetches the 3 handicap odds options and renders them as selectable options with amount input and possible-win preview. `EditBetModal` shows a 3-option radio group for handicap bets. `MyBetsPage` shows market `"Handicap"` and the `displayLabel` as prediction.
- No top-scorer, champion-winner, or other markets were added. Final visual/UX design polish remains deferred.

---

## Dashboard & Admin Overview

### Dashboard Summary (`GET /api/dashboard/summary`)
- **`DashboardService.getSummary(String email)`** is a single `@Transactional(readOnly = true)` method that aggregates data from `BetRepository`, `RoundRepository`, `MatchRepository`, `TeamRepository`, and `PlayerRepository` plus `BetService.isSeasonBettingOpen()` into one flat `DashboardSummaryResponse`.
- **No new calculations** — all data is already persisted. The service only groups, counts, and selects; it does not compute odds, simulate, or write.
- **Admin flags** — `adminCanGenerateSchedule`/`adminCanOpenNextRound`/`adminCanSimulateNextRound` are only set to `true` when the caller has `Role.ADMIN`. Regular users always receive `false` for these flags.
- **League highlights** — only populated when the relevant stat > 0 (e.g. `topScorerGoals > 0`), so the frontend receives `null` fields pre-simulation and can safely hide the section.
- `DashboardController` at `/api/dashboard/**` requires no SecurityConfig change — `anyRequest().authenticated()` already covers it.

### Admin Overview (`GET /api/admin/overview`)
- **`AdminService.getAdminOverview()`** is a `@Transactional(readOnly = true)` method returning `AdminOverviewResponse`.
- **Data state**: counts teams, players, groups players by team to classify squads as complete (≥18 players) or missing (0 players).
- **Round state**: derives `nextRoundToOpen` (first NOT_STARTED round whose predecessors are all FINISHED) and `nextRoundToSimulate` (first OPEN_FOR_BETS round), plus a `roundsByStatus` map.
- **Betting state**: counts open/settled/cancelled bets across all users; calls `betService.isSeasonBettingOpen()`.
- **Recommended actions**: a `List<String>` expressing the single most relevant next step — "Import league data" → "Generate schedule" → "Open betting for Round N" → "Simulate Round N" → "Season complete".
- Secured at `/api/admin/**` (ROLE_ADMIN); regular users receive 403 Forbidden.

### `BetService.isSeasonBettingOpen()` visibility
Changed from package-private to `public` so both `DashboardService` and `AdminService` (in the same package) can call it without duplicating the Round 1 check logic.

---

## Key Design Choices

- **BigDecimal for money** — prevents floating-point errors in financial operations.
- **Odds snapshot on bet** — odds can change; saved value is the contract.
- **@Transactional on bet placement and settlement** — balance and bet state are always consistent.
- **DTO layer** — API contract is decoupled from entity changes; no accidental data leaks.
- **Thin controllers** — controllers only parse requests and call one service method.
