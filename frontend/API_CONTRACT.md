# API and role map

Base prefix `/api`. Authenticated requests use `Authorization: Bearer <JWT>`. Login returns `{ message: JWT }`. Profile is the source for frontend role display. RM = RELATIONSHIP_MANAGER; Risk = RISK_OFFICER. Administration is not a superset of the Risk/RM permissions.

| Endpoint | Method | Access | Request / response |
| --- | --- | --- | --- |
| /auth/users/register | POST | Public | firstName, lastName, email, password, financialInstitutionId / RegisterResponse |
| /auth/users/login | POST | Public | email, password / message (JWT) |
| /auth/users/verify-email?token=… | GET | Public | token query / text |
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
| /credit-limits | POST | Risk | financialInstitutionId, counterpartyId, limitAmount / CreditLimitResponse |
| /credit-limits/{id} | PUT | Risk | limitAmount / CreditLimitResponse |
| /credit-limits/{id}/exposure | GET | RM, Risk, Admin | CreditExposureResponse |
| /credit-requests | POST | RM | creditLimitId, amount / CreditRequestResponse |
| /credit-requests/my | GET | RM | page, size, sortBy, direction / Page<CreditRequestResponse> |
| /credit-requests/{id} | GET | RM, Risk | CreditRequestResponse, subject to institution and ownership rules |
| /credit-requests/{id}/use | PATCH | RM | no body / CreditRequestResponse |
| /credit-requests/{id}/cancel | PATCH | RM | no body / CreditRequestResponse |
| /credit-requests/pending | GET | Risk | Pageable / Page<CreditRequestResponse> |
| /credit-requests/{id}/review | GET | Risk | CreditRequestReviewResponse |
| /credit-requests/{id}/approve | PATCH | Risk | no body / CreditRequestResponse |
| /credit-requests/{id}/reject | PATCH | Risk | reason / CreditRequestResponse |
| /credit-requests/events | GET | Authenticated | SSE event credit-request-status; creditRequestId, status |
| /financial-institutions | POST | Admin | name, status / FinancialInstitutionResponse |
| /financial-institutions/{id} | PUT | Admin | name, status / FinancialInstitutionResponse |
| /audit-logs | GET | Admin | Pageable; createdAt descending / Page<AuditLogResponse> |

Counterparty status: ACTIVE, FROZEN, CLOSED. Institution status: ACTIVE, INACTIVE. Request status: PENDING_APPROVAL, RESERVED, USED, REJECTED, CANCELLED, EXPIRED. Only RESERVED requests expose RM use/cancel actions; only pending requests expose Risk approve/reject. The frontend does not compute approval eligibility or override service rules.

Spring ErrorResponse contains timestamp, status, error, message, path. Only safe messages are displayed. 401 invalidates the local session; 403 is a permissions error; server faults show a generic retry message.

Credit request responses retain existing IDs, amounts, timestamps, and statuses and add `counterpartyName`, `counterpartyStatus`, `requesterName`, `financialInstitutionId`, and `financialInstitutionName`. Review responses additionally include `expiresAt`, `counterpartyStatus`, and institution ID/name. Requester entities, email addresses, and credentials are not returned.

All request list/search/detail/review/action endpoints and credit-limit creation/update/exposure are scoped to the authenticated account's financial institution. ADMIN has the same institution boundary for exposure and does not gain RM or Risk permissions. RM requests also require requester ownership. The existing `financialInstitutionId` field for limit creation must match the authenticated institution; the backend uses the authenticated institution to create the limit. Scheduled expiry remains an internal process across institutions.

The frontend displays monetary amounts in BHD as whole dinars (for example, `BD 750,000`) and request references as `CR-000028`. Display rounding does not change API values or backend BigDecimal calculations; mutation amounts remain decimal strings.

SSE subscriptions send an initial `:connected` comment to flush the response immediately. The frontend ignores SSE comments and uses the response to show live connection status. Internal ASYNC and ERROR dispatches are permitted after the original request security checks; normal API requests remain authenticated.

The counterparty register remains shared under the existing schema. SSE event scoping is still pending the approved later security increment; authenticated subscribers currently receive global status events.
