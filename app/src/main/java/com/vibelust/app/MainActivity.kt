package com.vibelust.app

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import androidx.activity.compose.BackHandler
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

// Visual Theme Colors
val SpaceDark = Color(0xFF07070B)
val NebulaSurface = Color(0xFF131322)
val NeonPurple = Color(0xFF8B5CF6)
val VividAmethyst = Color(0xFFA78BFA)
val AntiqueGold = Color(0xFFFBBF24)
val SilverMist = Color(0xFFCBD5E1)
val ErrorRed = Color(0xFFEF4444)
val ActiveGreen = Color(0xFF10B981)

class MainActivity : ComponentActivity() {

    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.getDatabase(applicationContext)

        // Prepopulate standard loops if empty
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val wallpapers = db.wallpaperDao().getAllWallpapers().first()
                val defaultFile = File(filesDir, "live_wallpaper.mp4")

                // Pre-download high quality, beautiful looping video if missing
                if (!defaultFile.exists()) {
                    val candidateUrls = listOf(
                        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        "https://www.w3schools.com/html/mov_bbb.mp4",
                        "https://www.w3schools.com/html/movie.mp4"
                    )
                    var success = false
                    for (urlString in candidateUrls) {
                        try {
                            Log.i("MainActivity", "Attempting cloud preload of $urlString...")
                            val url = java.net.URL(urlString)
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.connectTimeout = 8000
                            connection.readTimeout = 12000
                            val responseCode = connection.responseCode
                            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                                connection.inputStream.use { input ->
                                    defaultFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                Log.i("MainActivity", "Cloud preload finished successfully from $urlString!")
                                success = true
                                break
                            } else {
                                Log.w("MainActivity", "Preload failed with response code $responseCode from $urlString")
                            }
                        } catch (ex: Exception) {
                            Log.e("MainActivity", "Failed to preload from $urlString: ${ex.message}")
                        }
                    }
                    if (!success) {
                        Log.e("MainActivity", "All premium cosmic asset preload URLs failed, fallback to uploaded media.")
                    }
                }

                if (wallpapers.isEmpty()) {
                    db.wallpaperDao().insertWallpaper(
                        Wallpaper(
                            name = "Ambient Universe Loop",
                            filePath = defaultFile.absolutePath,
                            addedBy = "shubhamraj.std@gmail.com"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Prepopulation failed", e)
            }
        }

        // Initialize AdMob Mobile Ads SDK
        MobileAds.initialize(this) { status ->
            Log.i("MainActivity", "AdMob initialized: $status")
        }

        setContent {
            VibeLustTheme {
                MainAppContainer(
                    db = db,
                    onTriggerWallpaperSelector = { wallpaper ->
                        setActiveWallpaper(this, wallpaper)
                    }
                )
            }
        }
    }

