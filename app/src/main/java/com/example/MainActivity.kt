package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.FinanceDatabase
import com.example.data.TransactionRepository
import com.example.ui.FinanceApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.FinanceViewModel
import com.example.viewmodel.FinanceViewModelFactory

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: FinanceViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Notification Channels
        com.example.ui.NotificationHelper.createNotificationChannel(this)

        // Schedule Native System Alert Worker (Runs every 15 mins even with the app closed)
        val periodicWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.ui.FinanceNotificationWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        ).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "FinanceSystemAlertsWork",
            androidx.work.ExistingPeriodicWorkPolicy.REPLACE, // REPLACE to ensure update is registered
            periodicWorkRequest
        )
        
        // Run a quick one-time check immediately on startup
        val oneTimeWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.ui.FinanceNotificationWorker>().build()
        androidx.work.WorkManager.getInstance(this).enqueue(oneTimeWorkRequest)
        
        // Initialize Database & Repository
        val database = FinanceDatabase.getDatabase(this)
        val repository = TransactionRepository(database.transactionDao())
        
        // Construct ViewModel via clean standard custom Provider Factory
        val factory = FinanceViewModelFactory(application, repository)
        viewModel = ViewModelProvider(this, factory)[FinanceViewModel::class.java]

        // Handle focused transaction from system notification intent
        val txId = intent.getIntExtra("TRANSACTION_ID", -1)
        if (txId != -1) {
            viewModel.focusTransaction(txId)
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FinanceApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val txId = intent.getIntExtra("TRANSACTION_ID", -1)
        if (txId != -1 && ::viewModel.isInitialized) {
            viewModel.focusTransaction(txId)
        }
    }
}
