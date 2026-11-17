package com.example.passportstudio

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val Background = Color(0xFF091326)
private val Surface = Color(0xFF17243B)
private val Surface2 = Color(0xFF21314B)
private val Accent = Color(0xFF5C35C8)
private val Accent2 = Color(0xFF6FB8FF)
private val TextPrimary = Color(0xFFF5F7FB)
private val TextSecondary = Color(0xFFAEBBD0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val w: Window = window
        w.statusBarColor = android.graphics.Color.rgb(24, 35, 58)
        w.navigationBarColor = android.graphics.Color.rgb(9, 19, 38)
        setContent { PassportStudioApp() }
    }
}

data class PhotoSize(val title: String, val subtitle: String, val cmW: Float, val cmH: Float, val recommended: Boolean = false)

private val photoSizes = listOf(
    PhotoSize("6×4 سم", "جواز السفر والمعاملات", 6f, 4f, true),
    PhotoSize("4.5×3.5 سم", "تأشيرات شنغن وأوروبا والإمارات", 4.5f, 3.5f),
    PhotoSize("5×5 سم", "تأشيرات وصور شخصية", 5f, 5f)
)

enum class Screen { HOME, EDITOR }

data class EditorState(
    val source: Bitmap,
    val size: PhotoSize,
    val background: Int = android.graphics.Color.WHITE,
    val removeBackground: Boolean = false,
    val cropScale: Float = 1f
)

@Composable
fun PassportStudioApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.HOME) }
    var editor by remember { mutableStateOf<EditorState?>(null) }
    var savedUris by remember { mutableStateOf(loadSavedUris(context)) }
    var selectedSize by remember { mutableStateOf(photoSizes.first()) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bitmap = ImageUtils.loadBitmap(context, uri) ?: return@rememberLauncherForActivityResult
        editor = EditorState(bitmap, selectedSize)
        screen = Screen.EDITOR
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        val uri = pendingCameraUri ?: return@rememberLauncherForActivityResult
        val bitmap = ImageUtils.loadBitmap(context, uri) ?: return@rememberLauncherForActivityResult
        editor = EditorState(bitmap, selectedSize)
        screen = Screen.EDITOR
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "images").apply { mkdirs() }
            val photo = File.createTempFile("passport_", ".jpg", file)
            val uri = FileProvider.getUriForFile(context, context.getString(R.string.file_provider_authority), photo)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent,
        secondary = Accent2,
        background = Background,
        surface = Surface,
        onBackground = TextPrimary,
        onSurface = TextPrimary
    )) {
        CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Box(Modifier.fillMaxSize().background(Background)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        selectedSize = selectedSize,
                        onSizeSelected = { selectedSize = it },
                        savedUris = savedUris,
                        onPick = { galleryLauncher.launch("image/*") },
                        onCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSaved = { uri ->
                            val bitmap = ImageUtils.loadBitmap(context, uri)
                            if (bitmap != null) {
                                editor = EditorState(bitmap, selectedSize)
                                screen = Screen.EDITOR
                            }
                        }
                    )
                    Screen.EDITOR -> EditorScreen(
                        state = editor,
                        onBack = { screen = Screen.HOME },
                        onChange = { editor = it },
                        onSaved = { uri ->
                            savedUris = (listOf(uri.toString()) + savedUris).distinct().take(20)
                            persistSavedUris(context, savedUris)
                            screen = Screen.HOME
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    selectedSize: PhotoSize,
    onSizeSelected: (PhotoSize) -> Unit,
    savedUris: List<Uri>,
    onPick: () -> Unit,
    onCamera: () -> Unit,
    onOpenSaved: (Uri) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(shape = RoundedCornerShape(18.dp), color = Accent, modifier = Modifier.size(50.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ContentCut, null, tint = Color.White, modifier = Modifier.size(25.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("صور المعاملات PRO", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text("استوديو الجوازات والطباعة الرسمية", color = TextSecondary, fontSize = 13.sp)
                }
                Icon(Icons.Filled.Settings, null, tint = TextSecondary, modifier = Modifier.size(25.dp))
            }
        }

        item {
            BannerCard()
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = "التقاط بالكاميرا",
                    subtitle = "صورة جديدة فورية",
                    icon = Icons.Filled.CameraAlt,
                    background = Color(0xFF70C5EE),
                    onClick = onCamera
                )
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = "اختيار من المعرض",
                    subtitle = "تعديل صورة سابقة",
                    icon = Icons.Filled.Collections,
                    background = Color(0xFF7BADED),
                    onClick = onPick
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("قياسات صور المعاملات الشائعة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("دقة الإخراج 300 DPI • جاهزة للطباعة", color = TextSecondary, fontSize = 12.sp)
                }
                Icon(Icons.Filled.Straighten, null, tint = Accent2)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                photoSizes.forEach { size ->
                    SizeCard(size, selectedSize == size, Modifier.weight(1f), onSizeSelected)
                }
            }
        }

        item { StandardsCard() }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("سجل الصور المحفوظة (${savedUris.size})", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("اضغط على أي صورة لإعادة التعديل أو الحفظ", color = TextSecondary, fontSize = 12.sp)
                }
                Icon(Icons.Filled.WorkspacePremium, null, tint = Color(0xFF83BDF8))
            }
        }

        if (savedUris.isEmpty()) {
            item { EmptySavedCard() }
        } else {
            items(savedUris) { uri ->
                SavedRow(uri = uri, onClick = { onOpenSaved(uri) })
            }
        }
    }
}

