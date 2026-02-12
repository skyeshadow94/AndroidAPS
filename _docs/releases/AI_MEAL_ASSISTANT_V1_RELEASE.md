# AI Meal Assistant v1 Release (Fork)

## Release status

This branch contains the **v1 release payload** for AI Meal Assistant in your fork.

## Included v1 feature set

- Treatment dialog entry point for AI Meal Assistant.
- In-app image capture (label + portion) and gallery selection.
- Deterministic barcode nutrition lookup (OpenFoodFacts) to prefill carbs/100g.
- Optional LLM refinement via user-selected provider mode:
  - OpenAI-compatible
  - Anthropic-compatible
  - Local-only fallback
- Confidence/uncertainty gating that blocks low-confidence direct apply.
- Local audit log stream (`meal_assistant_audit.jsonl`) for traceability.

## Safety guarantees kept in v1

- Assistant remains advisory-only.
- Existing AAPS treatment constraints and confirmation flow remain final gate.
- Network/LLM failures degrade safely to deterministic/local paths.

## v1 release acceptance gates

1. Build APK from Android Studio in your fork environment.
2. Validate camera capture and gallery ingestion on a physical phone.
3. Validate barcode lookup success/failure handling.
4. Validate provider endpoint flow (OpenAI/Anthropic-compatible).
5. Validate low-confidence gating (Use estimate disabled).
6. Validate treatment flow still runs standard constraints/confirmation.

## Suggested release tag in your fork

- `v1.0.0-meal-assistant`

## Suggested publish text

> AI Meal Assistant v1 adds deterministic-first carb estimation with barcode nutrition lookup, image-assisted optional LLM refinement, confidence gating, and auditability while preserving AAPS treatment safety guardrails.
