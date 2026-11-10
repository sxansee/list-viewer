package app.fileviewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Icon from the app's own vector set (res/drawable/ic_*.xml). */
@Composable
fun Ico(res: Int, desc: String? = null, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painterResource(res), desc, modifier, tint)
}

/** The launcher icon as a view: teal gradient tile with the sheet-and-list mark. */
@Composable
fun LogoTile(size: Dp, modifier: Modifier = Modifier, desc: String? = null) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(Brush.linearGradient(listOf(Color(0xFF0E7C7B), Color(0xFF063F44)))),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(R.drawable.ic_logo_mark), desc, Modifier.fillMaxSize())
    }
}

/** Rounded tile with a short label (the file extension), tinted with the colour of the file family. */
@Composable
fun ExtTile(label: String, color: Color, size: Dp = 46.dp) {
    val dark = isSystemInDarkTheme()
    val fg = if (dark) lerp(color, Color.White, 0.45f) else color
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = if (dark) 0.22f else 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

@Composable
fun FileTile(info: Info, size: Dp = 46.dp) {
    ExtTile(info.ext.ifEmpty { "?" }.uppercase().take(4), info.cat.color, size)
}

/** Centered "something went wrong" panel with optional action buttons below the text. */
@Composable
fun MessageState(
    iconRes: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Ico(iconRes, null, Modifier.size(32.dp), MaterialTheme.colorScheme.onErrorContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        actions()
    }
}
