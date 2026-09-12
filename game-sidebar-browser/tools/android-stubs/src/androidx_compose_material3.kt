@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName", "FunctionName")

package androidx.compose.material3

import androidx.compose.runtime.Composable
import androidx.compose.ui.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Sp

typealias TextStyle = androidx.compose.ui.text.TextStyle

class Typography(
    val displaySmall: TextStyle = TextStyle(),
    val headlineSmall: TextStyle = TextStyle(),
    val headlineMedium: TextStyle = TextStyle(),
    val titleLarge: TextStyle = TextStyle(),
    val titleMedium: TextStyle = TextStyle(),
    val titleSmall: TextStyle = TextStyle(),
    val bodyLarge: TextStyle = TextStyle(),
    val bodyMedium: TextStyle = TextStyle(),
    val bodySmall: TextStyle = TextStyle(),
    val labelLarge: TextStyle = TextStyle(),
    val labelSmall: TextStyle = TextStyle(),
)

class ColorScheme(
    val primary: Color = Color(0),
    val onPrimary: Color = Color(0),
    val secondary: Color = Color(0),
    val onSecondary: Color = Color(0),
    val background: Color = Color(0),
    val onBackground: Color = Color(0),
    val surface: Color = Color(0),
    val onSurface: Color = Color(0),
    val surfaceVariant: Color = Color(0),
    val onSurfaceVariant: Color = Color(0),
    val outline: Color = Color(0),
    val outlineVariant: Color = Color(0),
    val error: Color = Color(0),
    val onError: Color = Color(0),
)

fun darkColorScheme(
    primary: Color = Color(0),
    onPrimary: Color = Color(0),
    primaryContainer: Color = Color(0),
    secondary: Color = Color(0),
    onSecondary: Color = Color(0),
    background: Color = Color(0),
    onBackground: Color = Color(0),
    surface: Color = Color(0),
    onSurface: Color = Color(0),
    surfaceVariant: Color = Color(0),
    onSurfaceVariant: Color = Color(0),
    outline: Color = Color(0),
    outlineVariant: Color = Color(0),
    error: Color = Color(0),
    onPrimaryContainer: Color = Color(0),
    tertiary: Color = Color(0),
    onTertiary: Color = Color(0),
    onError: Color = Color(0),
    surfaceContainer: Color = Color(0),
): ColorScheme = ColorScheme()

object MaterialTheme {
    val typography: Typography = Typography()
    val colorScheme: ColorScheme = ColorScheme()

    @Composable
    operator fun invoke(
        colorScheme: ColorScheme = ColorScheme(),
        typography: Typography = Typography(),
        content: @Composable () -> Unit,
    ) {
    }
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0),
    style: TextStyle = TextStyle(),
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color(0),
) {
}

@Composable
fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {}

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null,
    label: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    textStyle: TextStyle = TextStyle(),
    minLines: Int = 1,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions(),
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions(),
) {
}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
}

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    color: Color = Color(0),
    shape: androidx.compose.ui.graphics.Shape? = null,
    content: @Composable () -> Unit,
) {
}

@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0),
    floatingActionButton: @Composable (() -> Unit)? = null,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
}

@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0),
    contentColor: Color = Color(0),
    content: @Composable () -> Unit,
) {
}

@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color(0),
) {
}

class PaddingValues {
    val left: Dp = Dp(0f)
    val top: Dp = Dp(0f)
    val right: Dp = Dp(0f)
    val bottom: Dp = Dp(0f)
}
