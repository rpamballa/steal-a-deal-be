# Postgres + Flyway runbook

The default (no-profile) and `test` profiles continue to run against in-memory H2 with `ddl-auto=update`. The `prod` profile runs against Postgres with Flyway-managed migrations and `ddl-auto=validate`.

## Local Postgres for prod-profile development

```bash
docker run --rm \
  --name stealadeal-pg \
  -e POSTGRES_DB=stealadeal \
  -e POSTGRES_USER=stealadeal \
  -e POSTGRES_PASSWORD=stealadeal \
  -p 5432:5432 \
  postgres:16

SPRING_PROFILES_ACTIVE=prod \
DATABASE_URL=jdbc:postgresql://localhost:5432/stealadeal \
DATABASE_USERNAME=stealadeal \
DATABASE_PASSWORD=stealadeal \
BOOTSTRAP_ADMIN_PASSWORD=Admin123! \
./gradlew bootRun
```

On first start Flyway will run [V1__init_schema.sql](../src/main/resources/db/migration/V1__init_schema.sql) and stamp `flyway_schema_history`. Hibernate then validates the live schema against the entity model.

## Migrations

- Filename pattern: `V{n}__{snake_case_description}.sql`, monotonically increasing.
- Postgres-only SQL. The H2 dev path skips Flyway entirely (`spring.flyway.enabled=false` in `application.yml`).
- For dev/test schema changes: update the `@Entity` and let `ddl-auto=update` add the column; mirror the change as a new `V{n+1}__*.sql` for prod.
- Never edit an applied migration. Always add a new file.

## Environment variables (prod)

| Variable | Required | Default |
|---|---|---|
| `DATABASE_URL` | yes (in prod) | `jdbc:postgresql://localhost:5432/stealadeal` |
| `DATABASE_USERNAME` | yes | `stealadeal` |
| `DATABASE_PASSWORD` | yes | `stealadeal` |
| `DATABASE_POOL_MAX` | no | `20` |
| `DATABASE_POOL_MIN` | no | `5` |
| `BOOTSTRAP_ADMIN_EMAIL` | no | `admin@stealadeal.local` |
| `BOOTSTRAP_ADMIN_PASSWORD` | **yes** | _(no default — fail-fast)_ |
| `AUTH_TOKEN_TTL_HOURS` | no | `24` |
| `SERVER_PORT` | no | `8282` |
