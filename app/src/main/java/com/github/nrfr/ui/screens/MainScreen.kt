package com.github.nrfr.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.nrfr.R
import com.github.nrfr.manager.CarrierConfigManager
import com.github.nrfr.model.SimCardInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onShowAbout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSimCard by remember { mutableStateOf<SimCardInfo?>(null) }
    var isSimCardMenuExpanded by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    fun refreshConfig(delayed: Boolean) {
        if (delayed) {
            scope.launch {
                delay(1000)
                refreshTrigger += 1
            }
        } else refreshTrigger += 1
    }

    val simCards = remember(context, refreshTrigger) {
        CarrierConfigManager.getSimCards(context)
    }

    LaunchedEffect(simCards, selectedSimCard) {
        selectedSimCard = when {
            selectedSimCard == null -> simCards.firstOrNull {
                it.carrierName.contains("CMLink", ignoreCase = true)
            } ?: simCards.firstOrNull()
            else -> simCards.find { it.subId == selectedSimCard?.subId } ?: simCards.firstOrNull()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            modifier = Modifier.size(40.dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Backup Calling 测试")
                    }
                },
                actions = {
                    IconButton(onClick = onShowAbout) {
                        Icon(Icons.Default.Info, contentDescription = "关于")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "目标：CMLink 正常提供流量，Vodafone 在无 Wi‑Fi 时通过 Backup Calling 工作。",
                style = MaterialTheme.typography.bodyMedium
            )

            SimCardSelector(
                simCards = simCards,
                selectedSimCard = selectedSimCard,
                isExpanded = isSimCardMenuExpanded,
                onExpandedChange = { isSimCardMenuExpanded = it },
                onSimCardSelected = { selectedSimCard = it }
            )

            selectedSimCard?.let { sim ->
                StatusCard(sim)

                Button(
                    onClick = {
                        try {
                            val delayed = CarrierConfigManager.setChinaMobile46000NonRoaming(
                                context,
                                sim.subId
                            )
                            Toast.makeText(context, "已尝试：46000 视为非漫游", Toast.LENGTH_SHORT).show()
                            refreshConfig(delayed)
                        } catch (e: Exception) {
                            Toast.makeText(context, "设置失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("① 将 46000 视为非漫游（推荐）")
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val delayed = CarrierConfigManager.setForceHomeNetwork(context, sim.subId)
                            Toast.makeText(context, "已尝试：Force Home", Toast.LENGTH_SHORT).show()
                            refreshConfig(delayed)
                        } catch (e: Exception) {
                            Toast.makeText(context, "设置失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("② Force Home（所有网络视为非漫游）")
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val delayed = CarrierConfigManager.resetCarrierConfig(context, sim.subId)
                            Toast.makeText(context, "已清除 CarrierConfig Override", Toast.LENGTH_SHORT).show()
                            refreshConfig(delayed)
                        } catch (e: Exception) {
                            Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("恢复默认")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("测试条件", style = MaterialTheme.typography.titleMedium)
                    Text("• 只对 CMLink 操作。")
                    Text("• Wi‑Fi 关闭，CMLink 作为默认移动数据卡。")
                    Text("• Vodafone 的 Wi‑Fi 通话保持开启。")
                    Text("• 三星“数据切换和备用通话”保持开启。")
                    Text("• 先试 46000，不行再试 Force Home。")
                    Text("• 这只改变 Android 的漫游判定，不改变运营商实际计费。")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimCardSelector(
    simCards: List<SimCardInfo>,
    selectedSimCard: SimCardInfo?,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSimCardSelected: (SimCardInfo) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = selectedSimCard?.let {
                "SIM ${it.slot} · ${it.carrierName} · subId ${it.subId}"
            } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("选择数据 SIM（选 CMLink）") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            simCards.forEach { sim ->
                DropdownMenuItem(
                    text = { Text("SIM ${sim.slot} · ${sim.carrierName} · subId ${sim.subId}") },
                    onClick = {
                        onSimCardSelected(sim)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusCard(sim: SimCardInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("当前读取到的配置", style = MaterialTheme.typography.titleMedium)
            Text("运营商：${sim.carrierName}")
            Text("逻辑 SIM：${sim.slot}")
            Text("subId：${sim.subId}")
            if (sim.currentConfig.isEmpty()) {
                Text("未读到配置")
            } else {
                sim.currentConfig.forEach { (key, value) ->
                    Text("$key：$value")
                }
            }
        }
    }
}
