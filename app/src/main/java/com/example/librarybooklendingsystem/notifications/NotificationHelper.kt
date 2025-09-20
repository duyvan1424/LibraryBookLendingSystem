package com.example.librarybooklendingsystem.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.example.librarybooklendingsystem.MainActivity
import com.example.librarybooklendingsystem.R

object NotificationHelper {
	fun showNotification(context: Context, title: String, message: String, destination: String? = null) {
		val channelId = "default_channel_id"
		val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(channelId, "Thông báo", NotificationManager.IMPORTANCE_DEFAULT)
			notificationManager.createNotificationChannel(channel)
		}

		val intent = Intent(context, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
			destination?.let {
				putExtra("destination", it)
			}
		}
		
		val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		} else {
			PendingIntent.FLAG_UPDATE_CURRENT
		}
		
		val pendingIntent: PendingIntent = TaskStackBuilder.create(context).run {
			addNextIntentWithParentStack(intent)
			getPendingIntent(0, pendingIntentFlags)
		} ?: PendingIntent.getActivity(context, 1001, intent, pendingIntentFlags)

		val notification = NotificationCompat.Builder(context, channelId)
			.setSmallIcon(R.drawable.ic_book)
			.setContentTitle(title)
			.setContentText(message)
			.setAutoCancel(true)
			.setContentIntent(pendingIntent)
			.build()

		notificationManager.notify(System.currentTimeMillis().toInt(), notification)
	}
}


