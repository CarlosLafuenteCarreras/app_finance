package com.carloslafuente.finance.ui.gestion

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carloslafuente.finance.R
import com.carloslafuente.finance.data.local.PlanRole
import com.carloslafuente.finance.domain.PlanLine
import com.carloslafuente.finance.ui.formatMoney
import com.carloslafuente.finance.ui.parseDecimal
import java.math.BigDecimal
import java.math.RoundingMode

@StringRes
internal fun PlanRole?.labelRes(): Int = when (this) {
    PlanRole.CORE -> R.string.plan_core
    PlanRole.SATELLITE -> R.string.plan_satellite
    PlanRole.OUTSIDE -> R.string.plan_outside
    null -> R.string.plan_none
}

internal fun formatWeight(percent: BigDecimal): String =
    percent.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',') + " %"

/** Each planned asset's weight against its target, grouped by role. */
@Composable
internal fun PlanSection(lines: List<PlanLine>, onOpenAsset: (Long) -> Unit) {
    Column {
        SectionHeader(R.string.plan_section)
        PlanRole.entries.forEach { role ->
            val group = lines.filter { it.role == role }
            if (group.isEmpty()) return@forEach
            val targets = group.mapNotNull { it.target }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                Text(stringResource(role.labelRes()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // A group total only means something where the targets add up; ceilings do not.
                if (role != PlanRole.OUTSIDE && targets.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.plan_weight_target,
                            formatWeight(group.sumOf { it.weight }),
                            formatWeight(targets.sumOf { it }),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // One scale per group so the bars can be compared with each other.
            val scale = group.maxOf { maxOf(it.weight, it.target ?: BigDecimal.ZERO) }.toFloat().coerceAtLeast(1f)
            group.forEach { line -> PlanItem(line, scale, onClick = { onOpenAsset(line.assetId) }) }
        }
        Text(
            stringResource(R.string.plan_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PlanItem(line: PlanLine, scale: Float, onClick: () -> Unit) {
    val outside = line.role == PlanRole.OUTSIDE
    val target = line.target
    val gap = line.gap
    val over = gap != null && gap.signum() < 0
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(line.name, style = MaterialTheme.typography.bodyLarge)
        PlanBar(line.weight.toFloat() / scale, target?.let { it.toFloat() / scale }, exceeded = outside && over)
        Row(Modifier.fillMaxWidth()) {
            Text(
                when {
                    target == null -> stringResource(R.string.plan_weight_only, formatWeight(line.weight))
                    outside -> stringResource(R.string.plan_weight_ceiling, formatWeight(line.weight), formatWeight(target))
                    else -> stringResource(R.string.plan_weight_target, formatWeight(line.weight), formatWeight(target))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (gap != null) {
                val amount = formatMoney(gap.abs())
                Text(
                    stringResource(
                        when {
                            outside && over -> R.string.plan_gap_exceeded
                            outside -> R.string.plan_gap_room
                            over -> R.string.plan_gap_over
                            else -> R.string.plan_gap_missing
                        },
                        amount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    // Being off target is normal and fixed with new money; only a broken ceiling is flagged.
                    color = if (outside && over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A bar for the current weight with a tick at the target; both as fractions of the bar's width. */
@Composable
private fun PlanBar(fraction: Float, targetFraction: Float?, exceeded: Boolean) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(14.dp)) {
        val bar = 6.dp.toPx()
        val top = (size.height - bar) / 2
        val radius = CornerRadius(bar / 2)
        drawRoundRect(track, Offset(0f, top), Size(size.width, bar), radius)
        drawRoundRect(fill, Offset(0f, top), Size(size.width * fraction.coerceIn(0f, 1f), bar), radius)
        if (targetFraction != null) {
            val x = (size.width * targetFraction.coerceIn(0f, 1f)).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
            drawLine(tick, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanDialog(
    assetName: String,
    role: PlanRole?,
    weight: BigDecimal?,
    onDismiss: () -> Unit,
    onConfirm: (PlanRole?, BigDecimal?) -> Unit,
) {
    var selected by remember { mutableStateOf(role) }
    var text by remember { mutableStateOf(weight?.stripTrailingZeros()?.toPlainString()?.replace('.', ',').orEmpty()) }
    val parsed = parseDecimal(text)?.takeIf { it.signum() > 0 && it <= BigDecimal(100) }
    val valid = text.isBlank() || parsed != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(assetName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.plan_role), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf<PlanRole?>(null) + PlanRole.entries).forEach { option ->
                        FilterChip(
                            selected = option == selected,
                            onClick = { selected = option },
                            label = { Text(stringResource(option.labelRes())) },
                        )
                    }
                }
                if (selected != null) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = {
                            Text(
                                stringResource(
                                    if (selected == PlanRole.OUTSIDE) R.string.plan_ceiling_label else R.string.plan_target_label,
                                ),
                            )
                        },
                        isError = !valid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected, parsed) }, enabled = valid) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
