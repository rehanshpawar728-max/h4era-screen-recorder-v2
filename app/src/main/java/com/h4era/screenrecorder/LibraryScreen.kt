package com.h4era.screenrecorder

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(listOf<Rec>()) }
    var sort by remember { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var toDelete by remember { mutableStateOf<Rec?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        refresh++
        RecorderState.libraryVersion++
    }

    LaunchedEffect(refresh, RecorderState.libraryVersion) {
        items = withContext(Dispatchers.IO) { MediaStoreManager.query(ctx) }
    }

    val sorted = when (sort) {
        0 -> items.sortedByDescending { it.dateSec }
        1 -> items.sortedBy { it.dateSec }
        else -> items.sortedByDescending { it.size }
    }

    val d = toDelete
    if (d != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete recording?") },
            text = { Text(d.name) },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    val sender = MediaStoreManager.delete(ctx, d.uri)
                    if (sender != null) {
                        launcher.launch(IntentSenderRequest.Builder(sender).build())
                    } else {
                        refresh++
                        RecorderState.libraryVersion++
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.width(8.dp))
            Text("Recordings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Newest", sort == 0, Modifier.weight(1f)) { sort = 0 }
            Chip("Oldest", sort == 1, Modifier.weight(1f)) { sort = 1 }
            Chip("Largest", sort == 2, Modifier.weight(1f)) { sort = 2 }
        }
        Spacer(Modifier.height(8.dp))

        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recordings yet.\nTap REC on the home screen to start.", color = Muted)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(sorted, key = { it.id }) { r ->
                    val date = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                        .format(Date(r.dateSec * 1000))
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Thumb(r.uri, Modifier.size(width = 112.dp, height = 64.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        r.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        fmtTime(r.durationMs / 1000) + " • " + r.width + "x" + r.height,
                                        color = Muted,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        Formatter.formatShortFileSize(ctx, r.size) + " • " + date,
                                        color = Muted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Row {
                                TextButton(onClick = { playVideo(ctx, r.uri) }) { Text("Play") }
                                TextButton(onClick = { shareVideo(ctx, r.uri) }) { Text("Share") }
                                TextButton(onClick = { toDelete = r }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}
