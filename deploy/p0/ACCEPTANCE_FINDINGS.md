# Recommendation Acceptance Findings

Local smoke observations (2026-08-25):

- `http://localhost:9192` accepts the disposable seed identities from
  `deploy/rtc/multi-vm/seed-test-users.sql`: A/B/C emails are
  `rtc-deploy-a@example.test`, `rtc-deploy-b@example.test`, and
  `rtc-deploy-c@example.test`; password is `password`.
- The current database has only 17 approved videos. That is enough to exercise
  the ranking and deterministic global tie-break, but it is not enough to judge
  recommendation quality or long-term novelty. Add substantially more
  approved videos and distinct watch/like/profile signals before treating
  offline relevance metrics as meaningful. The acceptance script observes A/B
  ordering and checks the cross-replica Redis candidate pool. It only requires
  different A/B ordering with `-RequireDifferentHome`, after distinct signals
  have explicitly been seeded.
- `deploy/p0/recommendation-acceptance.ps1` automates authentication, stable
  same-session HOME pages, signal-aware A/B observation, cross-instance pool
  sharing, HOT availability, FOLLOWING/FRIENDS author filtering, and
  LONG_VIDEO duration checking. Use `-RequireDifferentHome` only when distinct
  profile/behavior signals have been seeded deliberately.
- Use `-SeedBehaviorSignals` to make the script seed evidence through the public
  watch endpoint: account A completes a returned measurable video and account B
  quickly skips a returned measurable video, preferring a different item. The
  script then fetches fresh sessions after the asynchronous profile update.
  This writes durable watch/profile evidence and must use disposable accounts.
  The path is evidence-based; it does not force an A/B difference with user
  IDs, hashes, random ordering, or date seeds. Combine it with
  `-RequireDifferentHome` only when the database has enough approved
  content/features for those signals to affect ranking.
- The recommendation DTO exposes top-level `duration` in milliseconds while
  `t_video.duration` and the watch endpoint use seconds. LONG_VIDEO acceptance
  therefore requires every returned top-level value to be at least `60000`,
  and signal payloads convert the returned value from milliseconds to seconds.
- Before rebuilding the image, the generic endpoint
  `/api/video/recommended?feedMode=LONG_VIDEO` returned short videos. The
  service now derives `minDuration=60` from the `LONG_VIDEO` channel as well as
  the dedicated `/api/video/long/recommended` route. Rebuild/restart the image
  before rerunning the script.

## Commands

```powershell
docker compose --env-file deploy/p0/.env -f deploy/p0/docker-compose.yml up -d --build
pwsh -File deploy/p0/recommendation-acceptance.ps1 -BaseUrl http://localhost:9192 -PeerBaseUrl http://localhost:9193
pwsh -File deploy/p0/recommendation-acceptance.ps1 -BaseUrl http://localhost:9192 -PeerBaseUrl http://localhost:9193 -SeedBehaviorSignals -RequireDifferentHome
```
