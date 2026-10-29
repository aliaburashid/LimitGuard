# LimitGuard frontend

A separate React + TypeScript application for the existing Spring API. No backend changes are required.

## Run

```sh
cd frontend
npm ci
npm run dev
```

Open http://127.0.0.1:5173. Start the existing backend independently using its established configuration. Vite proxies `/api` to `http://localhost:8080`; set `API_PROXY_TARGET` in `.env.local` for another backend address. Do not place secrets in frontend environment variables.

```sh
npm test
npm run build
```

For production, serve `dist/` through a TLS reverse proxy and route `/api` to the existing backend on the same origin. Configure SPA fallback for `/verify-email` and `/reset-password`. `VITE_API_BASE_URL` is optional; a separate origin requires CORS support already provided by your deployment. The frontend does not change Spring Security or CORS.

## Security and data

JWTs remain in memory and are lost on refresh or sign-out. Profile responses determine displayed permissions; the backend remains authoritative. All requests use the central API client. Financial amounts are submitted as decimal strings rather than converted to floating-point numbers. Display calculations use JavaScript numeric arithmetic and are informational; the backend validates all credit decisions. Currency is displayed in BHD; DTOs have no currency field.

SSE uses a streaming fetch with the Bearer header, reconnect backoff, and abort on sign-out. No tokens are put in URLs. Status events invalidate and refetch authorized views. The UI does not display event IDs or unrelated event payloads. The current backend stream broadcasts status events to authenticated subscribers; frontend filtering is not a confidentiality boundary.

Profile pictures support a circular drag-and-zoom editor, including keyboard positioning. The 512px cropped JPEG is uploaded using the existing multipart endpoint; after successful upload, an in-memory preview updates all avatars. Preview URLs are revoked on replacement and sign-out. Because no picture-download endpoint is provided, existing photos cannot be restored after reload or on another device; initials remain the fallback. Backend paths are never interpolated into image URLs.

## Motion and design

Motion is used for restrained page and toast entrances. CSS handles hover and loading feedback; reduced-motion preferences are honored. GSAP, Lenis and Three.js add no benefit to these workflows and are omitted.

Research: [banking dashboards](https://dribbble.com/tags/dashboard-banking), [fintech dashboards](https://dribbble.com/tags/fintechdashboard), and [enterprise fintech search](https://dribbble.com/search/fintech%20dashboard%20design). Design principles extracted: a stable navigation rail, readable neutral surfaces, compact registers, clear metric hierarchy, and semantic states. LimitGuard's interface is original and contains no copied assets.

## Current API limitations

- No credit-limit listing or counterparty-to-limit lookup. Exposure uses a known ID; create/update workflows are available to Risk Officers.
- No portfolio aggregate. Dashboard shows actual accessible counterparty and request counts, not invented financial balances. Its exposure-category illustration is explicitly labeled as nonfinancial data.
- No user listing/lookup or financial institution listing/lookup. Admin workflows use explicit known IDs with confirmation. Registration asks for the institution ID.
- Counterparty responses have no institution or exposure data. Details explain this limitation.
- Risk Officers can list pending requests only. No complete Risk Officer request history or Admin request access is exposed.
- Request responses do not include approval history or rejection reasons. Detail screens show current status and actual timestamps, not invented lifecycle history.
- No notification inbox, risk score, export, or reservation analytics endpoint. These features are not simulated.

See [API_CONTRACT.md](API_CONTRACT.md) for the exact integration map.

## Browser verification

With the frontend dev server running, run `npm run test:browser`. The checked-in Playwright configuration uses macOS Chrome; set `PLAYWRIGHT_CHROME_PATH` to another compatible Chromium executable when needed. Browser tests use API fixtures confined to `tests/` and verify role visibility, exact mutation payloads, review/confirmation, backend errors, corporate group headroom messages, SSE notifications, keyboard submission, dialog focus restoration, responsive layout and ML analytics. Production screens contain no fixture data or demo login.

Validation completed: production TypeScript/Vite build, eight API/SSE/crop tests, nine browser workflow tests, and visual review of desktop login, desktop exposure, 1920-pixel dashboard, and mobile register. Dependency installation reports zero known vulnerabilities. No live backend was reachable at localhost:8080 during verification; live authentication, persistence, business transitions and SSE delivery must be checked with the running backend and authorized test accounts.


## Corporate groups and parallel risk assessment

Open **Limits & exposure** for Corporate Group Management. Admin and Risk Officer users can create groups, attach existing unassigned companies, and set/update their institution's shared credit limit. Company rows show verified individual limits, used/reserved exposure and headroom; group figures come from the existing backend. Unidentified facilities can be loaded with the in-app ID lookup. Admin does not use the RO-only request search endpoint.

Relationship Managers can select **Request credit** on an available company row and submit a decimal amount through the existing credit-request endpoint. Capacity refreshes after successful and rejected operations and through the existing SSE stream. Requests are never automatically retried, and no data is created on mount.

Risk Analytics also offers **Run group assessment**, independent of the selected credit request. It calls the existing server batch endpoint once and displays each returned success/failure while preserving single-company prediction. Worker and batch timing appear in standard Spring Boot INFO logs.

See [the presentation guide](../docs/CONCURRENCY.md) for the two-RM walkthrough. Automated browser tests exercise forms, role permissions, errors, missing-facility lookup and two separate RM contexts against mocked API state; they do not modify the real banking database.
