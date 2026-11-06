package ir.madreseyar.teacher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector

internal object TeacherPalette {
    val Navy = Color(0xFF063B5A)
    val DeepTeal = Color(0xFF087C87)
    val Teal = Color(0xFF079F9C)
    val TealBright = Color(0xFF20C7BC)
    val Background = Color(0xFFF1F7FB)
    val Text = Color(0xFF152E43)
    val Muted = Color(0xFF718294)
    val Border = Color(0xFFDCE8F0)
    val Blue = Color(0xFF2588E8)
    val Green = Color(0xFF16A36A)
    val Purple = Color(0xFF7953D2)
    val Orange = Color(0xFFEA941B)
    val Red = Color(0xFFE34D5B)
    val PaleBlue = Color(0xFFEAF4FF)
    val PaleGreen = Color(0xFFE8F7F1)
    val PalePurple = Color(0xFFF1EDFF)
    val PaleOrange = Color(0xFFFFF4E9)
}

@Composable
internal fun TeacherHeroHeader(
    title: String,
    subtitle: String,
    greeting: String? = null,
    greetingSubtitle: String? = null,
    dateText: String? = null,
    timeText: String? = null,
    notificationCount: Int = 0,
    onSearch: (() -> Unit)? = null,
    onNotifications: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
) {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(TeacherPalette.Navy, TeacherPalette.DeepTeal, Color(0xFF0A9293))))
            .statusBarsPadding()
    ) {
        SchoolBackdrop(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = Color.White.copy(alpha = .12f)) {
                    Icon(Icons.Default.School, null, tint = Color(0xFF65DED1), modifier = Modifier.padding(8.dp).size(25.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(.76f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onSearch != null) HeroActionIcon(Icons.Default.Search, "جست‌وجو", onSearch)
                if (onNotifications != null) {
                    Box {
                        HeroActionIcon(Icons.Default.NotificationsNone, "اعلان‌ها", onNotifications)
                        if (notificationCount > 0) {
                            Surface(Modifier.align(Alignment.TopEnd).size(9.dp), CircleShape, TeacherPalette.Red) {}
                        }
                    }
                }
                if (onRefresh != null) HeroActionIcon(Icons.Default.Refresh, "تازه‌سازی", onRefresh)
            }
            if (greeting == null && dateText.isNullOrBlank()) Spacer(Modifier.height(24.dp))

            if (greeting != null) {
                Spacer(Modifier.height(17.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(58.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFBDECE8), Color(0xFF4EBBB7))))
                            .border(2.dp, Color.White.copy(.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                            Text(
                                greeting.trim().split(Regex("\\s+")).takeLast(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                                color = TeacherPalette.Navy, fontWeight = FontWeight.Black, fontSize = 18.sp
                            )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(greeting, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!greetingSubtitle.isNullOrBlank()) Text(greetingSubtitle, color = Color.White.copy(.82f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (!dateText.isNullOrBlank()) {
                Spacer(Modifier.height(13.dp))
                Surface(color = Color.White.copy(.13f), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(dateText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (!timeText.isNullOrBlank()) Text(timeText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TeacherCompactHeader(
    title: String,
    subtitle: String,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(TeacherPalette.Navy, TeacherPalette.DeepTeal)))
            .statusBarsPadding()
    ) {
        SchoolBackdrop(Modifier.matchParentSize())
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(.12f)) {
                Icon(Icons.Default.School, null, tint = Color(0xFF65DED1), modifier = Modifier.padding(8.dp).size(24.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(.78f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            action?.invoke(this)
        }
    }
}

@Composable
private fun HeroActionIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        Modifier.padding(start = 5.dp).size(38.dp).clickable(onClick = onClick),
        RoundedCornerShape(12.dp), Color.White.copy(.12f)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun SchoolBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val wall = Color.White.copy(alpha = .105f)
        val window = Color(0xFFBCEFEA).copy(alpha = .22f)
        val baseY = h * .91f
        // A subtle school silhouette on the left, as in the approved visual reference.
        drawRoundRect(wall, Offset(w * .035f, h * .43f), Size(w * .31f, h * .47f), CornerRadius(18f, 18f))
        drawRect(wall.copy(alpha = .75f), Offset(w * .075f, h * .34f), Size(w * .23f, h * .10f))
        for (r in 0..2) for (c in 0..2) {
            drawRoundRect(window, Offset(w * (.075f + c * .075f), h * (.51f + r * .105f)), Size(w * .042f, h * .052f), CornerRadius(4f, 4f))
        }
        drawRect(Color.White.copy(alpha = .15f), Offset(w * .17f, h * .77f), Size(w * .055f, h * .13f))
        drawLine(Color.White.copy(alpha = .28f), Offset(w * .25f, h * .22f), Offset(w * .25f, h * .36f), 2.dp.toPx())
        drawLine(Color.White.copy(alpha = .25f), Offset(w * .25f, h * .23f), Offset(w * .31f, h * .26f), 2.dp.toPx())
        drawLine(Color.White.copy(alpha = .18f), Offset(0f, baseY), Offset(w * .42f, baseY), 2.dp.toPx())
        // Trees and soft foliage.
        listOf(Offset(w * .025f, h * .72f), Offset(w * .37f, h * .69f)).forEach { p ->
            drawLine(Color(0xFF8DE2C5).copy(.22f), Offset(p.x, p.y + h * .15f), Offset(p.x, p.y), 3.dp.toPx())
            drawCircle(Color(0xFF8DE2C5).copy(.13f), w * .045f, Offset(p.x, p.y - h * .015f))
            drawCircle(Color(0xFFB3F0D9).copy(.10f), w * .035f, Offset(p.x + w * .035f, p.y + h * .015f))
        }
    }
}

@Composable
internal fun TeacherPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TeacherPalette.Border.copy(alpha = .72f)),
    ) { Column(Modifier.padding(13.dp), content = content) }
}

@Composable
internal fun TeacherMetricTile(
    label: String,
    value: String,
    helper: String,
    icon: ImageVector,
    tint: Color,
    surface: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(19.dp),
        color = surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, color = TeacherPalette.Text, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1)
            Text(value, color = TeacherPalette.Text, fontWeight = FontWeight.Black, fontSize = 19.sp, maxLines = 1)
            Text(helper, color = TeacherPalette.Muted, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun TeacherSectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null, icon: ImageVector? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = TeacherPalette.DeepTeal, modifier = Modifier.size(20.dp))
        if (icon != null) Spacer(Modifier.width(7.dp))
        Text(title, color = TeacherPalette.Text, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
            Text(action, color = TeacherPalette.DeepTeal, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
    }
}

@Composable
internal fun TeacherQuickTile(title: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, TeacherPalette.Border),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(38.dp), RoundedCornerShape(12.dp), tint.copy(alpha = .10f)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(23.dp)) }
            }
            Spacer(Modifier.height(6.dp))
            Text(title, color = TeacherPalette.Text, fontWeight = FontWeight.Bold, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun TeacherTag(text: String, color: Color, background: Color = color.copy(alpha = .10f)) {
    Surface(shape = RoundedCornerShape(50), color = background) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun TeacherFilterChip(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) Color(0xFFE1F7F5) else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) TeacherPalette.Teal else TeacherPalette.Border),
        shadowElevation = if (selected) 2.dp else 1.dp,
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = if (selected) TeacherPalette.Teal else TeacherPalette.Muted, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)) }
            Text(label, color = if (selected) TeacherPalette.DeepTeal else TeacherPalette.Muted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun TeacherStudentAction(title: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Surface(
        Modifier.width(45.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = .075f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .13f)),
    ) {
        Column(Modifier.padding(vertical = 6.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(2.dp))
            Text(title, color = TeacherPalette.Text, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
internal fun TeacherInitialAvatar(name: String, modifier: Modifier = Modifier) {
    val initials = name.trim().split(Regex("\\s+")).takeLast(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").ifBlank { "؟" }
    Surface(modifier, CircleShape, Color(0xFFE3F1F8), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White), shadowElevation = 1.dp) {
        Box(contentAlignment = Alignment.Center) { Text(initials, color = TeacherPalette.DeepTeal, fontWeight = FontWeight.Black, fontSize = 13.sp) }
    }
}

@Composable
internal fun TeacherProgressLine(label: String, value: Int, tint: Color, suffix: String = "") {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = TeacherPalette.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("$value$suffix", color = tint, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFFEAF0F4))) {
            Box(Modifier.fillMaxWidth((value.coerceIn(0, 100)) / 100f).fillMaxHeight().clip(CircleShape).background(tint))
        }
    }
}
