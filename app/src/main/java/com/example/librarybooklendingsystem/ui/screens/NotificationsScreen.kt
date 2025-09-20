package com.example.librarybooklendingsystem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import com.example.librarybooklendingsystem.ui.components.CommonHeader
import com.example.librarybooklendingsystem.data.FirebaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

data class NotificationItem(
    val id: String,
    val bookTitle: String,
    val status: String,
    val borrowDate: Date?,
    val expectedReturnDate: Date?,
    val actualReturnDate: Date?,
    val fineAmount: Long,
    val finePaid: Boolean,
    val overdueDays: Int,
    val renewalsUsed: Int,
    val maxRenewals: Int,
    val timestamp: Date = Date()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavController) {
    val notifications = remember { mutableStateListOf<NotificationItem>() }
    val error = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val isLoading = remember { androidx.compose.runtime.mutableStateOf(true) }
    val showRenewalDialog = remember { androidx.compose.runtime.mutableStateOf(false) }
    val selectedNotification = remember { androidx.compose.runtime.mutableStateOf<NotificationItem?>(null) }
    val renewalMessage = remember { androidx.compose.runtime.mutableStateOf("") }
    val showSuccessMessage = remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            isLoading.value = true
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                error.value = "Bạn cần đăng nhập để xem thông báo"
                isLoading.value = false
                return@LaunchedEffect
            }
            
            val db = FirebaseFirestore.getInstance()
            
            // Load borrow notifications
            val borrowSnapshot = db.collection("borrows")
                .whereEqualTo("userId", currentUser.uid)
                .limit(50)
                .get()
                .await()

            notifications.clear()
            for (doc in borrowSnapshot.documents) {
                val notification = NotificationItem(
                    id = doc.id,
                    bookTitle = doc.getString("bookTitle") ?: "Sách",
                    status = doc.getString("status") ?: "",
                    borrowDate = doc.getTimestamp("borrowDate")?.toDate(),
                    expectedReturnDate = doc.getTimestamp("expectedReturnDate")?.toDate(),
                    actualReturnDate = doc.getTimestamp("actualReturnDate")?.toDate(),
                    fineAmount = (doc.getLong("fineAmount") ?: 0L),
                    finePaid = doc.getBoolean("finePaid") ?: false,
                    overdueDays = (doc.getLong("overdueDays") ?: 0L).toInt(),
                    renewalsUsed = (doc.getLong("renewalsUsed") ?: 0L).toInt(),
                    maxRenewals = (doc.getLong("maxRenewals") ?: 2L).toInt(),
                    timestamp = doc.getTimestamp("borrowDate")?.toDate() ?: Date()
                )
                notifications.add(notification)
            }
            
            // Load system notifications
            val systemSnapshot = db.collection("notifications")
                .whereEqualTo("userId", currentUser.uid)
                .limit(20)
                .get()
                .await()
                
            for (doc in systemSnapshot.documents) {
                val title = doc.getString("title") ?: "Thông báo"
                val message = doc.getString("message") ?: ""
                val type = doc.getString("type") ?: ""
                
                // Convert system notification to NotificationItem
                val notification = NotificationItem(
                    id = doc.id,
                    bookTitle = title,
                    status = when (type) {
                        "renewal_approved" -> "Gia hạn được duyệt"
                        "renewal_rejected" -> "Gia hạn bị từ chối"
                        else -> "Thông báo hệ thống"
                    },
                    borrowDate = null,
                    expectedReturnDate = null,
                    actualReturnDate = null,
                    fineAmount = 0L,
                    finePaid = false,
                    overdueDays = 0,
                    renewalsUsed = 0,
                    maxRenewals = 0,
                    timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date()
                )
                notifications.add(notification)
            }
            
            // Sort by timestamp (newest first)
            notifications.sortByDescending { it.timestamp }
            isLoading.value = false
        } catch (e: Exception) {
            error.value = e.message ?: "Lỗi khi tải thông báo"
            isLoading.value = false
        }
    }

    Scaffold(
        topBar = {
            CommonHeader(
                title = "Thông báo",
                onBackClick = { navController.popBackStack() },
                showShareButton = false,
                showNotificationsButton = false,
                showBackButton = true
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading.value) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (error.value != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Error",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error.value!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "No notifications",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Chưa có thông báo",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Các thông báo về sách mượn sẽ hiển thị ở đây",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                       LazyColumn(
                           modifier = Modifier.fillMaxSize(),
                           contentPadding = PaddingValues(16.dp),
                           verticalArrangement = Arrangement.spacedBy(12.dp)
                       ) {
                           items(notifications) { notification ->
                               NotificationCard(
                                   notification = notification,
                                   onRenewalClick = { selectedNotif ->
                                       selectedNotification.value = selectedNotif
                                       showRenewalDialog.value = true
                                   }
                               )
                           }
                       }
            }
        }
    }
    
    // Renewal Dialog
    if (showRenewalDialog.value && selectedNotification.value != null) {
        val notification = selectedNotification.value!!
        RenewalDialog(
            notification = notification,
            onDismiss = { 
                showRenewalDialog.value = false
                selectedNotification.value = null
                renewalMessage.value = ""
            },
            onConfirm = { message ->
                renewalMessage.value = message
                showRenewalDialog.value = false
                selectedNotification.value = null
                showSuccessMessage.value = true
            }
        )
    }
    
    // Success message
    if (showSuccessMessage.value) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(3000) // Show for 3 seconds
            showSuccessMessage.value = false
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text(
                    text = renewalMessage.value,
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun NotificationCard(
    notification: NotificationItem,
    onRenewalClick: (NotificationItem) -> Unit = {}
) {
    val (icon, iconColor, statusColor, statusText) = getNotificationInfo(notification)
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = notification.status == "Đang mượn" && 
                         notification.renewalsUsed < notification.maxRenewals,
                onClick = { 
                    if (notification.status == "Đang mượn" && 
                        notification.renewalsUsed < notification.maxRenewals) {
                        onRenewalClick(notification)
                    }
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.status == "Đang mượn" && 
                               notification.renewalsUsed < notification.maxRenewals) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with icon and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Status",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = notification.bookTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Status details
            when (notification.status) {
                "Đang mượn" -> {
                    BorrowingInfo(notification, dateFormat)
                }
                "Quá hạn" -> {
                    OverdueInfo(notification, dateFormat)
                }
                "Chờ duyệt gia hạn" -> {
                    RenewalPendingInfo(notification, dateFormat)
                }
                "Đã trả" -> {
                    ReturnedInfo(notification, dateFormat)
                }
                "Gia hạn được duyệt" -> {
                    RenewalApprovedInfo(notification, dateFormat)
                }
                "Gia hạn bị từ chối" -> {
                    RenewalRejectedInfo(notification, dateFormat)
                }
                "Thông báo hệ thống" -> {
                    SystemNotificationInfo(notification, dateFormat)
                }
                else -> {
                    GenericInfo(notification, dateFormat)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Timestamp
            Text(
                text = "Ngày mượn: ${dateFormat.format(notification.borrowDate ?: Date())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BorrowingInfo(notification: NotificationItem, dateFormat: SimpleDateFormat) {
    Column {
        Text(
            text = "📚 Sách đang được mượn",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Hạn trả: ${dateFormat.format(notification.expectedReturnDate ?: Date())}",
            style = MaterialTheme.typography.bodySmall
        )
        if (notification.renewalsUsed < notification.maxRenewals) {
            Text(
                text = "Gia hạn: ${notification.renewalsUsed}/${notification.maxRenewals} lần",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "💡 Nhấn để gia hạn sách",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        } else {
            Text(
                text = "Đã sử dụng hết số lần gia hạn",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun OverdueInfo(notification: NotificationItem, dateFormat: SimpleDateFormat) {
    Column {
        Text(
            text = "⚠️ Sách đã quá hạn",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Quá hạn: ${notification.overdueDays} ngày",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        if (notification.fineAmount > 0) {
            Text(
                text = "Phí phạt: ${notification.fineAmount}đ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (notification.finePaid) "✅ Đã thanh toán" else "❌ Chưa thanh toán",
                style = MaterialTheme.typography.bodySmall,
                color = if (notification.finePaid) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun RenewalPendingInfo(notification: NotificationItem, dateFormat: SimpleDateFormat) {
    Column {
        Text(
            text = "⏳ Đang chờ duyệt gia hạn",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFF9800)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Hạn trả hiện tại: ${dateFormat.format(notification.expectedReturnDate ?: Date())}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Gia hạn: ${notification.renewalsUsed}/${notification.maxRenewals} lần",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "📤 Yêu cầu đã gửi đến admin",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFF9800),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ReturnedInfo(notification: NotificationItem, dateFormat: SimpleDateFormat) {
    Column {
        Text(
            text = "✅ Đã trả sách",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ngày trả: ${dateFormat.format(notification.actualReturnDate ?: Date())}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun RenewalApprovedInfo(notification: NotificationItem, dateFormat: SimpleDateFormat) {
    Column {
        Text(
            text = "✅ Gia hạn được duyệt",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = notification.bookTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RenewalRejectedInfo(notification: NotificationItem, dateFormat: SimpleDateFormat) {
    Column {
        Text(
            text = "❌ Gia hạn bị từ chối",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFF44336)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = notification.bookTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SystemNotificationInfo(notification: NotificationItem, dateFormat: SimpleDateFormat) {
    Column {
        Text(
            text = "📢 Thông báo hệ thống",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = notification.bookTitle,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun GenericInfo(notification: NotificationItem, dateFormat: SimpleDateFormat) {
    Text(
        text = "Trạng thái: ${notification.status}",
        style = MaterialTheme.typography.bodyMedium
    )
}

fun getNotificationInfo(notification: NotificationItem): Tuple4<ImageVector, Color, Color, String> {
    return when (notification.status) {
        "Đang mượn" -> Tuple4(
            Icons.Default.Info,
            Color(0xFF2196F3),
            Color(0xFF2196F3),
            "Đang mượn"
        )
        "Quá hạn" -> Tuple4(
            Icons.Default.Warning,
            Color(0xFFF44336),
            Color(0xFFF44336),
            "Quá hạn"
        )
        "Chờ duyệt gia hạn" -> Tuple4(
            Icons.Default.Info,
            Color(0xFFFF9800),
            Color(0xFFFF9800),
            "Chờ duyệt"
        )
        "Đã trả" -> Tuple4(
            Icons.Default.CheckCircle,
            Color(0xFF4CAF50),
            Color(0xFF4CAF50),
            "Đã trả"
        )
        "Gia hạn được duyệt" -> Tuple4(
            Icons.Default.CheckCircle,
            Color(0xFF4CAF50),
            Color(0xFF4CAF50),
            "Đã duyệt"
        )
        "Gia hạn bị từ chối" -> Tuple4(
            Icons.Default.Warning,
            Color(0xFFF44336),
            Color(0xFFF44336),
            "Bị từ chối"
        )
        "Thông báo hệ thống" -> Tuple4(
            Icons.Default.Info,
            Color(0xFF2196F3),
            Color(0xFF2196F3),
            "Hệ thống"
        )
        else -> Tuple4(
            Icons.Default.Info,
            Color(0xFF757575),
            Color(0xFF757575),
            notification.status
        )
    }
}

@Composable
fun RenewalDialog(
    notification: NotificationItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val renewalDays = 7 // Default renewal period
    val newReturnDate = Calendar.getInstance().apply {
        time = notification.expectedReturnDate ?: Date()
        add(Calendar.DAY_OF_MONTH, renewalDays)
    }.time
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val isLoading = remember { androidx.compose.runtime.mutableStateOf(false) }
    val errorMessage = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Gia hạn sách",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Sách: ${notification.bookTitle}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hạn trả hiện tại: ${dateFormat.format(notification.expectedReturnDate ?: Date())}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Hạn trả mới: ${dateFormat.format(newReturnDate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Số lần gia hạn: ${notification.renewalsUsed + 1}/${notification.maxRenewals}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Bạn có chắc chắn muốn gia hạn sách này không?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ Yêu cầu gia hạn sẽ được gửi đến admin để duyệt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                
                if (errorMessage.value != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage.value!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isLoading.value) return@TextButton
                    
                    isLoading.value = true
                    errorMessage.value = null
                    
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val success = FirebaseManager.requestRenewal(notification.id)
                            if (success) {
                                onConfirm("✅ Yêu cầu gia hạn đã được gửi đến admin! Bạn sẽ nhận được thông báo khi được duyệt.")
                            } else {
                                errorMessage.value = "Không thể gia hạn. Vui lòng thử lại."
                            }
                        } catch (e: Exception) {
                            errorMessage.value = e.message ?: "Lỗi không xác định"
                        } finally {
                            isLoading.value = false
                        }
                    }
                },
                enabled = !isLoading.value
            ) {
                if (isLoading.value) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Xác nhận gia hạn")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading.value
            ) {
                Text("Hủy")
            }
        }
    )
}

// Helper class for returning multiple values
data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

