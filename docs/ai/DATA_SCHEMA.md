# NIVO AI Data Schema

Machine-readable schemas live in `docs/ai/schemas/`. JSONL records should wrap one task input and its expected output plus lineage metadata.

## Common envelope

Required fields: `record_id`, `task`, `schema_version`, `input`, `output`, `source`, `generator_version`, `prompt_version`, `created_at`, `group_id`.

`source` is one of `synthetic`, `teacher`, `hard_case`, or `human`. Teacher records additionally carry provider/model, latency and token usage, never credentials.

`group_id` is the split boundary. All records from one template, query family, or near-duplicate cluster must share a group and remain in exactly one of train/validation/test.

## Content Tagger

Input supports title, description, ASR transcript, OCR text, author information, category hints and optional metadata. Output is validated by `schemas/content_tagger_output.schema.json`; controlled vocabularies and cross-field business rules are applied after JSON Schema validation.

## Search Summarizer

Input supports a query and at most the configured Top-K compact result records. Output is validated by `schemas/search_summarizer_output.schema.json`. The factuality gate builds an evidence set from titles/categories/topics/locations and rejects unsupported entities, numbers, places, or claims.

## Split and sampling defaults

- Group split: 90% train, 5% validation, 5% test.
- Raw source ratio default: synthetic:teacher = 100:1 (configurable).
- Training sampling default: synthetic 80%, teacher 15%, hard_case 5%.
- All defaults are configuration, not code constants.
