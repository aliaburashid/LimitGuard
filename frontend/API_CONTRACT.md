# API and role map

Base prefix `/api`. Authenticated requests use `Authorization: Bearer <JWT>`. Login returns `{ message: JWT }`. Profile is the source for frontend role display. RM = RELATIONSHIP_MANAGER; Risk = RISK_OFFICER. Administration is not a superset of the Risk/RM permissions.

| Endpoint | Method | Access | Request / response |
| --- | --- | --- | --- |
| /financial-institutions/directory | GET | Public | Active institutions ordered by name and ID / array of `{ id, name }`; empty array when none exist |
| /auth/sso/providers | GET | Public | Configured provider availability / `{ google, microsoft }` booleans |
| /auth/sso/exchange | POST | Public, completed OAuth session required | JSON `{}` / existing `{ message: JWT }`, single use, expires after 60 seconds |
| /auth/users/register | POST | Public | firstName, lastName, email, password, financialInstitutionId / RegisterResponse with `verification: { challengeId, email, expiresAt, resendAfter }`; six-digit code sent by email |
| /auth/users/login | POST | Public | email, password / message (JWT) |
| /auth/users/verification-code/verify | POST | Public, rate-limited | challengeId, code (six-digit string) / message; 400 on invalid, expired, used or exhausted code |
| /auth/users/verification-code/resend | POST | Public, rate-limited | challengeId / new challenge timing; 429 before 60 seconds |
| /auth/users/verification-code/change-email | POST | Public, rate-limited | challengeId, email / rotated challenge and new code; only pending accounts |
| /auth/users/verification-code/request | POST | Public, rate-limited | email, password / resume pending verification after checking password |
| /auth/users/verify-email?token=… | GET | Public | token query / text |
| /auth/users/password-reset-code/request | POST | Public, rate-limited | email / `{ challengeId, email, expiresAt, resendAfter }`; code emailed for active accounts |
| /auth/users/password-reset-code/resend | POST | Public, rate-limited | challengeId / replacement code timing; 60-second cooldown |
| /auth/users/password-reset-code/verify | POST | Public, rate-limited | challengeId, six-digit code / `{ token }` for the existing reset-password endpoint; token expires in 5 minutes |
| /auth/users/forgot-password | POST | Public | email / text |
| /auth/users/reset-password | POST | Public | token, newPassword / text |
| /auth/users/change-password | PATCH | Authenticated | currentPassword, newPassword / text |
| /auth/users/profile | GET/PATCH | Authenticated | PATCH firstName, lastName, email / UserProfileResponse |
| /auth/users/profile-picture | PATCH | Authenticated | multipart image / UserProfileResponse |
| /auth/users/{id}/role | PATCH | Admin | role / text |
| /auth/users/{id}/deactivate | PATCH | Admin | reason / text |
| /counterparties | GET | RM, Risk, Admin | name, page, size, sort query / Page<CounterpartyResponse> |
| /counterparties/{id} | GET | RM, Risk, Admin | CounterpartyResponse |
| /counterparties | POST | Risk | name / CounterpartyResponse |
| /counterparties/{id} | PUT | Risk | name / CounterpartyResponse |
| /counterparties/{id}/status | PATCH | Risk | status / CounterpartyResponse |
| /credit-limits | GET | RM, Risk, Admin | Paginated institution facility directory. Each item has `exposure` (CreditExposureResponse), `name`, and `status`. |
| /credit-limits | POST | Risk | financialInstitutionId, counterpartyId, limitAmount / CreditLimitResponse |
| /credit-limits/{id} | PUT | Risk | limitAmount / CreditLimitResponse |
| /credit-limits/{id}/exposure | GET | RM, Risk, Admin | CreditExposureResponse |
| /credit-limits/exposure-history | GET | RM | Optional includeCurrent=true / array of individual facility observations with capturedAt (UTC), limitAmount, usedAmount, reservedAmount, synthetic, and current=true on the optional live reading. Scoped to the RM’s institution and own request facilities. Missing dates are omitted. The live reading is never stored as history. |
| /credit-requests | POST | RM | creditLimitId, amount / CreditRequestResponse |
| /credit-requests/my | GET | RM | page, size, sortBy, direction / Page<CreditRequestResponse> |
| /credit-requests/{id} | GET | RM, Risk | CreditRequestResponse, subject to institution and ownership rules |
| /credit-requests/{id}/use | PATCH | RM | no body / CreditRequestResponse |
| /credit-requests/{id}/cancel | PATCH | RM | no body / CreditRequestResponse |
| /credit-requests/pending | GET | Risk | Pageable / Page<CreditRequestResponse> |
| /credit-requests/{id}/review | GET | Risk | CreditRequestReviewResponse |
| /credit-requests/{id}/approve | PATCH | Risk | no body / CreditRequestResponse |
| /credit-requests/{id}/reject | PATCH | Risk | reason / CreditRequestResponse |
| /credit-requests/events | GET | Authenticated, institution-scoped | SSE events `credit-request-status`, `counterparty-status`, `credit-limit-updated`, `group-credit-limit-updated` (payloads below). Only events belonging to the authenticated user's financial institution are delivered. |
| /financial-institutions | POST | Admin | name, status / FinancialInstitutionResponse |
| /financial-institutions/{id} | PUT | Admin | name, status / FinancialInstitutionResponse |
| /audit-logs | GET | Admin | Pageable; createdAt descending / Page<AuditLogResponse> |

