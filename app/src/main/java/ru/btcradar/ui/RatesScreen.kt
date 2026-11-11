package ru.btcradar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.btcradar.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatesScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onAmountRub: (Double) -> Unit,
    onAmountBtc: (Double) -> Unit,
    onHideUnusable: (Boolean) -> Unit,
    onOpenSources: () -> Unit,
    onOpenTor: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenOffer: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopBar(state, onRefresh, onOpenSources, onOpenTor, onOpenAlerts, onOpenBrowser, onOpenVault)
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            AmountPanel(state, onAmountRub, onAmountBtc, onHideUnusable)

            val buy = state.scan.offers
                .filter { it.direction == Direction.BUY_BTC }
                .rankFor(state.amountRub, state.hideUnusable)
            val sell = state.scan.offers
                .filter { it.direction == Direction.SELL_BTC }
                .rankFor(state.amountBtc, state.hideUnusable)

            BoxWithConstraints(Modifier.weight(1f)) {
                // На планшете/в альбоме — две колонки рядом, как просили.
                // На узком телефоне две колонки нечитаемы, поэтому вкладки.
                val wide = maxWidth >= 600.dp

                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        OfferColumn(
                            title = "Купить BTC  (₽ → BTC)",
                            subtitle = "дешевле = выше",
                            accent = Good,
                            offers = buy,
                            amountIn = state.amountRub,
                            spot = state.scan.spot,
                            onOpenOffer = onOpenOffer,
                            modifier = Modifier.weight(1f)
                        )
                        VerticalDivider()
                        OfferColumn(
                            title = "Продать BTC  (BTC → ₽)",
                            subtitle = "дороже = выше",
                            accent = Warn,
                            offers = sell,
                            amountIn = state.amountBtc,
                            spot = state.scan.spot,
                            onOpenOffer = onOpenOffer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    var tab by remember { mutableIntStateOf(0) }
                    Column(Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = tab) {
                            Tab(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                text = { Text("Купить BTC (${buy.size})", fontSize = 13.sp) }
                            )
                            Tab(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                text = { Text("Продать BTC (${sell.size})", fontSize = 13.sp) }
                            )
                        }
                        if (tab == 0) {
                            OfferColumn(
                                title = "Купить BTC  (₽ → BTC)",
                                subtitle = "дешевле = выше",
                                accent = Good,
                                offers = buy,
                                amountIn = state.amountRub,
                                spot = state.scan.spot,
                                onOpenOffer = onOpenOffer,
                                showHeader = false,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            OfferColumn(
                                title = "Продать BTC  (BTC → ₽)",
                                subtitle = "дороже = выше",
                                accent = Warn,
                                offers = sell,
                                amountIn = state.amountBtc,
                                spot = state.scan.spot,
                                onOpenOffer = onOpenOffer,
                                showHeader = false,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            StatusStrip(state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    state: UiState,
    onRefresh: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenTor: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenVault: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text("BTC/RUB Radar", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val spot = state.scan.spot
                Text(
                    if (spot != null) "спот ${fmtRub(spot.rubPerBtc)} ₽  ·  ${fmtAgo(state.scan.scannedAtMs)}"
                    else "спот недоступен  ·  ${fmtAgo(state.scan.scannedAtMs)}",
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            TorChip(state.tor, onOpenTor)
            IconButton(onClick = onOpenBrowser) {
                Icon(Icons.Default.Public, "Браузер")
            }
            IconButton(onClick = onOpenVault) {
                Icon(Icons.Default.Lock, "Сейф")
            }
            IconButton(onClick = onOpenAlerts) {
                val active = state.alerts.count { it.enabled }
                if (active > 0) {
                    BadgedBox(badge = { Badge { Text("$active") } }) {
                        Icon(Icons.Default.NotificationsActive, "Алерты")
                    }
                } else {
                    Icon(Icons.Default.Notifications, "Алерты")
                }
            }
            IconButton(onClick = onOpenSources) {
                Icon(Icons.Default.Tune, "Источники")
            }
            IconButton(onClick = onRefresh, enabled = !state.scanning) {
                if (state.scanning) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, "Обновить")
                }
            }
        }
    )
}

@Composable
private fun TorChip(tor: TorState, onClick: () -> Unit) {
    val (label, color) = when {
        tor.verified -> "TOR ✓" to TorPurple
        tor.live -> "TOR ?" to Warn
        else -> "TOR ✕" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MonoSmall, color = color) },
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        colors = AssistChipDefaults.assistChipColors(containerColor = Color.Transparent),
        modifier = Modifier.padding(end = 4.dp)
    )
}

@Composable
private fun AmountPanel(
    state: UiState,
    onAmountRub: (Double) -> Unit,
    onAmountBtc: (Double) -> Unit,
    onHideUnusable: (Boolean) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AmountField(
                    label = "Сумма, ₽",
                    value = state.amountRub,
                    onValue = onAmountRub,
                    modifier = Modifier.weight(1f)
                )
                AmountField(
                    label = "Сумма, BTC",
                    value = state.amountBtc,
                    onValue = onAmountBtc,
                    decimals = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.hideUnusable, onCheckedChange = onHideUnusable)
                Text(
                    "Прятать непроходные по лимитам и резерву",
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onHideUnusable(!state.hideUnusable) }
                )
            }
        }
    }
}

