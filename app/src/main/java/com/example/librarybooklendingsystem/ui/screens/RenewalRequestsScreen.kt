package com.example.librarybooklendingsystem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.librarybooklendingsystem.R
import com.example.librarybooklendingsystem.data.FirebaseManager
import com.example.librarybooklendingsystem.ui.components.CommonHeader
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class RenewalRequest(
    val id: String,
    val bookTitle: String,
    val userName: String,
    val userEmail: String,
    val currentReturnDate: Date?,
    val renewalDays: Int = 7,
    val renewalsUsed: Int,
    val maxRenewals: Int,
    val requestDate: Date?,
    val status: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenewalRequestsScreen(navController: NavController) {
    val renewalRequests = remember { mutableStateListOf<RenewalRequest>() }
    val error = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    val showApprovalDialog = remember { mutableStateOf(false) }
    val selectedRequest = remember { mutableStateOf<RenewalRequest?>(null) }
    val isApproving = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            isLoading.value = true
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("borrows")
                .whereEqualTo("status", "Chờ duyệt gia hạn")
                .get()
                .await()

            renewalRequests.clear()
            for (doc in snapshot.documents) {
                val request = RenewalRequest(
                    id = doc.id,
                    bookTitle = doc.getString("bookTitle") ?: "Sách",
                    userName = doc.getString("studentName") ?: doc.getString("userEmail") ?: "Người dùng",
                    userEmail = doc.getString("userEmail") ?: "Người dùng",
                    currentReturnDate = doc.getTimestamp("expectedReturnDate")?.toDate(),
                    renewalDays = (doc.getLong("renewDays") ?: 7L).toInt(),
                    renewalsUsed = (doc.getLong("renewalsUsed") ?: 0L).toInt(),
                    maxRenewals = (doc.getLong("maxRenewals") ?: 2L).toInt(),
                    requestDate = doc.getTimestamp("renewRequestDate")?.toDate(),
                    status = doc.getString("status") ?: ""
                )
                renewalRequests.add(request)
            }
            
            // Sort by request date (newest first)
            renewalRequests.sortByDescending { it.requestDate ?: Date(0) }
            isLoading.value = false
        } catch (e: Exception) {
            error.value = e.message ?: "Lỗi khi tải yêu cầu gia hạn"
            isLoading.value = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(80.dp),
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Duyệt gia hạn",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0288D1),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = Color.White,
                            modifier = Modifier.size(45.dp)
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFFF5F5F5)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Thẻ thống kê
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic),
                        contentDescription = "Stats Icon",
                        modifier = Modifier.size(40.dp),
                        tint = Color(0xFF0288D1)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Thống kê yêu cầu gia hạn",
                        fontSize = 16.sp,
                        color = Color(0xFF0288D1)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tổng số yêu cầu: ${renewalRequests.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading.value) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFF0288D1)
                )
            } else if (error.value != null) {
                Text(
                    text = error.value!!,
                    fontSize = 16.sp,
                    color = Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (renewalRequests.isEmpty()) {
                Text(
                    text = "Không có yêu cầu gia hạn nào cần duyệt",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(renewalRequests) { request ->
                        RenewalRequestCard(
                            request = request,
                            onApprove = { selectedRequest.value = it; showApprovalDialog.value = true },
                            onReject = { selectedRequest.value = it; showApprovalDialog.value = true }
                        )
                    }
                }
            }
        }
    }

    // Approval Dialog
    if (showApprovalDialog.value && selectedRequest.value != null) {
        val request = selectedRequest.value!!
        ApprovalDialog(
            request = request,
            isProcessing = isApproving.value,
            onDismiss = { 
                showApprovalDialog.value = false
                selectedRequest.value = null
            },
            onApprove = { 
                isApproving.value = true
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val success = FirebaseManager.approveRenewal(request.id)
                        if (success) {
                            // Remove from list
                            renewalRequests.removeAll { it.id == request.id }
                        }
                    } catch (e: Exception) {
                        error.value = e.message ?: "Lỗi khi duyệt gia hạn"
                    } finally {
                        isApproving.value = false
                        showApprovalDialog.value = false
                        selectedRequest.value = null
                    }
                }
            },
            onReject = {
                isApproving.value = true
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val success = FirebaseManager.rejectRenewal(request.id)
                        if (success) {
                            // Remove from list
                            renewalRequests.removeAll { it.id == request.id }
                        } else {
                            error.value = "Không thể từ chối gia hạn. Vui lòng thử lại."
                        }
                    } catch (e: Exception) {
                        error.value = e.message ?: "Lỗi khi từ chối gia hạn"
                    } finally {
                        isApproving.value = false
                        showApprovalDialog.value = false
                        selectedRequest.value = null
                    }
                }
            }
        )
    }
}

@Composable
fun RenewalRequestCard(
    request: RenewalRequest,
    onApprove: (RenewalRequest) -> Unit,
    onReject: (RenewalRequest) -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val newReturnDate = Calendar.getInstance().apply {
        time = request.currentReturnDate ?: Date()
        add(Calendar.DAY_OF_MONTH, request.renewalDays)
    }.time

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Pending",
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = request.bookTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Chờ duyệt",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Request details
            Text(
                text = "Người yêu cầu: ${request.userName}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hạn trả hiện tại: ${dateFormat.format(request.currentReturnDate ?: Date())}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Hạn trả mới: ${dateFormat.format(newReturnDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Số lần gia hạn: ${request.renewalsUsed + 1}/${request.maxRenewals}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Ngày yêu cầu: ${dateFormat.format(request.requestDate ?: Date())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onApprove(request) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Approve",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Duyệt")
                }
                
                Button(
                    onClick = { onReject(request) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Reject",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Từ chối")
                }
            }
        }
    }
}

@Composable
fun ApprovalDialog(
    request: RenewalRequest,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    isProcessing: Boolean = false
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val newReturnDate = Calendar.getInstance().apply {
        time = request.currentReturnDate ?: Date()
        add(Calendar.DAY_OF_MONTH, request.renewalDays)
    }.time

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Xác nhận duyệt gia hạn",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Sách: ${request.bookTitle}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Người yêu cầu: ${request.userName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Hạn trả hiện tại: ${dateFormat.format(request.currentReturnDate ?: Date())}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Hạn trả mới: ${dateFormat.format(newReturnDate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Bạn có chắc chắn muốn duyệt gia hạn này không?",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onApprove,
                enabled = !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Duyệt")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onReject,
                enabled = !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Từ chối")
                }
            }
        }
    )
}
