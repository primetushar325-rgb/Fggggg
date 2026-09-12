@file:Suppress("PackageDirectoryMismatch", "unused", "UNUSED_PARAMETER", "ClassName")

package androidx.compose.ui.text

import androidx.compose.ui.Sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

class TextStyle(
    val color: Color = Color(0L),
    val fontSize: Sp = Sp(14f),
    val fontWeight: FontWeight? = null,
    val fontFamily: FontFamily? = null,
    val letterSpacing: Sp = Sp(0f),
    val lineHeight: Sp = Sp(20f),
    val textAlign: TextAlign? = null,
)
