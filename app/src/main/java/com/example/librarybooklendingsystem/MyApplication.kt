package com.example.librarybooklendingsystem

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.librarybooklendingsystem.data.FirebaseManager
import com.example.librarybooklendingsystem.notifications.DueReminderWorker
import java.util.concurrent.TimeUnit

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseManager.initialize(this)

        // Run every 6 hours to check for due dates and overdue books
        val workRequest = PeriodicWorkRequestBuilder<DueReminderWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.MINUTES) // Start checking after 1 minute
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "due-reminder-worker",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}


