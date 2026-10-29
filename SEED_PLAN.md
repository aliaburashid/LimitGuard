# Normal development database and proposed seed changes

Status: additive plan approved, both legacy seeder bodies replaced, but execution stopped before the first INSERT because existing credit limit #1 changed from BHD 1,000,000 to BHD 2,000,000. No new seed data was written. See the execution note at the end. The original approved figures below are retained as the audit baseline.

Latest update: the user separately approved adding Citi's missing shared limit for the existing Gulf Investment Group. That narrow repair is now complete; it did not run the broader seed or create/consume any credit requests. Details follow at the end.

## 1. Removed tooling and preserved work

Git inspection found the separate system entirely in untracked additions: `DEMO_GUIDE.md`; `demo/README.md`, `demo/VERIFICATION.md`, `demo/fixtures.py`, `demo/demo.py`, `demo/run_backend.py`, `demo/concurrency_demo.py`, `demo/check_consistency.sql`, `demo/test_fixtures.py`; generated `demo/local/` inventories, snapshots, logs, screenshots and cached Python bytecode; and `frontend/src/demo/Inventory.tsx`. These have been removed. The four obsolete screenshots under `frontend/screenshots/` were also removed, and browser tests no longer regenerate them.

Removed the inventory provider/import controls, PRACTICE/FINAL DEMO selectors, dataset-based request/group hiding, fixture name rewriting/badges, final-dataset confirmation and their CSS. Cleaned corresponding API documentation and redesign notes. Kept independent mocked browser test data, with ordinary company names and no snapshot dependency. These tests are not an application seeding system.

No tracked demo Spring profile existed. The removed Python launcher supplied the demo profile externally; existing `application.properties` and `application-dev.properties` remain unchanged. No Maven dependency belonged to the separate system. Recharts, its lockfile dependencies, professional dashboard, dedicated Risk Analytics page, banking controls, API client improvements and notifications remain.

All existing backend tests and business/security/model code are unchanged. `Thread.sleep(5000)` in `approveCreditRequest()` is byte-for-byte unchanged.

## 2. Original seed files

Retained `src/main/java/com/example/limitguard/config/DemoDataSeeder.java` and `DemoCreditRequestSeeder.java`. Each now requires `limitguard.seed.enabled=true` in addition to the existing dev profile. It is unset by default, so an ordinary IntelliJ run uses existing data without executing these seeders. This opt-in is intentional: the old startup seeders can otherwise write before approval, generate inconsistent pending amounts and reapply company statuses.

**The old seed contents have not been replaced or run. Do not enable them yet.** After approval, update these same two original files for the records below rather than reintroducing scripts, generations, manifests, profiles or another database:

- Data seeder: target the existing Citi institution and verified employees by existing IDs/emails; add only the approved companies, memberships, individual/group limits and six-ratio financial profiles. Preserve existing user/password/role/institution records and all existing business records. No employee creation is needed.
- Request seeder: replace the legacy rotating-status/small-amount generation with the explicit valid history and pending requests below; maintain stable seed keys, skip consumed records on rerun and never refill exposure, reset status or extend expiry. Validate prerequisites and name collisions before writing. Derive added balances from the new RESERVED/USED records; do not recalculate existing balances by overwriting them.
- Make the approved additive seed a single transactional operation so partial fixture creation cannot leave exposure inconsistent. This is seeder transaction handling, not a change to service transactions or locks. Stop if the target institution/accounts/limits differ from the audited assumptions.
- Execute only after approval, then leave seeding disabled for subsequent ordinary launches. The user does not need a separate runtime environment or recurring seeding command.

## 3. Exact database and backup

The normal dev profile resolves to `jdbc:postgresql://localhost:5432/limitguard`. A read-only connection confirmed database `limitguard`, PostgreSQL role `postgres`, server address `::1`, port `5432`. IntelliJ's `LimitguardApplication` configuration supplies the required secret environment values and has no datasource/profile override. No database configuration was changed.

Backup: `backups/limitguard-20261029-085420.dump`, custom pg_dump archive, 49,667 bytes, owner-only mode 0600 inside a private directory excluded from Git.

SHA-256: `c8159bcaf1a9996304f5fb35351e202b2eaa2b6232dc2361c39e045c896310f3`.

