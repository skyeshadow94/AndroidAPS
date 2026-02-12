package app.aaps.core.interfaces.meal

import app.aaps.core.data.model.MealAssistantRequest
import app.aaps.core.data.model.MealAssistantResponse
import io.reactivex.rxjava3.core.Single

/**
 * Pluggable LLM provider abstraction to keep provider selection user-configurable.
 */
interface MealAssistantProvider {
    val providerId: String
    fun supportsBarcodeLookup(): Boolean
    fun supportsNutritionLabelOcr(): Boolean
    fun supportsPortionEstimation(): Boolean

    /**
     * Analyze meal data and return a nutrition estimate with uncertainty.
     */
    fun analyze(request: MealAssistantRequest): Single<MealAssistantResponse>
}
