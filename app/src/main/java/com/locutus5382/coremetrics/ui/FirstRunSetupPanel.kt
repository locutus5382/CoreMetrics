package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Human-readable permission choices, shown only through explicit user action.
 * On Android 13+ notifications are required by the recorder's current UX.
 * Overlay and Usage Access remain optional. All data stays on this device.
 */
@Composable
internal fun FirstRunSetupPanel(
    notificationsGranted:Boolean,overlayGranted:Boolean,usageGranted:Boolean,
    onNotifications:()->Unit,onOverlay:()->Unit,onUsage:()->Unit,
    onHudTile:()->Unit,onDismiss:()->Unit
) {
    GlassPanel {
        Text("FIRST-TIME SETUP & PRIVACY",
            color=iceBlue,style=MaterialTheme.typography.titleSmall)
        Text("CoreMetrics is offline and has no Internet permission. "+
            "Enable only the permissions you need. No screen capture, "+
            "background game recording or automatic startup.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        PermissionStep(
            name="1 · Notifications",
            explanation="Required by CoreMetrics on Android 13+ for the "+
                "user-started foreground recorder and Stop & Save notification.",
            granted=notificationsGranted,
            button="Allow notifications",
            onClick=onNotifications)
        PermissionStep(
            name="2 · Display over other apps",
            explanation="Optional. Shows the floating READY / REC pill over Eden. "+
                "The overlay does not capture the screen.",
            granted=overlayGranted,button="Allow overlay",onClick=onOverlay)
        PermissionStep(
            name="3 · Usage access",
            explanation="Optional. Records the Android foreground app package "+
                "while sampling, not the game title within an emulator.",
            granted=usageGranted,button="Usage settings",onClick=onUsage)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick=onHudTile){Text("Add HUD tile")}
            TextButton(onClick=onDismiss){Text("Dismiss guide")}
        }
        Text("You can reopen this guide from Settings. Permissions can be "+
            "revoked in Android Settings; recordings remain locally stored.",
            color=coreSecondary,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PermissionStep(
    name:String,explanation:String,granted:Boolean,
    button:String,onClick:()->Unit
) {
    HorizontalDivider(color=coreBorder)
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f),
            verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(name,color=coreText,
                style=MaterialTheme.typography.labelLarge)
            Text(explanation,color=coreSecondary,
                style=MaterialTheme.typography.bodySmall)
            Text(if(granted)"✓ Granted" else if(name.startsWith("1"))
                "Required for REC on Android 13+" else "Optional",
                color=if(granted)coreMint else solarGold,
                style=MaterialTheme.typography.labelSmall)
        }
        if(!granted)OutlinedButton(onClick=onClick){Text(button)}
    }
}
