package com.example.librarybooklendingsystem.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

class DueReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result {
        return try {
            val now = Date()
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.d("DueReminderWorker", "No user logged in, skipping")
                return Result.success()
            }

            Log.d("DueReminderWorker", "Starting due reminder check for user: ${currentUser.uid}")

            // Check both "Đang mượn" and "Quá hạn" books
            // Sends reminders 2 days and 1 day before due date
            val snapshot = db.collection("borrows")
                .whereEqualTo("userId", currentUser.uid)
                .whereIn("status", listOf("Đang mượn", "Quá hạn"))
                .get()
                .await()

            Log.d("DueReminderWorker", "Found ${snapshot.documents.size} borrows to check")

            for (doc in snapshot.documents) {
                val expectedTs = doc.getTimestamp("expectedReturnDate")
                val expected = expectedTs?.toDate() ?: continue
                val currentStatus = doc.getString("status") ?: ""
                val bookTitle = doc.getString("bookTitle") ?: "sách"

                val daysDiff = daysBetween(now, expected)

                Log.d("DueReminderWorker", "Checking book: $bookTitle, status: $currentStatus, daysDiff: $daysDiff")

                // Notify 2 days before due (only for "Đang mượn" books)
                if (currentStatus == "Đang mượn" && daysDiff == 2) {
                    val title = "Sắp đến hạn trả sách"
                    val message = "${bookTitle} sẽ đến hạn sau 2 ngày. Bạn có thể gia hạn nếu cần."
                    Log.d("DueReminderWorker", "Sending reminder notification: $message")
                    NotificationHelper.showNotification(applicationContext, title, message)
                }
                
                // Also notify 1 day before due as additional reminder
                if (currentStatus == "Đang mượn" && daysDiff == 1) {
                    val title = "Sắp đến hạn trả sách"
                    val message = "${bookTitle} sẽ đến hạn sau 1 ngày. Bạn có thể gia hạn nếu cần."
                    Log.d("DueReminderWorker", "Sending reminder notification: $message")
                    NotificationHelper.showNotification(applicationContext, title, message)
                }

                // Mark overdue and compute fine
                if (now.after(expected)) {
                    val overdueDays = daysBetween(expected, now)
                    val finePerDay = (doc.getLong("finePerDay") ?: 5000L).toInt()
                    val fineAmount = overdueDays * finePerDay
                    
                    // Only update if not already marked as overdue
                    if (currentStatus != "Quá hạn") {
                        val updates = hashMapOf(
                            "status" to "Quá hạn",
                            "overdueSince" to Timestamp(expected),
                            "overdueDays" to overdueDays,
                            "finePerDay" to finePerDay,
                            "fineAmount" to fineAmount,
                            "finePaid" to false
                        ) as Map<String, Any>
                        db.collection("borrows").document(doc.id).update(updates).await()

                        val msg = "${bookTitle} đã quá hạn $overdueDays ngày. Phí tạm tính: ${fineAmount}đ. Vui lòng trả sách hoặc nộp phạt để tiếp tục mượn sách mới."
                        Log.d("DueReminderWorker", "Sending overdue notification: $msg")
                        NotificationHelper.showNotification(applicationContext, "Sách quá hạn - Nhắc nhở lần 1", msg)
                    } else {
                        // Send additional reminders for already overdue books
                        if (overdueDays % 3 == 0) { // Every 3 days
                            val msg = "${bookTitle} đã quá hạn $overdueDays ngày. Phí tạm tính: ${fineAmount}đ. Vui lòng trả sách hoặc nộp phạt."
                            Log.d("DueReminderWorker", "Sending overdue reminder: $msg")
                            NotificationHelper.showNotification(applicationContext, "Nhắc nhở sách quá hạn", msg)
                        }
                    }
                }
            }

            Log.d("DueReminderWorker", "Due reminder check completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e("DueReminderWorker", "Error processing reminders/overdues", e)
            Result.retry()
        }
    }

    private fun daysBetween(start: Date, end: Date): Int {
        val calStart = Calendar.getInstance().apply { time = start; setToMidnight() }
        val calEnd = Calendar.getInstance().apply { time = end; setToMidnight() }
        val diffMs = calEnd.timeInMillis - calStart.timeInMillis
        return (diffMs / (24L * 60L * 60L * 1000L)).toInt()
    }

    private fun Calendar.setToMidnight() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}


