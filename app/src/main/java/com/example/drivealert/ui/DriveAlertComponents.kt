package com.example.drivealert.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.drivealert.model.AccountRole
import com.example.drivealert.model.AlertSeverity
import com.example.drivealert.model.Route
import com.example.drivealert.model.SyncStatus
import com.example.drivealert.ui.theme.Border
import com.example.drivealert.ui.theme.DriveRed
import com.example.drivealert.ui.theme.DriveRedSoft
import com.example.drivealert.ui.theme.Info
import com.example.drivealert.ui.theme.InfoSoft
import com.example.drivealert.ui.theme.Ink
import com.example.drivealert.ui.theme.InkRaised
import com.example.drivealert.ui.theme.Success
import com.example.drivealert.ui.theme.SuccessSoft
import com.example.drivealert.ui.theme.Surface
import com.example.drivealert.ui.theme.TextMuted
import com.example.drivealert.ui.theme.TextPrimary
import com.example.drivealert.ui.theme.TextSecondary
import com.example.drivealert.ui.theme.Warning
import com.example.drivealert.ui.theme.WarningSoft

val PagePadding = 20.dp
val CardShape = RoundedCornerShape(16.dp)
val ControlShape = RoundedCornerShape(12.dp)

@Composable
fun DriveAlertLogo(modifier: Modifier = Modifier, compact: Boolean = false, iconOnly: Boolean = false) {
    Row(modifier = modifier.semantics { contentDescription = "DriveAlert logo" }, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(if (compact) 28.dp else 44.dp)) {
            val shield = Path().apply {
                moveTo(size.width * .5f, 0f)
                lineTo(size.width * .9f, size.height * .16f)
                lineTo(size.width * .82f, size.height * .67f)
                quadraticTo(size.width * .5f, size.height, size.width * .18f, size.height * .67f)
                lineTo(size.width * .1f, size.height * .16f)
                close()
            }
            drawPath(shield, DriveRed)
            val road = Path().apply {
                moveTo(size.width * .42f, size.height * .76f)
                lineTo(size.width * .47f, size.height * .3f)
                lineTo(size.width * .53f, size.height * .3f)
                lineTo(size.width * .58f, size.height * .76f)
                close()
            }
            drawPath(road, TextPrimary)
            drawLine(DriveRed, Offset(size.width * .5f, size.height * .38f), Offset(size.width * .5f, size.height * .52f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        if (!iconOnly) {
            Spacer(Modifier.width(10.dp))
            Column {
                Text("DriveAlert", style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!compact) Text("Drowsiness monitoring", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}

@Composable
fun AppPage(
    title: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    role: AccountRole? = null,
    currentRoute: String? = null,
    onNavigate: (String) -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (title != null) AppHeader(title, subtitle, onBack)
        val contentModifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PagePadding)
        Column(
            modifier = if (scroll) contentModifier.verticalScroll(rememberScrollState()) else contentModifier,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        if (role != null && currentRoute != null) BottomNavigation(role, currentRoute, onNavigate)
    }
}

@Composable
fun AppHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
            }
        } else {
            DriveAlertLogo(Modifier.padding(start = 8.dp), compact = true, iconOnly = true)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(containerColor = DriveRed, contentColor = TextPrimary, disabledContainerColor = DriveRedSoft, disabledContentColor = TextMuted),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = ControlShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Surface, contentColor = TextPrimary),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun StatusPill(label: String, color: Color, background: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        else Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SeverityPill(severity: AlertSeverity) {
    val (color, bg) = when (severity) {
        AlertSeverity.Normal -> Success to SuccessSoft
        AlertSeverity.Initial -> Info to InfoSoft
        AlertSeverity.Repeated -> Warning to WarningSoft
        AlertSeverity.Escalated -> DriveRed to DriveRedSoft
    }
    StatusPill(severity.name, color, bg)
}

@Composable
fun SyncPill(status: SyncStatus) {
    val label = when (status) {
        SyncStatus.NotEligible -> "Not Eligible"
        SyncStatus.Pending -> "Pending"
        SyncStatus.Synchronized -> "Synchronized"
        SyncStatus.Failed -> "Failed"
    }
    val (color, bg) = when (status) {
        SyncStatus.Synchronized -> Success to SuccessSoft
        SyncStatus.Pending -> Warning to WarningSoft
        SyncStatus.NotEligible -> TextSecondary to InkRaised
        SyncStatus.Failed -> DriveRed to DriveRedSoft
    }
    StatusPill(label, color, bg)
}

@Composable
fun LabeledValue(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(.46f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(.54f).padding(start = 12.dp))
    }
}

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.Info) {
    SectionCard(modifier) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        shape = ControlShape,
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(InkRaised), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = DriveRed) }
    }
}

@Composable
fun StepIndicator(current: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Step $current of $total", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { index ->
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (index < current) DriveRed else Border))
            }
        }
    }
}

@Composable
private fun BottomNavigation(role: AccountRole, currentRoute: String, onNavigate: (String) -> Unit) {
    data class Item(val label: String, val route: String, val icon: ImageVector)
    val items = if (role == AccountRole.Driver) listOf(
        Item("Home", Route.DriverHome, Icons.Rounded.Home),
        Item("Alerts", Route.Alerts, Icons.Rounded.History),
        Item("Contacts", Route.Contacts, Icons.Rounded.Groups),
        Item("Settings", Route.DriverSettings, Icons.Rounded.Settings),
    ) else listOf(
        Item("Requests", Route.PendingRequests, Icons.Rounded.Notifications),
        Item("Drivers", Route.ConnectedDrivers, Icons.Rounded.Groups),
        Item("Settings", Route.ContactSettings, Icons.Rounded.Settings),
    )
    NavigationBar(containerColor = InkRaised, tonalElevation = 0.dp) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { if (currentRoute != item.route) onNavigate(item.route) },
                icon = { Icon(item.icon, item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = DriveRed, selectedTextColor = DriveRed, indicatorColor = DriveRedSoft, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted),
            )
        }
    }
}

@Composable
fun LoadingBlock(label: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = DriveRed, strokeWidth = 3.dp)
        Text(label, color = TextSecondary)
    }
}

@Composable
fun Divider() = HorizontalDivider(color = Border, thickness = 1.dp)
