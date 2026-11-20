package com.nooraudio.alarm

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Navy = Color(0xFF071B24)
private val Ocean = Color(0xFF0E323B)
private val Teal = Color(0xFF1A665E)
private val Gold = Color(0xFFEBC47E)
private val Cream = Color(0xFFFFF8EA)
private val Muted = Color(0xFF9DB7B6)
private val Hairline = Color(0xFF2B5257)

class MainActivity : ComponentActivity() {
    private val resumed = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Gold,
                background = Navy,
                surface = Ocean,
                onSurface = Cream,
                onBackground = Cream
            )) {
                TilawatHome(resumed.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.restoreAll(this)
        resumed.intValue++
    }
}

private data class EditorDraft(
    val id: Int = 0,
    val name: String = "Morning Adhkar",
    val hour: Int = 6,
    val minute: Int = 0,
    val daily: Boolean = true,
    val date: String = LocalDate.now().toString(),
    val sourceType: String = "files",
    val files: List<String> = emptyList(),
    val folder: String = "",
    val sourceLabel: String = "",
    val shuffle: Boolean = false,
    val enabled: Boolean = true
) {
    fun toAlarm(context: Context): AudioAlarm = AudioAlarm(
        id = if (id == 0) AlarmStore.nextId(context) else id,
        name = name.trim(), hour = hour, minute = minute,
        daily = daily, date = date, enabled = enabled,
        sourceType = sourceType, files = files, folder = folder,
        sourceLabel = sourceLabel, shuffle = shuffle
    )

    companion object {
        fun fromAlarm(a: AudioAlarm) = EditorDraft(
            a.id, a.name, a.hour, a.minute, a.daily, a.date,
            a.sourceType, a.files, a.folder, a.sourceLabel, a.shuffle, a.enabled
        )
    }
}

private fun clockText(hour: Int, minute: Int): String {
    val hour12 = when (val h = hour % 12) { 0 -> 12 else -> h }
    return "%d:%02d %s".format(Locale.US, hour12, minute, if (hour < 12) "AM" else "PM")
}

private fun nextSummary(alarm: AudioAlarm): String {
    if (alarm.daily) return "Every day"
    return try {
        LocalDate.parse(alarm.date).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US))
    } catch (_: Exception) { alarm.date }
}

private fun readDisplayName(context: Context, uri: Uri): String {
    var cursor: Cursor? = null
    return try {
        cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor?.moveToFirst() == true) cursor.getString(0) ?: "Audio track" else "Audio track"
    } catch (_: Exception) { "Audio track" } finally { cursor?.close() }
}

private fun keepAccess(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: Exception) { /* Provider granted temporary access only. */ }
}

