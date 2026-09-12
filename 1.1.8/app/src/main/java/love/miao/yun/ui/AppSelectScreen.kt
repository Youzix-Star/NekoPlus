package love.miao.yun.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import love.miao.yun.ui.theme.MiaoColors
import love.miao.yun.util.MiaoConfig

data class AppInfo(
    val name: String,
    val packageName: String,
    val iconBitmap: Bitmap?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val enabledApps = remember { mutableStateListOf<String>() }
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf(listOf<AppInfo>()) }
    var isLoading by remember { mutableStateOf(true) }

    val miaoBlue = MiaoColors.blue()
    val textPrimary = MiaoColors.textPrimary()
    val textSecondary = MiaoColors.textSecondary()
    val cardBg = MiaoColors.cardBg()
    val bg = MiaoColors.bg()
    val border = MiaoColors.border()
    val switchOff = MiaoColors.switchOff()

    LaunchedEffect(Unit) {
        val cfg = MiaoConfig.load(context)
        enabledApps.clear()
        enabledApps.addAll(cfg.enabledApps)
        installedApps = withContext(Dispatchers.IO) { getInstalledApps(context) }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择应用") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MiaoColors.topBar())
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索应用...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = textSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, null, tint = textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = miaoBlue,
                    unfocusedBorderColor = border,
                    unfocusedContainerColor = cardBg,
                    focusedContainerColor = cardBg
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "已选择 ${enabledApps.size} 个应用",
                    fontSize = 13.sp,
                    color = textSecondary
                )
                Row {
                    TextButton(onClick = {
                        val filtered = installedApps.filter {
                            searchQuery.isEmpty() ||
                                    it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.packageName.contains(searchQuery, ignoreCase = true)
                        }
                        val allSelected = filtered.all { it.packageName in enabledApps }
                        if (allSelected) {
                            filtered.forEach { enabledApps.remove(it.packageName) }
                        } else {
                            filtered.forEach { app ->
                                if (app.packageName !in enabledApps) {
                                    enabledApps.add(app.packageName)
                                }
                            }
                        }
                        saveEnabledApps(context, enabledApps.toSet())
                    }) {
                        val filtered = installedApps.filter {
                            searchQuery.isEmpty() ||
                                    it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.packageName.contains(searchQuery, ignoreCase = true)
                        }
                        val allSelected = filtered.isNotEmpty() && filtered.all { it.packageName in enabledApps }
                        Text(
                            if (allSelected) "取消全选" else "全选",
                            fontSize = 13.sp,
                            color = miaoBlue
                        )
                    }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = miaoBlue)
                }
            } else {
                val filtered = remember(installedApps, searchQuery) {
                    installedApps.filter {
                        searchQuery.isEmpty() ||
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                it.packageName.contains(searchQuery, ignoreCase = true)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = filtered,
                        key = { it.packageName },
                        contentType = { "app_item" }
                    ) { app ->
                        AppItem(
                            app = app,
                            isEnabled = app.packageName in enabledApps,
                            miaoBlue = miaoBlue,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            cardBg = cardBg,
                            border = border,
                            switchOff = switchOff,
                            onToggle = {
                                if (app.packageName in enabledApps) {
                                    enabledApps.remove(app.packageName)
                                } else {
                                    enabledApps.add(app.packageName)
                                }
                                saveEnabledApps(context, enabledApps.toSet())
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppItem(
    app: AppInfo,
    isEnabled: Boolean,
    miaoBlue: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBg: Color,
    border: Color,
    switchOff: Color,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, border),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imgBitmap = remember(app.packageName) {
                app.iconBitmap?.asImageBitmap()
            }
            if (imgBitmap != null) {
                Image(
                    bitmap = imgBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Box(Modifier.size(36.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary
                )
                Text(
                    app.packageName,
                    fontSize = 11.sp,
                    color = textSecondary
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = miaoBlue,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = switchOff,
                    uncheckedThumbColor = Color.White
                )
            )
        }
    }
}

/**
 * 加载应用列表，按 packageName 去重（双卡手机可能有重复）
 */
private fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    return try {
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
            .mapNotNull { ri ->
                try {
                    val name = ri.loadLabel(pm)?.toString() ?: return@mapNotNull null
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    val drawable = try { ri.loadIcon(pm) } catch (_: Exception) { null }
                    val bitmap = drawable?.let { drawableToBitmap(it) }
                    AppInfo(name, pkg, bitmap)
                } catch (_: Exception) {
                    null
                }
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    return try {
        when (drawable) {
            is BitmapDrawable -> {
                val bmp = drawable.bitmap
                if (bmp.width > 0 && bmp.height > 0) bmp else null
            }
            is AdaptiveIconDrawable -> {
                val size = 96
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                bitmap
            }
            else -> {
                val w = drawable.intrinsicWidth.coerceIn(1, 256)
                val h = drawable.intrinsicHeight.coerceIn(1, 256)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
                bitmap
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun saveEnabledApps(context: Context, apps: Set<String>) {
    val cfg = MiaoConfig.load(context)
    cfg.enabledApps = apps
    cfg.save(context)
}
