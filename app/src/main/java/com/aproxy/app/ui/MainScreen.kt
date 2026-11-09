package com.aproxy.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aproxy.app.R
import com.aproxy.app.proxy.EngineState
import com.aproxy.app.proxy.ProxyConfig
import com.aproxy.app.proxy.ProxyProtocol
import com.aproxy.app.proxy.TrafficState
import com.aproxy.app.ui.theme.AProxyPalette
import com.aproxy.app.util.BatteryUtils
import com.aproxy.app.util.CertificateUtils

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val config by viewModel.config.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val traffic by viewModel.traffic.collectAsStateWithLifecycle()
    val deviceIp by viewModel.deviceIp.collectAsStateWithLifecycle()

    var portDialogProtocol by rememberSaveable { mutableStateOf<String?>(null) }
    var batteryExempt by remember {
        mutableStateOf(BatteryUtils.isIgnoringOptimizations(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = BatteryUtils.isIgnoringOptimizations(context)
                viewModel.refreshIp()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        // Start anyway: the proxy works even if the notification is hidden.
        viewModel.start()
    }

    val startProxy = {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED

        if (needsNotificationPermission) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.start()
        }
    }

    val copyAddress: (String) -> Unit = { address ->
        clipboard.setText(AnnotatedString(address))
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        AProxyPalette.BackgroundTop,
                        AProxyPalette.Background,
                        Color(0xFF050911),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Header(engine)
            Spacer(Modifier.height(22.dp))

            TrafficCard(traffic, engine.clients)
            Spacer(Modifier.height(16.dp))

            ProtocolsCard(
                config = config,
                engine = engine,
                onToggle = viewModel::setProtocolEnabled,
                onPortClick = { portDialogProtocol = it.name },
                onShareCertificate = { CertificateUtils.share(context) },
            )
            Spacer(Modifier.height(16.dp))

            ConnectionCard(
                ip = deviceIp,
                config = config,
                onCopy = copyAddress,
            )
            Spacer(Modifier.height(16.dp))

            BatteryCard(
                exempt = batteryExempt,
                onRequest = {
                    BatteryUtils.openFirst(context, BatteryUtils.exemptionIntents(context))
                },
            )

            if (!engine.error.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                ErrorCard(engine.error.orEmpty())
            }

            Spacer(Modifier.height(22.dp))
            ActionButton(
                running = engine.running,
                starting = engine.starting,
                canStart = config.enabledCount > 0,
                onStart = startProxy,
                onStop = viewModel::stop,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.info_footer),
                style = MaterialTheme.typography.bodySmall,
                color = AProxyPalette.TextMuted.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    val dialogProtocol = portDialogProtocol
        ?.let { name -> ProxyProtocol.entries.firstOrNull { it.name == name } }
    if (dialogProtocol != null) {
        PortDialog(
            protocol = dialogProtocol,
            currentPort = config.portOf(dialogProtocol),
            usedPorts = config.usedPorts(except = dialogProtocol),
            onDismiss = { portDialogProtocol = null },
            onConfirm = { port ->
                viewModel.setPort(dialogProtocol, port)
                portDialogProtocol = null
            },
        )
    }
}

@Composable
private fun Header(state: EngineState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AProxyPalette.Cyan, AProxyPalette.Violet),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = null,
                tint = AProxyPalette.Background,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = AProxyPalette.Text,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = AProxyPalette.TextMuted,
            )
        }
        StatusPill(state)
    }
}

