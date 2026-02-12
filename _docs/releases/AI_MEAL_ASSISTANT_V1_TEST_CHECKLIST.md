# AI Meal Assistant v1 Test Checklist

## Device preflight

- [ ] Android camera permission flow works.
- [ ] Endpoint/model/api key can be entered and edited.
- [ ] No crashes when opening/closing assistant repeatedly.

## Functional checks

- [ ] From Treatment Dialog, open AI Meal Assistant.
- [ ] Capture label image in-app.
- [ ] Capture portion image in-app.
- [ ] Select label/portion image from gallery.
- [ ] Barcode lookup fills carbs/100g for known code.
- [ ] Unknown barcode returns safe non-blocking message.
- [ ] Local-only mode computes deterministic estimate.
- [ ] OpenAI-compatible endpoint returns and parses estimate.
- [ ] Anthropic-compatible endpoint returns and parses estimate.
- [ ] Network failure falls back safely.

## Safety checks

- [ ] Low confidence/high uncertainty disables **Use estimate**.
- [ ] User can still manually enter carbs in Treatment Dialog.
- [ ] Existing AAPS confirmation dialog appears before actions.
- [ ] Existing carbs/bolus constraints still apply.

## Traceability checks

- [ ] `meal_assistant_audit.jsonl` created locally.
- [ ] Contains lookup and estimate events with timestamps.

## Regression checks

- [ ] Treatment dialog normal non-assistant workflow unaffected.
- [ ] No app ANR when switching provider modes.
