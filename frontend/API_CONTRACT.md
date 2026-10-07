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
| /credit-requests/{id} | GET | RM, Risk | CreditRequestResponse, subject to service ownership rules |
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
