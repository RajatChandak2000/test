package com.acc.spendsentry

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acc.spendsentry.data.TransactionRepository
import com.acc.spendsentry.model.TransactionDraft
import com.acc.spendsentry.reminder.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val viewModel: TransactionViewModel by viewModels {
        val app = application
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TransactionViewModel(TransactionRepository(app)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpendSentryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpendSentryScreen(viewModel = viewModel)
                }
            }
        }
    }

    companion object {
        fun openAppPendingIntent(context: Context) =
            PendingIntent.getActivity(
                context,
                1001,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

@Composable
private fun SpendSentryTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF89B4FF),
            secondary = Color(0xFF68D7C4),
            tertiary = Color(0xFFF2C66D),
            background = Color(0xFF0E1321),
            surface = Color(0xFF121A2B),
            surfaceVariant = Color(0xFF1B2438),
            onPrimary = Color(0xFF08111F),
            onSecondary = Color(0xFF05110E),
            onTertiary = Color(0xFF1D1402),
            onBackground = Color(0xFFEAF0FF),
            onSurface = Color(0xFFEAF0FF),
            onSurfaceVariant = Color(0xFFB5C0DB),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1B4ED8),
            secondary = Color(0xFF0C9488),
            tertiary = Color(0xFFC67D10),
            background = Color(0xFFF6F8FC),
            surface = Color(0xFFF9FBFF),
            surfaceVariant = Color(0xFFE6EDF9),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color(0xFF0E1321),
            onSurface = Color(0xFF0E1321),
            onSurfaceVariant = Color(0xFF51607A),
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

class TransactionViewModel(private val repository: TransactionRepository) : ViewModel() {
    val drafts = repository.drafts

    fun updateDraft(draft: TransactionDraft) {
        repository.updateDraft(draft)
    }

    fun acknowledge(draft: TransactionDraft) {
        repository.acknowledgeDraft(draft.id)
    }

    fun delete(draft: TransactionDraft) {
        repository.deleteDraft(draft.id)
    }

    fun cancelReminder(draft: TransactionDraft, context: Context) {
        ReminderScheduler.cancelReminder(context, draft.id.hashCode())
    }
}

enum class TransactionFilter {
    ALL,
    PENDING,
    LOGGED,
}

enum class TransactionSort {
    NEWEST_FIRST,
    OLDEST_FIRST,
}