`pg_restore --list` successfully read the archive (101 listing lines). A full restore was not attempted. The backup contains private account data and must remain local. It does not include cluster-wide role definitions. Recheck and take a fresh backup immediately before an approved seed if the database has changed.

## 4. Existing data and accounts

Read-only audit: 70 users, 32 companies, 3 groups, 78 individual limits and 237 requests across all banks; 5 financial profiles. Every individual limit's used/reserved balances reconcile to the corresponding USED/RESERVED requests. No negative/over-limit individual balances or duplicate decision records were found.

Reuse these four existing active, email-verified employees, all at **Citi, institution ID 5**:

- Main RM: `aliaburashid@hotmail.com`, user ID 15, RELATIONSHIP_MANAGER.
- Main RO: `burashid993@gmail.com`, user ID 23, RISK_OFFICER.
- Second RO: `fatima.alzayani@example.com`, user ID 59, RISK_OFFICER.
- Second RM: `sara.alhaddad@example.com`, user ID 58, RELATIONSHIP_MANAGER.

No additional accounts, credential changes or password resets are proposed. Existing hashes were neither displayed nor modified. Existing credentials must be known to the presenter; their validity was not tested by logging in. Any later account recovery uses the normal secure recovery flow.

Citi currently has 12 limits and 39 requests: 12 CANCELLED, 7 EXPIRED, 1 PENDING_APPROVAL, 9 REJECTED, 5 RESERVED and 5 USED. Its existing pending request **#183, BHD 80,000**, was created on 2026-09-18 for Gulf Meridian Logistics W.L.L., seed key `demo-5-37-1`. It is legacy seeder data and is excluded from the proposed presentation requests. It will remain unchanged. Risk Analytics already warns about pending amounts below the current threshold.

Existing groups remain untouched: Bahrain Holding Group (one member, Citi group limit BHD 6m), Gulf Investment Group (four members, no group limit), and Al Noor Holdings (no members or group limit). Gulf Meridian belongs to Gulf Investment Group, so legacy request #183 also lacks a Citi shared group limit. Do not use it to demonstrate a normal approval.

## 5. Corporate groups, companies and opening capacity

All monetary figures below are BHD. Group exposure is the sum of each member's used plus reserved balance at Citi; pending requests contribute zero.

**Gulf Aviation Holdings — four companies, group limit 12,000,000.**

- Gulf Aviation Services — aviation services; individual limit 4,000,000; used 200,000; reserved 0; individual headroom 3,800,000.
- Gulf Cargo Logistics — air freight; same individual figures.
- Gulf Aircraft Maintenance — aircraft engineering; same individual figures.
- Gulf Airport Technology — airport systems; same individual figures.

Opening group exposure 800,000 and headroom 11,200,000. Four pending requests of 600,000 per company total 9,600,000. Even if every pending request is approved, exposure becomes 10,400,000 and group headroom remains 1,600,000; each company also has sufficient capacity. This prevents unrelated headroom failures during approval races.

**Pearl Infrastructure Group — two companies, group limit 2,000,000.**

- Pearl Construction — civil construction; individual limit 1,500,000; used 800,000; reserved 0; individual headroom 700,000.
- Pearl Engineering — infrastructure engineering; individual limit 1,600,000; used 800,000; reserved 0; individual headroom 800,000.

Opening group exposure 1,600,000 and headroom 400,000. Each company's used history consists of two 400,000 reservations subsequently used, one owned by each RM. Both RMs can therefore discover both facilities through their own ordinary request history.

**Arabian Energy Group — two companies, group limit 2,000,000.**

- Arabian Renewable Energy — clean energy; individual limit 1,600,000; used 600,000; reserved 0; individual headroom 1,000,000.
- Arabian Industrial Utilities — industrial utilities; same individual figures.

Opening group exposure 1,200,000 and headroom 800,000. Each company's used history consists of two 300,000 reservations subsequently used, one owned by each RM. No competing 450,000 requests will be pre-created or reserved.

**Additional ungrouped company:** Bahrain Digital Services, individual limit 3,000,000, opening used 250,000 and reserved 750,000, headroom 2,000,000. This company provides lifecycle records and capacity for immediate live requests without consuming a corporate group's scenario capacity.

Total proposed additions: **3 groups, 9 companies, 9 Citi individual limits, 3 Citi group limits and 9 financial profiles**. Use plausible six-ratio profiles for the existing ML API, current supported financial year-end and no fabricated model predictions. Companies and groups use their ordinary names with no practice/final prefixes.

