# Database Plan — Football League Simulator

Database: H2 (local dev), managed by Hibernate/JPA.
All monetary values use DECIMAL(19,2) — maps to BigDecimal in Java.

---

## Enums

```
MatchStatus:      SCHEDULED | BETTING_OPEN | IN_PROGRESS | FINISHED
Prediction:       HOME_WIN | DRAW | AWAY_WIN
BetStatus:        OPEN | WON | LOST | CANCELLED
BetType:          SINGLE | COMBO
BetMarket:        MATCH_RESULT | CORRECT_SCORE | HANDICAP
HandicapSelection: HOME_MINUS_ONE | HANDICAP_DRAW | AWAY_PLUS_ONE
Role:             USER | ADMIN
Position:         GK | CB | LB | RB | DM | CM | AM | LW | RW | ST
PlayerStatus:     FIT | INJURED | SUSPENDED
WeatherCondition: CLEAR | RAIN | WIND | HOT | COLD | STORM
```

---

## Table: users

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto-increment | |
| username | VARCHAR(50) | UNIQUE, NOT NULL | |
| email | VARCHAR(100) | UNIQUE, NOT NULL | |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt |
| balance | DECIMAL(19,2) | NOT NULL, DEFAULT 1000.00 | BigDecimal in Java |
| role | VARCHAR(20) | NOT NULL, DEFAULT 'USER' | Enum: USER, ADMIN |
| created_at | TIMESTAMP | NOT NULL | |

Constraints:
- balance >= 0 (enforced in service, not DB check — H2 has limited CHECK support)
- username and email must be unique

---

## Table: teams

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto-increment | |
| name | VARCHAR(100) | UNIQUE, NOT NULL | |
| skill_level | INT | NOT NULL | 0–100 |
| morale | INT | NOT NULL | 0–100 |
| injuries | INT | NOT NULL | 0–50 |
| wins | INT | NOT NULL, DEFAULT 0 | Updated after each match |
| draws | INT | NOT NULL, DEFAULT 0 | |
| losses | INT | NOT NULL, DEFAULT 0 | |
| goals_for | INT | NOT NULL, DEFAULT 0 | |
| goals_against | INT | NOT NULL, DEFAULT 0 | |
| points | INT | NOT NULL, DEFAULT 0 | Computed: wins*3 + draws |
| default_formation | VARCHAR(20) | NOT NULL, DEFAULT '4-3-3' | e.g. "4-3-3" — used when displaying the team's lineup |
| display_name | VARCHAR(100) | NULLABLE | Optional human-friendly name from real-data import; falls back to `name` when blank |

Note: `points` is stored for query convenience; always updated atomically with wins/draws/losses.

---

## Table: rounds

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto-increment | |
| round_number | INT | UNIQUE, NOT NULL | 1-based |
| name | VARCHAR(50) | | e.g. "Matchday 1" |

---

## Table: matches

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto-increment | |
| round_id | BIGINT | FK → rounds.id, NOT NULL | |
| home_team_id | BIGINT | FK → teams.id, NOT NULL | |
| away_team_id | BIGINT | FK → teams.id, NOT NULL | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'SCHEDULED' | Enum: MatchStatus |
| home_score | INT | NULLABLE | Null until match is finished |
| away_score | INT | NULLABLE | Null until match is finished |
| home_win_odds | DECIMAL(6,2) | NULLABLE | Computed before simulation |
| draw_odds | DECIMAL(6,2) | NULLABLE | |
| away_win_odds | DECIMAL(6,2) | NULLABLE | |
| played_at | TIMESTAMP | NULLABLE | Set when match is simulated |
| weather_condition | VARCHAR(20) | NULLABLE | Enum: WeatherCondition — assigned when the schedule is generated, visible to users before betting/simulation |
| weather_impact | INT | NULLABLE | Small signed magnitude derived from the condition (e.g. RAIN/STORM are negative — fewer goals); feeds into odds/simulation alongside the condition's effect on draw chance and randomness |

Constraints:
- home_team_id != away_team_id (enforced in service)
- A team cannot appear twice in the same round (enforced in service)

---

## Table: bets

