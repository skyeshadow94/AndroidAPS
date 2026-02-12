# AI Meal Assistant implementation plan (AAPS)

## Goals
- Add assistive meal intake from barcode/label/portion image.
- Keep all current AAPS treatment flows and constraints unchanged.
- Make LLM provider selectable by user.
- Improve precision with explicit uncertainty and verification.
- Minimize cost and latency.

## Non-negotiable safety boundaries
1. AI output is advisory only.
2. User must confirm/edit carbs before treatment submission.
3. Existing constraints and confirmation dialogs remain the final gate.
4. Low-confidence requests must fail safe to manual entry.

## Proposed UX flow
1. Open treatment dialog and tap **AI Meal Assistant**.
2. Capture barcode (or nutrition label image).
3. Capture portion image (or enter weight manually).
4. System computes estimate and displays:
   - carbs/protein/fat,
   - confidence,
   - carb uncertainty range,
   - warnings.
5. User adjusts values and confirms.
6. Final values go through existing AAPS constraints/confirmation.

## Architecture
- `MealAssistantProvider` interface for provider-agnostic analysis.
- `MealLearningEngine` interface for local personalized adjustment.
- `MealAssistantRequest/Response` and `NutritionEstimate` data models.

## Storage strategy
- Local-first storage for low latency and offline reliability.
- Store structured meal records for learning; store full-resolution images only with explicit opt-in.
- Keep cloud sync optional and asynchronous (backup/recovery), never required for in-session recommendation.

## Accuracy strategy
- Two-pass verification:
  1) vision/LLM extraction,
  2) deterministic normalization against nutrition facts.
- Always return uncertainty and confidence.
- Add user correction feedback loop to improve future suggestions.

## Cost strategy
- Image downscaling/compression before upload.
- Reuse barcode lookups via cache.
- Use lightweight model first, optional high-accuracy retry for uncertain cases.
- Batch non-critical learning sync operations.

## Latency strategy
- Parallelize barcode lookup and portion analysis where possible.
- Timebox provider call and degrade gracefully to manual entry.
- Keep all learning features non-blocking for treatment entry.

## Rollout plan
- Phase 1: advisor-only scaffold + provider abstraction + logging.
- Phase 2: barcode+label+portion assistant in treatment UI.
- Phase 3: local learning adjustments with transparent explanations.
- Phase 4: optional encrypted cloud backup/sync.

## Suggested metrics
- % estimates within ±10g / ±20g of user-confirmed carbs.
- Median and p95 assistant latency.
- Cost per successful estimate.
- User override rate and correction magnitude.
