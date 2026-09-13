# Next Task

Implement Phase 1/2 foundations without changing production traffic:

1. Add a small `nivo_ai` package for schema loading, envelope validation, controlled vocabularies and manifest types.
2. Add a benchmark harness that records load time, warmup, first-token latency, tokens/sec, VRAM and OOM for candidate sizes only when explicitly enabled.
3. Add a strict adapter/base compatibility check and a dry-run command; do not change the current online model selection yet.
4. Add tests for JSON Schema validation and group-split leakage prevention.

Do not call Teacher APIs, generate large datasets, download candidate models, or train until these checks pass.