@Composable
private fun TilawatHome(resumeVersion: Int) {
    val context = LocalContext.current
    var alarms by remember { mutableStateOf(AlarmStore.all(context)) }
    var draft by remember { mutableStateOf<EditorDraft?>(null) }
    var deleting by remember { mutableStateOf<AudioAlarm?>(null) }
    var infoOpen by remember { mutableStateOf(false) }
    val exactAllowed = AlarmScheduler.hasExactAccess(context)
    val notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(resumeVersion) {
        alarms = AlarmStore.all(context)
    }
    fun refresh() { alarms = AlarmStore.all(context) }

    val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        if (selected.isNotEmpty()) {
            selected.forEach { keepAccess(context, it) }
            val names = selected.map { readDisplayName(context, it) }
            draft = draft?.copy(
                sourceType = "files", files = selected.map(Uri::toString), folder = "",
                sourceLabel = if (names.size == 1) names[0]
                    else "${names.size} tracks • ${names.first()}"
            )
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { selected ->
        if (selected != null) {
            keepAccess(context, selected)
            val folderLabel = selected.lastPathSegment?.substringAfterLast(':')?.ifBlank { "Selected folder" }
                ?: "Selected folder"
            draft = draft?.copy(
                sourceType = "folder", files = emptyList(), folder = selected.toString(),
                sourceLabel = folderLabel
            )
        }
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
    }

    val upcoming = alarms.filter { it.enabled }
        .mapNotNull { item -> AlarmScheduler.nextTime(item)?.let { item to it } }
        .minByOrNull { it.second }

    Scaffold(
        containerColor = Navy,
        bottomBar = {
            Surface(color = Navy, shadowElevation = 14.dp) {
                Button(
                    onClick = { draft = EditorDraft() },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp)
                        .height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text("Create a listening schedule", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandLogo()
                    Spacer(Modifier.width(13.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TILAWAT", color = Gold, fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp, letterSpacing = 1.3.sp)
                        Text("ALARM", color = Muted, fontSize = 11.sp, letterSpacing = 2.2.sp)
                    }
                    IconButton(onClick = {
                        try { AudioAlarmService.stop(context) } catch (_: Exception) { }
                    }) {
                        Icon(Icons.Default.Stop, "Stop playback", tint = Gold)
                    }
                    IconButton(onClick = { infoOpen = true }) {
                        Icon(Icons.Default.Notifications, "Playback settings", tint = Gold)
                    }
                }
            }
            item { HeroCard(upcoming) }
            if (!exactAllowed) {
                item {
                    PermissionCard(
                        title = "Allow precise alarms",
                        detail = "Required for reliable, on-time playback on Android 12+. Without it, Android may delay playback.",
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                })
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                })
                            }
                        }
                    )
                }
            }
            if (!notificationsAllowed) {
                item {
                    PermissionCard(
                        title = "Enable playback notifications",
                        detail = "Show the current track and Stop / Next controls while recitation plays.",
                        onClick = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Your listening times", color = Cream,
                            fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Morning, evening, or whenever you choose", color = Muted, fontSize = 12.sp)
                    }
                    Surface(color = Ocean, shape = RoundedCornerShape(12.dp)) {
                        Text("${alarms.count { it.enabled }} active", color = Gold,
                            fontSize = 12.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                    }
                }
            }
            if (alarms.isEmpty()) {
                item { EmptyCard(onAdd = { draft = EditorDraft() }) }
            } else {
                items(
                    items = alarms.sortedWith(compareBy<AudioAlarm> { !it.enabled }
                        .thenBy { AlarmScheduler.nextTime(it) ?: Long.MAX_VALUE }),
                    key = { it.id }
                ) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onEdit = { draft = EditorDraft.fromAlarm(alarm) },
                        onDelete = { deleting = alarm },
                        onToggle = { checked ->
                            val changed = alarm.copy(enabled = checked)
                            AlarmStore.upsert(context, changed)
                            AlarmScheduler.schedule(context, changed)
                            refresh()
                        },
                        onPreview = {
                            try { AudioAlarmService.play(context, alarm.id) }
                            catch (_: Exception) { }
                        }
                    )
                }
            }
            item {
                Text("Each schedule can play one MP3, multiple files in order, or all audio from a selected folder.\nYour audio stays on your device.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp))
            }
        }
    }

    if (draft != null) {
        ScheduleEditor(
            draft = draft!!,
            onUpdate = { draft = it },
            onDismiss = { draft = null },
            onChooseFiles = { filesPicker.launch(arrayOf("audio/*")) },
            onChooseFolder = { folderPicker.launch(null) },
            onSave = {
                draft?.let { current ->
                    val alarm = current.toAlarm(context)
                    AlarmStore.upsert(context, alarm)
                    AlarmScheduler.schedule(context, alarm)
                    refresh()
                    draft = null
                }
            }
        )
    }
    deleting?.let { alarm ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Ocean,
            title = { Text("Delete this schedule?", color = Cream) },
            text = { Text("“${alarm.name}” will no longer play automatically.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    AlarmScheduler.cancel(context, alarm.id)
                    AlarmStore.delete(context, alarm.id)
                    refresh()
                    deleting = null
                }) { Text("Delete", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep", color = Cream) } }
        )
    }
    if (infoOpen) {
        AlertDialog(
            onDismissRequest = { infoOpen = false },
            containerColor = Ocean,
            title = { Text("Playback & permissions", color = Cream) },
            text = {
                Text(
                    "• Audio uses your phone's ALARM volume.\n\n" +
                        "• If precise alarms are disabled, times can be delayed.\n\n" +
                        "• Keep MP3s in their original location and do not revoke folder access.\n\n" +
                        "• Some phones require Battery > Unrestricted for consistent playback.\n\n" +
                        "• To stop audio, use the notification or the button on the home screen.\n\n" +
                        "• Do Not Disturb may silence alarms depending on your settings.",
                    color = Muted, fontSize = 13.sp, lineHeight = 19.sp
                )
            },
            confirmButton = { TextButton(onClick = { infoOpen = false }) { Text("Got it", color = Gold) } }
        )
    }
}

