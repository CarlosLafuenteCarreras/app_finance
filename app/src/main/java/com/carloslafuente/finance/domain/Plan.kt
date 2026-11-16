package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.AssetEntity
import com.carloslafuente.finance.data.local.AssetType
import com.carloslafuente.finance.data.local.PlanRole
import java.math.BigDecimal
import java.math.MathContext

private val HUNDRED = BigDecimal(100)

/** One row of the plan document's product table. */
data class PlanProduct(
    val name: String,
    val isin: String?,
    val type: AssetType,
    val role: PlanRole,
    /** Target weight in percent; null for products outside the plan. */
    val weight: BigDecimal?,
)

private val isinPattern = Regex("""\b[A-Z]{2}[A-Z0-9]{9}\d\b""")
private val activeWeight = Regex("""Activo\D*?(\d+(?:[.,]\d+)?)\s*%""", RegexOption.IGNORE_CASE)

/**
 * Reads the products out of the plan's Markdown: the table whose header has "ISIN", "Tipo" and
 * "Estado" columns. A row counts when its state is "Activo — NN%" (a target weight) or "Fuera
 * del plan"; anything else (removed, on hold) is skipped. A product is a satellite when its ISIN
 * is mentioned under a second-level heading that contains "SATÉLITE". Empty if there is no such table.
 */
fun parsePlanMarkdown(text: String): List<PlanProduct> {
    val lines = text.lines()
    val satelliteIsins = mutableSetOf<String>()
    var inSatellites = false
    for (line in lines) {
        if (line.startsWith("## ")) inSatellites = line.contains("SATÉLITE", ignoreCase = true)
        if (inSatellites) satelliteIsins += isinPattern.findAll(line).map { it.value }
    }

    fun cells(line: String) = line.trim().trim('|').split('|').map { it.trim() }
    val headerIndex = lines.indexOfFirst { it.trimStart().startsWith("|") && "ISIN" in it && "Estado" in it }
    if (headerIndex < 0) return emptyList()
    val header = cells(lines[headerIndex])
    val isinColumn = header.indexOf("ISIN")
    val typeColumn = header.indexOf("Tipo")
    val stateColumn = header.indexOf("Estado")
    if (typeColumn < 0) return emptyList()

    return lines.drop(headerIndex + 2).takeWhile { it.trimStart().startsWith("|") }.mapNotNull { line ->
        val row = cells(line)
        if (row.size != header.size) return@mapNotNull null
        val state = row[stateColumn]
        val weight = activeWeight.find(state)?.groupValues?.get(1)?.replace(',', '.')?.toBigDecimal()
        val outside = state.contains("Fuera del plan", ignoreCase = true)
        if (weight == null && !outside) return@mapNotNull null
        val isin = isinPattern.find(row[isinColumn])?.value
        PlanProduct(
            name = row[0],
            isin = isin,
            type = when {
                row[typeColumn].startsWith("ETF", ignoreCase = true) -> AssetType.ETF
                row[typeColumn].startsWith("Acci", ignoreCase = true) -> AssetType.STOCK
                else -> AssetType.FUND
            },
            role = when {
                outside -> PlanRole.OUTSIDE
                isin in satelliteIsins -> PlanRole.SATELLITE
                else -> PlanRole.CORE
            },
            weight = weight,
        )
    }
}

/** Where an asset stands against the plan. */
data class PlanLine(
    val assetId: Long,
    val name: String,
    val role: PlanRole,
    val value: BigDecimal,
    /** Current weight in percent: of the plan for core and satellites, of plan plus outside for the rest. */
    val weight: BigDecimal,
    /** Target weight in percent, or the ceiling for an asset outside the plan. Null when not set. */
    val target: BigDecimal?,
    /** Euros missing to reach the target (negative when above it). Null without a target. */
    val gap: BigDecimal?,
)

/**
 * Compares what is held with the plan. Core and satellite weights are measured against the plan's
 * own value, because assets outside the plan do not count for its weights; those are measured
 * against everything, since their ceilings limit how much of the whole they may take.
 */
fun planLines(assets: List<AssetEntity>, values: Map<Long, BigDecimal>): List<PlanLine> {
    val planned = assets.filter { it.planRole != null }
    fun valueOf(asset: AssetEntity) = values[asset.id] ?: BigDecimal.ZERO
    val planValue = planned.filter { it.planRole != PlanRole.OUTSIDE }.sumOf(::valueOf)
    val allValue = planned.sumOf(::valueOf)

    return planned.sortedWith(compareBy({ it.planRole }, { -(it.planWeight ?: BigDecimal.ZERO) })).map { asset ->
        val value = valueOf(asset)
        val whole = if (asset.planRole == PlanRole.OUTSIDE) allValue else planValue
        PlanLine(
            assetId = asset.id,
            name = asset.name,
            role = asset.planRole!!,
            value = value,
            weight = if (whole.signum() > 0) value.multiply(HUNDRED).divide(whole, MathContext.DECIMAL64) else BigDecimal.ZERO,
            target = asset.planWeight,
            gap = asset.planWeight?.let { it.multiply(whole).divide(HUNDRED, MathContext.DECIMAL64) - value },
        )
    }
}