The schema has no company industry field. Industries above are explanatory descriptions; storing/filtering industries would require an optional schema/API change and is outside this seed plan.

## 6. Requests and decisions

Create **16 distinct PENDING_APPROVAL requests at BHD 600,000 each**, all owned by main RM ID 15. Each of the four aviation companies receives one request in each category:

- 4 for normal approval (minimum requested: 3).
- 4 for rejection with a live reason (minimum requested: 3).
- 4 for two Risk Officers attempting approval of the same request (minimum requested: 3).
- 4 for concurrent approval versus rejection (minimum requested: 2).

The categories are assignments documented with actual IDs after seeding, not new statuses, company-name suffixes or application filters. Pending records have no decision, no reserved exposure and no reservation expiry. Both ROs are in Citi and neither is the requester. The main RM receives the subsequent status notifications at the real existing account.

Add **12 historical USED requests** to support the opening group exposure: four aviation requests at 200,000; four Pearl requests at 400,000; four Arabian requests at 300,000. These amounts qualify for immediate reservation, followed by use; no fabricated RO approval is required for them.

At Bahrain Digital Services add **7 lifecycle records** owned by the main RM:

- 3 RESERVED requests at 250,000 each: one for cancellation, one for marking used, one for observing expiry.
- 1 historical USED request at 250,000.
- 1 historical CANCELLED request at 200,000, contributing no current exposure.
- 1 historical EXPIRED request at 200,000, contributing no current exposure.
- 1 historical REJECTED request at 600,000 with a recorded RO decision/reason, contributing no exposure.

This is **35 new requests total**: 16 pending, 13 used, 3 reserved, 1 cancelled, 1 expired and 1 rejected. The separate rejected history is additional to the four pending requests designated for live rejection. No new pending request is at or below 500,000.

For ordinary reserved records use a valid 24-hour lifetime from creation. For the expiry example only, create an explicitly historical reservation whose creation is 23h55m before the approved seed time and expiry is 5m after it. The existing scheduler checks every minute and performs the actual transition/release/email/SSE. This gives one near-term expiry observation; repeated immediate expiry is not possible without preparing another historical reservation or waiting 24 hours. No arm-expiry script or timestamp-reset command will be added. Its reserved opening exposure is included until the scheduler expires it.

Historical terminal records have sensible ordered timestamps and, where applicable, the original 24-hour reservation expiry. Only the rejected 600,000 history needs a pre-recorded RO decision. All new pending approval/rejection decisions are made live through existing services.

## 7. Risk Officer concurrency through the frontend

Keep the RM browser signed in for SSE. Open separate Chrome profiles or separate browsers for the main and second RO (ordinary tabs share authentication state). Open the same pending request ID in each session and review its profile/ML prediction normally. Trigger the two decisions as closely together as possible.

The existing approval holds the request's pessimistic lock during the unchanged 5-second sleep. The other decision waits, then checks the changed status. Expected result: one valid decision succeeds; the loser receives the existing backend state/conflict response. A double approval reserves exactly 600,000 once; approval/rejection results in either RESERVED with one approved decision or REJECTED with one rejected decision. Rejection releases no pending reservation because none existed.

After each race, verify the final request status, exactly one decision and matching balance change. Confirm one winning decision's email dispatch separately from inbox receipt. No concurrency script or service/lock modification is proposed. These outcomes are expected from inspected code; live PostgreSQL concurrency has not been rerun during this cleanup.

## 8. Corporate headroom through the frontend

**Individual headroom sufficient, group headroom insufficient:** submit 450,000 for Pearl Construction. Individual capacity is 700,000; shared capacity is 400,000. The existing reservation path must reject it with the group-specific message and leave exposure unchanged. Pearl Engineering provides the same repeatable blocked example. Failed attempts consume no capacity. The create form shows both capacities and explicitly explains when a company fits individually but its group cannot fit the request.

**Two RMs competing for different subsidiaries:** RM 1 selects Arabian Renewable Energy and RM 2 selects Arabian Industrial Utilities in separate sessions. Each submits a new 450,000 request live. Both are below the approval threshold and therefore attempt immediate reservation. At most one can fit in the initial 800,000 shared headroom. Expected outcome: one RESERVED request, one rejected creation, group exposure 1,650,000 and headroom 350,000. The group's approved 2,000,000 limit remains intact.