(This table also supports combo/accumulator bets alongside single bets —
see `bet_selections` below. The old `(user_id, match_id)` unique constraint was dropped because
a user's open bets are now spread across single bets *and* combo selections; "one open bet per
match" is enforced in `BetService` instead, across both.)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto-increment | |
| user_id | BIGINT | FK → users.id, NOT NULL | |
| bet_type | VARCHAR(10) | NOT NULL, DEFAULT 'SINGLE' | Enum: SINGLE, COMBO |
| market | VARCHAR(20) | NOT NULL, DEFAULT 'MATCH_RESULT' | Enum: BetMarket (MATCH_RESULT, CORRECT_SCORE, HANDICAP). Combo bets are always MATCH_RESULT |
| match_id | BIGINT | FK → matches.id, NULLABLE | Set for SINGLE bets only; NULL for COMBO (selections live in `bet_selections`) |
| prediction | VARCHAR(20) | NULLABLE | Enum: HOME_WIN, DRAW, AWAY_WIN — set for SINGLE MATCH_RESULT bets only; NULL for CORRECT_SCORE and HANDICAP |
| predicted_home_goals | INT | NULLABLE | set for SINGLE CORRECT_SCORE bets only (0-6) |
| predicted_away_goals | INT | NULLABLE | set for SINGLE CORRECT_SCORE bets only (0-6) |
| handicap_selection | VARCHAR(20) | NULLABLE | set for SINGLE HANDICAP bets only. Enum: HandicapSelection (HOME_MINUS_ONE, HANDICAP_DRAW, AWAY_PLUS_ONE). European Handicap -1 (home); no pushes |
| amount | DECIMAL(19,2) | NOT NULL | Stake; > 0, <= user balance at placement/edit time |
| odds | DECIMAL(6,2) | NULLABLE | Odds snapshot for SINGLE bets only |
| total_odds | DECIMAL(10,2) | NULLABLE | Product of selection odds snapshots — COMBO bets only |
| possible_win | DECIMAL(19,2) | NOT NULL | `amount * odds` (single) or `amount * total_odds` (combo) |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'OPEN' | Enum: OPEN, WON, LOST, CANCELLED |
| profit | DECIMAL(19,2) | NULLABLE | Set on settlement/loss: `possibleWin - amount` (won) or `-amount` (lost); unchanged/ignored for CANCELLED |
| created_at | TIMESTAMP | NOT NULL | |
| settled_at | TIMESTAMP | NULLABLE | Set when WON/LOST (settlement) or CANCELLED (cancellation) |

Constraints:
- amount > 0 (enforced in service)
- match.status must be BETTING_OPEN at placement/edit time (enforced in service)
- For SINGLE: a user cannot have two open bets on the same match (enforced in service, replacing the old DB-level unique constraint)
- For COMBO: at least 2 selections, no duplicate match within the combo, and (per the existing single-bet rule) no match that the user already has another open bet/selection on
- For CORRECT_SCORE: predicted_home_goals/predicted_away_goals must each be 0-6 (enforced via DTO validation); odds are computed from a Poisson goal model and snapshotted at placement/edit time, never recalculated retroactively
- For HANDICAP: handicap_selection must be a valid HandicapSelection enum value; odds computed from a Poisson goal model (same expectedGoals used for CORRECT_SCORE) summed into three handicap buckets (HOME_MINUS_ONE/HANDICAP_DRAW/AWAY_PLUS_ONE) with a 1.10 bookmaker margin; no pushes — every handicap bet settles WON or LOST; odds snapshotted at placement, re-snapshotted only when selection changes on edit

---

## Table: bet_selections

one row per match prediction inside a COMBO bet (a SINGLE bet has no rows here; its match/prediction/odds live directly on `bets`).

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto-increment | |
| bet_id | BIGINT | FK → bets.id, NOT NULL | Owning combo bet (cascade delete/orphan-removal — selections are replaced wholesale on edit) |
| match_id | BIGINT | FK → matches.id, NOT NULL | |
| prediction | VARCHAR(20) | NOT NULL | Enum: HOME_WIN, DRAW, AWAY_WIN |
| odds_snapshot | DECIMAL(6,2) | NOT NULL | Odds for this prediction at combo placement/edit time — never recalculated retroactively |

Constraints:
- No duplicate match_id within the same bet (enforced in service)
- match.status must be BETTING_OPEN when the selection is created/edited (enforced in service)
- Combo selections remain MATCH_RESULT-only

---

## Table: players

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto-increment | |
| team_id | BIGINT | FK → teams.id, NOT NULL | |
| full_name | VARCHAR(100) | NOT NULL | |
| position | VARCHAR(10) | NOT NULL | Enum: Position (GK, CB, LB, RB, DM, CM, AM, LW, RW, ST) |
| jersey_number | INT | NULLABLE | |
| rating | INT | NOT NULL | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'FIT' | Enum: PlayerStatus (FIT, INJURED, SUSPENDED) |
| injury_description | VARCHAR(255) | NULLABLE | |
| injured_until_round | INT | NULLABLE | Legacy field, kept for backward compatibility only — not used by the match-count injury system |
| injury_matches_remaining | INT | NOT NULL, DEFAULT 0 | Counts down by 1 after each of the team's matches; 0 = not currently injured |
| injury_matches_total | INT | NULLABLE, DEFAULT 0 | Original injury duration, kept for display ("3 of 5 matches remaining") |
| goals | INT | NOT NULL, DEFAULT 0 | Simulation-generated only — always 0 on import |
| assists | INT | NOT NULL, DEFAULT 0 | Simulation-generated only — always 0 on import |
| red_cards | INT | NOT NULL, DEFAULT 0 | Simulation-generated only — always 0 on import |
| suspension_matches_remaining | INT | NOT NULL, DEFAULT 0 | Set to 1 when a red card is issued; counts down the same way as injuries, starting from the team's *next* match |
| starter | BOOLEAN | NOT NULL, DEFAULT false | |
| substitute | BOOLEAN | NOT NULL, DEFAULT false | |
| lineup_order | INT | NULLABLE | Display order within the lineup |
| data_source | VARCHAR(100) | NULLABLE | Provenance of the player's data when loaded from the real-data file, e.g. "365Scores", "official", "Transfermarkt", "manual". Values starting with `EXAMPLE` mark template/placeholder data that the loader never imports |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

Constraints:
- A player cannot be both a starter and a substitute (enforced in service)
- Injured/suspended players are excluded from starters/substitutes in lineup responses and
  surfacing instead under unavailable players (enforced in service)

---

## Table: match_events

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto-increment | |
| match_id | BIGINT | FK → matches.id, NOT NULL | |
| team_id | BIGINT | FK → teams.id, NOT NULL | The team the event happened for |
| player_id | BIGINT | FK → players.id, NULLABLE | Scorer (GOAL) or carded player (RED_CARD); null only if no eligible player exists |
| assist_player_id | BIGINT | FK → players.id, NULLABLE | GOAL events only — set ~75% of the time |
| event_type | VARCHAR(20) | NOT NULL | Enum: MatchEventType (GOAL, RED_CARD) |
| match_minute | INT | NOT NULL | 1–90. Named `match_minute` (not `minute`) — `MINUTE` is a reserved word in H2 |
| description | VARCHAR(255) | NULLABLE | e.g. "Dor Peretz scored, assisted by Kervin Andrade", "Tyrese Asante received a red card" |
| created_at | TIMESTAMP | NOT NULL | |

Constraints:
- Immutable — created once during match simulation, never updated (no `updated_at`)
- Generated solely by `SimulationService`; never imported, never invented outside simulation
- Ordered for display by `(match_minute ASC, id ASC)`

---

## Real Data Seed (Israeli Premier League 2025/2026)

Real league data lives in `backend/src/main/resources/data/`:
- `ligat-haal-2025-2026.schema.md` — documents the JSON format and validation rules
- `ligat-haal-2025-2026.template.json` — structural template with one clearly-marked
  `EXAMPLE`-only team/players (never imported)
- `ligat-haal-2025-2026.json` — the actual seed file: 14 real Ligat Ha'Al teams with
  `name`, `displayName`, `skillLevel`, `morale`, `defaultFormation`; `players[]` is
  currently empty for every team pending verified squad data

`LeagueDataImportService` reads this file with Jackson, validates it (see schema.md for
the full rule list), and imports it as an **upsert**: existing teams are matched by
`name` and existing players by `(team, fullName)`. Nothing is ever deleted, and nothing
is invented — teams whose `players[]` stays empty are simply reported as "squad missing"
warnings. Entries whose `dataSource` starts with `EXAMPLE` are always skipped.

`POST /api/admin/seed` now prefers this real-data import (so the league reflects real
2025/2026 teams) and falls back to the old random demo seed only if the file is absent
or invalid. `POST /api/admin/data/import` runs the import explicitly and always upserts
from the bundled file, regardless of whether teams already exist.

Note: this milestone adds the infrastructure only — no fake squads are seeded. Real Israeli
Premier League 2025/2026 squad data will be loaded later from a structured data file.

---

## Relationships Summary

```
users       1 ── * bets
teams       1 ── * matches (as home_team)
teams       1 ── * matches (as away_team)
teams       1 ── * players
rounds      1 ── * matches
matches     1 ── * bets             (SINGLE bets only — bet.match_id)
bets        1 ── * bet_selections   (COMBO bets only)
matches     1 ── * bet_selections
```

---

## Seeding / Initial Data

Seeding is admin-triggered (no automatic DataInitializer):
- `POST /api/admin/seed` seeds teams — preferring the real Israeli Premier League
  2025/2026 data file when present (see "Real Data Seed" above), falling back to a
  simple random demo seed of 8 teams otherwise.
- `POST /api/admin/generate-schedule` generates the full round-robin schedule
  (every team plays every other team home and away) once teams exist.
- `POST /api/admin/data/import` explicitly imports/updates teams and players from the
  real-data file (upsert by name / by team+fullName); never deletes, never invents.
- ADMIN role is assigned automatically on register/login by matching `app.admin.email`.

All matches start with status = SCHEDULED. Odds are computed when betting opens for a round.
