# Bootstrap the shared PostgreSQL database

Auth, Credit, and Activity use one PostgreSQL database and share the `public` schema. Auth and Credit both use `public.audit_logs`. The [V001 baseline](../database/schema.sql) creates the schema, including Credit's notification outbox and Activity's email delivery queue. It inserts no rows.

Use the baseline for a **new, empty database**. It runs in one transaction and takes a transaction-scoped advisory lock to serialize concurrent bootstrap attempts. A rerun or a run against a populated `public` schema fails before creating tables. Treat V001 as a fixed baseline. Future upgrades to an installed database should use separate, reviewed migrations. The development database and existing banking data do not need this baseline.

## Create and initialize a new database

Run these commands from the repository root. Use a PostgreSQL role that can connect to the server, create databases, and create objects in its new database's `public` schema. Supply its credentials through your normal secure PostgreSQL authentication setup. For the service launches below, set `DB_PASSWORD` securely if the server requires a password.

```sh
export DB_USERNAME='your_local_database_role'
export LIMITGUARD_NEW_DB='limitguard_fresh'
export DB_URL="jdbc:postgresql://127.0.0.1:5432/$LIMITGUARD_NEW_DB"

createdb --host=127.0.0.1 --port=5432 --username="$DB_USERNAME" "$LIMITGUARD_NEW_DB"

psql -X --host=127.0.0.1 --port=5432 --username="$DB_USERNAME" \
  --dbname="$LIMITGUARD_NEW_DB" --set=ON_ERROR_STOP=1 --file=database/schema.sql

psql -X --host=127.0.0.1 --port=5432 --username="$DB_USERNAME" \
  --dbname="$LIMITGUARD_NEW_DB" --set=ON_ERROR_STOP=1 \
  --command="SELECT current_database(); SELECT count(*) AS public_tables FROM pg_tables WHERE schemaname = 'public';"
```

Continue after both creation and initialization succeed. The result should name `limitguard_fresh` and report **17 public tables**. Configure a shared `JWT_SECRET` for Auth and Credit, and matching `INTERNAL_SERVICE_KEY` values for Auth, Credit, and Activity for authenticated flows. Use local secrets supplied through the environment.

## Verify service startup

For a schema-only smoke test, start the services from the shell with the database variables above. These commands use spare ports, keep Hibernate in `validate` mode, and disable the background jobs, Kafka listeners, and email delivery.

```sh
(cd auth-service && ./mvnw -q -DskipTests package)
(cd credit-service && ./mvnw -q -DskipTests package)
(cd activity-service && ./mvnw -q -DskipTests package)

java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar \
  --server.address=127.0.0.1 --server.port=18091 \
  --spring.profiles.active=default --spring.jpa.hibernate.ddl-auto=validate \
  --spring.sql.init.mode=never --limitguard.seed.demo.enabled=false \
  --spring.mail.host=127.0.0.1 --spring.mail.port=1025 &
auth_pid=$!

java -jar credit-service/target/credit-service-0.0.1-SNAPSHOT.jar \
  --server.address=127.0.0.1 --server.port=18092 \
  --spring.profiles.active=default --spring.jpa.hibernate.ddl-auto=validate \
  --spring.sql.init.mode=never --limitguard.seed.citi.enabled=false \
  --limitguard.seed.gib.enabled=false --limitguard.seed.unit3.enabled=false \
  --limitguard.notification-relay.enabled=false \
  --limitguard.credit-request-expiry.enabled=false \
  --limitguard.exposure-snapshots.enabled=false &
credit_pid=$!

GMAIL_USERNAME= GMAIL_APP_PASSWORD= java -jar activity-service/target/activity-service-0.0.1-SNAPSHOT.jar \
  --server.address=127.0.0.1 --server.port=18093 \
  --spring.profiles.active=default --spring.jpa.hibernate.ddl-auto=validate \
  --spring.sql.init.mode=never --auth-service.url=http://127.0.0.1:18091 \
  --spring.kafka.listener.auto-startup=false \
  --activity.email.enabled=false --activity.email.delivery-enabled=false &
activity_pid=$!
```

Check for a Spring Boot startup success line for each service and no schema validation errors. These are startup checks. A fresh database has no institutions or users. Provision accounts and external services separately for application testing. When finished, stop these three processes with `kill "$auth_pid" "$credit_pid" "$activity_pid"`.

Activity accesses the delivery table through JDBC. Hibernate validates mapped entities, so Activity startup alone cannot verify the delivery table. Check its read path on the same database:

```sh
psql -X --host=127.0.0.1 --port=5432 --username="$DB_USERNAME" \
  --dbname="$LIMITGUARD_NEW_DB" --set=ON_ERROR_STOP=1 \
  --command="SELECT event_id, financial_institution_id, credit_request_id, requester_id, event_type, reason FROM public.credit_request_email_deliveries WHERE delivery_status = 'PENDING' ORDER BY queued_at, event_id LIMIT 25;"
```

## Schema inventory

- **Auth:** `financial_institutions`, `users`, `email_verification_codes`, `password_reset_codes`, and `user_tokens`. Users reference institutions. Token and code rows reference users, with an optional user for password-reset codes. Unique keys cover institution names, user emails, tokens, challenge IDs, one verification code per user, and one reset-code row per email.
- **Shared audit:** `audit_logs` has optional foreign keys to the actor and institution. Its `details` column is `text`, matching the Auth and Credit mappings and incorporating the existing widening patch.
- **Credit portfolio:** `counterparties`, `corporate_groups`, `corporate_group_memberships`, `counterparty_financial_profiles`, `credit_limits`, and `group_credit_limits`. The foreign keys enforce the catalog relationships. Unique keys cover company and group names, one group membership and one profile per counterparty, and one facility per institution and counterparty or group.
- **Credit workflow:** `credit_requests` references a facility and requester. Its optional seed key is unique. `approval_decisions` references the request and deciding user, with at most one decision per request. `credit_exposure_snapshots` uses an institution/facility/reporting-date primary key and a reporting-date index. `notification_outbox` uses an identity primary key and a check constraint for the fields expected by each event type.
- **Activity:** `credit_request_email_deliveries` uses the event ID as its primary key, allowing the queue insert to deduplicate events. It checks supported email event types and defaults the delivery state to `PENDING`. Event references in the outbox and delivery queue, and institution/facility identifiers in historical snapshots, follow the existing schema without foreign keys.

V001 defines the column types, defaults, relationship constraints, unique keys, and checks for existing status and event values. PostgreSQL creates the indexes backing the primary and unique keys. The reporting-date index brings the baseline to **33 indexes**, including those constraint indexes.
