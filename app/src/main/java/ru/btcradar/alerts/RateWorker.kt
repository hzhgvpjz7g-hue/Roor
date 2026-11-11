package ru.btcradar.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.btcradar.net.Repository

/**
 * Фоновый опрос курсов и проверка алертов.
 *
 * Запускается через WorkManager. ВАЖНО понимать про частоту:
 * минимальный период периодической задачи в Android — 15 минут, и систему
 * не обмануть. Плюс в Doze (телефон лежит с выключенным экраном) запуск
 * может откладываться ещё сильнее. То есть это «проверять примерно раз
 * в 15–30 минут», а не секундный тикер. Для секундного отслеживания
 * нужен foreground-сервис с постоянным уведомлением — это отдельное
 * решение, если захочешь (жрёт батарею заметно).
 *
 * TTS из фона, когда экран погашен, работает нестабильно — поэтому
 * основной сигнал здесь уведомление, а голос догоняет, когда приложение
 * открыто. Так честно.
 */
class RateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = Repository(applicationContext)

        val sources = repo.loadSources()
        val tor = repo.loadTor()
        // В фоне Tor используем только если прокси реально поднят —
        // будить Orbot из воркера не станем.
        val torLive = ru.btcradar.net.TorHttp.isProxyListening(tor)

        val scan = runCatching { repo.scan(sources, tor, torLive) }.getOrNull()
            ?: return Result.retry()

        val now = System.currentTimeMillis()
        val alerts = repo.loadAlerts()
        var changed = false

        val updated = alerts.map { alert ->
            if (!alert.canFire(now)) return@map alert
            val offer = alert.bestTrigger(scan.offers) ?: return@map alert

            Notifier.notifyHit(applicationContext, ru.btcradar.model.AlertHit(alert, offer))
            changed = true

            if (alert.oneShot) alert.copy(enabled = false, lastFiredMs = now)
            else alert.copy(lastFiredMs = now)
        }

        if (changed) repo.saveAlerts(updated)
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "rate_watch"
    }
}