Counterparty status: ACTIVE, FROZEN, CLOSED. Institution status: ACTIVE, INACTIVE. Request status: PENDING_APPROVAL, RESERVED, USED, REJECTED, CANCELLED, EXPIRED. Only RESERVED requests expose RM use/cancel actions; only pending requests expose Risk approve/reject. The frontend does not compute approval eligibility or override service rules.

Spring ErrorResponse contains timestamp, status, error, message, path. Only safe messages are displayed. 401 invalidates the local session; 403 is a permissions error; server faults show a generic retry message.

Credit request responses retain existing IDs, amounts, timestamps, and statuses and add `counterpartyName`, `counterpartyStatus`, `requesterName`, `financialInstitutionId`, and `financialInstitutionName`. Review responses additionally include `expiresAt`, `counterpartyStatus`, and institution ID/name. Requester entities, email addresses, and credentials are not returned.

All request list/search/detail/review/action endpoints and credit-limit creation/update/exposure are scoped to the authenticated account's financial institution. ADMIN has the same institution boundary for exposure and does not gain RM or Risk permissions. RM requests also require requester ownership. The existing `financialInstitutionId` field for limit creation must match the authenticated institution; the backend uses the authenticated institution to create the limit. Scheduled expiry remains an internal process across institutions.

The frontend displays monetary amounts in BHD as whole dinars (for example, `BD 750,000`) and request references as `CR-000028`. Display rounding does not change API values or backend BigDecimal calculations; mutation amounts remain decimal strings.

SSE subscriptions send an initial `:connected` comment to flush the response immediately. The frontend ignores SSE comments and uses the response to show live connection status. Internal ASYNC and ERROR dispatches are permitted after the original request security checks; normal API requests remain authenticated.

The counterparty register remains shared under the existing schema. SSE notifications are scoped by financial institution. Authenticated users subscribe to their own institution's events, and credit request status updates are delivered only to subscribers belonging to that institution. Cross-institution event isolation is covered by backend tests.


## Integrated corporate and ML APIs

- `GET /corporate-group-memberships`: membership ID, corporateGroupId, corporateGroupName, counterpartyId; all authenticated staff roles listed above.
- `GET /group-credit-limits`: bank-scoped paginated directory with id, corporateGroupId, corporateGroupName, financialInstitutionId, amount.
- `GET /group-credit-limits/groups/{corporateGroupId}/exposure` and `/headroom`: JSON monetary numbers; calculated by the backend.
- `PUT /group-credit-limits/{id}`: ADMIN or RISK_OFFICER, `{limitAmount}`.
- `GET /counterparties/{id}/financial-profile`: saved six ratios, year end and timestamps; authorized staff roles.
- `POST /counterparties/{id}/risk-prediction`: RISK_OFFICER only, no body; `{probability, riskLevel, modelVersion}`. No prediction timestamp or confidence/explanation is supplied. The UI labels its local response-receipt time accordingly.
- `GET /credit-requests/search`: RISK_OFFICER; optional status, counterpartyId, requesterId and pagination. Used for institution request history and recent outcomes.

