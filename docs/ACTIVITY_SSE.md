# Activity SSE API

[Local setup](../README.md#running-the-project-locally) · [Notification workflow](CONCURRENCY.md#9-asynchronous-email-and-sse)

**Request** `GET /api/credit-requests/events` with `Authorization: Bearer <JWT>` and `Accept: text/event-stream`.

React calls the endpoint on the frontend origin. Vite forwards `/api` to Gateway on port 8080 by default. Gateway's exact path and GET predicate (order `-1`) route the request to Activity on port 8083 ahead of the broader Credit route. Activity calls Auth's internal authorization endpoint using the JWT and the shared `INTERNAL_SERVICE_KEY`. An active user with a valid institution can receive that institution's events.

## Response and events

A successful subscription returns HTTP `200` with `Content-Type: text/event-stream` and the initial comment `:connected`. Each message has an `event:` name and a `data:` line containing JSON. A blank line terminates the message.

```text
:connected

event:credit-request-status
data:{"creditRequestId":125,"status":"RESERVED"}

```

| Event name | JSON fields |
| --- | --- |
| `credit-request-status` | `creditRequestId`, `status` |
| `counterparty-status` | `counterpartyId`, `status` |
| `credit-limit-updated` | `creditLimitId`, `oldLimitAmount`, `newLimitAmount` |
| `group-credit-limit-updated` | `groupCreditLimitId`, `corporateGroupId`, `oldLimitAmount`, `newLimitAmount` |

IDs and amounts are JSON numbers. Statuses are strings. Events carry changes for live UI refresh. Refresh authoritative state through the regular API after reconnecting. The stream provides no event IDs or replay.

## Connection lifecycle and errors

- Activity gives each stream a five-minute timeout and removes completed, failed, and timed-out emitters. It also drops an emitter whose write fails and continues sending to other clients.
- React uses authenticated streaming fetch. It retries after interruption or temporary failure, starting at one second and backing off to at most 30 seconds. Auth rechecks the user on each reconnect. Logout or session replacement stops the old subscription.
- **401 Unauthorized** covers missing or invalid bearer credentials and inactive accounts. React clears the affected session.
- **403 Forbidden** covers Auth access denial or an account without a valid institution. React stops retrying and shows access denied.
- **503 Service Unavailable** covers Auth outages, unexpected upstream HTTP errors, or missing Activity internal service-key configuration. React retries while the session is current.
- An I/O failure after the stream starts can close the connection. Activity has no endpoint-specific HTTP 204 error handler. The client reconnects on stream closure.

## Local deployment limit

Use one Activity instance for complete local SSE delivery. Emitters are held in that instance's memory. With several replicas sharing one Kafka consumer group, Kafka assigns each partition to one consumer, so an event can arrive on a different replica from the recipient's browser. Multi-replica SSE requires a fan-out design that reaches every instance hosting eligible connections. This is future deployment work.

Activity has no Swagger dependency. Auth and Credit keep service-local OpenAPI documentation linked from the [README](../README.md#4-explore-the-api).
