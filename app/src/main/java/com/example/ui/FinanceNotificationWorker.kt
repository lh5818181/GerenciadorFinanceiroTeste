package com.example.ui

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.FinanceDatabase
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class FinanceNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val database = FinanceDatabase.getDatabase(applicationContext)
            val transactions = database.transactionDao().getAllTransactions().first()

            val sharedPrefs = applicationContext.getSharedPreferences("finance_notifications_native", Context.MODE_PRIVATE)
            val notifiedKeys = sharedPrefs.getStringSet("notified_keys", emptySet()) ?: emptySet()
            val newlyNotifiedKeys = notifiedKeys.toMutableSet()

            val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
            var notifiedCount = 0

            // Check and notify for pending or newly recorded/changed items
            for (tx in transactions) {
                // Key format: transaction_id + status (to notify upon state changes, e.g., Pending -> Paid/Received)
                val key = "tx_${tx.id}_${tx.status}"
                if (!notifiedKeys.contains(key)) {
                    // Check rule for "RECEITA" received
                    if (tx.type == "RECEITA" && tx.status == "Recebido") {
                        NotificationHelper.sendNativeNotification(
                            applicationContext,
                            tx.id,
                            "Recebimento de Receita! 🎉",
                            "Sua receita '${tx.title}' de R$ ${currencyFormatter.format(tx.amount)} foi registrada como recebida!",
                            tx.id
                        )
                        newlyNotifiedKeys.add(key)
                        notifiedCount++
                    }
                    // Check rule for "DESPESA" paid or pending
                    else if (tx.type == "DESPESA") {
                        if (tx.status == "Pendente") {
                            val cal = Calendar.getInstance().apply { timeInMillis = tx.dateMillis }
                            val isPastOrToday = tx.dateMillis <= System.currentTimeMillis() || 
                                                cal.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                            
                            if (isPastOrToday) {
                                NotificationHelper.sendNativeNotification(
                                    applicationContext,
                                    tx.id + 10000, // offset id to prevent collision
                                    "Alerta de Despesa Pendente ⚠️",
                                    "A despesa '${tx.title}' no valor de R$ ${currencyFormatter.format(tx.amount)} consta como pendente para pagamento.",
                                    tx.id
                                )
                                newlyNotifiedKeys.add(key)
                                notifiedCount++
                            }
                        } else if (tx.status == "Pago") {
                            NotificationHelper.sendNativeNotification(
                                applicationContext,
                                tx.id + 20000, // offset id
                                "Gasto Registrado com Sucesso ✅",
                                "O pagamento da sua despesa '${tx.title}' no valor de R$ ${currencyFormatter.format(tx.amount)} está confirmado.",
                                tx.id
                            )
                            newlyNotifiedKeys.add(key)
                            notifiedCount++
                        }
                    }
                }
                
                // Allow maximum of 3 notifications per background run to keep sound hygiene polite
                if (notifiedCount >= 3) {
                    break
                }
            }

            // Save our new notified checklist states
            sharedPrefs.edit().putStringSet("notified_keys", newlyNotifiedKeys).apply()

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
}