@Composable
private fun BrandLogo() {
    Box(
        modifier = Modifier.size(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Teal, Ocean))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Gold.copy(alpha = .17f), radius = size.minDimension * .41f)
            drawCircle(Gold, radius = size.minDimension * .27f,
                style = Stroke(width = 2.5.dp.toPx()))
            val c = center
            drawLine(Gold, c, androidx.compose.ui.geometry.Offset(c.x, c.y - 10.dp.toPx()),
                2.2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Gold, c,
                androidx.compose.ui.geometry.Offset(c.x + 8.dp.toPx(), c.y + 3.dp.toPx()),
                2.2.dp.toPx(), cap = StrokeCap.Round)
        }
        Text("☾", fontSize = 16.sp, color = Gold,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 2.dp))
    }
}

@Composable
private fun HeroCard(upcoming: Pair<AudioAlarm, Long>?) {
    Box(
        modifier = Modifier.fillMaxWidth().height(197.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1C6C66), Color(0xFF104349), Ocean)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = .05f), radius = 120.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * .98f, size.height * .12f))
            drawCircle(Gold.copy(alpha = .13f), radius = 78.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * .89f, size.height * .44f),
                style = Stroke(1.dp.toPx()))
        }
        Icon(Icons.Default.Alarm, contentDescription = null, tint = Gold.copy(alpha = .27f),
            modifier = Modifier.size(110.dp).align(Alignment.CenterEnd).offset(x = (-15).dp))
        Column(modifier = Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦", color = Gold, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text("A MOMENT OF PEACE", color = Gold,
                    fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            }
            Column {
                Text("Let your day begin\nwith beautiful recitation.",
                    fontSize = 23.sp, lineHeight = 29.sp,
                    fontWeight = FontWeight.Bold, color = Cream)
                Spacer(Modifier.height(13.dp))
                Text(
                    if (upcoming == null) "Create your first listening schedule below"
                    else "NEXT • ${upcoming.first.name}  ·  ${Instant.ofEpochMilli(upcoming.second).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US))}",
                    fontSize = 11.sp, lineHeight = 16.sp, color = Color(0xFFEDE6D4),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF3B342B), shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, Gold.copy(alpha = .25f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = Gold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Cream, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(detail, color = Color(0xFFDDCFC0), fontSize = 11.sp, lineHeight = 16.sp)
            }
            Text("Allow", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyCard(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Ocean).clickable(onClick = onAdd).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.MusicNote, null, tint = Gold, modifier = Modifier.size(37.dp))
        Spacer(Modifier.height(12.dp))
        Text("Your recitations, right on time", color = Cream,
            fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(7.dp))
        Text("Add a morning or night schedule and select your own MP3 files.",
            color = Muted, fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AlarmCard(
    alarm: AudioAlarm,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPreview: () -> Unit
) {
    Surface(
        color = Ocean, shape = RoundedCornerShape(21.dp),
        border = BorderStroke(1.dp, Hairline.copy(alpha = if (alarm.enabled) .8f else .4f))
    ) {
        Column(modifier = Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(Teal.copy(alpha = .65f)), contentAlignment = Alignment.Center) {
                    Icon(if (alarm.sourceType == "folder") Icons.Default.Folder else Icons.Default.MusicNote,
                        null, tint = Gold, modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(alarm.name, color = Cream, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(nextSummary(alarm), color = Muted, fontSize = 12.sp)
                }
                Switch(checked = alarm.enabled, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Gold, checkedThumbColor = Navy,
                        uncheckedTrackColor = Hairline, uncheckedThumbColor = Muted
                    ), modifier = Modifier.height(35.dp))
            }
            Spacer(Modifier.height(13.dp))
            Text(clockText(alarm.hour, alarm.minute), color = if (alarm.enabled) Gold else Muted,
                fontSize = 31.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(alarm.sourceLabel.ifBlank { "No audio selected" }, color = Muted,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Hairline, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPreview) {
                    Icon(Icons.Default.PlayArrow, null, tint = Gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Test play", color = Gold, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, "Edit schedule", tint = Muted, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "Delete schedule", tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ScheduleEditor(
    draft: EditorDraft,
    onUpdate: (EditorDraft) -> Unit,
    onDismiss: () -> Unit,
    onChooseFiles: () -> Unit,
    onChooseFolder: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val hasSource = (draft.sourceType == "files" && draft.files.isNotEmpty()) ||
        (draft.sourceType == "folder" && draft.folder.isNotBlank())
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.91f).padding(horizontal = 14.dp),
            shape = RoundedCornerShape(25.dp), color = Ocean
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 13.dp, top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (draft.id == 0) "New listening time" else "Edit listening time",
                            color = Cream, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Choose when and what to play", color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Muted) }
                }
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text("SCHEDULE NAME", color = Gold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.name, onValueChange = { onUpdate(draft.copy(name = it)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("e.g. Evening Surah", color = Muted) },
                        colors = editorFieldColors(), shape = RoundedCornerShape(13.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("TIME", color = Gold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Navy, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            TimePickerDialog(context, { _, hour, minute ->
                                onUpdate(draft.copy(hour = hour, minute = minute))
                            }, draft.hour, draft.minute, false).show()
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Alarm, null, tint = Gold)
                            Spacer(Modifier.width(12.dp))
                            Text(clockText(draft.hour, draft.minute), color = Cream,
                                fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("CHANGE", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("REPEAT", color = Gold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ChoicePill("Every day", selected = draft.daily,
                            modifier = Modifier.weight(1f), onClick = { onUpdate(draft.copy(daily = true)) })
                        ChoicePill("Specific date", selected = !draft.daily,
                            modifier = Modifier.weight(1f), onClick = { onUpdate(draft.copy(daily = false)) })
                    }
                    if (!draft.daily) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val d = try { LocalDate.parse(draft.date) }
                                    catch (_: Exception) { LocalDate.now() }
                                DatePickerDialog(context, { _, year, month, day ->
                                    onUpdate(draft.copy(date = LocalDate.of(year, month + 1, day).toString()))
                                }, d.year, d.monthValue - 1, d.dayOfMonth).apply {
                                    datePicker.minDate = System.currentTimeMillis() - 86_400_000L
                                }.show()
                            },
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, Hairline)
                        ) {
                            Text("Date: ${draft.date}", color = Cream)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("AUDIO SOURCE", color = Gold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Select one MP3, several MP3s, or an entire folder.",
                        color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(11.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SourceButton(
                            icon = Icons.Default.MusicNote, label = "Pick MP3s",
                            modifier = Modifier.weight(1f), onClick = onChooseFiles
                        )
                        SourceButton(
                            icon = Icons.Default.Folder, label = "Pick folder",
                            modifier = Modifier.weight(1f), onClick = onChooseFolder
                        )
                    }
                    if (hasSource) {
                        Spacer(Modifier.height(13.dp))
                        Surface(color = Teal.copy(alpha = .24f), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = Gold,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Selected ${if (draft.sourceType == "folder") "folder" else "audio"}",
                                        color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(draft.sourceLabel, color = Muted, fontSize = 12.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Shuffle playlist", color = Cream, fontWeight = FontWeight.Medium,
                                fontSize = 13.sp)
                            Text("Off: plays in file-name / selection order", color = Muted, fontSize = 11.sp)
                        }
                        Switch(checked = draft.shuffle, onCheckedChange = { onUpdate(draft.copy(shuffle = it)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = Navy))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Playback starts automatically at the scheduled time and stops at the end of the playlist.",
                        fontSize = 11.sp, color = Muted, lineHeight = 17.sp)
                    if (showError) {
                        Spacer(Modifier.height(12.dp))
                        Text("Enter a name, choose audio, and select a future date/time.",
                            color = Color(0xFFFFB3A8), fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = Hairline)
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(.8f),
                        border = BorderStroke(1.dp, Hairline), shape = RoundedCornerShape(13.dp)) {
                        Text("Cancel", color = Cream)
                    }
                    Button(onClick = {
                        val testAlarm = AudioAlarm(0, draft.name, draft.hour, draft.minute,
                            draft.daily, draft.date, true, draft.sourceType,
                            draft.files, draft.folder, draft.sourceLabel, draft.shuffle)
                        val futureDate = AlarmScheduler.nextTime(testAlarm) != null
                        if (!hasSource || draft.name.isBlank() || !futureDate) showError = true
                        else onSave()
                    }, modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy),
                        shape = RoundedCornerShape(13.dp)) {
                        Text("Save schedule", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Teal else Navy,
        border = BorderStroke(1.dp, if (selected) Gold.copy(alpha = .5f) else Hairline)) {
        Text(label, modifier = Modifier.padding(vertical = 12.dp),
            textAlign = TextAlign.Center, color = if (selected) Cream else Muted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
    }
}

@Composable
private fun SourceButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(14.dp),
        color = Navy, border = BorderStroke(1.dp, Hairline)) {
        Column(modifier = Modifier.padding(vertical = 18.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Gold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(7.dp))
            Text(label, color = Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Cream,
    unfocusedTextColor = Cream,
    focusedBorderColor = Gold,
    unfocusedBorderColor = Hairline,
    cursorColor = Gold,
    focusedContainerColor = Navy,
    unfocusedContainerColor = Navy
)