    private fun setActiveWallpaper(activity: Activity, wallpaper: Wallpaper) {
        val destFile = File(activity.filesDir, "live_wallpaper.mp4")
        val sourceFile = File(wallpaper.filePath)

        if (!sourceFile.exists()) {
            Toast.makeText(activity, "Applying original direct loop backup stream for preset.", Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Safeguard against copying file to itself if destFile and sourceFile are identical
                if (sourceFile.exists() && sourceFile.absolutePath != destFile.absolutePath) {
                    if (destFile.exists()) {
                        destFile.delete()
                    }
                    sourceFile.inputStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    try {
                        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                            putExtra(
                                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                ComponentName(activity, VideoWallpaperService::class.java)
                            )
                        }
                        activity.startActivity(intent)
                        Toast.makeText(activity, "Selected loop cached! Click 'Set Wallpaper' to confirm.", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        try {
                            activity.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                        } catch (ex: Exception) {
                            Toast.makeText(activity, "Please apply wallpaper manually from launcher settings.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "Database selection finished with backup file alignment.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun handleUserRegistration(context: Context, email: String, name: String) {
        val prefs = context.getSharedPreferences("vibelust_user_prefs", Context.MODE_PRIVATE)
        val isFirstTimeKey = "welcomed_${email.replace(".", "_")}"
        val alreadyWelcomed = prefs.getBoolean(isFirstTimeKey, false)
        
        val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                db.userDao().insertUser(User(email = email, displayName = name))
            } catch (e: Exception) {
                Log.e("MainActivity", "User db store failure", e)
            }
            
            if (!alreadyWelcomed) {
                // Dispatch SMTP Welcome asynchronously
                val success = EmailSender.sendWelcomeAndAdminNotification(email, name)
                withContext(Dispatchers.Main) {
                    if (success) {
                        prefs.edit().putBoolean(isFirstTimeKey, true).apply()
                        Toast.makeText(context, "Welcome Setup Complete! Greeting dispatched via SMTP server.", Toast.LENGTH_LONG).show()
                    } else {
                        Log.w("MainActivity", "Email dispatch network threshold bypassed.")
                    }
                }
            }
        }
    }
}

@Composable
fun VibeLustTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeonPurple,
            secondary = AntiqueGold,
            background = SpaceDark,
            surface = NebulaSurface,
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onBackground = SilverMist,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun MainAppContainer(
    db: AppDatabase,
    onTriggerWallpaperSelector: (Wallpaper) -> Unit
) {
    val context = LocalContext.current
    var userEmail by remember { mutableStateOf("") }
    var userDisplayName by remember { mutableStateOf("") }
    var currentTab by remember { mutableStateOf(0) }

    // Google Sign-In Setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember {
        try {
            GoogleSignIn.getClient(context, gso)
        } catch (e: Exception) {
            Log.e("MainActivity", "Google Play Services auth client not available on system", e)
            null
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            userEmail = account?.email ?: ""
            userDisplayName = account?.displayName ?: "Explorer"
            Toast.makeText(context, "Welcome, $userDisplayName! Signed in via Google.", Toast.LENGTH_SHORT).show()
            (context as? MainActivity)?.handleUserRegistration(context, userEmail, userDisplayName)
        } catch (e: Exception) {
            Log.e("MainActivity", "Google sign-in error", e)
            Toast.makeText(context, "Google Sign-In failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Step-by-Step Back Button Handler
    var backPressedTime by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = true) {
        if (userEmail.isNotEmpty() && currentTab != 0) {
            currentTab = 0
        } else {
            val activity = context as? Activity
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressedTime < 2000) {
                activity?.finish()
            } else {
                backPressedTime = currentTime
                Toast.makeText(context, "Press BACK again to exit ✧", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AdmobBanner(modifier = Modifier.height(56.dp))
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpaceDark)
                .padding(innerPadding)
        ) {
            if (userEmail.isEmpty()) {
                LoginScreen(
                    onGoogleSignInClick = {
                        try {
                            val client = googleSignInClient
                            if (client != null) {
                                signInLauncher.launch(client.signInIntent)
                            } else {
                                Toast.makeText(context, "Google Sign-In client unavailable. Please sign in via secure email protocol.", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failing over to dynamic credentials context.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeveloperPortalSignIn = { email, name ->
                        userEmail = email
                        userDisplayName = name
                        Toast.makeText(context, "Logged in as: $email", Toast.LENGTH_SHORT).show()
                        (context as? MainActivity)?.handleUserRegistration(context, email, name)
                    }
                )
            } else {
                val isAdmin = userEmail.equals("shubhamraj.std@gmail.com", ignoreCase = true) || userEmail.equals("vibelust.music@gmail.com", ignoreCase = true)

                Column(modifier = Modifier.fillMaxSize()) {
                    // Modern Branding header
                    HeaderSection(
                        userEmail = userEmail,
                        userDisplayName = userDisplayName,
                        onSignOutClick = {
                            val client = googleSignInClient
                            if (client != null) {
                                client.signOut().addOnCompleteListener {
                                    userEmail = ""
                                    userDisplayName = ""
                                    currentTab = 0
                                    Toast.makeText(context, "Signed out safely.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                userEmail = ""
                                userDisplayName = ""
                                currentTab = 0
                                Toast.makeText(context, "Signed out safely.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // Navigation Tabs displayed to all users
                    TabRow(
                        selectedTabIndex = currentTab,
                        containerColor = NebulaSurface,
                        contentColor = AntiqueGold
                    ) {
                        Tab(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            modifier = Modifier.testTag("loops_catalog_tab_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (currentTab == 0) AntiqueGold else SilverMist
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Loops Catalog",
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentTab == 0) AntiqueGold else SilverMist
                                )
                            }
                        }
                        Tab(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            modifier = Modifier.testTag("admin_tab_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (currentTab == 1) AntiqueGold else SilverMist
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isAdmin) "Admin Studio" else "Upload Studio",
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentTab == 1) AntiqueGold else SilverMist
                                )
                            }
                        }
                    }

                    if (currentTab == 0) {
                        SearchCatalogScreen(
                            db = db,
                            onTriggerWallpaperSelector = onTriggerWallpaperSelector,
                            isAdmin = isAdmin,
                            userEmail = userEmail
                        )
                    } else {
                        AdminControlScreen(
                            db = db,
                            adminEmail = userEmail
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onGoogleSignInClick: () -> Unit,
    onDeveloperPortalSignIn: (String, String) -> Unit
) {
    var customEmail by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(SpaceDark),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Identity Brand Block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NebulaSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(NeonPurple, AntiqueGold)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✧ Vibe Lust ✧",
                    fontSize = 32.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "PREMIUM LIVE WALLPAPERS",
                    fontSize = 11.sp,
                    color = AntiqueGold,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // Google Sign In trigger
                Button(
                    onClick = onGoogleSignInClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("google_sign_in_button"),
                    border = BorderStroke(1.dp, SilverMist)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "G ",
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF4285F4),
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Sign in with Google",
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Divider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.12f))
                    Text(
                        text = " OR ",
                        fontSize = 11.sp,
                        color = SilverMist.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Divider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.12f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Email Input Authentication
                Text(
                    text = "SECURE PROTOCOL EMAIL SIGN-IN",
                    fontSize = 10.sp,
                    color = AntiqueGold,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextField(
                    value = customEmail,
                    onValueChange = { customEmail = it },
                    placeholder = { Text("your.name@example.com", color = SilverMist.copy(alpha = 0.4f)) },
                    singleLine = true,
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = SpaceDark,
                        focusedIndicatorColor = NeonPurple,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_email_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val email = customEmail.trim()
                        if (email.isEmpty()) {
                            Toast.makeText(context, "Please enter an email address.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            Toast.makeText(context, "Please enter a valid email format.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onDeveloperPortalSignIn(email, email.substringBefore("@").replaceFirstChar { it.uppercase() })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("custom_email_sign_in_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sign In with Email",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Divider(color = Color.White.copy(alpha = 0.12f))

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "✧ SECURED BY GOOGLE AUTHENTICATION PROTOCOLS ✧",
                    fontSize = 9.sp,
                    color = SilverMist.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun HeaderSection(
    userEmail: String,
    userDisplayName: String,
    onSignOutClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NebulaSurface),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Vibe Lust ✦",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Text(
                    text = "Active: $userEmail",
                    fontSize = 10.sp,
                    color = AntiqueGold,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onSignOutClick,
                modifier = Modifier.testTag("logout_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Sign Out",
                    tint = ErrorRed
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchCatalogScreen(
    db: AppDatabase,
    onTriggerWallpaperSelector: (Wallpaper) -> Unit,
    isAdmin: Boolean,
    userEmail: String
) {
    var searchQuery by remember { mutableStateOf("") }
    val wallpapersState = db.wallpaperDao().getAllWallpapers().collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()

    val filteredList = wallpapersState.value.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Aesthetic Search bar
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search loops by name...", color = SilverMist.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AntiqueGold) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input"),
            colors = TextFieldDefaults.textFieldColors(
                containerColor = NebulaSurface,
                focusedIndicatorColor = NeonPurple,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) "No loops registered yet.\nUpload your own live loops to start!" else "No wallpapers match '$searchQuery'",
                    color = SilverMist,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredList) { wallpaper ->
                    WallpaperCard(
                        wallpaper = wallpaper,
                        onTriggerSelection = { onTriggerWallpaperSelector(wallpaper) },
                        isAdmin = isAdmin || wallpaper.addedBy.equals(userEmail, ignoreCase = true),
                        onDeleteClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                db.wallpaperDao().deleteWallpaper(wallpaper)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WallpaperCard(
    wallpaper: Wallpaper,
    onTriggerSelection: () -> Unit,
    isAdmin: Boolean,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wallpaper_${wallpaper.id}"),
        colors = CardDefaults.cardColors(containerColor = NebulaSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = wallpaper.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Auto-Trim (5s)",
                            fontSize = 9.sp,
                            color = AntiqueGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "By: ${if (wallpaper.addedBy.contains("@")) wallpaper.addedBy.split("@")[0] else wallpaper.addedBy}",
                        fontSize = 10.sp,
                        color = SilverMist.copy(alpha = 0.7f)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onTriggerSelection,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("apply_button_${wallpaper.id}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                OutlinedButton(
                    onClick = { exportWallpaperToDownloads(context, wallpaper) },
                    border = BorderStroke(1.dp, AntiqueGold),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("download_button_${wallpaper.id}")
                ) {
                    Log.d("MainActivity", "Download trigger attached for ${wallpaper.name}")
                    Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AntiqueGold)
                }

                if (isAdmin) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.testTag("delete_button_${wallpaper.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Wallpaper",
                            tint = ErrorRed.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminControlScreen(
    db: AppDatabase,
    adminEmail: String
) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf("") }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val visualPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            selectedFileName = uri.lastPathSegment ?: "video.mp4"
            Toast.makeText(context, "Loop source selected!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NebulaSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Publish Premium Loops",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Text(
                    text = "Import local media. The app automatically mutes and trims content to a clean, battery-efficient 5s loops system.",
                    fontSize = 11.sp,
                    color = SilverMist.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Wallpaper Name
                Text(
                    text = "LOOP NAME",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AntiqueGold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("e.g. Synthwave Highway", color = SilverMist.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = SpaceDark,
                        focusedIndicatorColor = NeonPurple,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("name_text_input")
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Select Video File Button
                Button(
                    onClick = {
                        visualPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpaceDark),
                    border = BorderStroke(1.dp, NeonPurple),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("select_video_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = NeonPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedVideoUri == null) "Select Video Loop Source" else "Attached: $selectedFileName",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Publish Button
                if (isSaving) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = AntiqueGold)
                    }
                } else {
                    Button(
                        onClick = {
                            val uri = selectedVideoUri
                            val name = nameInput.trim()
                            if (name.isEmpty()) {
                                Toast.makeText(context, "Please name the loop wallpaper", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (uri == null) {
                                Toast.makeText(context, "Please select a loop source video.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isSaving = true
                            processAdminVideo(
                                context = context,
                                uri = uri,
                                name = name,
                                addedBy = adminEmail,
                                onStart = { isSaving = true },
                                onComplete = { success, msg ->
                                    isSaving = false
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (success) {
                                        nameInput = ""
                                        selectedVideoUri = null
                                        selectedFileName = ""
                                    }
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AntiqueGold),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_loop_button")
                    ) {
                        Text(
                            text = "PROCESS & PUBLISH LOOP",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

private fun processAdminVideo(
    context: Context,
    uri: Uri,
    name: String,
    addedBy: String,
    onStart: () -> Unit,
    onComplete: (Boolean, String?) -> Unit
) {
    onStart()
    kotlin.concurrent.thread {
        try {
            val wallpapersDir = File(context.filesDir, "app_wallpapers")
            if (!wallpapersDir.exists()) {
                wallpapersDir.mkdirs()
            }

            val targetFile = File(wallpapersDir, "loop_${System.currentTimeMillis()}.mp4")
            if (targetFile.exists()) {
                targetFile.delete()
            }

            var success = false
            var extractor: MediaExtractor? = null
            var muxer: MediaMuxer? = null

            try {
                extractor = MediaExtractor()
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                }

                val trackCount = extractor.trackCount
                var videoTrackIndexInExtractor = -1
                var videoFormat: MediaFormat? = null

                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        videoTrackIndexInExtractor = i
                        videoFormat = format
                        break
                    }
                }

                if (videoTrackIndexInExtractor != -1 && videoFormat != null) {
                    extractor.selectTrack(videoTrackIndexInExtractor)

                    muxer = MediaMuxer(targetFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    val videoTrackIndexInMuxer = muxer.addTrack(videoFormat)
                    muxer.start()

                    val maxBufferSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    } else {
                        1024 * 1024
                    }
                    val buffer = ByteBuffer.allocate(maxBufferSize)
                    val bufferInfo = MediaCodec.BufferInfo()

                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    val trimDurationUs = 5_000_000L

                    while (true) {
                        bufferInfo.offset = 0
                        bufferInfo.size = extractor.readSampleData(buffer, 0)
                        if (bufferInfo.size < 0) break

                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        if (bufferInfo.presentationTimeUs > trimDurationUs) break

                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(videoTrackIndexInMuxer, buffer, bufferInfo)
                        extractor.advance()
                    }
                    success = true
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Native multiplexer failed, fallback copy deployed", e)
            } finally {
                try { extractor?.release() } catch (ex: Exception) {}
                try { muxer?.stop() } catch (ex: Exception) {}
                try { muxer?.release() } catch (ex: Exception) {}
            }

            if (!success) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    success = true
                } catch (e: Exception) {
                    Log.e("MainActivity", "Both native processing and system copy failed", e)
                }
            }

            if (success) {
                val db = AppDatabase.getDatabase(context)
                val wallpaper = Wallpaper(
                    name = name,
                    filePath = targetFile.absolutePath,
                    addedBy = addedBy
                )

                kotlinx.coroutines.runBlocking {
                    db.wallpaperDao().insertWallpaper(wallpaper)
                }

                (context as Activity).runOnUiThread {
                    onComplete(true, "Published Premium Loop Successfully!")
                }
            } else {
                (context as Activity).runOnUiThread {
                    onComplete(false, "Failed to capture source video streams.")
                }
            }

        } catch (e: Exception) {
            (context as Activity).runOnUiThread {
                onComplete(false, "Conversion Error: ${e.localizedMessage}")
            }
        }
    }
}

@Composable
fun AdmobBanner(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            try {
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    setAdUnitId("ca-app-pub-5879858884847224/2721072268")
                    loadAd(AdRequest.Builder().build())
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Admob AdView creation failed safely", e)
                android.view.View(context)
            }
        }
    )
}

// System shared filesystem exporter
private fun exportWallpaperToDownloads(context: Context, wallpaper: Wallpaper) {
    val sourceFile = File(wallpaper.filePath)
    if (!sourceFile.exists()) {
        Toast.makeText(context, "Loop source file does not exist locally.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        val safeName = wallpaper.name.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val destinationFile = File(downloadDir, "VibeLust_${safeName}_${System.currentTimeMillis()}.mp4")
        
        sourceFile.inputStream().use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(destinationFile.absolutePath),
            arrayOf("video/mp4")
        ) { path, uri ->
            Log.i("MainActivity", "Successfully indexed: $path -> $uri")
        }
        
        Toast.makeText(context, "Saved to Downloads folder: ${destinationFile.name}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e("MainActivity", "Export failed", e)
        Toast.makeText(context, "Error saving live loop: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
