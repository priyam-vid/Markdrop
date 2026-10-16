package com.markdrop.app.ui.screens.settings

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.markdrop.app.ui.theme.SeedColors
import com.markdrop.app.util.HapticHelper
import com.markdrop.app.util.HapticType
import kotlin.math.*

@Composable
fun ColorPaletteGrid(
    selectedHex: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var showCustomDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            // Presets
            items(SeedColors) { seedColor ->
                ColorSwatch(
                    color = seedColor.color,
                    isSelected = seedColor.hex.equals(selectedHex, ignoreCase = true),
                    onClick = {
                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                        onColorSelected(seedColor.hex)
                    }
                )
            }

            // Expressive Gradient Circular Button for Custom Color placed at the END of presets
            item {
                CustomGradientColorSwatch(
                    onClick = {
                        HapticHelper.performHaptic(view, HapticType.LIGHT, true)
                        showCustomDialog = true
                    }
                )
            }
        }
    }

    if (showCustomDialog) {
        CustomColorWheelDialog(
            initialHex = selectedHex,
            onDismiss = { showCustomDialog = false },
            onColorConfirmed = { hex ->
                onColorSelected(hex)
                showCustomDialog = false
            }
        )
    }
}

@Composable
fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White
            )
        }
    }
}

@Composable
fun CustomGradientColorSwatch(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbowColors = listOf(
        Color(0xFFFF0000),
        Color(0xFFFF7F00),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF0000FF),
        Color(0xFF4B0082),
        Color(0xFF9400D3),
        Color(0xFFFF0000)
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Brush.sweepGradient(rainbowColors))
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "Custom Color Wheel",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CustomColorWheelDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onColorConfirmed: (String) -> Unit
) {
    val initialColor = remember(initialHex) {
        try {
            Color(AndroidColor.parseColor(if (initialHex.startsWith("#")) initialHex else "#$initialHex"))
        } catch (_: Exception) {
            Color(0xFF6750A4)
        }
    }

    var hue by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(1f) }
    var value by remember { mutableStateOf(1f) }

    // Initialize HSV from initial color
    LaunchedEffect(initialHex) {
        val hsv = FloatArray(3)
        val colorInt = try {
            AndroidColor.parseColor(if (initialHex.startsWith("#")) initialHex else "#$initialHex")
        } catch (_: Exception) {
            AndroidColor.parseColor("#6750A4")
        }
        AndroidColor.colorToHSV(colorInt, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    val currentColor = Color.hsv(hue, saturation, value)

    fun colorToHex(c: Color): String {
        val r = (c.red * 255).roundToInt().coerceIn(0, 255)
        val g = (c.green * 255).roundToInt().coerceIn(0, 255)
        val b = (c.blue * 255).roundToInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    var hexText by remember(currentColor) {
        mutableStateOf(colorToHex(currentColor).removePrefix("#"))
    }
    var redText by remember(currentColor) {
        mutableStateOf((currentColor.red * 255).roundToInt().coerceIn(0, 255).toString())
    }
    var greenText by remember(currentColor) {
        mutableStateOf((currentColor.green * 255).roundToInt().coerceIn(0, 255).toString())
    }
    var blueText by remember(currentColor) {
        mutableStateOf((currentColor.blue * 255).roundToInt().coerceIn(0, 255).toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Theme Color Studio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Side-by-side color preview (Previous vs. New)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Previous", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(initialColor)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(colorToHex(initialColor), style = MaterialTheme.typography.labelSmall)
                    }

                    Text("→", style = MaterialTheme.typography.titleLarge)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("New", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(currentColor)
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(colorToHex(currentColor), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                // Interactive HSV Color Wheel Canvas
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                fun handlePointer(offset: Offset) {
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val dx = offset.x - centerX
                                    val dy = offset.y - centerY
                                    val radius = size.width / 2f

                                    val dist = sqrt(dx * dx + dy * dy)
                                    saturation = (dist / radius).coerceIn(0f, 1f)

                                    val angleRad = atan2(dy.toDouble(), dx.toDouble())
                                    var angleDeg = Math.toDegrees(angleRad).toFloat()
                                    if (angleDeg < 0) angleDeg += 360f
                                    hue = angleDeg.coerceIn(0f, 360f)
                                }

                                detectTapGestures { offset -> handlePointer(offset) }
                            }
                            .pointerInput(Unit) {
                                fun handlePointer(offset: Offset) {
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val dx = offset.x - centerX
                                    val dy = offset.y - centerY
                                    val radius = size.width / 2f

                                    val dist = sqrt(dx * dx + dy * dy)
                                    saturation = (dist / radius).coerceIn(0f, 1f)

                                    val angleRad = atan2(dy.toDouble(), dx.toDouble())
                                    var angleDeg = Math.toDegrees(angleRad).toFloat()
                                    if (angleDeg < 0) angleDeg += 360f
                                    hue = angleDeg.coerceIn(0f, 360f)
                                }

                                detectDragGestures { change, _ ->
                                    handlePointer(change.position)
                                }
                            }
                    ) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val radius = size.width / 2f

                        // Draw sweep gradient for Hue
                        val sweepColors = listOf(
                            Color.Red, Color.Yellow, Color.Green,
                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                        )
                        drawCircle(
                            brush = Brush.sweepGradient(sweepColors, center = Offset(centerX, centerY)),
                            radius = radius,
                            center = Offset(centerX, centerY)
                        )

                        // Overlay radial white gradient for Saturation
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 1f), Color.Transparent),
                                center = Offset(centerX, centerY),
                                radius = radius
                            ),
                            radius = radius,
                            center = Offset(centerX, centerY)
                        )

                        // Overlay black gradient according to value/brightness
                        if (value < 1f) {
                            drawCircle(
                                color = Color.Black.copy(alpha = 1f - value),
                                radius = radius,
                                center = Offset(centerX, centerY)
                            )
                        }

                        // Draw selector handle
                        val selectorAngleRad = Math.toRadians(hue.toDouble())
                        val selectorDist = saturation * radius
                        val selX = centerX + (selectorDist * cos(selectorAngleRad)).toFloat()
                        val selY = centerY + (selectorDist * sin(selectorAngleRad)).toFloat()

                        drawCircle(
                            color = Color.Black.copy(alpha = 0.4f),
                            radius = 12.dp.toPx(),
                            center = Offset(selX, selY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 10.dp.toPx(),
                            center = Offset(selX, selY),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = currentColor,
                            radius = 7.dp.toPx(),
                            center = Offset(selX, selY)
                        )
                    }
                }

                // Hue Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Hue", style = MaterialTheme.typography.bodySmall)
                        Text("${hue.roundToInt()}°", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Value / Brightness Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Brightness / Lightness", style = MaterialTheme.typography.bodySmall)
                        Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Synchronized HEX and RGB inputs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val textFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )

                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
                            hexText = clean
                            if (clean.length == 6) {
                                try {
                                    val parsed = AndroidColor.parseColor("#$clean")
                                    val hsv = FloatArray(3)
                                    AndroidColor.colorToHSV(parsed, hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    value = hsv[2]
                                } catch (_: Exception) {}
                            }
                        },
                        label = { Text("HEX") },
                        prefix = { Text("#") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = textFieldColors
                    )

                    OutlinedTextField(
                        value = redText,
                        onValueChange = { input ->
                            val num = input.filter { it.isDigit() }.take(3)
                            redText = num
                            val r = num.toIntOrNull()?.coerceIn(0, 255)
                            val g = greenText.toIntOrNull()?.coerceIn(0, 255)
                            val b = blueText.toIntOrNull()?.coerceIn(0, 255)
                            if (r != null && g != null && b != null) {
                                val hsv = FloatArray(3)
                                AndroidColor.RGBToHSV(r, g, b, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            }
                        },
                        label = { Text("R") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        colors = textFieldColors
                    )

                    OutlinedTextField(
                        value = greenText,
                        onValueChange = { input ->
                            val num = input.filter { it.isDigit() }.take(3)
                            greenText = num
                            val r = redText.toIntOrNull()?.coerceIn(0, 255)
                            val g = num.toIntOrNull()?.coerceIn(0, 255)
                            val b = blueText.toIntOrNull()?.coerceIn(0, 255)
                            if (r != null && g != null && b != null) {
                                val hsv = FloatArray(3)
                                AndroidColor.RGBToHSV(r, g, b, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            }
                        },
                        label = { Text("G") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        colors = textFieldColors
                    )

                    OutlinedTextField(
                        value = blueText,
                        onValueChange = { input ->
                            val num = input.filter { it.isDigit() }.take(3)
                            blueText = num
                            val r = redText.toIntOrNull()?.coerceIn(0, 255)
                            val g = greenText.toIntOrNull()?.coerceIn(0, 255)
                            val b = num.toIntOrNull()?.coerceIn(0, 255)
                            if (r != null && g != null && b != null) {
                                val hsv = FloatArray(3)
                                AndroidColor.RGBToHSV(r, g, b, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            }
                        },
                        label = { Text("B") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        colors = textFieldColors
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorConfirmed(colorToHex(currentColor)) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply Theme")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