SSE payloads:

- `credit-request-status`: `{creditRequestId, status}`, including USED in this implementation. RESERVED does not identify whether reservation was immediate or approved, so its toast says “reserved successfully”.
- `counterparty-status`: `{counterpartyId, status}`.
- `credit-limit-updated`: `{creditLimitId, oldLimitAmount, newLimitAmount}`.
- `group-credit-limit-updated`: `{groupCreditLimitId, corporateGroupId, oldLimitAmount, newLimitAmount}`.

The fetch-based stream uses a captured session bearer token in the Authorization header, validates content type and payloads, parses chunked CRLF events, reconnects with exponential backoff, stops on 401/403, and aborts on logout. Session-owned UI state is remounted on account changes. Toasts are delivered globally while signed in, with bounded history and short-window duplicate suppression. Data refresh is debounced, reconciles after reconnect, and runs periodically while visible because the server supplies no replay IDs/history. Updates received during a disconnection cannot be reconstructed as historical toasts.

There is no individual credit-limit listing endpoint. Facility choices use the authenticated RM's request records; facilities without request history can be accessed by credit-limit ID. Current exposure is always fetched from the API. Group totals are labelled as group totals and exclude ungrouped limits. The company directory is shared by the existing schema. Industry and rejection/decision reasons are not present in the existing response DTOs; detailed audit access remains ADMIN-only.

The request directory follows all pages before calculating status counts; it never treats the first page as an institution total. All authorized records appear in the normal application without dataset filters or imported files.


## Corporate group management

- `GET /corporate-groups`: paginated shared group directory. `POST /corporate-groups` with `{name}` creates a group; ADMIN or RISK_OFFICER.
- `GET /counterparties`: existing paginated company directory. The UI selects existing unassigned counterparties.
- `POST /corporate-group-memberships` with `{corporateGroupId, counterpartyId}` adds membership; ADMIN or RISK_OFFICER. Existing membership conflicts remain backend errors.
- `POST /group-credit-limits` with `{corporateGroupId, limitAmount}` sets a shared limit for the authenticated user's institution; ADMIN or RISK_OFFICER. `PUT /group-credit-limits/{id}` sends `{limitAmount}` for updates.
- RM company-row actions use the existing `POST /credit-requests` with `{creditLimitId, amount}`. Exposure and shared headroom are reloaded after success or rejection and on SSE events. No automatic POST retry occurs.

No new backend endpoints or authorization rules were added. Admin never calls the RO-only request search. Individual facility discovery uses permitted request history and explicit in-app ID lookups because no complete directory endpoint exists; group totals remain backend-authoritative. Missing individual values are not synthesized as zero. The lookup verifies the returned counterparty before attaching the facility to a group row. No groups, memberships, limits, requests or seed data are created on mount.


New registrations use email codes instead of links. Codes expire in 10 minutes, permit at most five guesses and become unusable after successful verification or resend. Only a keyed HMAC is stored in the new `email_verification_codes` table; the raw code is sent only to the mail service. Verification/resend actions lock the current challenge to serialize concurrent requests. A 60-second resend cooldown applies to the pending registration, in addition to existing per-IP request limits. Correct email/password credentials can reopen an unverified account's challenge after browser storage is lost. Existing verification-token GET URLs and password-reset links remain supported for previously sent emails. No banking data or existing account verification states are migrated.

Pending challenge identifiers and email/timing are held in browser session storage to restore the screen after reload; no password or entered code is stored. Successful verification returns to email login and never automatically signs in.

For legacy registrations with an outstanding verification link, pending email correction is refused without altering the link. This preserves existing links and prevents them from verifying a different email address. New code-only registrations support email correction normally.

The Forgot Password UI now requests a six-digit email code, verifies it, and then shows the new-password form. Password-recovery codes use a separate purpose and table from registration codes. Codes expire after 10 minutes, allow five guesses, and are replaced on resend after a 60-second cooldown. Unknown or inactive email addresses follow the same response shape without sending email or creating reset tokens. The reset token is returned only after successful code verification, stays in browser component memory, expires after five minutes, and is consumed by the existing password-reset service under a row lock. Pending code metadata can restore after reload; a reset token is never persisted or shown as an editable field. Existing password-reset token callers remain supported.
