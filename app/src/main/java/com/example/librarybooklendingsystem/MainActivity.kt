package com.example.librarybooklendingsystem
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.librarybooklendingsystem.ui.screens.*
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import com.example.librarybooklendingsystem.data.AuthState
import com.example.librarybooklendingsystem.ui.theme.LibraryBookLendingSystemTheme
import com.example.librarybooklendingsystem.data.FirebaseManager
import kotlin.math.sign
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarybooklendingsystem.ui.navigation.AppNavigation
import com.example.librarybooklendingsystem.ui.viewmodels.BookViewModel
import com.example.librarybooklendingsystem.ui.viewmodels.CategoryViewModel
import com.example.librarybooklendingsystem.notifications.NotificationHelper
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.librarybooklendingsystem.notifications.DueReminderWorker

class MainActivity : ComponentActivity() {
    private lateinit var analytics: FirebaseAnalytics

	private val requestNotificationPermission = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { isGranted ->
		if (isGranted) {
			Log.d("Notif", "Notification permission granted")
		} else {
			Log.w("Notif", "Notification permission denied")
		}
	}

	companion object {
		const val CHANNEL_ID = "default_channel_id"
	}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        analytics = Firebase.analytics
        analytics.setAnalyticsCollectionEnabled(true)

        // Initialize Firebase
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("FirebaseCheck", "Firebase được khởi tạo trong MainActivity")
            }
            
            val db = FirebaseFirestore.getInstance()
            Log.d("FirebaseCheck", "Firestore instance created successfully")
            
            // Test connection
            db.collection("books").get()
                .addOnSuccessListener { 
                    Log.d("FirebaseCheck", "Kết nối Firebase thành công! Số lượng sách: ${it.size()}")
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseCheck", "Lỗi kết nối Firebase: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("FirebaseCheck", "Lỗi khởi tạo Firebase: ${e.message}")
            e.printStackTrace()
        }

        // Notifications: request permission (Android 13+) and create channel
        if (Build.VERSION.SDK_INT >= 33) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        createNotificationChannel()

        // Get FCM token (for push when app closed)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "Registration token: $token")
                // Save token to user doc for server-side notifications
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(currentUser.uid)
                        .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                }
            } else {
                Log.w("FCM", "Fetching registration token failed", task.exception)
            }
        }

        // Initialize AuthState and realtime notifications
        AuthState.init(this)
        FirebaseManager.initialize(this)

        // Handle deep link from notifications (also for cold start)
        val destination = intent?.getStringExtra("destination")
        Log.d("MainActivity", "Initial destination from intent: $destination")


        setContent {
            LibraryBookLendingSystemTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(initialDestination = destination)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        // Update intent extras so compose reads the latest destination if activity is reused
        setIntent(intent)
        
        // Handle deep link from notification
        intent?.let { 
            val destination = it.getStringExtra("destination")
            if (destination == "notifications") {
                Log.d("MainActivity", "Deep link to notifications received")
                // The AppNavigation will handle this in LaunchedEffect
            }
        }
    }
}

private fun MainActivity.createNotificationChannel() {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		val name = "Thông báo chung"
		val descriptionText = "Kênh thông báo mặc định"
		val importance = NotificationManager.IMPORTANCE_DEFAULT
		val channel = NotificationChannel(MainActivity.CHANNEL_ID, name, importance).apply {
			description = descriptionText
		}
		val notificationManager: NotificationManager = getSystemService(NotificationManager::class.java)
		notificationManager.createNotificationChannel(channel)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isAdmin by AuthState.isAdmin.collectAsStateWithLifecycle()
    val userRole by AuthState.currentUserRole.collectAsStateWithLifecycle()
    val isLoggedIn by AuthState.isLoggedIn.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val bookViewModel: BookViewModel = viewModel()
    val categoryViewModel: CategoryViewModel = viewModel()

    LaunchedEffect(Unit) {
        if (isLoggedIn && (currentRoute == "login" || currentRoute == "signup")) {
            if (isAdmin && userRole == "admin") {
                navController.navigate("admin_dashboard") {
                    popUpTo(0) { inclusive = true }
                }
            } else {
                navController.navigate("home") {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                MainDrawer(
                    navController = navController,
                    onCloseDrawer = {
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    categoryViewModel = categoryViewModel
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                if (currentRoute != "login" && currentRoute != "signup") {
                    BottomNavigationBar(
                        navController = navController,
                        onOpenDrawer = {
                            scope.launch {
                                drawerState.open()
                            }
                        },
                        categoryViewModel = categoryViewModel
                    )
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(
                    "login",
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() }
                ) { LoginScreen(navController) }
                
                composable(
                    "signup",
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() }
                ) { SignUpScreen(navController) }
                
                composable(
                    "home",
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() }
                ) { 
                    HomeScreen(
                        navController = navController,
                        viewModel = bookViewModel,
                        categoryViewModel = categoryViewModel
                    )
                }

                composable(
                    route = "bookDetails/{bookId}",
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId")
                    if (bookId != null) {
                        BookDetailsScreen(navController, bookId)
                    }
                }
                composable(
                    route = "borrowbook/{bookId}",
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId")
                    BorrowBookScreen(navController = navController, bookId = bookId)
                }
                composable("borrowbook") { 
                    BorrowBookScreen(navController = navController) 
                }
                composable("account") { AccountScreen(navController) }
                composable("admin_dashboard") { 
                    if (isAdmin && userRole == "admin") {
                        AdminDashboardScreen(navController)
                    } else {
                        LaunchedEffect(Unit) {
                            navController.navigate("home") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }
                composable("create_admin") { CreateAdminScreen(navController) }
                composable("pending_approvals") { PendingBooksApprovalScreen(navController) }
                composable("library_stats") { LibraryStatsScreen(navController) }
                composable("user_stats") { UserStatsScreen(navController) }
                composable("renewal_requests") { RenewalRequestsScreen(navController) }
                composable("borrowed_books_stats") { BorrowedBooksStatsScreen(navController) }
                composable("returned_books_stats") { ReturnedBooksStatsScreen(navController) }
                // Fallback notifications route in case this NavHost is used
                composable("notifications") { NotificationsScreen(navController) }
            }
        }
    }
}
