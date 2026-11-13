package com.eric.personalledger

import android.Manifest
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.eric.personalledger.data.CandidateConfirmationResult
import com.eric.personalledger.data.PendingLedgerCandidate
import com.eric.personalledger.data.SqliteLedgerRepository
import com.eric.personalledger.domain.EntryType
import com.eric.personalledger.domain.LedgerEntry
import com.eric.personalledger.domain.LedgerRules
import com.eric.personalledger.domain.MonthlyCategoryTotal
import com.eric.personalledger.ui.LedgerEntryFormScreen
import com.eric.personalledger.ui.LedgerHomeScreen
import com.eric.personalledger.ui.LedgerHomeState
import com.eric.personalledger.ui.LedgerSettingsScreen
import com.eric.personalledger.ui.LedgerStatisticsScreen
import com.eric.personalledger.ui.LedgerTab
import com.eric.personalledger.ui.PendingReviewScreen
import com.eric.personalledger.ui.theme.PersonalLedgerTheme
import com.eric.personalledger.notifications.PaymentNotificationListenerService
import com.eric.personalledger.notifications.PendingReviewNotifier
import com.eric.personalledger.widgets.QuickEntryWidgetProvider
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var selectedMonth by mutableStateOf(YearMonth.now())
    private var homeState by mutableStateOf<LedgerHomeState>(LedgerHomeState.Loading)
    private var showEntryForm by mutableStateOf(false)
    private var entryFormSession by mutableStateOf(0)
    private var focusEntryAmount by mutableStateOf(false)
    private var widgetMessage by mutableStateOf<String?>(null)
    private val launchState by lazy { ViewModelProvider(this)[LaunchState::class.java] }
    private var showPendingReview by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var selectedTab by mutableStateOf(LedgerTab.LEDGER)
    private var editingEntry by mutableStateOf<LedgerEntry?>(null)
    private var formError by mutableStateOf<String?>(null)
    private var isSavingEntry by mutableStateOf(false)
    private var isSavingBudget by mutableStateOf(false)
    private var notificationAccessEnabled by mutableStateOf(false)
    private var reminderPermissionGranted by mutableStateOf(false)
    private var remindersEnabled by mutableStateOf(false)
    private var settingsError by mutableStateOf<String?>(null)
    private var pendingCandidates by mutableStateOf<List<PendingLedgerCandidate>>(emptyList())
    private var pendingAccounts by mutableStateOf(emptyList<com.eric.personalledger.domain.LedgerAccount>())
    private var pendingLoading by mutableStateOf(false)
    private var pendingError by mutableStateOf<String?>(null)
    private var pendingBusyCandidateId by mutableStateOf<String?>(null)
    private var loadJob: Job? = null
    private var pendingLoadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entryFormSession = savedInstanceState?.getInt(STATE_ENTRY_SESSION) ?: 0
        if (savedInstanceState?.getBoolean(STATE_NEW_ENTRY_OPEN) == true) {
            showEntryForm = true
            focusEntryAmount = savedInstanceState.getBoolean(STATE_FOCUS_AMOUNT)
        }
        enableEdgeToEdge()
        setContent {
            PersonalLedgerTheme(darkTheme = false, dynamicColor = false) {
                if (showEntryForm) {
                    val loaded = homeState as? LedgerHomeState.Loaded
                    key(entryFormSession) {
                        LedgerEntryFormScreen(
                            accounts = loaded?.accounts.orEmpty(),
                            initialEntry = editingEntry,
                            errorMessage = formError,
                            accountsLoading = homeState == LedgerHomeState.Loading,
                            onRetryAccounts = if (homeState == LedgerHomeState.Failed) ::loadMonth else null,
                            autoFocusAmount = focusEntryAmount,
                            isSaving = isSavingEntry,
                            onBack = { showEntryForm = false },
                            onSave = ::saveEntry,
                            onDelete = ::deleteEntry,
                        )
                    }
                } else if (showPendingReview) {
                    PendingReviewScreen(
                        candidates = pendingCandidates,
                        accounts = pendingAccounts,
                        busyCandidateId = pendingBusyCandidateId,
                        loading = pendingLoading,
                        errorMessage = pendingError,
                        onBack = {
                            showPendingReview = false
                            loadMonth()
                        },
                        onConfirm = ::confirmCandidate,
                        onIgnore = ::ignoreCandidate,
                    )
                } else if (showSettings) {
                    LedgerSettingsScreen(
                        widgetMessage = widgetMessage,
                        onAddDesktopWidget = ::addDesktopWidget,
                        notificationAccessEnabled = notificationAccessEnabled,
                        reminderPermissionGranted = reminderPermissionGranted,
                        remindersEnabled = remindersEnabled,
                        errorMessage = settingsError,
                        onOpenNotificationSettings = ::openNotificationListenerSettings,
                        onOpenAppNotificationSettings = ::openAppNotificationSettings,
                        onReminderPermissionResult = ::refreshNotificationAccess,
                        onBack = { showSettings = false },
                    )
                } else if (selectedTab == LedgerTab.LEDGER) {
                    LedgerHomeScreen(
                        month = selectedMonth,
                        state = homeState,
                        onMonthSelected = { month ->
                            selectedMonth = month
                            loadMonth()
                        },
                        onRetry = ::loadMonth,
                        onAddEntry = { openNewEntry() },
                        onEditEntry = { entry ->
                            entryFormSession++
                            focusEntryAmount = false
                            editingEntry = entry
                            formError = null
                            showEntryForm = true
                        },
                        onOpenStatistics = { selectedTab = LedgerTab.STATISTICS },
                        onOpenSettings = { showSettings = true },
                        onOpenPending = {
                            showPendingReview = true
                            loadPendingCandidates()
                        },
                        onSaveBudget = ::saveBudget,
                    )
                } else {
                    LedgerStatisticsScreen(
                        month = selectedMonth,
                        state = homeState,
                        onMonthSelected = { month ->
                            selectedMonth = month
                            loadMonth()
                        },
                        onRetry = ::loadMonth,
                        onOpenLedger = { selectedTab = LedgerTab.LEDGER },
                        onAddEntry = { openNewEntry() },
                    )
                }
            }
        }
        loadMonth()
        refreshNotificationAccess()
        if (!launchState.intentHandled && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            handleLaunchIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchState.intentHandled = false
        handleLaunchIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_ENTRY_SESSION, entryFormSession)
        outState.putBoolean(STATE_NEW_ENTRY_OPEN, showEntryForm && editingEntry == null)
        outState.putBoolean(STATE_FOCUS_AMOUNT, focusEntryAmount)
        super.onSaveInstanceState(outState)
    }

    private fun handleLaunchIntent(launchIntent: Intent?) {
        when {
            launchIntent?.action == ACTION_ADD_ENTRY -> openNewEntry(fromWidget = true)
            launchIntent?.getBooleanExtra(EXTRA_OPEN_PENDING_REVIEW, false) == true -> openPendingReview()
            else -> return
        }
        // Keep the launch intent stable for lifecycle observers, but consume its navigation once.
        // Rotating after a save must not open another empty entry.
        launchState.intentHandled = true
    }

    private fun openNewEntry(fromWidget: Boolean = false) {
        // A second widget tap resumes a draft instead of silently erasing it.
        if (showEntryForm || isSavingEntry) return
        entryFormSession++
        editingEntry = null
        formError = null
        focusEntryAmount = fromWidget
        showSettings = false
        showPendingReview = false
        selectedTab = LedgerTab.LEDGER
        showEntryForm = true
        if (fromWidget && selectedMonth != YearMonth.now()) {
            selectedMonth = YearMonth.now()
            loadMonth()
        } else if (homeState == LedgerHomeState.Failed) {
            loadMonth()
        }
    }

    private fun addDesktopWidget() {
        val manager = AppWidgetManager.getInstance(this)
        widgetMessage = if (!manager.isRequestPinAppWidgetSupported) {
            "当前桌面不支持应用内添加。请长按桌面空白处，在小组件或窗口小工具中选择本应用的「记一笔」。"
        } else {
            try {
                val accepted = manager.requestPinAppWidget(
                    ComponentName(this, QuickEntryWidgetProvider::class.java), null, null,
                )
                if (accepted) "请在系统弹窗中确认添加。若没有弹窗，也可从桌面的小组件列表添加。"
                else "桌面未接受添加请求，请从桌面的小组件列表手动添加。"
            } catch (_: IllegalStateException) {
                "请从桌面的小组件列表手动添加「记一笔」。"
            } catch (_: SecurityException) {
                "请从桌面的小组件列表手动添加「记一笔」。"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationAccess()
        if (showPendingReview) {
            if (pendingBusyCandidateId == null && !pendingLoading) loadPendingCandidates()
        } else if (!showEntryForm && !showSettings && !isSavingEntry && !isSavingBudget) {
            loadMonth()
        }
    }

    private fun loadMonth() {
        val month = selectedMonth
        loadJob?.cancel()
        homeState = LedgerHomeState.Loading
        loadJob = lifecycleScope.launch {
            homeState = try {
                withContext(Dispatchers.IO) {
                    SqliteLedgerRepository(this@MainActivity).use { repository ->
                        val entries = repository.entriesForMonth(month, ZoneId.systemDefault())
                        LedgerHomeState.Loaded(
                            summary = LedgerRules.summarize(entries),
                            entries = entries,
                            budgetCents = repository.monthlyBudget(month),
                            pendingCount = repository.pendingCandidates().size,
                            accounts = repository.accounts(),
                            expenseCategories = LedgerRules.categoryTotals(entries, com.eric.personalledger.domain.EntryType.EXPENSE),
                            incomeCategories = LedgerRules.categoryTotals(entries, com.eric.personalledger.domain.EntryType.INCOME),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SQLiteException) {
                LedgerHomeState.Failed
            } catch (_: IllegalArgumentException) {
                LedgerHomeState.Failed
            }
        }
    }

    private fun saveBudget(amountCents: Long?) {
        if (isSavingBudget) return
        val targetMonth = selectedMonth
        isSavingBudget = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SqliteLedgerRepository(this@MainActivity).use { repository ->
                        repository.setMonthlyBudget(targetMonth, amountCents)
                    }
                }
                loadMonth()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SQLiteException) {
                homeState = LedgerHomeState.Failed
            } finally {
                isSavingBudget = false
            }
        }
    }

    private fun saveEntry(entry: LedgerEntry) {
        if (isSavingEntry) return
        val entryBeingEdited = editingEntry
        isSavingEntry = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SqliteLedgerRepository(this@MainActivity).use { repository ->
                        if (entryBeingEdited == null) repository.insertEntry(entry)
                        else repository.updateEntry(entry)
                    }
                }
                showEntryForm = false
                loadMonth()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SQLiteException) {
                formError = "保存失败，请检查账目后重试。"
            } catch (_: IllegalArgumentException) {
                formError = "保存失败，请检查账目后重试。"
            } finally {
                isSavingEntry = false
            }
        }
    }

    private fun deleteEntry(entry: LedgerEntry) {
        if (isSavingEntry) return
        isSavingEntry = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SqliteLedgerRepository(this@MainActivity).use { repository ->
                        repository.deleteEntry(entry.id)
                    }
                }
                showEntryForm = false
                loadMonth()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SQLiteException) {
                formError = "删除失败，请稍后重试。"
            } finally {
                isSavingEntry = false
            }
        }
    }

    private fun loadPendingCandidates() {
        pendingLoadJob?.cancel()
        pendingLoading = true
        pendingError = null
        pendingLoadJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    SqliteLedgerRepository(this@MainActivity).use { repository ->
                        repository.pendingCandidates() to repository.accounts()
                    }
                }
                pendingCandidates = result.first
                pendingAccounts = result.second
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SQLiteException) {
                pendingError = "暂时无法读取待确认记录，请稍后重试。"
            } finally {
                pendingLoading = false
            }
        }
    }

    private fun confirmCandidate(
        candidate: PendingLedgerCandidate,
        amountCents: Long,
        category: String,
        accountId: Long,
        occurredAtEpochMillis: Long,
        memo: String,
    ) {
        if (pendingBusyCandidateId != null) return
        pendingBusyCandidateId = candidate.id
        pendingError = null
        val entry = LedgerEntry(
            id = UUID.randomUUID().toString(),
            type = EntryType.EXPENSE,
            amountCents = amountCents,
            category = category,
            accountId = accountId,
            occurredAtEpochMillis = occurredAtEpochMillis,
            memo = memo,
            candidateId = candidate.id,
        )
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    SqliteLedgerRepository(this@MainActivity).use { repository ->
                        repository.confirmCandidate(candidate.id, entry)
                    }
                }
                PendingReviewNotifier.cancel(this@MainActivity, candidate.id)
                when (result) {
                    CandidateConfirmationResult.CONFIRMED,
                    CandidateConfirmationResult.ALREADY_PROCESSED,
                    CandidateConfirmationResult.NOT_FOUND -> loadPendingCandidates()
                }
                loadMonth()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SQLiteException) {
                pendingError = "确认失败，请稍后重试。"
            } catch (_: IllegalArgumentException) {
                pendingError = "这笔记录暂时无法保存，请检查金额和账户。"
            } finally {
                pendingBusyCandidateId = null
            }
        }
    }

    private fun ignoreCandidate(candidate: PendingLedgerCandidate) {
        if (pendingBusyCandidateId != null) return
        pendingBusyCandidateId = candidate.id
        pendingError = null
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SqliteLedgerRepository(this@MainActivity).use { repository ->
                        repository.ignoreCandidate(candidate.id)
                    }
                }
                PendingReviewNotifier.cancel(this@MainActivity, candidate.id)
                loadPendingCandidates()
                loadMonth()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SQLiteException) {
                pendingError = "忽略失败，请稍后重试。"
            } finally {
                pendingBusyCandidateId = null
            }
        }
    }

    private fun refreshNotificationAccess() {
        val listener = ComponentName(this, PaymentNotificationListenerService::class.java)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationAccessEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            notificationManager.isNotificationListenerAccessGranted(listener)
        } else {
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                .orEmpty()
                .split(':')
                .mapNotNull(ComponentName::unflattenFromString)
                .any { it == listener }
        }

        reminderPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.getNotificationChannel(PendingReviewNotifier.CHANNEL_ID)
                ?.importance != NotificationManager.IMPORTANCE_NONE
        } else {
            true
        }
        remindersEnabled = reminderPermissionGranted &&
            notificationManager.areNotificationsEnabled() && channelEnabled
    }

    private fun openNotificationListenerSettings() {
        settingsError = null
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            settingsError = "此设备无法打开通知访问设置。"
        }
    }

    private fun openAppNotificationSettings() {
        settingsError = null
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        } catch (_: ActivityNotFoundException) {
            settingsError = "此设备无法打开应用提醒设置。"
        }
    }

    private fun openPendingReview() {
        showEntryForm = false
        showSettings = false
        showPendingReview = true
        loadPendingCandidates()
    }

    companion object {
        const val ACTION_ADD_ENTRY = "com.eric.personalledger.ADD_ENTRY"
        private const val STATE_ENTRY_SESSION = "entry_form_session"
        private const val STATE_NEW_ENTRY_OPEN = "new_entry_open"
        private const val STATE_FOCUS_AMOUNT = "focus_entry_amount"
        const val ACTION_OPEN_PENDING_REVIEW = "com.eric.personalledger.OPEN_PENDING_REVIEW"
        const val EXTRA_OPEN_PENDING_REVIEW = "open_pending_review"
    }

    // Survives configuration changes, but must reset after process death so a fresh
    // desktop click is not swallowed by a flag restored from an old dashboard.
    class LaunchState : ViewModel() {
        var intentHandled = false
    }
}
