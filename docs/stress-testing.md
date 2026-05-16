# Stress-test data seeding

An opt-in seeder provisions a dedicated dealer ("Stress Test Motors",
license `STRESS-0001`) and N synthetic-but-realistic vehicle listings
for load testing the catalog, search, and dealer-portal endpoints.

It is **off by default**, independent of the normal demo seed, and
idempotent — re-running tops up to the target count and never
duplicates (listings use VINs prefixed `STRESS`).

## Enable

H2 dev (ephemeral, re-seeded each boot):

```bash
./gradlew bootRun --args='--app.seed.stress.enabled=true --app.seed.stress.vehicle-count=100'
```

or via env / JVM args on the built jar:

```bash
java -jar build/libs/StealADeal-1.0-SNAPSHOT.jar \
  --app.seed.stress.enabled=true --app.seed.stress.vehicle-count=100
```

Postgres prod profile (persists; safe to re-run):

```bash
SPRING_PROFILES_ACTIVE=prod \
DATABASE_URL=jdbc:postgresql://localhost:5432/stealadeal \
DATABASE_USERNAME=stealadeal DATABASE_PASSWORD=stealadeal \
BOOTSTRAP_ADMIN_PASSWORD=Admin123! \
java -jar build/libs/StealADeal-1.0-SNAPSHOT.jar \
  --app.seed.stress.enabled=true --app.seed.stress.vehicle-count=100
```

## Verify

```bash
curl -s 'http://localhost:8282/api/vehicles?make=' | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))'
```

## Drive load

The catalog and detail endpoints are public (no auth). Example with
hey (https://github.com/rakyll/hey) or ab:

```bash
hey -z 30s -c 50 'http://localhost:8282/api/vehicles'
hey -z 30s -c 50 'http://localhost:8282/api/vehicles?make=Toyota&maxPrice=30000'
ab -n 5000 -c 50 'http://localhost:8282/api/vehicles/1'
```

Distribution of the generated set: ~80% `LIVE`, plus a slice of
`RESERVED` / `DRAFT` / `SOLD` so status filters and the dealer
pipeline have realistic spread. Years 2015–2024, mileage and price
derived from age + a per-model price tier.
