package com.lhordkent.drivealert.ui.postauth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.R
import com.lhordkent.drivealert.postauth.SharingState
import com.lhordkent.drivealert.postauth.WarningStage
import com.lhordkent.drivealert.ui.theme.Border
import com.lhordkent.drivealert.ui.theme.DriveRed
import com.lhordkent.drivealert.ui.theme.DriveRedSoft
import com.lhordkent.drivealert.ui.theme.Info
import com.lhordkent.drivealert.ui.theme.InfoSoft
import com.lhordkent.drivealert.ui.theme.Ink
import com.lhordkent.drivealert.ui.theme.InkRaised
import com.lhordkent.drivealert.ui.theme.Success
import com.lhordkent.drivealert.ui.theme.SuccessSoft
import com.lhordkent.drivealert.ui.theme.Surface
import com.lhordkent.drivealert.ui.theme.TextMuted
import com.lhordkent.drivealert.ui.theme.TextPrimary
import com.lhordkent.drivealert.ui.theme.TextSecondary
import com.lhordkent.drivealert.ui.theme.Warning
import com.lhordkent.drivealert.ui.theme.WarningSoft

object PostAuthTestTags {
    const val PRIMARY_ACTION = "post_auth_primary_action"
    const val BOTTOM_NAV = "post_auth_bottom_nav"
    const val INVITE_EMAIL = "invite_email"
}

data class AppNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0,
)

@Composable
fun PostAuthScaffold(
    title: String,
    navItems: List<AppNavItem>,
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().background(Ink),
        containerColor = Ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { DriveAlertTopBar(title = title, showLogo = true) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding().testTag(PostAuthTestTags.BOTTOM_NAV),
                containerColor = InkRaised,
                tonalElevation = 0.dp,
            ) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = item.route == selectedRoute,
                        onClick = { onNavigate(item.route) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = {
                            Text(
                                if (item.badgeCount > 0) "${item.label} (${item.badgeCount})" else item.label,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DriveRed,
                            selectedTextColor = DriveRed,
                            indicatorColor = DriveRedSoft,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                        ),
                    )
                }
            }
        },
        content = content,
    )
}

@Composable
fun DriveAlertTopBar(
    title: String,
    showLogo: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink)
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            onBack != null -> IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            showLogo -> {
                Image(
                    painter = painterResource(R.drawable.drivealert_logo),
                    contentDescription = "DriveAlert logo",
                    modifier = Modifier.size(38.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            else -> Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
fun ScrollableScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(Ink)) {
        DriveAlertTopBar(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            content = content,
        )
    }
}

@Composable
fun RootContent(
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .widthIn(max = 600.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp).testTag(PostAuthTestTags.PRIMARY_ACTION),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DriveRed,
            contentColor = TextPrimary,
            disabledContainerColor = DriveRedSoft,
            disabledContentColor = TextMuted,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun GroupSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun StatusPill(label: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val colors = when (tone) {
        StatusTone.SUCCESS -> SuccessSoft to Success
        StatusTone.WARNING -> WarningSoft to Warning
        StatusTone.ERROR -> DriveRedSoft to DriveRed
        StatusTone.INFO -> InfoSoft to Info
        StatusTone.NEUTRAL -> InkRaised to TextSecondary
    }
    val icon = when (tone) {
        StatusTone.SUCCESS -> Icons.Rounded.CheckCircle
        StatusTone.WARNING -> Icons.Rounded.Warning
        StatusTone.ERROR -> Icons.Rounded.Error
        StatusTone.INFO, StatusTone.NEUTRAL -> Icons.Rounded.Info
    }
    Row(
        modifier = modifier.background(colors.first, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.second, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.second)
    }
}

enum class StatusTone { SUCCESS, WARNING, ERROR, INFO, NEUTRAL }

@Composable
fun StagePill(stage: WarningStage) {
    StatusPill(
        label = stage.label,
        tone = when (stage) {
            WarningStage.STAGE_1 -> StatusTone.INFO
            WarningStage.STAGE_2 -> StatusTone.WARNING
            WarningStage.STAGE_3 -> StatusTone.ERROR
        },
    )
}

@Composable
fun SharingPill(state: SharingState) {
    StatusPill(
        label = state.label,
        tone = when (state) {
            SharingState.SHARED -> StatusTone.SUCCESS
            SharingState.PENDING -> StatusTone.WARNING
            SharingState.FAILED -> StatusTone.ERROR
            SharingState.NO_CONTACT, SharingState.NOT_ELIGIBLE -> StatusTone.NEUTRAL
        },
    )
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    destructive: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (destructive) DriveRed else TextPrimary)
            supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = DriveRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
    )
}
