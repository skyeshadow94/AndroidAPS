package app.aaps.ui.dialogs

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogMealAssistantBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.roundToInt

class MealAssistantDialog : DialogFragment() {

    private var _binding: DialogMealAssistantBinding? = null
    private val binding get() = _binding!!
    private var estimatedCarbs: Double? = null

    private var labelImageUri: Uri? = null
    private var portionImageUri: Uri? = null
    private var labelImageBitmap: Bitmap? = null
    private var portionImageBitmap: Bitmap? = null
    private var pendingLabelCameraUri: Uri? = null
    private var pendingPortionCameraUri: Uri? = null
    private var barcodeLookup: BarcodeLookupResult? = null

    private val pickLabelImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        labelImageUri = uri
        if (uri != null) labelImageBitmap = null
        renderImageStatus()
    }

    private val pickPortionImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        portionImageUri = uri
        if (uri != null) portionImageBitmap = null
        renderImageStatus()
    }

    private val captureLabelImage = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            labelImageUri = pendingLabelCameraUri
            labelImageBitmap = null
        }
        renderImageStatus()
    }

    private val capturePortionImage = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            portionImageUri = pendingPortionCameraUri
            portionImageBitmap = null
        }
        renderImageStatus()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogMealAssistantBinding.inflate(LayoutInflater.from(requireContext()))
        setupProviderSpinner()
        setupActions()
        renderImageStatus()
        return androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun setupProviderSpinner() {
        val providers = listOf(OpenAiAdapter.providerName, AnthropicAdapter.providerName, LocalOnlyAdapter.providerName)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, providers)
        binding.providerSpinner.adapter = adapter
    }

    private fun setupActions() {
        binding.cancel.setOnClickListener { dismiss() }
        binding.calculate.setOnClickListener { calculateEstimate() }
        binding.lookupBarcode.setOnClickListener { lookupBarcodeNutrition() }
        binding.selectLabelImage.setOnClickListener { pickLabelImage.launch("image/*") }
        binding.selectPortionImage.setOnClickListener { pickPortionImage.launch("image/*") }
        binding.captureLabelImage.setOnClickListener {
            pendingLabelCameraUri = createTempImageUri("meal_assistant_label")
            captureLabelImage.launch(pendingLabelCameraUri)
        }
        binding.capturePortionImage.setOnClickListener {
            pendingPortionCameraUri = createTempImageUri("meal_assistant_portion")
            capturePortionImage.launch(pendingPortionCameraUri)
        }
        binding.useEstimate.setOnClickListener {
            estimatedCarbs?.let { carbs ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(RESULT_CARBS to carbs.roundToInt())
                )
                dismiss()
            }
        }
    }

    private fun createTempImageUri(prefix: String): Uri {
        val file = File.createTempFile(prefix, ".jpg", requireContext().cacheDir)
        return FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
    }

    private fun renderImageStatus() {
        if (_binding == null) return
        val yes = getString(R.string.meal_assistant_selected)
        val no = getString(R.string.meal_assistant_not_selected)
        binding.imageStatus.text = getString(
            R.string.meal_assistant_images_status,
            if (labelImageUri != null || labelImageBitmap != null) yes else no,
            if (portionImageUri != null || portionImageBitmap != null) yes else no
        )
    }

    private fun lookupBarcodeNutrition() {
        val barcode = binding.barcode.text.toString().trim()
        if (barcode.length < 8) {
            binding.barcodeLookupStatus.text = getString(R.string.meal_assistant_barcode_lookup_not_found)
            return
        }
        binding.lookupBarcode.isEnabled = false
        binding.barcodeLookupStatus.text = getString(R.string.meal_assistant_barcode_lookup_loading)
        thread {
            val result = runCatching { fetchOpenFoodFacts(barcode) }
            activity?.runOnUiThread {
                binding.lookupBarcode.isEnabled = true
                if (result.isSuccess) {
                    barcodeLookup = result.getOrNull()
                }
                val lookup = barcodeLookup
                if (lookup != null) {
                    binding.carbsPer100g.setText(String.format("%.1f", lookup.carbsPer100g))
                    binding.barcodeLookupStatus.text = getString(
                        R.string.meal_assistant_barcode_lookup_success,
                        lookup.productName,
                        lookup.carbsPer100g
                    )
                    appendAudit("barcode_lookup_success", JSONObject().apply {
                        put("barcode", barcode)
                        put("product", lookup.productName)
                        put("carbsPer100g", lookup.carbsPer100g)
                    })
                } else {
                    binding.barcodeLookupStatus.text = if (result.isSuccess) {
                        getString(R.string.meal_assistant_barcode_lookup_not_found)
                    } else {
                        getString(R.string.meal_assistant_barcode_lookup_error)
                    }
                    appendAudit("barcode_lookup_failure", JSONObject().apply {
                        put("barcode", barcode)
                        put("error", result.exceptionOrNull()?.message.orEmpty())
                    })
                }
            }
        }
    }

    private fun calculateEstimate() {
        val manualCarbsPer100g = binding.carbsPer100g.text.toString().toDoubleOrNull()?.takeIf { it >= 0 }
        val manualPortionGrams = binding.portionGrams.text.toString().toDoubleOrNull()?.takeIf { it > 0 }
        val endpoint = binding.endpoint.text.toString().trim()
        val apiKey = binding.apiKey.text.toString().trim()
        val model = binding.model.text.toString().trim().ifBlank { "gpt-4o-mini" }
        val providerName = binding.providerSpinner.selectedItem?.toString().orEmpty()
        val provider = providerAdapter(providerName)

        val deterministicCarbsPer100g = manualCarbsPer100g ?: barcodeLookup?.carbsPer100g
        val hasPortionEvidence = manualPortionGrams != null || portionImageUri != null || portionImageBitmap != null
        if (deterministicCarbsPer100g == null && provider == LocalOnlyAdapter) {
            binding.result.text = getString(R.string.meal_assistant_invalid_input)
            binding.warnings.text = getString(R.string.meal_assistant_warning_needs_images)
            binding.useEstimate.isEnabled = false
            estimatedCarbs = null
            return
        }
        if (!hasPortionEvidence) {
            binding.result.text = getString(R.string.meal_assistant_invalid_input)
            binding.warnings.text = getString(R.string.meal_assistant_warning_needs_images)
            binding.useEstimate.isEnabled = false
            estimatedCarbs = null
            return
        }

        val deterministicEstimate = if (deterministicCarbsPer100g != null && manualPortionGrams != null) {
            (deterministicCarbsPer100g / 100.0) * manualPortionGrams
        } else null

        if (provider == LocalOnlyAdapter || endpoint.isBlank() || apiKey.isBlank()) {
            if (deterministicEstimate == null) {
                binding.result.text = getString(R.string.meal_assistant_invalid_input)
                binding.warnings.text = getString(R.string.meal_assistant_warning_needs_images)
                binding.useEstimate.isEnabled = false
                estimatedCarbs = null
                return
            }
            renderResult(
                carbs = deterministicEstimate,
                uncertainty = max(5.0, deterministicEstimate * 0.18),
                confidence = if (barcodeLookup != null) 0.85 else 0.65,
                warning = getString(R.string.meal_assistant_warning_local_only)
            )
            appendAudit("estimate_local", JSONObject().apply {
                put("carbs", deterministicEstimate)
                put("barcodeBacked", barcodeLookup != null)
            })
            return
        }

        binding.calculate.isEnabled = false
        binding.result.text = getString(R.string.meal_assistant_processing_deterministic)

        thread {
            val network = runCatching {
                callLlmBackhaul(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    model = model,
                    provider = provider,
                    barcode = binding.barcode.text.toString().trim(),
                    carbsPer100g = deterministicCarbsPer100g,
                    portionGrams = manualPortionGrams,
                    deterministicEstimate = deterministicEstimate,
                    labelImageBase64 = labelImageBitmap?.let { encodeBitmapToBase64(it) } ?: labelImageUri?.let { encodeImageToBase64(it) },
                    portionImageBase64 = portionImageBitmap?.let { encodeBitmapToBase64(it) } ?: portionImageUri?.let { encodeImageToBase64(it) }
                )
            }

            activity?.runOnUiThread {
                binding.calculate.isEnabled = true
                if (network.isSuccess) {
                    val response = network.getOrThrow()
                    val base = deterministicEstimate ?: response.carbs
                    val mergedCarbs = if (deterministicEstimate != null) ((deterministicEstimate * 0.7) + (response.carbs * 0.3)) else response.carbs
                    val mergedUncertainty = max(response.uncertainty, max(5.0, base * 0.15))
                    val mergedConfidence = if (deterministicEstimate != null) max(response.confidence, 0.72) else response.confidence
                    renderResult(
                        carbs = mergedCarbs,
                        uncertainty = mergedUncertainty,
                        confidence = mergedConfidence,
                        warning = response.warning.ifBlank { getString(R.string.meal_assistant_warning_doublecheck) }
                    )
                    appendAudit("estimate_llm", JSONObject().apply {
                        put("provider", provider.providerName)
                        put("carbs", mergedCarbs)
                        put("confidence", mergedConfidence)
                        put("uncertainty", mergedUncertainty)
                        put("deterministicEstimate", deterministicEstimate)
                    })
                } else if (deterministicEstimate != null) {
                    renderResult(
                        carbs = deterministicEstimate,
                        uncertainty = max(5.0, deterministicEstimate * 0.2),
                        confidence = if (barcodeLookup != null) 0.8 else 0.6,
                        warning = getString(R.string.meal_assistant_warning_network_fallback)
                    )
                    appendAudit("estimate_fallback", JSONObject().apply {
                        put("carbs", deterministicEstimate)
                        put("error", network.exceptionOrNull()?.message.orEmpty())
                    })
                } else {
                    binding.result.text = getString(R.string.meal_assistant_invalid_input)
                    binding.warnings.text = getString(R.string.meal_assistant_warning_network_fallback)
                    binding.useEstimate.isEnabled = false
                    estimatedCarbs = null
                }
            }
        }
    }

    private fun renderResult(carbs: Double, uncertainty: Double, confidence: Double, warning: String) {
        val safeCarbs = carbs.coerceAtLeast(0.0)
        val safeConfidence = confidence.coerceIn(0.0, 1.0)
        estimatedCarbs = safeCarbs
        binding.result.text = getString(
            R.string.meal_assistant_result_format,
            safeCarbs.roundToInt(),
            uncertainty.roundToInt(),
            (safeConfidence * 100.0).roundToInt()
        )
        val lowConfidence = safeConfidence < 0.60 || uncertainty > max(15.0, safeCarbs * 0.35)
        binding.warnings.text = if (lowConfidence) {
            "${getString(R.string.meal_assistant_warning_low_confidence)}\n$warning"
        } else {
            warning
        }
        binding.useEstimate.isEnabled = safeCarbs > 0 && !lowConfidence
    }

    private fun fetchOpenFoodFacts(barcode: String): BarcodeLookupResult? {
        val endpoint = "https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=product_name,nutriments"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            connectTimeout = 10000
            readTimeout = 12000
        }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) return null
        val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        val root = JSONObject(body)
        if (root.optInt("status") != 1) return null
        val product = root.optJSONObject("product") ?: return null
        val nutriments = product.optJSONObject("nutriments") ?: return null
        val carbs = nutriments.optDouble("carbohydrates_100g", Double.NaN)
        if (!carbs.isFinite() || carbs < 0.0) return null
        val name = product.optString("product_name", "Unknown product").ifBlank { "Unknown product" }
        return BarcodeLookupResult(name, carbs)
    }

    private fun callLlmBackhaul(
        endpoint: String,
        apiKey: String,
        model: String,
        provider: LlmProviderAdapter,
        barcode: String,
        carbsPer100g: Double?,
        portionGrams: Double?,
        deterministicEstimate: Double?,
        labelImageBase64: String?,
        portionImageBase64: String?
    ): LlmResponse {
        val prompt = buildString {
            append("Return strict JSON only with keys carbs, uncertainty, confidence, warning. ")
            append("Confidence range is 0..1. Keep uncertainty realistic in grams. ")
            append("Prefer deterministic nutrition facts over visual guesses when carbsPer100g is present. ")
            append("Inputs: barcode=")
            append(if (barcode.isBlank()) "none" else barcode)
            append(", carbsPer100g=")
            append(carbsPer100g?.toString() ?: "none")
            append(", portionGrams=")
            append(portionGrams?.toString() ?: "none")
            append(", deterministicEstimate=")
            append(deterministicEstimate?.toString() ?: "none")
        }

        val requestPayload = provider.buildRequest(model, prompt, labelImageBase64, portionImageBase64)

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 12000
            readTimeout = 25000
        }

        connection.outputStream.use { os ->
            os.write(requestPayload.toString().toByteArray())
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        if (responseCode !in 200..299) {
            throw IllegalStateException("LLM request failed ($responseCode): $body")
        }

        val content = provider.extractContent(body)
        val parsedJson = runCatching { JSONObject(content) }.getOrElse {
            JSONObject().apply {
                put("carbs", deterministicEstimate ?: 0.0)
                put("uncertainty", max(7.0, (deterministicEstimate ?: 30.0) * 0.25))
                put("confidence", 0.4)
                put("warning", "Model returned non-JSON, using safer fallback")
            }
        }

        return LlmResponse(
            carbs = parsedJson.optDouble("carbs", deterministicEstimate ?: 0.0),
            uncertainty = parsedJson.optDouble("uncertainty", max(7.0, (deterministicEstimate ?: 30.0) * 0.25)),
            confidence = parsedJson.optDouble("confidence", 0.4).coerceIn(0.0, 1.0),
            warning = parsedJson.optString("warning", "")
        )
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val scaled = scaleBitmap(bitmap, 1280)
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            out.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun encodeImageToBase64(uri: Uri): String {
        val bytes = requireContext().contentResolver.openInputStream(uri)?.use { input ->
            val source = BitmapFactory.decodeStream(input) ?: return@use null
            val scaled = scaleBitmap(source, 1280)
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                out.toByteArray()
            }
        } ?: ByteArray(0)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = if (width > height) maxDim.toDouble() / width else maxDim.toDouble() / height
        val newW = (width * ratio).roundToInt().coerceAtLeast(1)
        val newH = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun appendAudit(event: String, payload: JSONObject) {
        runCatching {
            val line = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("event", event)
                put("payload", payload)
            }.toString()
            val file = File(requireContext().filesDir, "meal_assistant_audit.jsonl")
            file.appendText(line + "\n")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class LlmResponse(
        val carbs: Double,
        val uncertainty: Double,
        val confidence: Double,
        val warning: String
    )

    private data class BarcodeLookupResult(
        val productName: String,
        val carbsPer100g: Double
    )

    private interface LlmProviderAdapter {
        val providerName: String
        fun buildRequest(model: String, prompt: String, labelImageBase64: String?, portionImageBase64: String?): JSONObject
        fun extractContent(responseBody: String): String
    }

    private object OpenAiAdapter : LlmProviderAdapter {
        override val providerName: String = "OpenAI-compatible"

        override fun buildRequest(model: String, prompt: String, labelImageBase64: String?, portionImageBase64: String?): JSONObject {
            val contentArray = JSONArray().put(
                JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                }
            )
            labelImageBase64?.let {
                contentArray.put(
                    JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$it"))
                    }
                )
            }
            portionImageBase64?.let {
                contentArray.put(
                    JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$it"))
                    }
                )
            }
            return JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", contentArray)))
                put("temperature", 0.1)
                put("max_tokens", 180)
            }
        }

        override fun extractContent(responseBody: String): String {
            val root = JSONObject(responseBody)
            return root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
        }
    }

    private object AnthropicAdapter : LlmProviderAdapter {
        override val providerName: String = "Anthropic-compatible"

        override fun buildRequest(model: String, prompt: String, labelImageBase64: String?, portionImageBase64: String?): JSONObject {
            val contentArray = JSONArray().put(
                JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                }
            )
            labelImageBase64?.let {
                contentArray.put(
                    JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", it))
                    }
                )
            }
            portionImageBase64?.let {
                contentArray.put(
                    JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", it))
                    }
                )
            }
            return JSONObject().apply {
                put("model", model)
                put("max_tokens", 220)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", contentArray)))
            }
        }

        override fun extractContent(responseBody: String): String {
            val root = JSONObject(responseBody)
            return root.optJSONArray("content")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()
        }
    }

    private object LocalOnlyAdapter : LlmProviderAdapter {
        override val providerName: String = "Local-only"
        override fun buildRequest(model: String, prompt: String, labelImageBase64: String?, portionImageBase64: String?): JSONObject = JSONObject()
        override fun extractContent(responseBody: String): String = ""
    }

    private fun providerAdapter(name: String): LlmProviderAdapter {
        return when (name) {
            AnthropicAdapter.providerName -> AnthropicAdapter
            OpenAiAdapter.providerName -> OpenAiAdapter
            else -> LocalOnlyAdapter
        }
    }

    companion object {
        const val REQUEST_KEY = "meal_assistant_request"
        const val RESULT_CARBS = "meal_assistant_carbs"
    }
}
