package ru.btcradar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopBar(state, onRefresh, onOpenTor) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            val buy = state.scan.offers
                .filter { it.direction == Direction.BUY_BTC }
                .rankFor(state.amountRub, state.hideUnusable)
            val sell = state.scan.offers
                .filter { it.direction == Direction.SELL_BTC }
                .rankFor(state.amountBtc, state.hideUnusable)

            val buyAccent = Good
            val sellAccent = MaterialTheme.colorScheme.primary

            BoxWithConstraints(Modifier.weight(1f)) {
                // На планшете/в альбоме — две колонки рядом.
                // На узком телефоне всё едет одной лентой, чтобы списку хватало места.
                val wide = maxWidth >= 600.dp

                if (wide) {
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HeroCard(state, onAmountRub, onAmountBtc, onHideUnusable)
                            QuickActions(state, onOpenBrowser, onOpenVault, onOpenAlerts, onOpenSources)
                        }
                        Row(Modifier.weight(1f)) {
                            OfferColumn(
                                title = "Купить BTC  (₽ → BTC)",
                                subtitle = "дешевле = выше",
                                accent = buyAccent,
                                offers = buy,
                                amountIn = state.amountRub,
                                spot = state.scan.spot,
                                onOpenOffer = onOpenOffer,
                                modifier = Modifier.weight(1f)
                            )
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            OfferColumn(
                                title = "Продать BTC  (BTC → ₽)",
                                subtitle = "дороже = выше",
                                accent = sellAccent,
                                offers = sell,
                                amountIn = state.amountBtc,
                                spot = state.scan.spot,
                                onOpenOffer = onOpenOffer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    var tab by remember { mutableIntStateOf(0) }
                    val list = if (tab == 0) buy else sell
                    val amountIn = if (tab == 0) state.amountRub else state.amountBtc
                    val accent = if (tab == 0) buyAccent else sellAccent
                    val best = list.firstOrNull { it.isUsable(amountIn) }?.output(amountIn) ?: 0.0

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { HeroCard(state, onAmountRub, onAmountBtc, onHideUnusable) }
                        item { QuickActions(state, onOpenBrowser, onOpenVault, onOpenAlerts, onOpenSources) }
                        item {
                            Segmented(
                                selected = tab,
                                labels = listOf("Купить BTC · ${buy.size}", "Продать BTC · ${sell.size}"),
                                onSelect = { tab = it },
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        item {
                            Text(
                                if (tab == 0) "₽ → BTC · сверху то, где получишь больше биткоина"
                                else "BTC → ₽ · сверху то, где получишь больше рублей",
                                style = MonoSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        if (list.isEmpty()) {
                            item { EmptyHint() }
                        } else {
                            // Без key: имя обменника не уникально (у одного источника легко
                            // окажутся две строки с одинаковым названием), а дубликат ключа
                            // в LazyColumn — это краш, а не просто кривая анимация.
                            items(list) { offer ->
                                OfferCard(offer, amountIn, state.scan.spot, best, accent, onOpenOffer)
                            }
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
    onOpenTor: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Spacer(Modifier.width(10.dp))
                Text(
                    "BTC/RUB Radar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        actions = {
            TorChip(state.tor, onOpenTor)
            IconButton(onClick = onRefresh, enabled = !state.scanning) {
                if (state.scanning) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(Icons.Default.Refresh, "Обновить")
                }
            }
        }
    )
}

@Composable
private fun BrandMark() {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.CurrencyBitcoin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
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
        shape = RoundedCornerShape(999.dp),
        colors = AssistChipDefaults.assistChipColors(containerColor = Color.Transparent),
        modifier = Modifier.padding(end = 2.dp)
    )
}

/** Карточка в стиле макета: белая/графитовая, скругление 16, тонкая обводка. */
@Composable
private fun RadarCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun HeroCard(
    state: UiState,
    onAmountRub: (Double) -> Unit,
    onAmountBtc: (Double) -> Unit,
    onHideUnusable: (Boolean) -> Unit
) {
    RadarCard {
        Text(
            "1 BTC · СПОТ",
            style = MonoSmall,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val spot = state.scan.spot
            Text(
                if (spot != null) "${fmtRub(spot.rubPerBtc)} ₽" else "— ₽",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                fmtAgo(state.scan.scannedAtMs),
                style = MonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onHideUnusable(!state.hideUnusable) }
        ) {
            Checkbox(checked = state.hideUnusable, onCheckedChange = onHideUnusable)
            Text(
                "Прятать непроходные по лимитам и резерву",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun QuickActions(
    state: UiState,
    onOpenBrowser: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenSources: () -> Unit
) {
    val active = state.alerts.count { it.enabled }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickAction(Icons.Default.Public, "Браузер", onOpenBrowser, Modifier.weight(1f))
        QuickAction(Icons.Default.Lock, "Сейф", onOpenVault, Modifier.weight(1f))
        QuickAction(
            if (active > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
            "Алерты",
            onOpenAlerts,
            Modifier.weight(1f),
            badge = active
        )
        QuickAction(Icons.Default.Tune, "Источники", onOpenSources, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (badge > 0) {
            BadgedBox(badge = { Badge { Text("$badge") } }) {
                Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        } else {
            Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Сегментный переключатель, как вкладки в макете. */
@Composable
private fun Segmented(
    selected: Int,
    labels: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val outer = RoundedCornerShape(12.dp)
    val inner = RoundedCornerShape(9.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(outer)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, outer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(inner)
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Пусто.\nДобавь источники и нажми обновить.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        shape = RoundedCornerShape(11.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
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
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accent)
                Text(subtitle, style = MonoSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (offers.isEmpty()) {
            EmptyHint()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val best = offers.firstOrNull { it.isUsable(amountIn) }?.output(amountIn) ?: 0.0
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
    val shape = RoundedCornerShape(16.dp)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = if (isBest) BorderStroke(1.5.dp, accent)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (usable) 1f else 0.5f)
            .clip(shape)
            .clickable { offer.url?.let(onOpenOffer) }
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    offer.exchangerName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (offer.transport == Transport.TOR) {
                    Spacer(Modifier.width(6.dp))
                    Text("TOR", style = MonoSmall, color = TorPurple, fontWeight = FontWeight.Bold)
                }
                if (isBest) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .background(accent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "ЛУЧШИЙ",
                            style = MonoSmall,
                            fontSize = 10.sp,
                            letterSpacing = 0.6.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Сколько реально получишь на руки — по этому числу идёт сортировка
            Text(
                if (offer.direction == Direction.BUY_BTC) "${fmtBtc(output)} BTC"
                else "${fmtRub(output)} ₽",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = if (usable) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Эффективный курс — с учётом комиссии, а не витринный
            Text(
                "получишь · курс ${fmtRub(offer.effectiveRubPerBtc())} ₽/BTC",
                style = MonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(6.dp))

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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {

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
            MiniChip("резерв ${fmtCompactRub(offer.reserveOut)}", MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniChip(text: String, color: Color) {
    Box(
        Modifier
            .padding(top = 3.dp)
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MonoSmall, color = color)
    }
}

@Composable
private fun StatusStrip(state: UiState) {
    val failed = state.scan.results.filter { it.error != null }
    val ok = state.scan.results.count { it.error == null }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
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
