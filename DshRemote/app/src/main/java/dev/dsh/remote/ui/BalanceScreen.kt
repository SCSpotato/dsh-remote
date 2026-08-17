package dev.dsh.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.dsh.remote.data.DeepseekBalanceInfo
import dev.dsh.remote.ui.icons.DshIcon
import dev.dsh.remote.ui.icons.DshIcons
import dev.dsh.remote.ui.theme.DshAmber
import dev.dsh.remote.ui.theme.DshGreen
import dev.dsh.remote.ui.theme.DshPrimary

@Composable
fun BalanceScreen(vm: AppViewModel, onBack: () -> Unit) {
    val apiKey by vm.deepseekApiKey.collectAsState()
    val balance by vm.deepseekBalance.collectAsState()
    val loading by vm.deepseekBalanceLoading.collectAsState()
    val error by vm.deepseekBalanceError.collectAsState()
    var keyInput by remember { mutableStateOf(apiKey) }
    val bal = balance

    LaunchedEffect(Unit) { if (apiKey.isNotBlank()) vm.queryDeepseekBalance() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                DshIcon(DshIcons.ChevronLeft, tint = MaterialTheme.colorScheme.onSurface, size = 22.dp, contentDescription = Strings.str("back"))
            }
            Text(Strings.str("balance_title"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))

        Text("API Key", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("sk-…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = {
                vm.setDeepseekApiKey(keyInput)
                vm.queryDeepseekBalance()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (loading) Strings.str("querying") else Strings.str("save_and_query"))
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading -> {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            bal != null -> {
                for (info in bal.balance_infos) {
                    BalanceCard(info)
                    Spacer(Modifier.height(16.dp))
                }
                if (bal.balance_infos.isEmpty()) {
                    Text(Strings.str("no_balance_info"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                Text(Strings.str("fill_key_then_query"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BalanceCard(info: DeepseekBalanceInfo) {
    val total = info.total_balance.toDoubleOrNull() ?: 0.0
    val granted = info.granted_balance.toDoubleOrNull() ?: 0.0
    val toppedUp = info.topped_up_balance.toDoubleOrNull() ?: 0.0

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(
            Strings.str("total_balance", info.currency),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            info.total_balance,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            Strings.str("available", if (balanceAvailable(info)) Strings.str("yes") else Strings.str("no")),
            color = if (balanceAvailable(info)) DshGreen else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(12.dp))

        // Horizontal stacked bar: granted (赠送) vs topped-up (充值).
        if (total > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp)),
            ) {
                val grantedFrac = (granted / total).toFloat().coerceIn(0f, 1f)
                val toppedFrac = (toppedUp / total).toFloat().coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(grantedFrac)
                        .background(DshAmber),
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(toppedFrac.coerceAtMost(1f - grantedFrac))
                        .background(DshPrimary),
                )
            }
            Spacer(Modifier.height(6.dp))
            LegendRow(Strings.str("granted_balance"), info.granted_balance, DshAmber)
            LegendRow(Strings.str("topped_up_balance"), info.topped_up_balance, DshPrimary)
        }
    }
}

@Composable
private fun LegendRow(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(10.dp)
                .height(10.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun balanceAvailable(info: DeepseekBalanceInfo): Boolean =
    (info.total_balance.toDoubleOrNull() ?: 0.0) > 0.0
