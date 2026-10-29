# Risk Officer workspace

The professional overview and dedicated ML Risk Analytics page use the normal authenticated backend APIs. Recharts powers the exposure, request-status and corporate-group charts. No imported company inventory, dataset filter, or separate environment is required.

Individual headroom is limit minus used and reserved exposure. Group capacity is retrieved separately from the backend. Pending requests do not reserve exposure. Facilities are deduplicated by credit-limit ID and discovered from authorized request history; overview totals explicitly describe this scope. The backend currently has no complete individual-facility directory. Unused facilities can be accessed by ID in Credit exposure and Create credit request.

ML predictions use the saved six-ratio financial profile and the existing prediction endpoint. The UI labels the output Class 1 probability because the supplied model metadata does not establish a business definition for class 1. The response supplies a category and model version, but no confidence, explanation, or server prediction timestamp. Received locally identifies the browser response time. Changed financial inputs mark an earlier prediction stale.

The approval queue opens the dedicated assessment page. Decisions require confirmation and rejection requires a reason. The UI displays actual backend responses and conflicts. All four SSE event types remain supported with global pop-up notifications, bounded history, duplicate suppression, and refresh after reconnection.

Browser tests use independent mocked API responses under tests/; production screens contain no fixture data. These checks establish frontend behavior, not live database concurrency or email delivery. See ../SEED_PLAN.md for the current database findings, proposed data and verification results.
