# NIVO AI Audit

Date: 2026-08-22  
Scope: Phase 0 only; repository and local runtime inspection. No model or API was downloaded or invoked.

## Findings

### P0: Base model and adapter mismatch

`distill/config.py` selects `Qwen/Qwen3-8B-Instruct`, while `distill_lora_adapter/adapter_config.json` was trained for `Qwen/Qwen2.5-3B-Instruct`. Loading the adapter into Qwen3 is invalid. `summary_service.py` catches the exception and falls back to the unadapted configured base, so an apparently healthy service can silently serve the wrong model.

Required gate: refuse startup when adapter metadata and selected base model do not match, unless an explicit adapter-disabled mode is selected.

### P0: Search summary is a synchronous AI dependency

`SearchController.summary` calls `generateSummary` directly. The Java method is synchronized and waits for Python process startup and generation. The Python READY read and response read have no deadline. A slow load, OOM, crash, or hung generation can consume request threads and delay the summary endpoint indefinitely. Core search results are not in the same method, but the product contract currently exposes the AI call as a synchronous endpoint rather than an async enhancement.

Required gate: search results must be returned independently; summary work must be queued/best-effort with bounded timeouts and a deterministic non-AI fallback.

### P1: Protocol lacks correlation and bounded queueing

The summary stdin/stdout protocol sends raw JSON and relies on ordering plus `__END__`. It has no request ID, queue limit, generation deadline, or health/readiness endpoint. The generic worker has IDs, but Java does not use it.

### P1: Context is wider than the target summarizer contract

The current context includes users, follower totals, aggregate plays/likes/comments, collaborative signals and quality scores. The NIVO target requires compact Top-K result evidence and forbids unsupported facts. A new compact context contract is needed before training.

### P1: Data cleaner is not sufficient for NIVO tasks

Current cleaning expects long (300-800 Chinese-character) summaries, allows free-form output, uses random/stratified splitting, and does not validate a strict tagger schema or hallucination against result evidence. It is not reusable as the NIVO quality gate.

### P2: Reproducibility/versioning gaps

Current configuration contains fixed model/data assumptions and no common dataset manifest/model manifest/prompt version contract. Teacher generation has environment-based credentials, but audit metadata and resumable cache/cost controls are not standardized across both tasks.

## Runtime Evidence

Captured on the audit machine:

| Item | Value |
|---|---|
| GPU | NVIDIA GeForce RTX 4060 Laptop GPU |
| VRAM | 8188 MiB total, 7815 MiB free at audit |
| System RAM | 16,869,548,032 bytes (~15.7 GiB) |
| Driver / CUDA runtime | Driver 556.29 / CUDA 12.5 reported by `nvidia-smi` |
| Python | 3.12.13 (`server/python/.venv`) |
| PyTorch | 2.6.0+cu124, CUDA available: true |
| Transformers | 4.54.1 |
| PEFT | 0.19.1 |
| Accelerate | 1.14.0 |
| Measured model load/inference | Not run in Phase 0; benchmark is a Phase 1 deliverable |

No reliable startup latency, first-token latency, tokens/sec, OOM rate or 100-request stability numbers are claimed yet.

## Candidate Base Model Plan (not a selection)

Benchmark only after the contract and harness exist:

1. 0.5B-0.8B Chinese instruction model: lowest VRAM/latency candidate.
2. 1B-1.5B Chinese instruction model: quality candidate for hard cases.
3. Existing Qwen2.5-3B-Instruct: compatibility baseline because the adapter and worker already target it.

Qwen3-8B is not an online-search candidate. Any candidate must be tested with the exact tokenizer, quantization, adapter state and prompt version used in deployment. Model choice remains evidence-driven and is intentionally deferred.

## Risk Register

| Risk | Severity | Mitigation / owner phase |
|---|---:|---|
| Adapter/base mismatch | P0 | Metadata gate and manifest (Phase 1) |
| Search blocked by AI | P0 | Async enhancement and bounded queue (Phase 9-10) |
| Hallucinated summary facts | P0 | Compact evidence context + factuality validator (Phase 2-5) |
| Template leakage inflates metrics | P1 | Group split by family/topic/query cluster (Phase 6) |
| Teacher cost/rate limits | P1 | Cache, budget, bounded concurrency, resume (Phase 4) |
| 8 GiB VRAM pressure | P1 | Benchmark 4-bit candidates; no assumption of fit (Phase 1/12) |
| Legacy behavior regression | P1 | Keep old paths deprecated and rollbackable (Migration phase) |

## Phase 0 Exit Criteria

- Current base/adapter mismatch documented: complete.
- Search blocking and fallback behavior documented: complete.
- Hardware/runtime inventory captured: complete.
- No training or Teacher call performed: complete.

## Next Phase

Implement the target architecture interfaces and strict schemas in `docs/ai/` and `server/python/nivo_ai/` without changing production traffic. Then build a benchmark harness before selecting a base model.