@Composable
private fun StatusPill(state: EngineState) {
    val color = when {
        state.starting -> AProxyPalette.Amber
        state.running -> AProxyPalette.Mint
        else -> AProxyPalette.TextMuted
    }
    val label = when {
        state.starting -> stringResource(R.string.starting)
        state.running -> stringResource(R.string.status_running)
        else -> stringResource(R.string.status_stopped)
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun TrafficCard(traffic: TrafficState, clients: Int) {
    GlassCard {
        Row(Modifier.fillMaxWidth()) {
            SpeedStat(
                icon = Icons.Rounded.ArrowDownward,
                label = stringResource(R.string.download),
                value = formatSpeed(traffic.downloadSpeed),
                color = AProxyPalette.Cyan,
                modifier = Modifier.weight(1f),
            )
            SpeedStat(
                icon = Icons.Rounded.ArrowUpward,
                label = stringResource(R.string.upload),
                value = formatSpeed(traffic.uploadSpeed),
                color = AProxyPalette.Violet,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        ) {
            TrafficChart(
                history = traffic.history,
                modifier = Modifier.fillMaxSize(),
            )
            if (traffic.history.isEmpty()) {
                Text(
                    text = stringResource(R.string.traffic_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = AProxyPalette.TextMuted,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.last_60),
                style = MaterialTheme.typography.labelSmall,
                color = AProxyPalette.TextMuted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.clients, clients),
                style = MaterialTheme.typography.labelSmall,
                color = AProxyPalette.TextMuted,
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = AProxyPalette.Outline.copy(alpha = 0.55f))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.total),
                style = MaterialTheme.typography.labelMedium,
                color = AProxyPalette.TextMuted,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "↓ ${formatBytes(traffic.totalDownload)}",
                style = MaterialTheme.typography.bodySmall,
                color = AProxyPalette.Cyan,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "↑ ${formatBytes(traffic.totalUpload)}",
                style = MaterialTheme.typography.bodySmall,
                color = AProxyPalette.Violet,
            )
        }
    }
}

@Composable
private fun SpeedStat(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = AProxyPalette.TextMuted,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = AProxyPalette.Text,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProtocolsCard(
    config: ProxyConfig,
    engine: EngineState,
    onToggle: (ProxyProtocol, Boolean) -> Unit,
    onPortClick: (ProxyProtocol) -> Unit,
    onShareCertificate: () -> Unit,
) {
    GlassCard {
        SectionHeader(
            title = stringResource(R.string.servers),
            subtitle = stringResource(R.string.servers_hint),
        )
        Spacer(Modifier.height(16.dp))

        ProtocolRow(
            name = stringResource(R.string.http_name),
            description = stringResource(R.string.http_desc),
            icon = Icons.Rounded.Language,
            tint = AProxyPalette.Cyan,
            enabled = config.httpEnabled,
            port = config.httpPort,
            active = ProxyProtocol.HTTP in engine.active,
            canDisable = config.enabledCount > 1,
            onToggle = { onToggle(ProxyProtocol.HTTP, it) },
            onPortClick = { onPortClick(ProxyProtocol.HTTP) },
        )
        ProtocolDivider()
        ProtocolRow(
            name = stringResource(R.string.https_name),
            description = stringResource(R.string.https_desc),
            icon = Icons.Rounded.Lock,
            tint = AProxyPalette.Mint,
            enabled = config.httpsEnabled,
            port = config.httpsPort,
            active = ProxyProtocol.HTTPS in engine.active,
            canDisable = config.enabledCount > 1,
            onToggle = { onToggle(ProxyProtocol.HTTPS, it) },
            onPortClick = { onPortClick(ProxyProtocol.HTTPS) },
        )
        ProtocolDivider()
        ProtocolRow(
            name = stringResource(R.string.socks_name),
            description = stringResource(R.string.socks_desc),
            icon = Icons.Rounded.Router,
            tint = AProxyPalette.Violet,
            enabled = config.socksEnabled,
            port = config.socksPort,
            active = ProxyProtocol.SOCKS5 in engine.active,
            canDisable = config.enabledCount > 1,
            onToggle = { onToggle(ProxyProtocol.SOCKS5, it) },
            onPortClick = { onPortClick(ProxyProtocol.SOCKS5) },
        )

        if (config.httpsEnabled) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.https_warning),
                style = MaterialTheme.typography.bodySmall,
                color = AProxyPalette.Amber.copy(alpha = 0.9f),
            )
            TextButton(
                onClick = onShareCertificate,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(R.string.share_cert),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ProtocolRow(
    name: String,
    description: String,
    icon: ImageVector,
    tint: Color,
    enabled: Boolean,
    port: Int,
    active: Boolean,
    canDisable: Boolean,
    onToggle: (Boolean) -> Unit,
    onPortClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = AProxyPalette.Text,
                )
                if (active) {
                    Spacer(Modifier.width(7.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AProxyPalette.Mint),
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = AProxyPalette.TextMuted,
            )
            Spacer(Modifier.height(6.dp))
            PortChip(port = port, tint = tint, onClick = onPortClick)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            enabled = canDisable || !enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AProxyPalette.Background,
                checkedTrackColor = tint,
                uncheckedThumbColor = AProxyPalette.TextMuted,
                uncheckedTrackColor = AProxyPalette.SurfaceVariant,
                uncheckedBorderColor = AProxyPalette.Outline,
            ),
        )
    }
}

