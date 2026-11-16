package com.carloslafuente.finance.data.ai

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.carloslafuente.finance.domain.Extraction
import com.carloslafuente.finance.domain.parseExtraction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

private const val MODEL = "claude-haiku-5-5"
private const val MAX_TOKENS = 16000L

/** Longest image side sent to the model; larger screenshots add cost without making text more legible. */
private const val MAX_IMAGE_SIDE = 1568

private val nullableString = mapOf("type" to listOf("string", "null"))
private val nullableNumber = mapOf("type" to listOf("number", "null"))

private val orderProperties = mapOf(
    "type" to mapOf("type" to "string", "enum" to listOf("BUY", "SELL", "DIVIDEND", "INTEREST", "DEPOSIT", "WITHDRAWAL", "FEE")),
    "asset_name" to nullableString,
    "isin" to nullableString,
    "asset_type" to mapOf("type" to listOf("string", "null"), "enum" to listOf("STOCK", "ETF", "FUND", "BOND", null)),
    "date" to mapOf("type" to listOf("string", "null"), "description" to "YYYY-MM-DD"),
    "quantity" to nullableNumber,
    "price" to nullableNumber,
    "amount" to nullableNumber,
    "fee" to nullableNumber,
    "tax" to nullableNumber,
    "account" to nullableString,
)

private val extractionSchema = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "orders" to mapOf(
            "type" to "array",
            "items" to mapOf(
                "type" to "object",
                "properties" to orderProperties,
                "required" to orderProperties.keys.toList(),
                "additionalProperties" to false,
            ),
        ),
        "notes" to mapOf("type" to "string"),
    ),
    "required" to listOf("orders", "notes"),
    "additionalProperties" to false,
)

private fun systemPrompt(today: LocalDate) = """
You read what a retail investor in Spain copies from their brokers and turn it into structured transactions for their personal portfolio tracker. The material is screenshots, order confirmations or free text, usually in Spanish, mostly from MyInvestor (index funds) and Trade Republic (ETFs and stocks). The investor reviews your output before anything is saved, so a missing value they can fill in is far better than a guessed one.

Return every transaction you can read:
- type: BUY, SELL, DIVIDEND, INTEREST, DEPOSIT (money into the account), WITHDRAWAL or FEE.
- BUY and SELL carry quantity (units or shares) and price (EUR per unit). If only the total is shown, put it in amount and leave price null. fee is the broker's commission.
- DIVIDEND, INTEREST, DEPOSIT, WITHDRAWAL and FEE carry amount (gross EUR). tax is the withholding on a dividend or interest.
- All money is in EUR as charged by the broker. Sources write numbers the Spanish way: 1.234,56 means 1234.56.
- date is YYYY-MM-DD. Today is $today, for resolving things like "ayer". Use null when no date is shown.
- isin only when it is visible. asset_type is STOCK, ETF, FUND or BOND when the source or the name makes it clear, otherwise null.
- account is the broker or bank name when the material identifies it, otherwise null.

When the material is a snapshot of current holdings rather than a list of orders, return one BUY per position: quantity is the units held, price is the average purchase price if shown (otherwise put the invested amount in amount), and date is null. Do not use the current market price as the purchase price.

Use null for anything that is not visible; never invent a value. In notes, write one or two sentences in Spanish about anything ambiguous, unreadable or left out, or an empty string if there is nothing to say.
""".trimIndent()

enum class ExtractionError { INVALID_KEY, RATE_LIMITED, NETWORK, DECLINED, UNREADABLE }

sealed interface ExtractionResult {
    data class Success(val extraction: Extraction) : ExtractionResult
    data class Failure(val error: ExtractionError) : ExtractionResult
}

/** Asks Claude to read transactions out of pasted text and broker screenshots. */
class OrderExtractor(private val contentResolver: ContentResolver) {

    suspend fun extract(apiKey: String, text: String, images: List<Uri>, today: LocalDate): ExtractionResult =
        withContext(Dispatchers.IO) {
            try {
                val content = images.map { ContentBlockParam.ofImage(jpegSource(it)) } +
                    ContentBlockParam.ofText(text.ifBlank { "Extract the transactions from the attached images." })
                val params = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(MAX_TOKENS)
                    .system(systemPrompt(today))
                    .outputConfig(
                        OutputConfig.builder()
                            // Reading values off a screenshot needs little deliberation.
                            .effort(OutputConfig.Effort.LOW)
                            .format(JsonOutputFormat.builder().schema(schema()).build())
                            .build(),
                    )
                    .addUserMessageOfContentBlockParams(content)
                    .build()
                val message = AnthropicOkHttpClient.builder().apiKey(apiKey).build().messages().create(params)
                if (message.stopReason().getOrNull() == StopReason.REFUSAL) {
                    return@withContext ExtractionResult.Failure(ExtractionError.DECLINED)
                }
                val json = message.content().mapNotNull { it.text().getOrNull()?.text() }.joinToString("")
                ExtractionResult.Success(parseExtraction(json))
            } catch (e: UnauthorizedException) {
                ExtractionResult.Failure(ExtractionError.INVALID_KEY)
            } catch (e: RateLimitException) {
                ExtractionResult.Failure(ExtractionError.RATE_LIMITED)
            } catch (e: AnthropicIoException) {
                ExtractionResult.Failure(ExtractionError.NETWORK)
            } catch (e: Exception) {
                // Anything else: an answer that is not the expected JSON, an unreadable image, another API error.
                ExtractionResult.Failure(ExtractionError.UNREADABLE)
            }
        }

    private fun schema(): JsonOutputFormat.Schema =
        JsonOutputFormat.Schema.builder().putAllAdditionalProperties(extractionSchema.mapValues { JsonValue.from(it.value) }).build()

    /** The image as JPEG, scaled down so its longest side is at most [MAX_IMAGE_SIDE]. */
    private fun jpegSource(uri: Uri): Base64ImageSource {
        val original = contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)
        val scale = MAX_IMAGE_SIDE.toFloat() / maxOf(original.width, original.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
        } else {
            original
        }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        return Base64ImageSource.builder()
            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
            .data(Base64.encodeToString(bytes, Base64.NO_WRAP))
            .build()
    }
}
