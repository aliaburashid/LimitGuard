# LimitGuard — Concurrency and live demonstration guide

[← Back to the main README](../README.md)

These scenarios describe the concurrency controls now exercised through Credit Service. For live demonstrations, use authorized demo accounts and recheck fixture eligibility and balances first. The [original seed record](../SEED_PLAN.md) is a historical reference.

## 1. Why race conditions matter in banking

A race condition occurs when concurrent operations act on shared state and the result depends on timing. In LimitGuard the shared state includes a `CreditRequest` status, an individual `CreditLimit`'s used/reserved balances, and a `GroupCreditLimit` shared by multiple subsidiaries. Two concurrent operations must not each spend the same available capacity.

Spring MVC/Tomcat can process requests on different worker threads. PostgreSQL row locks inside Spring-managed transactions serialize conflicting changes to persisted records. Java `ReentrantLock` alone would not coordinate two separately running application instances against the same database.

**Safety is provided by transactional locking and status/headroom rechecks, not by the five-second `Thread.sleep(5000)` demonstration delay in approval.**

## 2. Scenario A — Two Risk Officers approve the same request

**Business problem:** Both ROs may read `PENDING_APPROVAL` and attempt to approve the same BD 600,000 request.

**Shared resource:** The credit request row, its approval decision, and the affected individual/group capacity.

**Without protection:** Duplicate approval records, repeated reservation or an inconsistent state.

**With protection:** The first transaction locks the request, validates it, reserves capacity and commits `RESERVED` plus one decision. The second waits, then sees that the request is no longer pending and must fail without a second reservation.

**Walkthrough:** Use two separate browser sessions for authorized Risk Officers at the same bank. Open the same eligible pending request and approve nearly simultaneously. Compare the final status, one approval decision, and the exact change in reserved exposure with the starting balances.

## 3. Scenario B — Approval versus rejection

Use a different eligible pending request. One Risk Officer approves and the other rejects with a reason. The request lock allows one decision. The final state is either `RESERVED` with one approved decision and one reservation, or `REJECTED` with one rejected decision and no new reservation. Use a new pending fixture to repeat the exercise.

## 4. Scenario C — Two RMs compete for a shared corporate-group limit

Choose two subsidiaries with separate facilities under the same bank's group limit. For example, with BHD 800,000 in group headroom and at least BHD 450,000 in individual headroom at each facility, submit two BHD 450,000 requests together from separate RM sessions. Exactly one should reserve. The other should see the revised group headroom of BHD 350,000 and be refused. Recheck balances and pending expiries immediately before the race.

## 5. Scenario D — Individual capacity versus group capacity

**Illustrative fixture:** A group limit of BHD 2,000,000 and exposure of BHD 1,600,000 leave BHD 400,000 in group headroom. Each of two member facilities has at least BHD 700,000 in individual headroom.

An RM submits BD 450,000 for either company. The request fits the individual facility but **does not fit the group**. The create operation should reject it with a group-specific capacity explanation. No new reservation or group exposure should be recorded.

This is a **business-rule demonstration** and is distinct from a simultaneous race.

## 6. Scenario E — Same facility, two immediate reservations

**Illustrative case, not a pre-seeded fixture:** A company has BD 100,000 available. Two RMs submit BD 70,000 simultaneously. Because both amounts are within the BD 500,000 automatic-reservation threshold, only one may reserve. The other must observe the updated BD 30,000 headroom and fail. Use an authorized facility with suitable live capacity or a dedicated isolated test fixture.

## 7. Scenario F — Cancellation, usage and expiry

These are lifecycle consistency cases. Confirm actual role and state-transition permissions before demonstrating them:

- **Double cancellation:** Two requests to cancel the *same RESERVED request* must not release the reserved amount twice.
- **Double mark-as-used:** Two operations on the same RESERVED request must not transfer reserved exposure into used exposure twice.
- **Expiry versus use:** The expiry scheduler and an RM attempt to change the same reservation. Only a valid transition based on current locked state should take effect; no double release or use.
- **Approval versus cancellation:** This is outside the current walkthrough. The present cancellation service permits an owner to cancel a RESERVED request and rejects invalid source states. Use an eligible reservation owned by the acting RM for a standalone cancellation demonstration.

## 8. Parallel risk assessment: meaningful multithreading

`CounterpartyFinancialProfile` stores six ratios: return on assets, profit margin, fixed asset turnover, debt-to-total-capital percentage, interest coverage, and total debt-to-EBITDA. `MlRiskPredictionService` sends authorized inputs to the Python FastAPI inference endpoint. `ParallelRiskAssessmentService` uses an executor to coordinate group-member assessments; individual failures should be represented independently instead of being mistaken for successful predictions.