@Composable
private fun PortChip(port: Int, tint: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = AProxyPalette.SurfaceVariant.copy(alpha = 0.9f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ":$port",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = tint,
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.port),
                tint = AProxyPalette.TextMuted.copy(alpha = 0.8f),
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun ProtocolDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 14.dp),
        color = AProxyPalette.Outline.copy(alpha = 0.5f),
    )
}

@Composable
private fun ConnectionCard(
    ip: String?,
    config: ProxyConfig,
    onCopy: (String) -> Unit,
) {
    val context = LocalContext.current

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AProxyPalette.Mint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WifiTethering,
                    contentDescription = null,
                    tint = AProxyPalette.Mint,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.connection_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = AProxyPalette.Text,
                )
                Text(
                    text = if (ip != null) {
                        stringResource(R.string.local_ip, ip)
                    } else {
                        stringResource(R.string.connect_hint_no_ip)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AProxyPalette.TextMuted,
                )
            }
        }

        if (ip != null) {
            Spacer(Modifier.height(10.dp))
            ProxyProtocol.entries
                .filter { config.isEnabled(it) }
                .forEach { protocol ->
                    AddressRow(
                        protocol = protocol.name,
                        address = "$ip:${config.portOf(protocol)}",
                        onCopy = onCopy,
                    )
                }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.connect_hint),
            style = MaterialTheme.typography.bodySmall,
            color = AProxyPalette.TextMuted,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = {
                BatteryUtils.openFirst(context, BatteryUtils.hotspotIntents())
            },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiTethering,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = stringResource(R.string.hotspot_button),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun AddressRow(
    protocol: String,
    address: String,
    onCopy: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCopy(address) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = protocol,
            style = MaterialTheme.typography.labelMedium,
            color = AProxyPalette.TextMuted,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = address,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = AProxyPalette.Text,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = stringResource(R.string.copy_address),
            tint = AProxyPalette.TextMuted,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun BatteryCard(
    exempt: Boolean,
    onRequest: () -> Unit,
) {
    if (exempt) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = AProxyPalette.Mint,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.battery_ok),
                    style = MaterialTheme.typography.titleSmall,
                    color = AProxyPalette.Text,
                )
            }
        }
    } else {
        GlassCard(
            borderColor = AProxyPalette.Amber.copy(alpha = 0.45f),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AProxyPalette.Amber.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryAlert,
                        contentDescription = null,
                        tint = AProxyPalette.Amber,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.battery_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = AProxyPalette.Text,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.battery_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = AProxyPalette.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AProxyPalette.Amber,
                    contentColor = AProxyPalette.Background,
                ),
            ) {
                Text(
                    text = stringResource(R.string.battery_button),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    GlassCard(borderColor = AProxyPalette.Rose.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = AProxyPalette.Rose,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.start_error),
                    style = MaterialTheme.typography.titleSmall,
                    color = AProxyPalette.Rose,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = AProxyPalette.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    running: Boolean,
    starting: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    if (running) {
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, AProxyPalette.Rose.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AProxyPalette.Rose,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = stringResource(R.string.stop),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    } else {
        Button(
            onClick = onStart,
            enabled = canStart && !starting,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AProxyPalette.Cyan,
                contentColor = AProxyPalette.Background,
                disabledContainerColor = AProxyPalette.Cyan.copy(alpha = 0.4f),
                disabledContentColor = AProxyPalette.Background.copy(alpha = 0.7f),
            ),
        ) {
            if (starting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = AProxyPalette.Background,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.starting),
                    style = MaterialTheme.typography.titleSmall,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.start),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AProxyPalette.Text,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = AProxyPalette.TextMuted,
        )
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = AProxyPalette.Outline.copy(alpha = 0.75f),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = AProxyPalette.Surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content,
        )
    }
}
