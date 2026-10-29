# LimitGuard — Concurrency and live demonstration guide

[← Back to the main README](../README.md)

This document extends the Unit 2 LimitGuard project with Unit 3 concurrency, parallel risk assessment and shared corporate-group capacity. It distinguishes **implemented mechanisms**, **proposed seed fixtures**, **expected outcomes**, and **live evidence still to collect**.

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
3. Open the **same** seeded pending BD 600,000 request ID in both profiles.
4. Click **Approve & Reserve** nearly simultaneously.
5. Verify final status `RESERVED`, **one** approval decision, and **exactly BD 600,000** additional reserved exposure (not BD 1,200,000). Record the losing operation's actual response.

**Evidence:** two session screenshots, final request/decision record, before/after limit balances and relevant logs. Expected behavior is not a substitute for a live race test.

## 3. Scenario B — Approval versus rejection

**Shared resource:** One pending BD 600,000 request and its decision record.

Open the same pending request in both RO sessions. One clicks Approve, the other Reject nearly simultaneously. The request lock ensures one valid terminal decision for this approval stage. The final state should be either `RESERVED` with one APPROVED decision and one reservation, or `REJECTED` with one REJECTED decision and no reservation. The losing action must not create a second decision.

**Repeatability:** Use a *different pending request* for the next attempt; do not reset completed requests.

## 4. Scenario C — Two RMs compete for a shared corporate-group limit

**Proposed fixture:** Arabian Energy Group, approved group limit BD 2,000,000; opening used exposure BD 1,200,000; **BD 800,000 group headroom**.

**Shared resource:** One `GroupCreditLimit` spanning Arabian Renewable Energy and Arabian Industrial Utilities, even though each company has its own `CreditLimit`.

**Unsafe outcome:** Both RMs independently see BD 800,000 available and each reserve BD 450,000, creating BD 900,000 of new exposure and exceeding group capacity by BD 100,000.

**Expected safe outcome:** One BD 450,000 request becomes `RESERVED`; the other is refused because only BD 350,000 remains. Final group exposure BD 1,650,000 and headroom BD 350,000.

**Walkthrough:** Open separate RM sessions for `aliaburashid@hotmail.com` and `sara.alhaddad@example.com`; choose different subsidiaries; submit BD 450,000 almost together. Verify exactly one reservation and the final group totals.

**Repeat:** Cancel the successful reservation through the ordinary authorized UI, verify BD 450,000 headroom is restored, then repeat. Do not mark the winning request USED if you intend to repeat the same race.

## 5. Scenario D — Individual capacity versus group capacity

**Proposed fixture:** Pearl Infrastructure Group, group limit BD 2,000,000, exposure BD 1,600,000, **BD 400,000 group headroom**. Pearl Construction has BD 700,000 individual headroom; Pearl Engineering has BD 800,000.

An RM submits BD 450,000 for either company. The request fits the individual facility but **does not fit the group**. The create operation should reject it with a group-specific capacity explanation. No new reservation or group exposure should be recorded.

This is a **business-rule demonstration** and is distinct from a simultaneous race.

## 6. Scenario E — Same facility, two immediate reservations

**Illustrative case, not a pre-seeded fixture:** A company has BD 100,000 available. Two RMs submit BD 70,000 simultaneously. Because both amounts are within the BD 500,000 automatic-reservation threshold, only one may reserve. The other must observe the updated BD 30,000 headroom and fail. Use an authorized facility with suitable live capacity or a dedicated isolated test fixture.

## 7. Scenario F — Cancellation, usage and expiry

These are lifecycle consistency cases. Confirm actual role and state-transition permissions before demonstrating them:

- **Double cancellation:** Two requests to cancel the *same RESERVED request* must not release the reserved amount twice.
- **Double mark-as-used:** Two operations on the same RESERVED request must not transfer reserved exposure into used exposure twice.
- **Expiry versus use:** The expiry scheduler and an RM attempt to change the same reservation. Only a valid transition based on current locked state should take effect; no double release or use.
- **Approval versus cancellation:** Use a pending amount **above BD 500,000**, not BD 300,000 (which would ordinarily auto-reserve). Whether pending cancellation is allowed and what happens after approval must be verified against the current service rules. Do not promise that a UI action is available without checking its authorization.

## 8. Parallel risk assessment: meaningful multithreading

`CounterpartyFinancialProfile` stores six ratios: return on assets, profit margin, fixed asset turnover, debt-to-total-capital percentage, interest coverage, and total debt-to-EBITDA. `MlRiskPredictionService` sends authorized inputs to the Python FastAPI inference endpoint. `ParallelRiskAssessmentService` uses an executor to coordinate group-member assessments; individual failures should be represented independently instead of being mistaken for successful predictions.

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

[← Return to the main README](../README.md)
