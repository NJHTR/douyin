# NIVO AI Current Architecture

Status: Phase 0 audit snapshot (2026-08-22). This document describes the code that exists today; it is not the target design.

## Existing Components

| Area | Current implementation | Evidence |
|---|---|---|
| General worker | `server/python/ai_worker.py`, JSON-lines protocol, `READY`, echo and Qwen2.5-3B modes | `server/python/tests/test_ai_worker_protocol.py` |
| Search summary | `server/python/search_summary.py` (legacy 3B) and `server/python/distill/summary_service.py` (distilled path) | Java `SearchSuggestionService` |
| Distillation | `server/python/distill/teacher_generator.py`, `data_cleaner.py`, `train_distill.py`, two-stage scripts | `server/python/distill/README.md` |
| Java integration | `SearchController` calls `SearchSuggestionService.generateSummary` synchronously | `server/src/main/java/com/douyin/controller/SearchController.java:70` |
| Context | Java builds video and user context; current cap is configuration-driven but includes aggregate/user fields | `SearchSuggestionService.buildSearchContext` |
| Content features | `ContentFeatureService` launches one-shot Python extraction during processing | `server/src/main/java/com/douyin/service/ContentFeatureService.java` |

## Current Search Sequence

`GET /search/summary` -> Java DB search/context construction -> start or reuse Python daemon -> write stdin -> blocking `readLine()` until `__END__` -> return summary or template fallback.

The ordinary search results endpoint is separate, but the summary endpoint itself is synchronous and not an asynchronous enhancement. The frontend requests `/search/summary` after search and displays the response when available.

## Current Model/Adapter State

- `server/python/distill/config.py` sets `TRAIN_CONFIG["base_model"]` to `Qwen/Qwen3-8B-Instruct`.
- `server/python/distill/summary_service.py` uses that setting for inference.
- `server/python/distill_lora_adapter/adapter_config.json` declares `base_model_name_or_path: Qwen/Qwen2.5-3B-Instruct`.
- Therefore the configured distilled inference path is base/adapter incompatible. The service catches adapter-load errors and continues with the base model, which can hide the mismatch.
- `server/python/ai_worker.py` independently hard-codes `Qwen/Qwen2.5-3B-Instruct` for its Qwen mode.

## Protocol and Reliability

- Python emits `READY`, then `SUMMARY:...` and `__END__`, or `ERROR:...`.
- Java waits on `daemonStdout.readLine()` for readiness and response without a timeout.
- `generateSummary` is `synchronized`; concurrent summary requests serialize behind model generation.
- A failed request is retried once and then uses a template fallback. Process restart is attempted, but startup can block the caller.
- No request ID/correlation exists in the summary protocol. The general `ai_worker.py` protocol does have IDs, but is not the path used by `SearchSuggestionService`.
- Python model loading is singleton-like per daemon and has an LRU cache in the distilled service.

## Data and Evaluation State

Existing distillation data is a search-summary-shaped record (`keyword`, `context`, `summary`) and the cleaner performs basic format, length, contradiction, blacklist and edit-distance checks. It uses a random/stratified split, not a group split by template/query family. The current task has no Content Tagger schema, no shared versioned manifest, no hard-case taxonomy, and no task-specific factuality metrics.

Existing `eval_holdout.jsonl` has 10 frozen protocol-style cases for the generic pipeline; it is not a Content Tagger or NIVO Search Summarizer benchmark.

## Non-goals of This Phase

No model download, Teacher API call, dataset generation, training, quantization, Java integration change, or deletion of legacy model code is performed in Phase 0-2.
