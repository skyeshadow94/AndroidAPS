package app.aaps.core.interfaces.meal

import app.aaps.core.data.model.MealAssistantResponse
import app.aaps.core.data.model.MealLearningOutcome

/**
 * Local learning interface to adapt future meal suggestions from historical outcomes.
 *
 * Implementations should be advisory-only and must not bypass dosing constraints.
 */
interface MealLearningEngine {
    fun recordOutcome(outcome: MealLearningOutcome)

    /**
     * Returns multiplicative adjustment to apply to carb estimate for similar meals.
     */
    fun suggestedCarbAdjustmentFactor(response: MealAssistantResponse): Double

    /**
     * Human-readable explanation for UI transparency.
     */
    fun explainAdjustment(response: MealAssistantResponse): String
}
