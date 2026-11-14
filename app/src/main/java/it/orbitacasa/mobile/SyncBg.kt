package it.orbitacasa.mobile

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Sync periodico in background. Si usa una catena di lavori singoli con ritardo (e non il lavoro
 * periodico di Android) perché quello non scende sotto i 15 minuti. Android decide comunque
 * l'orario esatto: con il telefono fermo (Doze) i controlli possono slittare.
 */
object SyncBg {
    private const val TAG = "sync_bg"

    private fun richiesta(min: Int): OneTimeWorkRequest =
        OneTimeWorkRequest.Builder(SyncWorker::class.java)
            .setInitialDelay(min.toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(TAG)
            .build()

    /** Applica l'impostazione corrente: annulla la catena e, se i minuti sono > 0, la riparte. */
    fun pianifica(ctx: Context) {
        try {
            val app = ctx.applicationContext
            val wm = WorkManager.getInstance(app)
            wm.cancelAllWorkByTag(TAG)
            val min = Sync.minBg(app)
            if (min > 0 && Sync.configurato(app)) wm.enqueue(richiesta(min))
        } catch (_: Exception) {
        }
    }

    /** Da richiamare all'apertura dell'app: se la catena si è interrotta (es. arresto forzato) la riparte. */
    fun assicura(ctx: Context) {
        val app = ctx.applicationContext
        Thread {
            try {
                val min = Sync.minBg(app)
                val wm = WorkManager.getInstance(app)
                val attivo = wm.getWorkInfosByTag(TAG).get().any {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.BLOCKED
                }
                if (min > 0 && Sync.configurato(app) && !attivo) wm.enqueue(richiesta(min))
                if ((min == 0 || !Sync.configurato(app)) && attivo) wm.cancelAllWorkByTag(TAG)
            } catch (_: Exception) {
            }
        }.start()
    }

    fun prossimo(ctx: Context) {
        try {
            val min = Sync.minBg(ctx)
            if (min > 0 && Sync.configurato(ctx)) WorkManager.getInstance(ctx).enqueue(richiesta(min))
        } catch (_: Exception) {
        }
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val app = applicationContext
        try {
            Repo.init(app)
            if (Sync.minBg(app) == 0) return Result.success()
            if (Sync.daPeriodico(app)) {
                val fine = CountDownLatch(1)
                Sync.sincronizza(app) { fine.countDown() }
                fine.await(8, TimeUnit.MINUTES)
            }
        } catch (_: Exception) {
        } finally {
            SyncBg.prossimo(app)
        }
        return Result.success()
    }
}