**Endpoint and authorization:** `POST /api/corporate-groups/{groupId}/risk-assessment`, no body, RISK_OFFICER. Risk Analytics now has an independent corporate-group selector and **Run group assessment** button. Select **Gulf Investment Group** to assess all four authorized Citi companies in one server batch. The existing single-company prediction remains available.

**Console logging:** Use the Credit Service console on port 8082. Look for `ML BATCH START`, `ML BATCH AUTHORIZED`, per-company `ML WORKER START`, `ML WORKER COMPLETE` or `ML WORKER FAILED`, and `ML BATCH COMPLETE` with `Total elapsed ms`. Worker logs include the thread name. The [Credit ML integration test](../credit-service/src/test/java/com/example/creditservice/service/MlEndpointIntegrationTest.java) checks overlapping workers and recovery of successful results when one company fails.

**Why a pool?** External prediction calls can spend time waiting for responses. A bounded executor can overlap independent work without creating unlimited threads. The actual throughput depends on executor settings and Python service capacity; no benchmark speedup is claimed without measurements.

**Important:** The model returns a positive-class score. Unless class mapping is verified, do not label that score a validated probability of default.

## 9. Asynchronous email and SSE

Credit commits the banking decision and its notification outbox row in the same PostgreSQL transaction. The relay publishes committed rows to Kafka's `credit-request-events` topic and marks publication after broker acknowledgement. A rolled-back decision leaves no committed notification. Publication can be retried, so the same event may be delivered more than once.

Activity's SSE consumer routes notifications to connected users in the event's institution. A separate optional Kafka consumer queues approval, rejection, and expiry emails for the verified requester when eligible. The queue deduplicates by event ID. An independently enabled delivery worker sends via SMTP. Both email components default to disabled. Auth account emails use a separate Auth SMTP path.

For a race demonstration, capture evidence at each boundary:

1. **PostgreSQL:** one committed decision, correct balances, and the corresponding outbox event.
2. **Kafka:** broker publication and Activity consumer progress.
3. **SSE:** the relevant authenticated browser refreshes or displays a notification. See the [Activity SSE API reference](ACTIVITY_SSE.md).
4. **Optional email:** verify the delivery record, SMTP outcome, and receipt in the intended test inbox separately.

## 10. Corporate Group Management in the normal frontend

Open **Limits & exposure**. The page reads groups, memberships, companies and bank-scoped shared limits from the existing APIs. Opening or refreshing the page performs reads only.

**Admin / Risk Officer workflow:**
1. Click **Create corporate group**, enter a name and confirm. A group is created only on explicit form submission.
2. Select the group and choose **Add company**. Choose an existing unassigned company and confirm. Already grouped companies are excluded; the backend enforces membership rules and returns any conflicts.
3. Click **Set shared limit**, enter the amount and confirm. The existing API derives the financial institution from the authenticated user. Existing limits use **Update shared limit**; backend reduction/headroom validation remains authoritative.
4. Review the complete member list, company status, individual limit, used/reserved exposure and available individual headroom. The group card displays the backend's total committed exposure and remaining shared headroom.

Credit exposes a paginated, institution-scoped facility directory at `GET /api/credit-limits` and individual exposure at `GET /api/credit-limits/{id}/exposure`. Use two authorized RM sessions for the shared-capacity demonstration and confirm both companies' individual headrooms and the group headroom before submitting.

The failed form stays open, retains the real backend error and displays refreshed individual/group figures. No request is automatically retried. The form does not replace server validation with an optimistic client balance. Frozen companies cannot be selected for a new request from the group table. RM users have no group-management mutation controls.

Frontend tests use isolated mocked state, including two separate browser contexts sharing one mocked group ledger. They verify group/member/limit payloads, both management roles, role restrictions, missing-facility lookup, duplicate/reduction errors, and one reservation plus one failed competing request with refreshed headroom. This establishes the frontend workflow; it does not consume the live prepared data or substitute for the live PostgreSQL race.

[← Return to the main README](../README.md)


## 11. Automated verification

- [Credit PostgreSQL concurrency suite](../credit-service/src/test/java/com/example/creditservice/service/CreditRequestConcurrencyIntegrationTest.java) covers competing approvals, rejection, cancellation, use, expiry and capacity changes.
- [Scheduled expiry feature](../credit-service/src/test/resources/features/automatic-reservation-expiry.feature) exercises automatic reservation expiry.
- [Activity SSE tests](../activity-service/src/test/java/com/example/activityservice/service/CreditRequestEventServiceTest.java) cover tenant-scoped delivery, Auth failures, and disconnected emitter cleanup.
- [Frontend workspace tests](../frontend/tests/workspace.spec.ts) exercise UI refresh and notification handling with mocked responses.

Use the [local setup runbook](../README.md#running-the-project-locally) for test database requirements.
