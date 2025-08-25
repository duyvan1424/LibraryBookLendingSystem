package com.example.librarybooklendingsystem.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object AuthState {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val _isLoading = mutableStateOf(false)
    private val _currentUserRole = MutableStateFlow<String?>(null)
    private val _isAdmin = MutableStateFlow(false)
    private val _currentUserShortId = MutableStateFlow<String?>(null)
    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    private lateinit var prefs: SharedPreferences

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        // Thêm listener cho FirebaseAuth để cập nhật trạng thái ngay lập tức
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            _isLoggedIn.value = user != null
            if (user == null) {
                _currentUserRole.value = null
                _isAdmin.value = false
                _currentUserShortId.value = null
                saveAuthState(false, null, null)
            }
        }
    }

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val currentUserShortId: StateFlow<String?>
        get() = _currentUserShortId.asStateFlow()

    val isLoading: Boolean
        get() = _isLoading.value

    val currentUserRole: StateFlow<String?>
        get() = _currentUserRole.asStateFlow()

    val isAdmin: StateFlow<Boolean>
        get() = _isAdmin.asStateFlow()

    val isLoggedIn: StateFlow<Boolean>
        get() = _isLoggedIn.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        // Restore saved state
        _currentUserRole.value = prefs.getString("user_role", null)
        _isAdmin.value = prefs.getBoolean("is_admin", false)
        _currentUserShortId.value = prefs.getString("short_id", null)
        _isLoggedIn.value = auth.currentUser != null
    }

    private fun saveAuthState(isAdmin: Boolean, role: String?, shortId: String?) {
        prefs.edit().apply {
            putBoolean("is_admin", isAdmin)
            putString("user_role", role)
            putString("short_id", shortId)
            apply()
        }
    }

    fun getCurrentUser() = auth.currentUser

    fun updateUserRole(onComplete: (Boolean) -> Unit = {}) {
        _isLoading.value = true
        val currentUser = auth.currentUser
        if (currentUser != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val document = db.collection("users").document(currentUser.uid).get().await()
                    if (document.exists()) {
                        val role = document.getString("role") ?: "user"
                        val shortId = document.getString("short_id")
                        _currentUserRole.value = role
                        _isAdmin.value = role == "admin"
                        _currentUserShortId.value = shortId
                        saveAuthState(role == "admin", role, shortId)
                        _isLoading.value = false
                        withContext(Dispatchers.Main) {
                            onComplete(true)
                        }
                    } else {
                        _currentUserRole.value = "user"
                        _isAdmin.value = false
                        _currentUserShortId.value = null
                        saveAuthState(false, "user", null)
                        _isLoading.value = false
                        withContext(Dispatchers.Main) {
                            onComplete(false)
                        }
                    }
                } catch (e: Exception) {
                    _isLoading.value = false
                    withContext(Dispatchers.Main) {
                        onComplete(false)
                    }
                }
            }
        } else {
            _currentUserRole.value = null
            _isAdmin.value = false
            _currentUserShortId.value = null
            saveAuthState(false, null, null)
            _isLoading.value = false
            onComplete(false)
        }
    }

    fun signIn(email: String, password: String, context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _isLoading.value = true
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val userRef = db.collection("users").document(user.uid)
                        userRef.get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    val role = document.getString("role") ?: "user"
                                    val shortId = document.getString("short_id")
                                    _currentUserRole.value = role
                                    _isAdmin.value = role == "admin"
                                    _currentUserShortId.value = shortId
                                    _isLoggedIn.value = true
                                    saveAuthState(role == "admin", role, shortId)
                                    _isLoading.value = false
                                    onSuccess()
                                } else {
                                    // Nếu document không tồn tại, tạo mới với role user
                                    val userData = hashMapOf(
                                        "role" to "user",
                                        "email" to email,
                                        "createdAt" to System.currentTimeMillis()
                                    )
                                    userRef.set(userData)
                                        .addOnSuccessListener {
                                            _currentUserRole.value = "user"
                                            _isAdmin.value = false
                                            _currentUserShortId.value = null
                                            _isLoggedIn.value = true
                                            saveAuthState(false, "user", null)
                                            _isLoading.value = false
                                            onSuccess()
                                        }
                                        .addOnFailureListener { e ->
                                            _isLoading.value = false
                                            onError("Lỗi khi tạo thông tin người dùng: ${e.message}")
                                        }
                                }
                            }
                            .addOnFailureListener { e ->
                                _isLoading.value = false
                                onError("Lỗi khi lấy thông tin người dùng: ${e.message}")
                            }
                    }
                } else {
                    _isLoading.value = false
                    onError("Đăng nhập thất bại: ${task.exception?.message}")
                }
            }
    }

    fun signOut(context: Context, onComplete: () -> Unit = {}) {
        _isLoading.value = true
        try {
            auth.signOut()
            _currentUserRole.value = null
            _isAdmin.value = false
            _currentUserShortId.value = null
            _isLoggedIn.value = false
            saveAuthState(false, null, null)
            Toast.makeText(context, "Đăng xuất thành công", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi đăng xuất: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            _isLoading.value = false
            onComplete()
        }
    }

    fun createUser(email: String, password: String, context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _isLoading.value = true
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val userData = hashMapOf(
                            "role" to "user",
                            "email" to email,
                            "createdAt" to System.currentTimeMillis()
                        )
                        db.collection("users").document(user.uid)
                            .set(userData)
                            .addOnSuccessListener {
                                // Không gửi email xác minh
                                _currentUserRole.value = "user"
                                _isAdmin.value = false
                                _currentUserShortId.value = null
                                _isLoggedIn.value = true
                                saveAuthState(false, "user", null)
                                _isLoading.value = false
                                Toast.makeText(context, "Tạo tài khoản thành công. Vui lòng kiểm tra email để xác minh.", Toast.LENGTH_LONG).show()
                                onSuccess()
                            }
                            .addOnFailureListener { e ->
                                _isLoading.value = false
                                onError("Lỗi khi tạo thông tin người dùng: ${e.message}")
                            }
                    }
                } else {
                    _isLoading.value = false
                    onError(task.exception?.message ?: "Lỗi tạo tài khoản")
                }
            }
    }

    fun createAdminAccount(email: String, password: String, context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _isLoading.value = true
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val userData = hashMapOf(
                            "role" to "admin",
                            "email" to email,
                            "createdAt" to System.currentTimeMillis()
                        )
                        db.collection("users").document(user.uid)
                            .set(userData)
                            .addOnSuccessListener {
                                val adminData = hashMapOf(
                                    "email" to email,
                                    "createdAt" to System.currentTimeMillis(),
                                    "isActive" to true
                                )
                                db.collection("admin").document(user.uid)
                                    .set(adminData)
                                    .addOnSuccessListener {
                                        // Không gửi email xác minh cho admin
                                        _currentUserRole.value = "admin"
                                        _isAdmin.value = true
                                        _currentUserShortId.value = null
                                        _isLoggedIn.value = true
                                        saveAuthState(true, "admin", null)
                                        _isLoading.value = false
                                        Toast.makeText(context, "Tài khoản admin đã được tạo. Vui lòng kiểm tra email để xác minh.", Toast.LENGTH_LONG).show()
                                        onSuccess()
                                    }
                                    .addOnFailureListener { e ->
                                        _isLoading.value = false
                                        onError("Lỗi khi tạo thông tin admin: ${e.message}")
                                    }
                            }
                            .addOnFailureListener { e ->
                                _isLoading.value = false
                                onError("Lỗi khi tạo quyền admin: ${e.message}")
                            }
                    }
                } else {
                    _isLoading.value = false
                    onError(task.exception?.message ?: "Lỗi tạo tài khoản admin")
                }
            }
    }

    fun setAdminRole(userId: String, email: String, context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _isLoading.value = true

        // Cập nhật role trong collection users
        val userRef = db.collection("users").document(userId)
        val userData = hashMapOf(
            "role" to "admin",
            "email" to email,
            "updatedAt" to System.currentTimeMillis()
        )

        userRef.set(userData)
            .addOnSuccessListener {
                // Tạo hoặc cập nhật document trong collection admin
                val adminRef = db.collection("admin").document(userId)
                val adminData = hashMapOf(
                    "email" to email,
                    "createdAt" to System.currentTimeMillis(),
                    "isActive" to true
                )

                adminRef.set(adminData)
                    .addOnSuccessListener {
                        _isLoading.value = false
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        _isLoading.value = false
                        onError("Lỗi khi cập nhật thông tin admin: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                onError("Lỗi khi cập nhật quyền admin: ${e.message}")
            }
    }
} 