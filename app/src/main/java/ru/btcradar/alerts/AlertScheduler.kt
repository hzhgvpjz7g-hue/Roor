package ru.btcradar.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AlertScheduler {

    /** Включить фоновый мониторинг с заданным периодом (минимум 15 мин). */
    fun enable(ctx: Context, periodMinutes: Long = 15) {
        val period = periodMinutes.coerceAtLeast(15)
        val req = PeriodicWorkRequestBuilder<RateWorker>(period, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            RateWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    fun disable(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(RateWorker.UNIQUE_NAME)
    }
}
