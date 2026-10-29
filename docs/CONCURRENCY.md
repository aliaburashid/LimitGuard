# LimitGuard — Concurrency and live demonstration guide

[← Back to the main README](../README.md)

This document extends the Unit 2 LimitGuard project with Unit 3 concurrency, parallel risk assessment and shared corporate-group capacity. It distinguishes **implemented mechanisms**, **proposed seed fixtures**, **expected outcomes**, and **live evidence still to collect**.

**Current status (2026-10-29):** the approved minimal seed is complete. See [the seed execution record](../SEED_PLAN.md#approved-minimal-seed--completed-2026-10-29). Pending **405** supports dual approval; pending **406** supports approval versus rejection; facilities **79/80** support the two-RM group race. The Exposure page now includes Corporate Group Management. No further seed runs or automatic banking-data changes are needed. Existing #402 is already approved and must not be reset.

## 1. Why race conditions matter in banking

A race condition occurs when concurrent operations act on shared state and the result depends on timing. In LimitGuard the shared state includes a `CreditRequest` status, an individual `CreditLimit`'s used/reserved balances, and a `GroupCreditLimit` shared by multiple subsidiaries. Two concurrent operations must not each spend the same available capacity.

Spring MVC/Tomcat can process requests on different worker threads. PostgreSQL row locks inside Spring-managed transactions serialize conflicting changes to persisted records. Java `ReentrantLock` alone would not coordinate two separately running application instances against the same database.

**Safety is provided by transactional locking and status/headroom rechecks, not by the five-second `Thread.sleep(5000)` demonstration delay in approval.**

## 2. Scenario A — Two Risk Officers approve the same request

**Business problem:** Both ROs may read `PENDING_APPROVAL` and attempt to approve the same BD 600,000 request.

**Shared resource:** The credit request row, its approval decision, and the affected individual/group capacity.

**Without protection:** Duplicate approval records, repeated reservation or an inconsistent state.

**With protection:** The first transaction locks the request, validates it, reserves capacity and commits `RESERVED` plus one decision. The second waits, then sees that the request is no longer pending and must fail without a second reservation.

**Live walkthrough (after approved seed is confirmed):**
1. Keep the main RM session open to observe status/SSE updates.
2. Open **two separate browser profiles**: main RO `burashid993@gmail.com` and second RO `fatima.alzayani@example.com`. Use existing authorized credentials; do not publish passwords.
3. Open the **same pending request #405**, BD 600,000 at Bahrain Trading Company, facility **1**, in both profiles.
4. Click **Approve & Reserve** nearly simultaneously.
5. Verify final status `RESERVED`, **one** approval decision, and **exactly BD 600,000** additional reserved exposure (not BD 1,200,000). Record the losing operation's actual response.

**Expected opening capacity:** facility 1 has limit BD 2,000,000, used 0, reserved BD 680,000 and headroom BD 1,320,000. After one successful BD 600,000 approval, reserved becomes BD 1,280,000 and headroom BD 720,000. Bahrain Holding Group (group 2, shared limit 1) remains within its BD 6,000,000 limit.

**Existing live evidence:** the IntelliJ console already shows one successful approval of request #402 and a second attempt failing after the first committed RESERVED. The database shows one decision for #402. It is used evidence, not a reusable pending request.

**Evidence:** two session screenshots, final request/decision record, before/after limit balances and relevant logs. Expected behavior is not a substitute for a live race test.

## 3. Scenario B — Approval versus rejection

**Shared resource:** One pending BD 600,000 request and its decision record.

Use **pending request #406**, 600,000 at facility 1, independent of the first approval race. Open it in both RO sessions. One clicks Approve, the other Reject with a reason nearly simultaneously. The request lock ensures one valid terminal decision for this approval stage. The final state should be either `RESERVED` with one APPROVED decision and one reservation, or `REJECTED` with one REJECTED decision and no reservation. The losing action must not create a second decision.

If the dual-approval exercise ran first, this exercise starts at reserved BD 1,280,000/headroom BD 720,000. An approval winner changes these to BD 1,880,000/BD 120,000. A rejection winner leaves BD 1,280,000/BD 720,000 unchanged. Exactly one decision is expected in either outcome.

**Separate cancellation:** main RM `aliaburashid@hotmail.com` can demonstrate cancellation of existing RESERVED request **#165, BD 15,000**, on Al Dana Petrochemicals, facility **33**. Recheck its state first. Expected result: CANCELLED, facility reserved 15,000 → 0 and headroom 2,485,000 → 2,500,000, used unchanged. This is outside the race groups and does not disturb their headroom. No cancellation was performed during the audit.

**Repeatability:** Use a *different pending request* for the next attempt; do not reset completed requests. The prepared requests provide one dual-approval attempt and one approval/rejection attempt; additional repeats require additional approved pending requests or normal live creation with sufficient headroom.

## 4. Scenario C — Two RMs compete for a shared corporate-group limit

**Prepared live fixture:** existing **Gulf Investment Group, group 3, shared limit 2**, at Citi, approved group limit **BD 2,000,000**. Its current exposure is BD 675,000, all reserved on Gulf Meridian. Citi facilities **79** for existing **Falcon Freight Services (company 5)** and **80** for **Bahrain Gateway Warehousing (company 6)** each have a BD 1,000,000 limit. Their opening used balances of BD 300,000 and BD 225,000 are backed by historical USED requests **403** and **404**. Opening group used is BD 525,000 and reserved stays BD 675,000: exposure **BD 1,200,000**, headroom **BD 800,000**. All pre-existing facility amounts/balances were preserved by seeding.

**Shared resource:** Existing `GroupCreditLimit` **2** spans Falcon Freight and Bahrain Gateway (and the other two current members), even though each company has its own `CreditLimit`. Individual headrooms are BD 700,000 and BD 775,000, so both BD 450,000 requests individually fit.

**Unsafe outcome:** Both RMs independently see BD 800,000 available and each reserve BD 450,000, creating BD 900,000 of new exposure and exceeding group capacity by BD 100,000.

**Expected safe outcome:** One BD 450,000 request becomes `RESERVED`; the other is refused because only BD 350,000 remains. Final group exposure BD 1,650,000 and headroom BD 350,000.

**Walkthrough:** Main RM `aliaburashid@hotmail.com` chooses Falcon Freight; second RM `sara.alhaddad@example.com` chooses Bahrain Gateway. Select facility **79** for Falcon and **80** for Bahrain Gateway through the Exposure page’s company rows. Submit BD 450,000 almost together, live rather than pre-created. Verify exactly one reservation and final group used BD 525,000, reserved BD 1,125,000, total BD 1,650,000 and headroom BD 350,000.

**Repeat:** Cancel the successful reservation through the ordinary authorized UI, verify BD 450,000 headroom is restored, then repeat. Do not mark the winning request USED if you intend to repeat the same race.

**Recheck immediately before presenting:** this minimal setup includes the existing 600,000 reservation #402, whose stored expiry is `2026-10-30 09:37:07.643964`. Expiry would raise group headroom to 1,400,000 and allow both requests. Do not change its expiry or stop the scheduler. If it expires or another group balance changes, re-audit and approve revised additive history before relying on an 800,000 starting headroom.

## 5. Scenario D — Individual capacity versus group capacity

**Earlier optional proposal, outside the current minimal seed:** Pearl Infrastructure Group does not currently exist. The larger plan proposed group limit BD 2,000,000, exposure BD 1,600,000, **BD 400,000 group headroom**. Pearl Construction has BD 700,000 individual headroom; Pearl Engineering has BD 800,000.

An RM submits BD 450,000 for either company. The request fits the individual facility but **does not fit the group**. The create operation should reject it with a group-specific capacity explanation. No new reservation or group exposure should be recorded.

This is a **business-rule demonstration** and is distinct from a simultaneous race.

## 6. Scenario E — Same facility, two immediate reservations

**Illustrative case, not a pre-seeded fixture:** A company has BD 100,000 available. Two RMs submit BD 70,000 simultaneously. Because both amounts are within the BD 500,000 automatic-reservation threshold, only one may reserve. The other must observe the updated BD 30,000 headroom and fail. Use an authorized facility with suitable live capacity or a dedicated isolated test fixture.

## 7. Scenario F — Cancellation, usage and expiry

These are lifecycle consistency cases. Confirm actual role and state-transition permissions before demonstrating them:

- **Double cancellation:** Two requests to cancel the *same RESERVED request* must not release the reserved amount twice.
- **Double mark-as-used:** Two operations on the same RESERVED request must not transfer reserved exposure into used exposure twice.
- **Expiry versus use:** The expiry scheduler and an RM attempt to change the same reservation. Only a valid transition based on current locked state should take effect; no double release or use.
- **Approval versus cancellation:** This is outside the current walkthrough. The present cancellation service permits an owner to cancel a RESERVED request and rejects invalid source states. Use the separate RESERVED cancellation request documented above; do not imply pending cancellation is supported.

## 8. Parallel risk assessment: meaningful multithreading

`CounterpartyFinancialProfile` stores six ratios: return on assets, profit margin, fixed asset turnover, debt-to-total-capital percentage, interest coverage, and total debt-to-EBITDA. `MlRiskPredictionService` sends authorized inputs to the Python FastAPI inference endpoint. `ParallelRiskAssessmentService` uses an executor to coordinate group-member assessments; individual failures should be represented independently instead of being mistaken for successful predictions.

**Endpoint and authorization:** `POST /api/corporate-groups/{groupId}/risk-assessment`, no body, RISK_OFFICER. Risk Analytics now has an independent corporate-group selector and **Run group assessment** button. Select **Gulf Investment Group** to assess all four authorized Citi companies in one server batch. The existing single-company prediction remains available.

**Live verification:** after the approved facilities were inserted, a Citi RO received four successful results for group 3: Gulf Meridian, Pearl Coast, Falcon and Bahrain Gateway. Database fingerprints across all twelve tables stayed unchanged. Pearl Coast stays frozen: its saved profile can be assessed, while new credit requests remain blocked by its frozen status.

**Console logging:** the service now logs INFO `ML BATCH START`, `ML BATCH AUTHORIZED`, per-company `ML WORKER START` / `ML WORKER COMPLETE` (or `ML WORKER FAILED`), and `ML BATCH COMPLETE` with `Total elapsed ms`. Worker lines contain group, institution, company ID/name and thread name. Total elapsed time uses a monotonic clock and includes authorization, worker collection and executor cleanup. Pool size, submission, authorization, response DTOs, failure isolation and shutdown behavior are preserved. Logger tests verified four overlapping workers and failure isolation; live endpoint checks verified all four real results. A separate IntelliJ console screenshot was not obtained because computer access to IntelliJ was not approved; no screenshot evidence is claimed.

For presenting: keep the existing IntelliJ Run console visible, open Risk Analytics, select Gulf Investment Group, and click **Run group assessment**. The UI displays actual per-company success/failure and positive-class scores. Worker names normally appear as `pool-N-thread-1` through `pool-N-thread-4`; the caller uses `http-nio-8080-exec-*`, while email uses `notification-*`. The UI does not invent a server duration when the response does not supply one.

**Why a pool?** External prediction calls can spend time waiting for responses. A bounded executor can overlap independent work without creating unlimited threads. The actual throughput depends on executor settings and Python service capacity; no benchmark speedup is claimed without measurements.

**Important:** The model returns a positive-class score. Unless class mapping is verified, do not label that score a validated probability of default.

## 9. Asynchronous email and SSE

Email notifications are scheduled after successful banking decisions using Spring async execution. SSE notifies connected frontend clients about credit-request status and other exposure changes.

For a concurrent approval race, separate the evidence:
1. **Database:** exactly one committed decision and correct balances.
2. **Application dispatch:** notification scheduling / mocked sender verification.
3. **SMTP:** server acceptance, if logs permit verification.
4. **Inbox:** actual receipt in the recipient's mailbox.

Mocked sender tests cannot prove delivery. A losing concurrent operation should not trigger a second winning-decision notification.


## 10. Corporate Group Management in the normal frontend

Open **Limits & exposure**. The page reads groups, memberships, companies and bank-scoped shared limits from the existing APIs. Opening or refreshing the page performs reads only.

**Admin / Risk Officer workflow:**
1. Click **Create corporate group**, enter a name and confirm. A group is created only on explicit form submission.
2. Select the group and choose **Add company**. Choose an existing unassigned company and confirm. Already grouped companies are excluded; the backend enforces membership rules and returns any conflicts.
3. Click **Set shared limit**, enter the amount and confirm. The existing API derives the financial institution from the authenticated user. Existing limits use **Update shared limit**; backend reduction/headroom validation remains authoritative.
4. Review the complete member list, company status, individual limit, used/reserved exposure and available individual headroom. The group card displays the backend's total committed exposure and remaining shared headroom.

The backend has no individual-facility directory. RO facility details are discovered from the authorized institution request history; RM details use their own request history. Admin does not call the RO-only search endpoint. For any undiscovered facility, **Find facility** focuses the in-app lookup; enter its facility ID. The response must match the selected company and pass existing backend institution authorization before the row is populated. Missing detail is labelled unavailable, not zero. Every group total comes from the backend even when some individual details are undiscovered.

**Two-RM walkthrough without Postman:**
1. Use separate browser profiles for main RM `aliaburashid@hotmail.com` and second RM `sara.alhaddad@example.com`.
2. Each opens **Limits & exposure → Gulf Investment Group**. Confirm shared headroom is **BD 800,000** immediately before starting.
3. Main RM clicks **Request credit** on Falcon Freight, facility **79**. Second RM does so on Bahrain Gateway, facility **80**. Their own historical records make these choices available naturally.
4. Enter **450000** in both forms. Both individual facilities have room. Submit **Confirm create credit request** nearly together. These amounts use the existing immediate reservation path.
5. One request should become RESERVED and the other should show the backend corporate-group headroom error. Both the successful page and the failed form immediately re-read capacity; the expected shared headroom is **BD 350,000** and exposure **BD 1,650,000**. SSE also refreshes other connected sessions.

The failed form stays open, retains the real backend error and displays refreshed individual/group figures. No request is automatically retried. The form does not replace server validation with an optimistic client balance. Frozen companies cannot be selected for a new request from the group table. RM users have no group-management mutation controls.

Frontend tests use isolated mocked state, including two separate browser contexts sharing one mocked group ledger. They verify group/member/limit payloads, both management roles, role restrictions, missing-facility lookup, duplicate/reduction errors, and one reservation plus one failed competing request with refreshed headroom. This establishes the frontend workflow; it does not consume the live prepared data or substitute for the live PostgreSQL race.

[← Return to the main README](../README.md)


## 11. Verification for the completed implementation

- Backend: **54 tests passed**, including the original non-PostgreSQL-concurrency tests, minimal seed definitions/default-disable checks and parallel-worker logging/failure-isolation checks. The dedicated PostgreSQL create-drop concurrency suite was preserved and not run against presentation data.
- Frontend: production build and lint passed; **31 unit tests and 27 browser tests passed**. Browser tests cover Admin/RO group creation, membership, shared limits, backend errors, facility lookup, mobile layout, all four SSE types, two separate RM sessions and refreshed shared headroom after a rejected competing request.
- Live read-only API checks verified all four group companies and their actual facilities, group exposure **1,200,000** / headroom **800,000**, untouched pending requests **405/406**, cancellation eligibility of **165**, and four successful ML results. All twelve database table fingerprints stayed unchanged during those API checks.
- No live approval, rejection, reservation race or cancellation was executed by verification. Existing banking services, locks, five-second delay, security and database configuration remain unchanged. No additional live groups or memberships were created for the frontend implementation.
