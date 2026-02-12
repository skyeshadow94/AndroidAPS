# AI Meal Assistant (v1) – README

This document explains the current AI Meal Assistant feature added to AndroidAPS.

## Purpose

The Meal Assistant helps users estimate meal carbohydrates using:

1. Deterministic nutrition data (barcode lookup and manual nutrition values), and
2. Optional image analysis with a selectable LLM provider.

It is **advisory only** and does **not** bypass standard AAPS treatment safety checks.

---

## Safety Principles

- AI outputs are suggestions, not automatic dosing commands.
- Existing Treatment Dialog constraints and confirmations remain the final gate.
- Low-confidence/high-uncertainty estimates are blocked from direct use.
- Fallback behavior prefers deterministic/local results when cloud calls fail.

---

## Current Workflow

1. Open Treatment Dialog and tap **AI Meal Assistant**.
2. Enter or scan a barcode and run **Lookup barcode nutrition**.
3. Capture or pick label/portion images.
4. Enter portion grams where available.
5. Tap **Calculate estimate**.
6. Review carbs, uncertainty, confidence, and warnings.
7. Use estimate only after confirmation; values flow back to Treatment Dialog.

---

## Data Sources and Priority

The feature prioritizes evidence in this order:

1. **Deterministic nutrition facts** (barcode/manual carbs per 100g + portion grams),
2. **Image-based LLM refinement** (optional, provider-dependent),
3. **Local fallback** if network/provider fails.

---

## Provider Options

The current UI supports:

- OpenAI-compatible endpoints,
- Anthropic-compatible endpoints,
- Local-only mode.

Provider mode is user-selectable.

---

## Audit Logging

A local audit file (`meal_assistant_audit.jsonl`) is written for key events:

- barcode lookup success/failure,
- estimate source path (local / LLM / fallback),
- confidence/uncertainty metadata.

This helps debugging and traceability.

---

## Known Limitations (Current v1)

- Barcode path currently expects manual barcode entry and online lookup.
- Label OCR and portion-size inference quality depends on image quality and model behavior.
- Clinical accuracy still requires user review and correction.
- Learning/personalization interfaces exist but are not yet fully wired into production logic.

---

## Recommended Next Steps

1. Add integrated barcode scanner UX (camera decode) with local cache.
2. Add deterministic nutrition normalization from label OCR.
3. Expand validation suite with meal benchmark datasets.
4. Add explicit privacy controls for image retention and export.
5. Add integration tests for provider adapters and fallback paths.

---

## Important Disclaimer

This feature is intended to assist carb estimation. It is not a replacement for clinical judgment or existing AAPS safeguards.