@Composable
private fun BannerCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(208.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1B4A69))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF10314C))
            for (i in 0..7) {
                drawCircle(Color(0xFF196B80).copy(alpha = 0.35f), radius = 70f + i * 16, center = androidx.compose.ui.geometry.Offset(w * (0.08f + i * 0.12f), h * 0.85f))
            }
            val paper = androidx.compose.ui.geometry.Rect(w * 0.34f, h * 0.08f, w * 0.84f, h * 0.92f)
            drawRoundRect(Color(0xFFF2F4F7), topLeft = paper.topLeft, size = paper.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
            for (r in 0..2) for (c in 0..2) {
                val x = paper.left + 28 + c * 92
                val y = paper.top + 28 + r * 58
                drawRoundRect(Color(0xFFDCE7EE), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(60f, 40f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
            }
            drawRoundRect(Color(0xFF2B8FB0).copy(alpha = 0.92f), androidx.compose.ui.geometry.Offset(w * 0.08f, h * 0.09f), androidx.compose.ui.geometry.Size(w * 0.25f, 32f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f))
        }
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Center) {
            Surface(color = Color(0xFF1B8EC4), shape = RoundedCornerShape(20.dp), modifier = Modifier.wrapContentWidth()) {
                Text("جاهز للطباعة والقص الفوري", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("تعديل صور المعاملات وإزالة الخلفية", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(5.dp))
            Text("استبدال الخلفية بالأبيض/الأزرق وضبط المقاسات الرسمية", color = Color(0xFFD6E9F7), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniTag("4×6")
                MiniTag("4.5×3.5")
                MiniTag("5×5")
            }
        }
    }
}

@Composable
private fun MiniTag(text: String) {
    Surface(color = Color(0xFF0B2238).copy(alpha = 0.74f), shape = RoundedCornerShape(8.dp)) {
        Text(text, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun ActionCard(modifier: Modifier, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, background: Color, onClick: () -> Unit) {
    Surface(modifier = modifier.height(168.dp).clickable(onClick = onClick), color = background, shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(9.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
            Text(subtitle, color = Color.White.copy(alpha = 0.80f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SizeCard(size: PhotoSize, selected: Boolean, modifier: Modifier, onClick: (PhotoSize) -> Unit) {
    val borderColor = if (selected) Accent2 else Color(0xFF3D4A60)
    Surface(
        modifier = modifier.height(166.dp).border(if (selected) 3.dp else 1.dp, borderColor, RoundedCornerShape(22.dp)).clickable { onClick(size) },
        shape = RoundedCornerShape(22.dp), color = if (selected) Color(0xFF5433A2) else Surface
    ) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            if (size.recommended) {
                Surface(color = Color(0xFF5E4A94), shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.TopStart)) {
                    Text("الأكثر طلباً", modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = Color.White, fontSize = 10.sp)
                }
            }
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Accent2, modifier = Modifier.align(Alignment.TopEnd).size(22.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                Text(size.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(size.subtitle, color = Color(0xFFE0E5EF), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
                Spacer(Modifier.height(7.dp))
                Text("${(size.cmW * 10).roundToInt()}×${(size.cmH * 10).roundToInt()} مم", color = Color(0xFFB7C2D2), fontSize = 11.sp)
                Text("الخلفية: أبيض أو أزرق فاتح", color = Color(0xFF9AABC0), fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun StandardsCard() {
    Surface(color = Surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF30415D), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF39A8E4), modifier = Modifier.padding(7.dp).size(21.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text("معايير صور الجوازات والمعاملات الرسمية", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            StandardLine("خلفية بلون موحّد (الأبيض الناصع هو الأكثر طلباً دولياً)")
            StandardLine("وضعية أمامية مستقيمة والنظر مباشرة للعدسة")
            StandardLine("يشغل الرأس والكتفان تقريباً 70% إلى 80% من مساحة الصورة")
            StandardLine("إضاءة متوازنة دون ظلال داكنة على الوجه أو الخلفية")
        }
    }
}

@Composable
private fun StandardLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF1A97D5), modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFFDCE5F0), fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun EmptySavedCard() {
    Surface(color = Color(0xFF17243B), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(150.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
            Icon(Icons.Filled.Image, null, tint = Color(0xFF64738B), modifier = Modifier.size(46.dp))
            Spacer(Modifier.height(7.dp))
            Text("لا توجد صور محفوظة بعد", color = Color(0xFF9DAAC0), fontWeight = FontWeight.Bold)
            Text("التقط أو اختر صورة للبدء", color = Color(0xFF74839A), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SavedRow(uri: Uri, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(uri) { ImageUtils.loadBitmap(context, uri) }
    Surface(color = Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(78.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                if (bitmap != null) Image(bitmap.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("صورة معاملة محفوظة", fontWeight = FontWeight.Bold)
                Text("اضغط لإعادة التعديل", color = TextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ArrowBack, null, tint = Accent2)
        }
    }
}

@Composable
private fun EditorScreen(state: EditorState?, onBack: () -> Unit, onChange: (EditorState) -> Unit, onSaved: (Uri) -> Unit) {
    if (state == null) return
    val context = LocalContext.current
    var showSaveDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val processed = remember(state.source, state.size, state.background, state.removeBackground) {
        ImageUtils.prepareForPrint(state.source, state.size, state.background, state.removeBackground)
    }

    Column(Modifier.fillMaxSize().background(Background).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "رجوع", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("استوديو التعديل", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("${state.size.title} • 300 DPI", color = TextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Filled.Tune, null, tint = Accent2)
        }

        Spacer(Modifier.height(10.dp))
        Surface(color = Surface, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .aspectRatio(state.size.cmW / state.size.cmH)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(state.background))
                ) {
                    Image(processed.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("الخلفية", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            BackgroundChip("أبيض", Color.White.toArgb(), state.background == Color.White.toArgb()) { onChange(state.copy(background = Color.White.toArgb())) }
            BackgroundChip("أزرق فاتح", Color(0xFFDCEEFF).toArgb(), state.background == Color(0xFFDCEEFF).toArgb()) { onChange(state.copy(background = Color(0xFFDCEEFF).toArgb())) }
            BackgroundChip("رمادي", Color(0xFFF0F1F3).toArgb(), state.background == Color(0xFFF0F1F3).toArgb()) { onChange(state.copy(background = Color(0xFFF0F1F3).toArgb())) }
            FilterChip(
                selected = state.removeBackground,
                onClick = { onChange(state.copy(removeBackground = !state.removeBackground)) },
                label = { Text("إزالة الخلفية") },
                leadingIcon = { Icon(Icons.Filled.ContentCut, null, modifier = Modifier.size(18.dp)) }
            )
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { showSaveDialog = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.Download, null)
                Spacer(Modifier.width(5.dp))
                Text("حفظ صورة")
            }
            Button(onClick = { showSaveDialog = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Icon(Icons.Filled.WorkspacePremium, null)
                Spacer(Modifier.width(5.dp))
                Text("جاهز للطباعة")
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!saving) showSaveDialog = false },
            title = { Text("حفظ الصورة") },
            text = { Text("سيتم حفظ الصورة بالمقاس ${state.size.cmW}×${state.size.cmH} سم وبأبعاد مناسبة لطباعة 300 DPI.") },
            confirmButton = {
                Button(enabled = !saving, onClick = {
                    saving = true
                    val uri = ImageUtils.saveToGallery(context, processed, state.size)
                    saving = false
                    if (uri != null) {
                        showSaveDialog = false
                        onSaved(uri)
                    }
                }) { Text(if (saving) "جاري الحفظ..." else "حفظ") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { showSaveDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun BackgroundChip(label: String, color: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color(color),
        contentColor = if (color == Color.White.toArgb()) Color(0xFF1E2B40) else Color(0xFF1E2B40),
        shape = RoundedCornerShape(13.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Accent2) else null
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

object ImageUtils {
    fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }

    fun prepareForPrint(source: Bitmap, size: PhotoSize, background: Int, removeBackground: Boolean): Bitmap {
        val widthPx = (size.cmW / 2.54f * 300f).roundToInt()
        val heightPx = (size.cmH / 2.54f * 300f).roundToInt()
        val cropped = centerCrop(source, widthPx, heightPx)
        return if (removeBackground) replaceUniformBackground(cropped, background) else cropped
    }

    private fun centerCrop(src: Bitmap, outW: Int, outH: Int): Bitmap {
        val ratio = outW.toFloat() / outH
        val srcRatio = src.width.toFloat() / src.height
        val cropW: Int
        val cropH: Int
        if (srcRatio > ratio) {
            cropH = src.height
            cropW = (src.height * ratio).roundToInt()
        } else {
            cropW = src.width
            cropH = (src.width / ratio).roundToInt()
        }
        val left = (src.width - cropW) / 2
        val top = (src.height - cropH) / 2
        return Bitmap.createScaledBitmap(Bitmap.createBitmap(src, left, top, cropW, cropH), outW, outH, true)
    }

    // Offline heuristic: intended for plain/light backgrounds. For complex scenes use a
    // dedicated segmentation model in a future Pro module.
    private fun replaceUniformBackground(src: Bitmap, bgColor: Int): Bitmap {
        val bitmap = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val edgeSamples = mutableListOf<Int>()
        for (x in 0 until bitmap.width step maxOf(1, bitmap.width / 24)) {
            edgeSamples += pixels[x]
            edgeSamples += pixels[(bitmap.height - 1) * bitmap.width + x]
        }
        for (y in 0 until bitmap.height step maxOf(1, bitmap.height / 24)) {
            edgeSamples += pixels[y * bitmap.width]
            edgeSamples += pixels[y * bitmap.width + (bitmap.width - 1)]
        }
        val avgR = edgeSamples.map { (it shr 16) and 0xFF }.average()
        val avgG = edgeSamples.map { (it shr 8) and 0xFF }.average()
        val avgB = edgeSamples.map { it and 0xFF }.average()
        val br = (bgColor shr 16) and 0xFF
        val bg = (bgColor shr 8) and 0xFF
        val bb = bgColor and 0xFF

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val d = abs(r - avgR) + abs(g - avgG) + abs(b - avgB)
            val brightness = (r + g + b) / 3
            // Avoid aggressively erasing dark hair/clothing; remove only pixels close to the
            // estimated border background.
            if (d < 55 && brightness > 115) {
                pixels[i] = (0xFF shl 24) or (br shl 16) or (bg shl 8) or bb
            }
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return bitmap
    }

    fun saveToGallery(context: Context, bitmap: Bitmap, size: PhotoSize): Uri? {
        val resolver = context.contentResolver
        val name = "passport_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/صور المعاملات PRO")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out) }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }
}

private fun loadSavedUris(context: Context): List<Uri> {
    val prefs = context.getSharedPreferences("saved_photos", Context.MODE_PRIVATE)
    return prefs.getStringSet("uris", emptySet())?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() } ?: emptyList()
}

private fun persistSavedUris(context: Context, uris: List<Uri>) {
    context.getSharedPreferences("saved_photos", Context.MODE_PRIVATE).edit().putStringSet("uris", uris.map { it.toString() }.toSet()).apply()
}
