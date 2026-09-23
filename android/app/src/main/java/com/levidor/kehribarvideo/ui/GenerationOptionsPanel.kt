package com.levidor.kehribarvideo.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.levidor.kehribarvideo.data.CapabilitiesResponse
import com.levidor.kehribarvideo.data.LocalCapabilityDefaults
import com.levidor.kehribarvideo.data.optionLabel
import com.levidor.kehribarvideo.viewmodel.GenerationOptions

/**
 * Süre / format / kalite / hareket / kamera / stil preseti / ürün koruma
 * seçeneklerini gösteren, hem normal üretim ekranı hem de reklam modu
 * tarafından paylaşılan panel. Sunucudan gelen [capabilities] varsa onun
 * listeleri, yoksa [LocalCapabilityDefaults] kullanılır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerationOptionsPanel(
    options: GenerationOptions,
    capabilities: CapabilitiesResponse?,
    showModeToggle: Boolean,
    onChange: (GenerationOptions) -> Unit
) {
    val durations = capabilities?.target_durations_seconds ?: LocalCapabilityDefaults.DURATIONS
    val aspects = capabilities?.aspect_ratios ?: LocalCapabilityDefaults.ASPECT_RATIOS
    val qualities = capabilities?.quality_presets ?: LocalCapabilityDefaults.QUALITIES
    val motions = capabilities?.motion_levels ?: LocalCapabilityDefaults.MOTIONS
    val cameras = capabilities?.camera_moves ?: LocalCapabilityDefaults.CAMERAS
    val presets = capabilities?.style_presets ?: LocalCapabilityDefaults.STYLE_PRESETS

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        if (showModeToggle) {
            OptionSection(title = "Mod") {
                ChipRow(
                    options = listOf("image_to_video", "text_to_video"),
                    selected = options.mode,
                    onSelect = { onChange(options.copy(mode = it)) }
                )
            }
        }

        OptionSection(title = "Süre") {
            ChipRow(
                options = durations.map { it.toString() },
                selected = options.durationSeconds.toString(),
                labelFor = { "$it sn" },
                onSelect = { onChange(options.copy(durationSeconds = it.toInt())) }
            )
        }

        OptionSection(title = "Format") {
            ChipRow(
                options = aspects,
                selected = options.aspectRatio,
                onSelect = { onChange(options.copy(aspectRatio = it)) }
            )
        }

        OptionSection(title = "Kalite") {
            ChipRow(
                options = qualities,
                selected = options.quality,
                labelFor = ::optionLabel,
                onSelect = { onChange(options.copy(quality = it)) }
            )
        }

        OptionSection(title = "Hareket") {
            ChipRow(
                options = motions,
                selected = options.motion,
                labelFor = ::optionLabel,
                onSelect = { onChange(options.copy(motion = it)) }
            )
        }

        OptionSection(title = "Kamera") {
            ChipRow(
                options = cameras,
                selected = options.camera,
                labelFor = ::optionLabel,
                onSelect = { onChange(options.copy(camera = it)) }
            )
        }

        OptionSection(title = "Stil Preseti (opsiyonel)") {
            ChipRow(
                options = listOf("(yok)") + presets,
                selected = options.stylePreset ?: "(yok)",
                labelFor = { if (it == "(yok)") it else optionLabel(it) },
                onSelect = { onChange(options.copy(stylePreset = if (it == "(yok)") null else it)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ürün Koruma", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Açıkken ürünün rengi/deseni/logosu video boyunca korunur.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = options.productProtection,
                onCheckedChange = { onChange(options.copy(productProtection = it)) }
            )
        }

        OutlinedTextField(
            value = options.customPrompt,
            onValueChange = { onChange(options.copy(customPrompt = it)) },
            label = { Text("Ek açıklama (opsiyonel)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5
        )
    }
}

@Composable
private fun OptionSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    labelFor: (String) -> String = { it },
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(labelFor(value)) }
            )
        }
    }
}
