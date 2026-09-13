# NIVO AI Architecture

Status: Phase 1 design, implementation-neutral. Only Content Tagger and Search Summarizer are in scope.

## Boundaries

| Component | Mode | Input | Output | Failure behavior |
|---|---|---|---|---|
| Content Tagger | asynchronous post-upload job | title/description/ASR/OCR/optional metadata | strict `ContentTaggerOutput` JSON | upload/video processing remains usable; job retries or marks `AI_TAGGING_UNAVAILABLE` |
| Search Summarizer | optional asynchronous enhancement | query + compact Top-K evidence | short `SearchSummarizerOutput` JSON | search results return immediately; summary omitted or deterministic template shown |
| Teacher provider | offline only | generation task payload | candidate labeled sample | no online request path; missing key disables generation only |
| Recommendation ranker | architecture placeholder only | candidate/features interface | none in this phase | existing deterministic ranking remains authoritative |

## Target Flow

`Upload -> ASR/OCR -> Tagger queue -> validator -> video metadata DB`

`Search -> search engine -> return Top-K results -> compact context -> bounded summarizer queue -> validator -> frontend update`

The search result response must never await model readiness, generation, or Teacher API. A queue is bounded; when full, the enhancement is dropped with a metric.

## Service Contracts

- Model singleton per worker process; load once and warm up explicitly.
- Readiness has a deadline and reports `model_name`, `base_model`, `adapter_version`, and schema version.
- Every request has an ID, enqueue deadline, generation deadline, and max input/output size.
- Health failure triggers bounded restart/backoff, never an unbounded caller wait.
- Logs contain task/model/prompt/dataset versions and timings, never API keys or raw sensitive content.
- Base model and adapter are validated from manifests before load; mismatch is a hard failure.

## Data Lineage

`generator_version + prompt_version + task_schema_version + input_hash + teacher_provider/model -> sample metadata -> dataset manifest -> model manifest`.

Synthetic data is the scale source. Teacher data is a configurable minority focused on hard cases. Source ratio and training sampling weights are independent configuration values.

## Recommendation Boundary

Keep interfaces for candidate generation, deterministic ranking, re-ranking, diversity, exploration and social/trending signals. Do not train or serve an AI ranker in this phase.

## Rollout and Rollback

1. Shadow/async generation with no user-visible dependency.
2. Validate schema/factuality and compare offline metrics.
3. Enable summary display behind a feature flag.
4. Roll back by disabling the enhancement or selecting a prior model manifest; search and video flows remain available.
