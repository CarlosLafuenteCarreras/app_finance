package com.carloslafuente.finance.domain

import com.carloslafuente.finance.data.local.AssetEntity
import com.carloslafuente.finance.data.local.AssetType
import com.carloslafuente.finance.data.local.PlanRole
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class PlanTest {

    @Test
    fun `reads targets, satellites and outside-plan products from the product table`() {
        val markdown = """
            ## 4. NÚCLEO INDEXADO
            **World Fund** · ISIN IE00BYX5NX33 · TER 0,12%

            ## 6. SATÉLITES TEMÁTICOS — 16%
            **Uranium ETF (NUCL)** · ISIN IE000M7V94E1 · TER 0,55%

            ## 7. EJECUCIÓN

            ## PRODUCTOS

            | Producto | ISIN | TER | Tipo | Estado |
            |---|---|---|---|---|
            | World Fund | IE00BYX5NX33 | 0,12% | Fondo | Activo — 63% |
            | Emerging Fund | (confirmar en bróker) | ~0,20% | Fondo | Activo — 13,5% |
            | Uranium ETF (NUCL) | IE000M7V94E1 | 0,55% | ETF | Activo — 8% |
            | NVIDIA Corporation | US67066G1040 | — | Acción | Fuera del plan |
            | Old Bond Fund | IE00B246KL88 | 0,16% | Fondo | Eliminado ago-2026 |

            Texto posterior.
        """.trimIndent()

        assertEquals(
            listOf(
                PlanProduct("World Fund", "IE00BYX5NX33", AssetType.FUND, PlanRole.CORE, BigDecimal("63")),
                PlanProduct("Emerging Fund", null, AssetType.FUND, PlanRole.CORE, BigDecimal("13.5")),
                PlanProduct("Uranium ETF (NUCL)", "IE000M7V94E1", AssetType.ETF, PlanRole.SATELLITE, BigDecimal("8")),
                PlanProduct("NVIDIA Corporation", "US67066G1040", AssetType.STOCK, PlanRole.OUTSIDE, null),
            ),
            parsePlanMarkdown(markdown),
        )
        assertEquals(emptyList<PlanProduct>(), parsePlanMarkdown("# Notas\n\n| a | b |\n|---|---|\n| 1 | 2 |"))
    }

    @Test
    fun `plan weights ignore outside-plan assets, whose ceiling is measured against everything`() {
        val assets = listOf(
            AssetEntity(1, "World", AssetType.FUND, planRole = PlanRole.CORE, planWeight = BigDecimal("80")),
            AssetEntity(2, "Uranium", AssetType.ETF, planRole = PlanRole.SATELLITE, planWeight = BigDecimal("20")),
            AssetEntity(3, "Nvidia", AssetType.STOCK, planRole = PlanRole.OUTSIDE, planWeight = BigDecimal("8")),
            AssetEntity(4, "Untracked", AssetType.STOCK),
        )
        val values = mapOf(1L to BigDecimal("7000"), 2L to BigDecimal("2000"), 3L to BigDecimal("1000"), 4L to BigDecimal("500"))

        val lines = planLines(assets, values).associateBy { it.assetId }

        assertEquals(setOf(1L, 2L, 3L), lines.keys)
        // Plan value is 9,000: World is 77.8 % of it and needs 200 more to be 80 %.
        assertEquals("77.8", lines.getValue(1).weight.setScale(1, java.math.RoundingMode.HALF_UP).toPlainString())
        assertEquals(0, BigDecimal("200").compareTo(lines.getValue(1).gap))
        assertEquals(0, BigDecimal("-200").compareTo(lines.getValue(2).gap))
        // Nvidia is 10 % of the 10,000 that plan and outside-plan assets add up to: 200 over its 8 % ceiling.
        assertEquals(0, BigDecimal("10").compareTo(lines.getValue(3).weight))
        assertEquals(0, BigDecimal("-200").compareTo(lines.getValue(3).gap))
    }
}