For repeated attempts, the successful request's owner cancels that RESERVED request using the normal UI, releasing 450,000 and restoring group headroom to 800,000. Reuse the pair and alternate subsidiaries/RMs. Do not mark the winner USED if another full 450,000-versus-450,000 attempt is needed. These are normal lifecycle actions, not resets or database edits.

Immediate reservation can also be shown on Bahrain Digital Services with a live request at or below 500,000. Reuse existing Bahrain Trading Company (Citi limit ID 1, headroom 320,000 at audit) to show an individual 450,000 failure, and existing FROZEN Pearl Coast Shipping Co. (Citi limit ID 39) to show a frozen counterparty failure. Recheck these existing balances/statuses before use; no modifications to them are proposed.

## 9. Email and SSE evidence

IntelliJ supplies nonempty GMAIL_USERNAME and GMAIL_APP_PASSWORD. The dev configuration uses smtp.gmail.com:587 with authentication and STARTTLS. Credential values were not displayed or modified. Presence/configuration is verified; SMTP authentication, provider acceptance and inbox delivery are not yet verified.

The existing approval/rejection methods register notifications after successful transaction commit. Status checks and row locking occur before registering the winning decision's notifications. Email methods retain `@Async("notificationExecutor")`; all four backend SSE publishers remain unchanged. Source inspection supports no duplicate email for a losing concurrent decision, but source inspection is not a live dispatch measurement.

Two new focused mail-sender tests exercise approval/rejection message construction and exactly one dispatch per invocation to a mocked JavaMailSender. They send no real email and do not verify asynchronous scheduling, commit callbacks, race behavior or inbox delivery. Existing tests remain unchanged.

For the approved live run, distinguish: committed decision and application dispatch evidence; SMTP/provider acceptance if available; actual inbox receipt at the RM mailbox. Record a single dispatch for the winning race request and none for the loser. An async failure can occur after a successful banking transaction; a saved RESERVED/REJECTED status or SSE toast alone does not prove email delivery.

All four SSE event types are preserved: credit-request-status, counterparty-status, credit-limit-updated and group-credit-limit-updated. Browser tests cover visible global toasts, refresh, duplicate suppression and incoming notifications above an open decision dialog. These browser checks use mocked event streams; actual cross-session SSE remains to be observed after approved data is available.

## 10. Limitations and validation

No backend business logic changes are required for the requested approval, rejection, reservation or group-headroom scenarios. Requests above 500,000 are pending and group reservation capacity is checked at approval, while the 450,000 live group examples check it immediately.

The existing API has no individual credit-limit directory. The frontend now uses authorized request history plus normal ID lookup, without imported files. Seeded grouped companies will be discoverable through the historical records assigned to each RM; Risk Officers see institution history. A company with no request history still needs its credit-limit ID. A complete searchable unused-facility selector would require an additional ordinary authenticated directory endpoint, not a change to reservation or locking logic. No such backend endpoint was added without discussing that scope.

Validation completed:

- Frontend production build and lint passed; 27 frontend unit tests passed.
- All 19 browser checks passed against the production preview using mocked APIs, including the new individual-versus-group headroom explanation, all four SSE toast types and conflict handling.
- All 46 existing backend tests outside `CreditRequestConcurrencyIntegrationTest` passed. Mockito required an explicit Java agent in the test invocation under this environment; no test implementation was changed.
- Two new email dispatch tests passed separately. They verify mocked mail-sender dispatch, not live SMTP or inbox delivery.
- Compared 60 protected tracked backend service, repository, security, model, scheduler, resource and test files against Git HEAD: all unchanged. Only the two original seeder classes gained opt-in guards; the new mail test is an additional file.
- `git diff --check` passed. Confirmed removed demo directories/files are absent, no runtime practice/final filtering remains, and the backup checksum is unchanged.

PostgreSQL concurrency integration tests remain preserved but are not run because their configured create-drop database would be written. The existing development database has only been read and backed up. Approval is requested for the additive seed plan above; no new seed data has yet been written.

## Approved execution attempt — stopped on a changed prerequisite