@Composable
private fun SpendSentryScreen(viewModel: TransactionViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    var notificationAccessGranted by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }
    var postNotificationsGranted by remember {
        mutableStateOf(hasPostNotificationPermission(context))
    }
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        postNotificationsGranted = granted
    }
    var filter by rememberSaveable { mutableStateOf(TransactionFilter.ALL) }
    var sortOrder by rememberSaveable { mutableStateOf(TransactionSort.NEWEST_FIRST) }
    var selectedDateIso by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                postNotificationsGranted = hasPostNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val today = remember { LocalDate.now() }
    val selectedDate = selectedDateIso?.let(LocalDate::parse)
    val visibleDrafts = remember(drafts, filter, sortOrder, selectedDateIso) {
        drafts
            .asSequence()
            .filter {
                when (filter) {
                    TransactionFilter.ALL -> true
                    TransactionFilter.PENDING -> !it.acknowledged
                    TransactionFilter.LOGGED -> it.acknowledged
                }
            }
            .filter { draft -> selectedDate == null || draft.toLocalDate() == selectedDate }
            .sortedBy {
                when (sortOrder) {
                    TransactionSort.NEWEST_FIRST -> -it.postedAtMillis
                    TransactionSort.OLDEST_FIRST -> it.postedAtMillis
                }
            }
            .toList()
    }
    val groupedDrafts = remember(visibleDrafts, sortOrder) {
        val grouped = visibleDrafts.groupBy { it.toLocalDate() }
        val dates = grouped.keys.sortedWith(
            if (sortOrder == TransactionSort.NEWEST_FIRST) compareByDescending { it } else compareBy { it },
        )
        dates.mapNotNull { date -> grouped[date]?.let { date to it } }
    }

    val totalCount = drafts.size
    val pendingCount = drafts.count { !it.acknowledged }
    val todayCount = drafts.count { it.toLocalDate() == today }
    val reviewedCount = drafts.count { it.acknowledged }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.surface,
                    ),
                    center = Offset(0f, 0f),
                    radius = 1400f,
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "SpendSentry",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Keep a clean log of every card transaction, grouped by day and ready to review before the details fade.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!notificationAccessGranted) {
                PermissionBanner(
                    title = "Turn on notification access",
                    body = "SpendSentry can only prefill transactions if Android allows it to read payment alerts.",
                    actionLabel = "Open settings",
                    onAction = { context.startActivity(notificationAccessIntent()) },
                )
            }

            if (Build.VERSION.SDK_INT >= 33 && !postNotificationsGranted) {
                PermissionBanner(
                    title = "Allow app notifications",
                    body = "SpendSentry uses local reminder notifications to keep your transaction log on track.",
                    actionLabel = "Allow notifications",
                    onAction = { postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(label = "All", value = totalCount.toString(), modifier = Modifier.weight(1f))
                MetricCard(label = "Pending", value = pendingCount.toString(), modifier = Modifier.weight(1f))
                MetricCard(label = "Today", value = todayCount.toString(), modifier = Modifier.weight(1f))
                MetricCard(label = "Logged", value = reviewedCount.toString(), modifier = Modifier.weight(1f))
            }

            DateStrip(
                drafts = drafts,
                selectedDateIso = selectedDateIso,
                onDateSelected = { selectedDateIso = it },
                onClear = { selectedDateIso = null },
            )

            FilterControls(
                filter = filter,
                sortOrder = sortOrder,
                onFilterChange = { filter = it },
                onSortToggle = {
                    sortOrder = if (sortOrder == TransactionSort.NEWEST_FIRST) TransactionSort.OLDEST_FIRST else TransactionSort.NEWEST_FIRST
                },
            )

            if (groupedDrafts.isEmpty()) {
                EmptyStateCard(
                    title = "Nothing to show yet",
                    body = "Detected transactions will appear here with their date, amount, and merchant already filled in where possible.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    groupedDrafts.forEach { (date, itemsForDate) ->
                        item(key = "header-$date") {
                            DayHeader(date = date, count = itemsForDate.size)
                        }
                        items(itemsForDate, key = { it.id }) { draft ->
                            TransactionCard(
                                draft = draft,
                                onSave = { updated ->
                                    viewModel.updateDraft(updated)
                                },
                                onAck = {
                                    viewModel.acknowledge(draft)
                                    viewModel.cancelReminder(draft, context)
                                },
                                onDelete = {
                                    viewModel.delete(draft)
                                    viewModel.cancelReminder(draft, context)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DateStrip(
    drafts: List<TransactionDraft>,
    selectedDateIso: String?,
    onDateSelected: (String) -> Unit,
    onClear: () -> Unit,
) {
    val start = remember { LocalDate.now().minusDays(13) }
    val end = remember { LocalDate.now() }
    val dateCounts = remember(drafts) { drafts.groupingBy { it.toLocalDate() }.eachCount() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Calendar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = selectedDateIso == null,
                onClick = onClear,
                label = { Text("All dates") },
            )
            var date = start
            while (!date.isAfter(end)) {
                val iso = date.toString()
                val count = dateCounts[date] ?: 0
                FilterChip(
                    selected = selectedDateIso == iso,
                    onClick = { onDateSelected(iso) },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(date.dayOfWeek.name.take(3), style = MaterialTheme.typography.labelSmall)
                            Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleSmall)
                            Text("$count", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                )
                date = date.plusDays(1)
            }
        }
    }
}

@Composable
private fun FilterControls(
    filter: TransactionFilter,
    sortOrder: TransactionSort,
    onFilterChange: (TransactionFilter) -> Unit,
    onSortToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = filter == TransactionFilter.ALL, onClick = { onFilterChange(TransactionFilter.ALL) }, label = { Text("All") })
        FilterChip(selected = filter == TransactionFilter.PENDING, onClick = { onFilterChange(TransactionFilter.PENDING) }, label = { Text("Pending") })
        FilterChip(selected = filter == TransactionFilter.LOGGED, onClick = { onFilterChange(TransactionFilter.LOGGED) }, label = { Text("Logged") })
        OutlinedButton(onClick = onSortToggle) {
            Text(if (sortOrder == TransactionSort.NEWEST_FIRST) "Newest" else "Oldest")
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("$count transaction${if (count == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AssistChip(
            onClick = {},
            label = { Text(date.format(DateTimeFormatter.ofPattern("MMM d"))) },
            colors = AssistChipDefaults.assistChipColors(),
        )
    }
}

@Composable
private fun TransactionCard(
    draft: TransactionDraft,
    onSave: (TransactionDraft) -> Unit,
    onAck: () -> Unit,
    onDelete: () -> Unit,
) {
    var merchant by rememberSaveable(draft.id) { mutableStateOf(draft.merchant) }
    var amount by rememberSaveable(draft.id) { mutableStateOf(draft.amount) }
    var currency by rememberSaveable(draft.id) { mutableStateOf(draft.currency) }
    val dateTime = draft.toLocalDateTime()

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (merchant.isNotBlank()) merchant else "Unlabelled transaction",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${draft.sourcePackage} · ${dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatAmount(amount, currency),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(dateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))) },
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (draft.acknowledged) "Logged" else "Pending") },
                )
                if (draft.currency.isNotBlank()) {
                    AssistChip(
                        onClick = {},
                        label = { Text(draft.currency) },
                    )
                }
            }

            Text(
                text = draft.rawTitle.ifBlank { draft.rawText.ifBlank { "No notification text captured." } },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text("Merchant") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount") },
                        modifier = Modifier.weight(0.7f),
                    )
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = { Text("Curr") },
                        modifier = Modifier.weight(0.45f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSave(
                                draft.copy(
                                    merchant = merchant.trim(),
                                    amount = amount.trim(),
                                    currency = currency.trim(),
                                ),
                            )
                        },
                    ) {
                        Text("Save")
                    }
                    TextButton(onClick = onAck) { Text("Mark logged") }
                    TextButton(onClick = onDelete) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, body: String) {
    OutlinedCard(shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun TransactionDraft.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(postedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun TransactionDraft.toLocalDateTime(): LocalDateTime {
    return Instant.ofEpochMilli(postedAtMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
}

private fun formatAmount(amount: String, currency: String): String {
    val trimmedAmount = amount.trim()
    if (trimmedAmount.isBlank()) return "?"
    return if (currency.isBlank()) trimmedAmount else "$currency $trimmedAmount"
}

private fun notificationAccessIntent(): Intent {
    return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun hasPostNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
