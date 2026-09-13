# P0 Docker Acceptance

This stack runs two stateless Spring API containers against shared MySQL,
Redis and Kafka. It is intended for local integration testing and does not
prove production HA or internet-scale capacity.

## Start

```powershell
Copy-Item deploy/p0/.env.example deploy/p0/.env
# edit deploy/p0/.env with reachable test dependencies and a real JWT_SECRET
docker compose --env-file deploy/p0/.env -f deploy/p0/docker-compose.yml up -d --build
pwsh -File deploy/p0/acceptance.ps1
# Authenticated recommendation/feed acceptance (uses disposable seed users)
pwsh -File deploy/p0/recommendation-acceptance.ps1 -BaseUrl http://localhost:9192 -PeerBaseUrl http://localhost:9193
# Optional: exercise real completion/quick-skip signals, then compare fresh HOME sessions
pwsh -File deploy/p0/recommendation-acceptance.ps1 -BaseUrl http://localhost:9192 -PeerBaseUrl http://localhost:9193 -SeedBehaviorSignals -RequireDifferentHome
```

The recommendation check logs in as `rtc-deploy-a@example.test` and
`rtc-deploy-b@example.test` with the local seed password `password`. It checks
stable same-session pages, observes A/B HOME ordering, HOT availability,
FOLLOWING/FRIENDS author filtering, and the LONG_VIDEO duration contract. If
the accounts have equivalent recommendation evidence, an identical HOME page
is valid: the account ID alone must never manufacture personalization. Use
`-RequireDifferentHome` only after deliberately seeding different profile or
behavior signals for A and B.

`-SeedBehaviorSignals` records a natural completion for a measurable video
already returned to A and an up-to-one-second quick skip for a measurable video
returned to B (preferring a different video), then waits for the asynchronous
profile worker before fetching new sessions. It writes durable watch/profile
evidence, so run it only with disposable test accounts.
This is an opt-in signal exercise, not a synthetic account split: the script
never uses user IDs, hashes, date seeds, or random ranking inputs. Add enough
approved videos and content features before using `-RequireDifferentHome`; if
the available evidence is equivalent, identical pages remain a valid result.

The recommendation DTO's top-level `duration` is milliseconds. The script
converts it to seconds only when calling `/api/video/watch/{videoId}` and checks
`LONG_VIDEO` against `60000` milliseconds, matching the server's 60-second
entity filter.

The API image build copies the example Spring configuration. Runtime secrets
are injected by Compose and are not stored in the image or repository.

## Windows Kafka

The Compose stack does not download or run Kafka. Start the Kafka installation
already present on the host with the isolated acceptance log directory, so a
stale lock in the normal `kafka-logs` directory cannot take down the broker:

```powershell
$kafkaHome = 'D:\kafka_2.13-3.9.2'
# Start ZooKeeper first if 127.0.0.1:2181 is not already listening.
Start-Process -FilePath "$kafkaHome\bin\windows\zookeeper-server-start.bat" `
  -ArgumentList "$kafkaHome\config\zookeeper.properties" -WorkingDirectory $kafkaHome
& "$kafkaHome\bin\windows\kafka-server-start.bat" `
  "$kafkaHome\config\server-codex-acceptance.properties"
```

That configuration exposes `localhost:9092` for host clients and
`host.docker.internal:29092` for the API containers, matching `deploy/p0/.env`.
Verify both listeners before starting the API stack:

```powershell
Test-NetConnection 127.0.0.1 -Port 9092
docker exec p0-api-a-1 bash -lc "timeout 3 bash -c '</dev/tcp/host.docker.internal/29092'"
```

The recommendation acceptance also verifies that one account/session reads
the same candidate pool through both API replicas and that the next page does
not repeat the first page. Use `-SkipCrossInstance` only when running a single
API replica.

When Kafka runs on the Windows host, it needs separate host and Docker
listeners. See `kafka-local.properties.example`: host tools use
`localhost:9092`, while Compose uses `host.docker.internal:29092`. Do not
advertise only `host.docker.internal`; the host broker/controller cannot
resolve Docker Desktop's container-only alias on every Windows installation.

## Fault injection

```powershell
pwsh -File deploy/p0/fault-injection.ps1 -Action status
pwsh -File deploy/p0/fault-injection.ps1 -Action stop-api-b
pwsh -File deploy/p0/fault-injection.ps1 -Action restore-api-b
```

Redis/Kafka/DB fault injection is performed against the endpoints configured
in `.env`; the script only performs reversible client-side checks by default.