@Composable
private fun AmountField(
    label: String,
    value: Double,
    onValue: (Double) -> Unit,
    decimals: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Ключа у remember намеренно НЕТ. С remember(value) состояние пересоздавалось бы
    // на каждом нажатии (ввод -> onValue -> новый value -> сброс text),
    // и курсор прыгал бы в конец, а "0." нельзя было бы набрать вообще.
    var text by remember {
        mutableStateOf(if (decimals) fmtBtc(value) else value.toLong().toString())
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            raw.replace(',', '.').toDoubleOrNull()
                ?.takeIf { it > 0 && it.isFinite() }
                ?.let(onValue)
        },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        textStyle = MonoNumber,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
private fun OfferColumn(
    title: String,
    subtitle: String,
    accent: Color,
    offers: List<Offer>,
    amountIn: Double,
    spot: SpotPrice?,
    onOpenOffer: (String) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true
) {
    Column(modifier) {
        if (showHeader) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(accent.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accent)
                Text(subtitle, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (offers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Пусто.\nДобавь источники\nи нажми обновить.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val best = offers.firstOrNull { it.isUsable(amountIn) }?.output(amountIn) ?: 0.0
                // Без key: имя обменника не уникально (у одного источника легко
                // окажутся две строки с одинаковым названием), а дубликат ключа
                // в LazyColumn — это краш, а не просто кривая анимация.
                items(offers) { offer ->
                    OfferCard(offer, amountIn, spot, best, accent, onOpenOffer)
                }
            }
        }
    }
}

@Composable
private fun OfferCard(
    offer: Offer,
    amountIn: Double,
    spot: SpotPrice?,
    bestOutput: Double,
    accent: Color,
    onOpenOffer: (String) -> Unit
) {
    val usable = offer.isUsable(amountIn)
    val output = offer.output(amountIn)
    // Насколько хуже лучшего предложения — главное число для принятия решения
    val lossPct = if (bestOutput > 0 && output > 0) (output - bestOutput) / bestOutput * 100.0 else null
    val isBest = usable && bestOutput > 0 && output >= bestOutput

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (usable) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
        ),
        border = if (isBest) BorderStroke(1.5.dp, accent) else null,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { offer.url?.let(onOpenOffer) }
    ) {
        Column(Modifier.padding(8.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBest) {
                    Text("ТОП", style = MonoSmall, color = accent, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    offer.exchangerName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (offer.transport == Transport.TOR) {
                    Text("TOR", style = MonoSmall, color = TorPurple, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(3.dp))

            // Эффективный курс — с учётом комиссии, а не витринный
            Text(
                "${fmtRub(offer.effectiveRubPerBtc())} ₽/BTC",
                style = MonoNumber,
                color = if (usable) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Сколько реально получишь на руки
            Text(
                buildString {
                    append("получишь ")
                    append(
                        if (offer.direction == Direction.BUY_BTC) "${fmtBtc(output)} BTC"
                        else "${fmtRub(output)} ₽"
                    )
                },
                style = MonoSmall,
                color = accent
            )

            Spacer(Modifier.height(3.dp))

            FlowChips(offer, spot, lossPct, usable, amountIn)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(
    offer: Offer,
    spot: SpotPrice?,
    lossPct: Double?,
    usable: Boolean,
    amountIn: Double
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {

        if (lossPct != null && lossPct < -0.001) {
            MiniChip(fmtPercent(lossPct) + " к топу", Bad)
        }

        offer.spreadVsSpot(spot)?.let { sp ->
            MiniChip("спред ${fmtPercent(sp)}", if (sp < -3) Bad else if (sp < 0) Warn else Good)
        }

        if (offer.feePercent > 0) {
            MiniChip("комиссия ${fmtPercent(offer.feePercent).removePrefix("+")}", Warn)
        }

        if (!offer.fitsLimits(amountIn)) {
            val lim = buildString {
                append("лимит ")
                append(offer.minIn?.let { fmtCompactRub(it) } ?: "—")
                append("..")
                append(offer.maxIn?.let { fmtCompactRub(it) } ?: "∞")
            }
            MiniChip(lim, Bad)
        }

        if (!offer.hasReserve(amountIn)) {
            MiniChip("резерва мало: ${fmtCompactRub(offer.reserveOut)}", Bad)
        } else if (offer.reserveOut != null && usable) {
            MiniChip("резерв ${fmtCompactRub(offer.reserveOut)}", Color.Gray)
        }
    }
}

@Composable
private fun MiniChip(text: String, color: Color) {
    Box(
        Modifier
            .padding(top = 2.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(text, style = MonoSmall, color = color)
    }
}

@Composable
private fun StatusStrip(state: UiState) {
    val failed = state.scan.results.filter { it.error != null }
    val ok = state.scan.results.count { it.error == null }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(
                "Источников ОК: $ok · с ошибкой: ${failed.size} · предложений: ${state.scan.offers.size}",
                style = MonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            failed.take(3).forEach {
                Text(
                    "✕ ${it.title}: ${it.error}",
                    style = MonoSmall,
                    color = Bad,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
