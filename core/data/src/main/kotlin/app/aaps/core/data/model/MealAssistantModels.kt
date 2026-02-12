package app.aaps.core.data.model

/**
 * Nutrition record normalized to grams for diabetes decision support.
 */
data class NutritionEstimate(
    val carbsG: Double,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
    val energyKcal: Double? = null,
    val sodiumMg: Double? = null,
    val confidence: Double,
    val uncertaintyCarbsG: Double
)

/**
 * Unified meal analysis request for barcode, label OCR and/or portion image estimation.
 */
data class MealAssistantRequest(
    val timestamp: Long,
    val barcode: String? = null,
    val nutritionLabelImageUri: String? = null,
    val portionImageUri: String? = null,
    val mealDescription: String? = null,
    val providerId: String,
    val modelId: String,
    val locale: String? = null
)

/**
 * Provider response used by UI to render estimate, uncertainty and user confirmation actions.
 */
data class MealAssistantResponse(
    val request: MealAssistantRequest,
    val nutritionEstimate: NutritionEstimate,
    val providerLatencyMs: Long,
    val modelVersion: String,
    val explanation: String,
    val warnings: List<String> = emptyList(),
    val requiresUserConfirmation: Boolean = true
)

/**
 * Local learning signal linking estimated/confirmed meal values to observed glycemic impact.
 */
data class MealLearningOutcome(
    val mealTimestamp: Long,
    val estimatedCarbsG: Double,
    val confirmedCarbsG: Double,
    val preMealGlucoseMgdl: Double? = null,
    val postMealPeakGlucoseMgdl: Double? = null,
    val postMealAucMgdlMin: Double? = null,
    val correctionInsulinU: Double? = null,
    val absorbedCarbsG: Double? = null,
    val tags: List<String> = emptyList()
)
