package com.drivealert.mvp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drivealert.mvp.R
import com.drivealert.mvp.ui.theme.Border
import com.drivealert.mvp.ui.theme.DriveRed
import com.drivealert.mvp.ui.theme.DriveRedSoft
import com.drivealert.mvp.ui.theme.Info
import com.drivealert.mvp.ui.theme.InfoSoft
import com.drivealert.mvp.ui.theme.Ink
import com.drivealert.mvp.ui.theme.InkRaised
import com.drivealert.mvp.ui.theme.Success
import com.drivealert.mvp.ui.theme.SuccessSoft
import com.drivealert.mvp.ui.theme.Surface
import com.drivealert.mvp.ui.theme.TextMuted
import com.drivealert.mvp.ui.theme.TextPrimary
import com.drivealert.mvp.ui.theme.TextSecondary
import com.drivealert.mvp.ui.theme.Warning
import com.drivealert.mvp.ui.theme.WarningSoft

@Composable
fun AppPage(
    horizontalPadding: Int = 24,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding.dp, vertical = 24.dp),
            content = content,
        )
    }
}

@Composable
fun BrandHeader(prominent: Boolean = false) {
    if (prominent) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.drivealert_logo),
                contentDescription = "DriveAlert logo",
                modifier = Modifier.size(112.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text("DriveAlert", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("LED device control", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.drivealert_logo),
                contentDescription = "DriveAlert logo",
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("DriveAlert", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("LED device control", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
fun ScreenTitle(title: String, description: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        color = TextPrimary,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(8.dp))
    Text(description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
}

@Composable
fun PrimaryButton(text: String, loading: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DriveRed,
            contentColor = TextPrimary,
            disabledContainerColor = DriveRedSoft,
            disabledContentColor = TextMuted,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = TextPrimary, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(if (loading) "Please wait" else text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary, disabledContentColor = TextMuted),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun GroupSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

enum class StatusTone { SUCCESS, WARNING, ERROR, INFO, NEUTRAL }

@Composable
fun StatusPill(label: String, tone: StatusTone) {
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
        Modifier.background(colors.first, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.second, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.second)
    }
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
fun FeedbackBanner(message: String, isError: Boolean) {
    val background = if (isError) DriveRedSoft else SuccessSoft
    val foreground = if (isError) DriveRed else Success
    Row(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (isError) Icons.Rounded.Error else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(20.dp),
        )
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
    }
}