On 2026-10-29 the user approved the plan, requiring a stop before writing if any prerequisite differs. Both original seeder files were replaced with the approved data definitions; the rotating-status seeder and legacy user/password creation are gone. The two components share a single Spring transaction, validate prerequisites before INSERT, hash-check all existing rows before commit, and remain disabled unless explicitly enabled. Request history cannot run outside the caller's transaction. A completed seed would be skipped on rerun without restoring consumed records.

The initial read-only recheck matched the approved baseline, including credit limit #1 at BHD 1,000,000. Before the write attempt a fresh backup was taken and its archive index checked:

- File: `backups/limitguard-before-approved-seed-20261029-091651.dump`.
- Size: 49,685 bytes; permissions 0600; excluded from Git.
- SHA-256: `af4229334c4dae4ba56f23be72ee1462b7295b68cc4ece2399b2638b33cc5008`.
- `pg_restore --list` succeeded, with 101 listing lines. A full restore was not attempted.

The transactional prerequisite check then detected that **Citi credit limit #1 for Bahrain Trading Company had become BHD 2,000,000**. Used exposure remains 0 and reserved exposure remains 680,000, so current individual headroom is **1,320,000**, rather than the approved 320,000. The seeder threw before its first INSERT and Spring rolled back the transaction. A separate read-only check confirmed all twelve tables' existing row hashes and row counts were unchanged across this attempt, with **zero** `development-citi-` requests. Counts remain 32 companies, 3 groups and 237 requests.

No pending request IDs, new group IDs or new credit-limit IDs exist yet. No decision, concurrency exercise, email or expiry demonstration was executed. The one-time seed context closed after the failure; the normal IntelliJ configuration was never enabled for seeding and no persistent enable flag was added.

All 52 non-PostgreSQL-concurrency backend tests passed, including four new seed checks for request counts, opening balances, lifecycle dates, disabled default registration and transaction declarations. Compilation and whitespace checks passed. Existing business services, locks, five-second sleep, resources and existing backend test files remain unchanged.

Resume only after the changed prerequisite is explicitly accepted. Preserve the BHD 2,000,000 limit rather than reverting it. If accepted, the individual-headroom failure example must also change: Bahrain Trading Company now has enough individual headroom for 450,000. A 1,400,000 request would exceed its current headroom, or the example could use another existing suitable limit after rechecking it. The proposed 3 groups, 9 companies and 35 new requests otherwise remain unchanged.

## Separately approved Gulf Meridian group-limit repair — completed

On 2026-10-29 the user approved adding a BHD 2,000,000 shared limit for Citi's existing Gulf Investment Group so that Gulf Meridian can be used for a live Risk Officer race. The addition was made through the existing authenticated `POST /api/group-credit-limits` service as existing Citi RO user 59, preserving its normal transaction and audit behavior. No business logic or credentials were changed.

- Corporate group ID: **3**, Gulf Investment Group.
- Newly created group credit-limit ID: **2**, institution **5**, Citi; amount **2,000,000**.
- API-verified group exposure: **75,000**; group headroom: **1,925,000**.
- Existing Gulf Meridian individual credit-limit ID: **37**; individual headroom remains **675,000**.
- Fresh backup immediately before the addition: `backups/limitguard-before-gulf-group-limit-20261029-093118.dump`, 49,685 bytes, archive index checked, SHA-256 `32fb961a735b89ea5e398b09e30363e2996c6b0151fe26cfce8f98879a7a4325`.
- Before/after row hashes confirmed **all 593 existing rows remained unchanged**. Exactly two rows were added: one group credit limit and its normal `GROUP_CREDIT_LIMIT_CREATED` audit entry. No credit requests or approval decisions were added or consumed.
- Authenticated API reads confirmed individual exposure is unchanged, group headroom is available, and legacy pending request #183 remains unchanged and reviewable. It is still legacy BHD 80,000 data and is not the recommended race request.

For the live demonstration, main RM user 15 creates a new **600,000** request for credit-limit **37** in the normal frontend. Main RO user 23 and second RO user 59 open that same new pending request in separate browser profiles and attempt approval. No approval, rejection or concurrency action was performed during repair verification. After the single successful reservation, individual headroom would be 75,000; its owner can cancel that new RESERVED request through the normal UI to restore capacity for another new 600,000 attempt.

Seeding remains disabled. The broader 3-group/9-company/16-pending-request seed has not run. Its strict original prerequisites intentionally remain unchanged until the revised baseline is approved; the separately added group limit is also now part of the live baseline.
