package net.buli.ibtc

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(ctx:Context, params:WorkerParameters): CoroutineWorker(ctx, params){
    override suspend fun doWork(): Result = withContext(Dispatchers.IO){
        try{ val wm = WalletManager(applicationContext); wm.init(); wm.price(); wm.getBalance(); Result.success() }
        catch(e:Exception){ Result.retry() }
    }
}