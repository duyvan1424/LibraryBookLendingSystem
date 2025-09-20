package com.example.librarybooklendingsystem.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.example.librarybooklendingsystem.MainActivity
import com.example.librarybooklendingsystem.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class MyFirebaseMessagingService : FirebaseMessagingService() {

	override fun onNewToken(token: String) {
		Log.d("FCM", "New token: $token")
		val user = FirebaseAuth.getInstance().currentUser
		if (user != null) {
			FirebaseFirestore.getInstance()
				.collection("users")
				.document(user.uid)
				.set(mapOf("fcmToken" to token), SetOptions.merge())
		}
	}

	override fun onMessageReceived(remoteMessage: RemoteMessage) {
		val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Thông báo"
		val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "Bạn có thông báo mới"
		showNotification(title, body)
	}

	private fun showNotification(title: String, message: String) {
		val channelId = "default_channel_id"
		val intent = Intent(this, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
			putExtra("destination", "notifications")
		}
		val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		} else {
			PendingIntent.FLAG_UPDATE_CURRENT
		}
		val pendingIntent: PendingIntent = TaskStackBuilder.create(this).run {
			addNextIntentWithParentStack(intent)
			getPendingIntent(0, pendingIntentFlags)
		} ?: PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)

		val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				channelId,
				"Thông báo chung",
				NotificationManager.IMPORTANCE_DEFAULT
			)
			notificationManager.createNotificationChannel(channel)
		}

		val notification = NotificationCompat.Builder(this, channelId)
			.setSmallIcon(R.drawable.ic_book)
			.setContentTitle(title)
			.setContentText(message)
			.setAutoCancel(true)
			.setContentIntent(pendingIntent)
			.build()

		notificationManager.notify(System.currentTimeMillis().toInt(), notification)
	}
}


