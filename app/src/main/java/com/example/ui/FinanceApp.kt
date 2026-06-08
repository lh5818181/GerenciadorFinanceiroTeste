package com.example.ui

import android.app.DatePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Transaction
import com.example.viewmodel.FinanceViewModel
import com.example.viewmodel.MonthlyBarData
import com.example.viewmodel.MonthlyStats
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val allTx by viewModel.allTransactions.collectAsStateWithLifecycle()
    val monthlyStats by viewModel.monthlyStats.collectAsStateWithLifecycle()
    val yearlyOverview by viewModel.yearlyOverview.collectAsStateWithLifecycle()
    val activeMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val activeYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val activeCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val categoriesList by viewModel.categoriesList.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val focusedTransactionId by viewModel.focusedTransactionId.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(0) } // 0: Panel, 1: List, 2: Add Form
    var searchQuery by remember { mutableStateOf("") }
    var showNotificationsBottomSheet by remember { mutableStateOf(false) }

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    // Seed database if entirely empty upon viewing
    LaunchedEffect(allTx) {
        if (allTx.isEmpty()) {
            viewModel.addSampleDataDirectly()
        }
    }

    // Request POST_NOTIFICATIONS permission on Android 13+ (API 33) to allow top bar system alerts
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(Unit) {
            val permissionStatus = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS"
            )
            if (permissionStatus != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launcher.launch("android.permission.POST_NOTIFICATIONS")
            }
        }
    }

    LaunchedEffect(focusedTransactionId) {
        if (focusedTransactionId != null) {
            currentTab = 1 // Switch automatically to Transactions list tab
            kotlinx.coroutines.delay(3000)
            viewModel.clearFocusedTransaction()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Controle Financeiro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Relatórios e Balanços Inteligentes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                actions = {
                    val unreadNotificationsCount = remember(notifications) {
                        notifications.count { !it.isRead }
                    }

                    // Botão de Notícias/Notificações com Badge
                    IconButton(
                        onClick = { showNotificationsBottomSheet = true },
                        modifier = Modifier.padding(end = 4.dp).testTag("notification_bell_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationsCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text(unreadNotificationsCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (unreadNotificationsCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = "Central de Notificações",
                                tint = if (unreadNotificationsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.addSampleDataDirectly() },
                        modifier = Modifier.padding(end = 8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddChart,
                            contentDescription = "Gerar Amostras",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Gerar Amostras",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.TrendingUp, contentDescription = "Painel") },
                    label = { Text("Painel", fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("tab_painel")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.ListAlt, contentDescription = "Transações") },
                    label = { Text("Transações", fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("tab_transacoes")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.AddCircle, contentDescription = "Novo") },
                    label = { Text("Novo", fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("tab_novo")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                    )
                )
        ) {
            // Month pill scroll list + Year switcher / Category chips (Sticky for Panel & List tabs)
            if (currentTab != 2) {
                MonthYearCategorySelectorSection(
                    activeMonth = activeMonth,
                    activeYear = activeYear,
                    activeCategory = activeCategory,
                    categoriesList = categoriesList,
                    onMonthSelected = { viewModel.setMonth(it) },
                    onYearChanged = { viewModel.setYear(it) },
                    onCategorySelected = { viewModel.setCategory(it) }
                )
            }

            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> DashboardScreen(
                        stats = monthlyStats,
                        yearlyOverview = yearlyOverview,
                        currencyFormatter = currencyFormatter,
                        selectedMonth = activeMonth,
                        selectedYear = activeYear,
                        onTransferToAddTab = { currentTab = 2 }
                    )
                    1 -> TransactionsScreen(
                        transactions = transactions,
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { searchQuery = it },
                        focusedTransactionId = focusedTransactionId,
                        onToggleStatus = { tx ->
                            val newStatus = if (tx.status == "Pago") "Pendente" 
                                            else if (tx.status == "Recebido") "Pendente"
                                            else if (tx.type == "RECEITA") "Recebido" 
                                            else "Pago"
                            viewModel.updateTransaction(tx.copy(status = newStatus))
                        },
                        onDeleteTx = { viewModel.deleteTransaction(it) },
                        currencyFormatter = currencyFormatter,
                        dateFormatter = dateFormatter
                    )
                    2 -> AddTransactionScreen(
                        onAddSuccess = { type, title, amount, date, category, paym, status ->
                            viewModel.addTransaction(type, title, amount, date, category, paym, status)
                            // Switch back to Transactions tab or Painel
                            currentTab = 1
                        },
                        currencyFormatter = currencyFormatter
                    )
                }
            }
        }

        // Dialog de Notificações Inteligentes
        if (showNotificationsBottomSheet) {
            AlertDialog(
                onDismissRequest = { showNotificationsBottomSheet = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Central de Notificações",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Alertas e Movimentações Recentes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showNotificationsBottomSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar")
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val unreadCount = notifications.count { !it.isRead }
                        if (unreadCount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { viewModel.markAllNotificationsAsRead() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Limpar alertas (Marcar lidos)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (notifications.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    "Tudo atualizado!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Nenhum alerta ou pendência financeira identificada no período.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(notifications, key = { it.id }) { notif ->
                                    val (icon, color, bg) = when (notif.type) {
                                        "SUCCESS" -> Triple(Icons.Default.Check, Color(0xFF00E676), Color(0xFF00E676).copy(alpha = 0.08f))
                                        "WARNING" -> Triple(Icons.Default.Warning, Color(0xFFFFD000), Color(0xFFFFD000).copy(alpha = 0.08f))
                                        "ERROR" -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                                        else -> Triple(Icons.Default.Info, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                viewModel.markNotificationAsRead(notif.id)
                                                if (notif.id.startsWith("pending_exp_")) {
                                                    val txId = notif.id.removePrefix("pending_exp_").toIntOrNull()
                                                    if (txId != null) {
                                                        viewModel.focusTransaction(txId)
                                                        showNotificationsBottomSheet = false
                                                    }
                                                } else if (notif.id.startsWith("rec_inc_")) {
                                                    val txId = notif.id.removePrefix("rec_inc_").toIntOrNull()
                                                    if (txId != null) {
                                                        viewModel.focusTransaction(txId)
                                                        showNotificationsBottomSheet = false
                                                    }
                                                } else if (notif.id.startsWith("high_category_expense_")) {
                                                    val parts = notif.id.removePrefix("high_category_expense_").split("_")
                                                    if (parts.isNotEmpty()) {
                                                        val catName = parts[0]
                                                        viewModel.setCategory(catName)
                                                        currentTab = 1
                                                        showNotificationsBottomSheet = false
                                                    }
                                                } else if (notif.id.startsWith("negative_balance_") || notif.id.startsWith("positive_balance_")) {
                                                    currentTab = 0
                                                    showNotificationsBottomSheet = false
                                                }
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (notif.isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else bg
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (notif.isRead) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f) else color.copy(alpha = 0.25f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(color.copy(alpha = 0.12f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = color,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = notif.title,
                                                        fontWeight = if (notif.isRead) FontWeight.SemiBold else FontWeight.ExtraBold,
                                                        fontSize = 12.sp,
                                                        color = if (notif.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (!notif.isRead) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .background(color, CircleShape)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = notif.message,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNotificationsBottomSheet = false }) {
                        Text("FECHAR", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun MonthYearCategorySelectorSection(
    activeMonth: Int,
    activeYear: Int,
    activeCategory: String,
    categoriesList: List<String>,
    onMonthSelected: (Int) -> Unit,
    onYearChanged: (Int) -> Unit,
    onCategorySelected: (String) -> Unit
) {
    val months = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)))
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${months[activeMonth]} $activeYear",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "PERÍODO E CATEGORIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            // Year switcher with customized balanced geometry
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), RoundedCornerShape(8.dp))
            ) {
                IconButton(
                    onClick = { onYearChanged(activeYear - 1) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowLeft,
                        contentDescription = "Ano anterior",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = activeYear.toString(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = { onYearChanged(activeYear + 1) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowRight,
                        contentDescription = "Próximo ano",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal scrolling month chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(months.size) { monthIdx ->
                val isSelected = monthIdx == activeMonth
                val monthShortName = months[monthIdx].take(3).uppercase()
                
                FilterChip(
                    selected = isSelected,
                    onClick = { onMonthSelected(monthIdx) },
                    label = { 
                        Text(
                            text = monthShortName,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        ) 
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        selectedBorderWidth = 1.5.dp,
                        borderWidth = 1.dp
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.testTag("month_chip_$monthIdx")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal scrolling category chips (The Category Filter feature!)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categoriesList) { category ->
                val isSelected = category.equals(activeCategory, ignoreCase = true)
                
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelected(category) },
                    label = { 
                        Text(
                            text = category.uppercase(),
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        ) 
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        selectedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        selectedBorderWidth = 1.5.dp,
                        borderWidth = 1.dp
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.secondary,
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.testTag("category_chip_$category")
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(
    stats: MonthlyStats,
    yearlyOverview: List<MonthlyBarData>,
    currencyFormatter: NumberFormat,
    selectedMonth: Int,
    selectedYear: Int,
    onTransferToAddTab: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    val monthNames = remember {
        listOf(
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Cards Grid
        BalanceSummaryCards(stats, currencyFormatter)

        // Empty state check
        if (stats.totalIncome == 0.0 && stats.totalExpense == 0.0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Sem dados",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Nenhuma transação cadastrada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Não há receitas ou despesas inseridas para ${monthNames[selectedMonth]} de $selectedYear.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(onClick = onTransferToAddTab) {
                        Text("Adicionar Nova Transação")
                    }
                }
            }
        } else {
            // Expenses by Category Card
            if (stats.categoryExpenses.isNotEmpty()) {
                CategoryDonutChartCard(stats.categoryExpenses, currencyFormatter)
            }

            // Payment Methods Breakdown Card
            if (stats.paymentExpenses.isNotEmpty()) {
                PaymentMethodsCard(stats.paymentExpenses, currencyFormatter)
            }

            // Invoices Status Tracking Card
            StatusTrackingCard(stats, currencyFormatter)
        }

        // Yearly History Dual Bar Chart
        YearlyTrendChartCard(yearlyOverview = yearlyOverview, activeMonthIndex = selectedMonth, currencyFormatter = currencyFormatter)
    }
}

@Composable
fun BalanceSummaryCards(stats: MonthlyStats, currencyFormatter: NumberFormat) {
    val totalBalanceColor = if (stats.balance >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val isPositive = stats.balance >= 0.0
    
    val totalBalanceBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Grand Balance Header Card with futuristic custom border and gradient accent
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Draw a thin futuristic accent line at the left edge
                    val barWidth = 5.dp.toPx()
                    drawRect(
                        color = totalBalanceColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(barWidth, size.height)
                    )
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = totalBalanceBg),
            border = BorderStroke(1.dp, totalBalanceColor.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SALDO GLOBAL DO MÊS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currencyFormatter.format(stats.balance),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = totalBalanceColor,
                        letterSpacing = (-0.5).sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(totalBalanceColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .border(1.dp, totalBalanceColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPositive) Icons.Default.Savings else Icons.Default.TrendingDown,
                        contentDescription = "Saldo",
                        tint = totalBalanceColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        val errorColor = MaterialTheme.colorScheme.error
        // Two Column Income vs Expenses Cards with balanced geometric styling
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Income (Receitas) Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .drawBehind {
                        drawRect(
                            color = Color(0xFF00E676),
                            topLeft = Offset(0f, 0f),
                            size = Size(4.dp.toPx(), size.height)
                        )
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RECEITAS",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowCircleUp,
                            contentDescription = "Receitas",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = currencyFormatter.format(stats.totalIncome),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF00E676)
                    )
                    Text(
                        text = "${currencyFormatter.format(stats.receivedIncome)} recebidas",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Expenses (Despesas) Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .drawBehind {
                        drawRect(
                            color = errorColor,
                            topLeft = Offset(0f, 0f),
                            size = Size(4.dp.toPx(), size.height)
                        )
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "DESPESAS",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowCircleDown,
                            contentDescription = "Despesas",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = currencyFormatter.format(stats.totalExpense),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "${currencyFormatter.format(stats.pendingExpenses)} pendentes",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryDonutChartCard(categoryExpenses: Map<String, Double>, currencyFormatter: NumberFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Despesas por Categoria",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val total = categoryExpenses.values.sum()
            val categories = categoryExpenses.keys.toList()
            val colors = listOf(
                Color(0xFF00E5FF), // Cyber Cyan
                Color(0xFF9D4EDD), // Cyber Purple
                Color(0xFFFF9E00), // Electric Amber
                Color(0xFFFF1744), // Crimson pink
                Color(0xFF00E676), // Emerald
                Color(0xFF3A86F0), // Bright Blue
                Color(0xFFE91E63), // Pink
                Color(0xFF8E24AA), // Violet
                Color(0xFF26A69A), // Teal
                Color(0xFFFFCA28)  // Light Golden
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Canvas Chart representing categories relative volumes with a sophisticated futuristic look
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val strokeWidth = 14.dp.toPx()
                        val chartSize = size.width - strokeWidth - 6.dp.toPx()
                        
                        // Reference circular faint tracker backplane grid
                        drawCircle(
                            color = Color.LightGray.copy(alpha = 0.08f),
                            radius = chartSize / 2f,
                            style = Stroke(width = strokeWidth)
                        )

                        var startAngle = -90f
                        categoryExpenses.forEach { (catName, amount) ->
                            val index = categories.indexOf(catName) % colors.size
                            val sweepAngle = ((amount / total) * 360f).toFloat()

                            // Rounded end cap neon arcs
                            drawArc(
                                color = colors[index],
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                size = Size(chartSize, chartSize),
                                topLeft = Offset((size.width - chartSize)/2f, (size.height - chartSize)/2f)
                            )
                            startAngle += sweepAngle
                        }
                    }
                    
                    // Central summary text inside donut
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "TOTAL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = currencyFormatter.format(total).split(",")[0], // Compact integer form for beautiful centering
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Scrollable legend with detailed spacing
                Column(
                    modifier = Modifier
                        .weight(1.4f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    categoryExpenses.forEach { (catName, amount) ->
                        val index = categories.indexOf(catName) % colors.size
                        val percentage = (amount / total) * 100

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(colors[index], CircleShape)
                            )
                            Text(
                                text = "$catName (${String.format(Locale.getDefault(), "%.1f", percentage)}%)",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currencyFormatter.format(amount),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentMethodsCard(paymentExpenses: Map<String, Double>, currencyFormatter: NumberFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Meios de Pagamento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val total = paymentExpenses.values.sum()
            paymentExpenses.forEach { (method, amount) ->
                val ratio = (amount / total).toFloat()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(method, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${currencyFormatter.format(amount)} (${String.format(Locale.getDefault(), "%.0f", ratio * 100)}%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Linear Progress Indicator representing ratio
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusTrackingCard(stats: MonthlyStats, currencyFormatter: NumberFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Fluxo de Caixa e Lançamentos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Expenditures Status
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Despesas (Pagas vs Pendentes)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    val totalDep = stats.paidExpenses + stats.pendingExpenses
                    if (totalDep > 0) {
                        val paidRatio = (stats.paidExpenses / totalDep).toFloat()
                        val pendingRatio = (stats.pendingExpenses / totalDep).toFloat()

                        if (paidRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(paidRatio)
                                    .fillMaxHeight()
                                    .background(Color(0xFF00E676)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (paidRatio > 0.25f) Text("Pagas", color = Color(0xFF060914), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        if (pendingRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(pendingRatio)
                                    .fillMaxHeight()
                                    .background(Color(0xFFFFD000)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (pendingRatio > 0.25f) Text("Pendentes", color = Color(0xFF060914), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Sem despesas registradas", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF00E676), CircleShape))
                        Text("Pago: ${currencyFormatter.format(stats.paidExpenses)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFD000), CircleShape))
                        Text("Pendente: ${currencyFormatter.format(stats.pendingExpenses)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // Income Status
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Receitas (Recebidas vs Pendentes)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    val totalInc = stats.receivedIncome + stats.pendingIncome
                    if (totalInc > 0) {
                        val recRatio = (stats.receivedIncome / totalInc).toFloat()
                        val pendIncRatio = (stats.pendingIncome / totalInc).toFloat()

                        if (recRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(recRatio)
                                    .fillMaxHeight()
                                    .background(Color(0xFF00E676)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (recRatio > 0.25f) Text("Recebido", color = Color(0xFF060914), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        if (pendIncRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(pendIncRatio)
                                    .fillMaxHeight()
                                    .background(Color(0xFF00E5FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (pendIncRatio > 0.25f) Text("Pendente", color = Color(0xFF060914), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Sem receitas registradas", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF00E676), CircleShape))
                        Text("Recebido: ${currencyFormatter.format(stats.receivedIncome)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF00E5FF), CircleShape))
                        Text("Pendente: ${currencyFormatter.format(stats.pendingIncome)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun YearlyTrendChartCard(
    yearlyOverview: List<MonthlyBarData>,
    activeMonthIndex: Int,
    currencyFormatter: NumberFormat
) {
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "Análise de Tendência Anual",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Acompanhamento de fluxo de caixa anual estruturado por mês.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Legend indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp, 10.dp).background(Color(0xFF00E676), RoundedCornerShape(2.dp)))
                    Text("Receitas", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp, 10.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp)))
                    Text("Despesas", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }

            // Canvas Bar Chart with high-tech look
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                val chartHeight = size.height - 10.dp.toPx()
                val topPadding = 5.dp.toPx()
                val totalWidth = size.width
                
                // Find maximum amount to establish a logical Y axis boundary
                val maxVal = yearlyOverview.flatMap { listOf(it.income, it.expense) }.maxOrNull() ?: 1000.0
                val scaleMax = if (maxVal == 0.0) 1000.0 else maxVal * 1.12
                
                // Draw horizontal dashed guidelines
                val linesCount = 4
                for (i in 0 until linesCount) {
                    val y = topPadding + (chartHeight * (i.toFloat() / (linesCount - 1)))
                    drawLine(
                        color = outlineVariantColor.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(totalWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val columnWidth = totalWidth / 12f
                val barThickness = 5.dp.toPx()

                yearlyOverview.forEachIndexed { idx, barData ->
                    // Column central x
                    val xCenter = idx * columnWidth + (columnWidth / 2f)

                    // Draw active highlighted bounding background
                    if (idx == activeMonthIndex) {
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.07f),
                            topLeft = Offset(idx * columnWidth + 1.dp.toPx(), topPadding),
                            size = Size(columnWidth - 2.dp.toPx(), chartHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }

                    // Income bar (Cyan/Green neon)
                    val incHeight = (barData.income / scaleMax * chartHeight).toFloat()
                    val incY = topPadding + chartHeight - incHeight
                    if (incHeight > 0f) {
                        drawRoundRect(
                            color = Color(0xFF00E676),
                            topLeft = Offset(xCenter - barThickness - 1.5.dp.toPx(), incY),
                            size = Size(barThickness, incHeight),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }

                    // Expense bar (Neon Rose)
                    val expHeight = (barData.expense / scaleMax * chartHeight).toFloat()
                    val expY = topPadding + chartHeight - expHeight
                    if (expHeight > 0f) {
                        drawRoundRect(
                            color = errorColor,
                            topLeft = Offset(xCenter + 1.5.dp.toPx(), expY),
                            size = Size(barThickness, expHeight),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }
            }

            // X-axis list of months beneath chart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                yearlyOverview.forEachIndexed { idx, barData ->
                    val isSelected = idx == activeMonthIndex
                    Text(
                        text = barData.monthLabel,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionsScreen(
    transactions: List<Transaction>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    focusedTransactionId: Int?,
    onToggleStatus: (Transaction) -> Unit,
    onDeleteTx: (Transaction) -> Unit,
    currencyFormatter: NumberFormat,
    dateFormatter: SimpleDateFormat
) {
    val filtered = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) {
            transactions
        } else {
            transactions.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true) ||
                        it.paymentMethod.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_transacoes_input"),
            placeholder = { Text("Pesquisar lançamentos...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpar")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = "Nada encontrado",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Nenhum lançamento encontrado",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Tente buscar por outro termo ou selecione outro mês.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filtered, key = { it.id }) { tx ->
                    TransactionItemRow(
                        tx = tx,
                        isFocused = tx.id == focusedTransactionId,
                        onToggleStatus = { onToggleStatus(tx) },
                        onDelete = { onDeleteTx(tx) },
                        currencyFormatter = currencyFormatter,
                        dateFormatter = dateFormatter
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionItemRow(
    tx: Transaction,
    isFocused: Boolean = false,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit,
    currencyFormatter: NumberFormat,
    dateFormatter: SimpleDateFormat
) {
    val isExpense = tx.type == "DESPESA"
    val accentColor = if (isExpense) MaterialTheme.colorScheme.error else Color(0xFF00E676)
    
    val catIcon = when (tx.category.lowercase()) {
        "alimentação", "comida", "restaurante" -> Icons.Default.Restaurant
        "moradia", "aluguel", "casa" -> Icons.Default.Home
        "transporte", "combustível", "gasolina", "carro" -> Icons.Default.DirectionsCar
        "lazer", "entretenimento", "cinema", "jogo" -> Icons.Default.SportsEsports
        "saúde", "dentista", "remédio" -> Icons.Default.MedicalServices
        "serviços", "energia", "água", "internet" -> Icons.Default.ReceiptLong
        "salário", "trabalho" -> Icons.Default.Payments
        "educação", "curso", "faculdade" -> Icons.Default.School
        else -> Icons.Default.LocalOffer
    }

    val isPaid = tx.status == "Pago" || tx.status == "Recebido"
    val badgeBg = if (isPaid) Color(0xFF00E676).copy(alpha = 0.12f) else Color(0xFFFFD000).copy(alpha = 0.12f)
    val badgeText = if (isPaid) Color(0xFF00E676) else Color(0xFFFFD000)

    val cardBgColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val cardBorder = if (isFocused) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transaction_item_${tx.id}")
            .drawBehind {
                // Drawing high-tech left edge micro bar
                drawRect(
                    color = accentColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(4.dp.toPx(), size.height)
                )
            },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded Icon representation of category
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accentColor.copy(alpha = 0.08f), CircleShape)
                    .border(1.dp, accentColor.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = catIcon,
                    contentDescription = tx.category,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Transaction Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = dateFormatter.format(Date(tx.dateMillis)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = tx.category.uppercase(),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                // If expense has a payment method, display it
                if (isExpense && tx.paymentMethod.isNotEmpty()) {
                    Text(
                        text = "Método: ${tx.paymentMethod}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Value & Custom Status Badge Column
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${if (isExpense) "-" else "+"} ${currencyFormatter.format(tx.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = accentColor,
                    letterSpacing = (-0.2).sp
                )

                // High-visibility interactive status click toggle target
                Surface(
                    onClick = onToggleStatus,
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    border = BorderStroke(1.dp, badgeText.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(badgeText, CircleShape)
                        )
                        Text(
                            text = tx.status.uppercase(),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeText,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Simple delete trash icon action
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Excluir",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onAddSuccess: (type: String, title: String, amount: Double, dateMillis: Long, category: String, payment: String, status: String) -> Unit,
    currencyFormatter: NumberFormat
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var type by remember { mutableStateOf("DESPESA") } // "RECEITA" or "DESPESA"
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dateSelected by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedCategory by remember { mutableStateOf("Alimentação") }
    var customCategory by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf("Cartão de Crédito") }
    var statusIsPaid by remember { mutableStateOf(true) } // Pago/Recebido is True, otherwise false.

    val categoriesExpense = listOf("Alimentação", "Moradia", "Transporte", "Lazer", "Saúde", "Serviços", "Educação", "Vestuário", "Outros")
    val categoriesIncome = listOf("Salário", "Investimentos", "Trabalho Extra", "Prêmio", "Outros")

    val paymentMethods = listOf("Cartão de Crédito", "Cartão de Débito", "Pix", "Dinheiro", "Boleto")

    val dateFormatterReadable = remember { SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")) }

    // Synchronize default category on type switcher toggle
    LaunchedEffect(type) {
        selectedCategory = if (type == "DESPESA") "Alimentação" else "Salário"
        statusIsPaid = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Novo Lançamento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "RESERVE OU REGISTRE SUAS TRANSAÇÕES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Elegant futuristic Segmented Switcher for Income vs Expense
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                Button(
                    onClick = { type = "DESPESA" },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("type_despesa_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (type == "DESPESA") MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else Color.Transparent,
                        contentColor = if (type == "DESPESA") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ),
                    elevation = null,
                    shape = RoundedCornerShape(8.dp),
                    border = if (type == "DESPESA") BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)) else null
                ) {
                    Icon(Icons.Default.ArrowCircleDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Despesa", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = { type = "RECEITA" },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("type_receita_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (type == "RECEITA") Color(0xFF00E676).copy(alpha = 0.15f) else Color.Transparent,
                        contentColor = if (type == "RECEITA") Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ),
                    elevation = null,
                    shape = RoundedCornerShape(8.dp),
                    border = if (type == "RECEITA") BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)) else null
                ) {
                    Icon(Icons.Default.ArrowCircleUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Receita", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Title / Description text field
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Descrição / Título") },
            placeholder = { Text("Ex: Supermercado, Aluguel, Salário") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tx_title_input"),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        )

        // Amount (Value) text field styled to match the cyber balance
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Valor (R$)") },
            placeholder = { Text("0,00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tx_amount_input"),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        )

        // Custom Date selection row with futuristic border accents
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Data do Lançamento", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormatterReadable.format(dateSelected.time).uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val active = Calendar.getInstance()
                                active.set(Calendar.YEAR, year)
                                active.set(Calendar.MONTH, month)
                                active.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                dateSelected = active
                            },
                            dateSelected.get(Calendar.YEAR),
                            dateSelected.get(Calendar.MONTH),
                            dateSelected.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Agendar", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Alterar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Tags / Category select options
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Categoria do Lançamento", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            val currentList = if (type == "DESPESA") categoriesExpense else categoriesIncome
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(currentList.size) { index ->
                    val cat = currentList[index]
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.5.dp
                        ),
                        modifier = Modifier.testTag("chip_cat_$cat")
                    )
                }
            }
            
            // Text field for custom tag entry if they click "Outros"
            if (selectedCategory == "Outros") {
                OutlinedTextField(
                    value = customCategory,
                    onValueChange = { customCategory = it },
                    label = { Text("Nome da Categoria Customizada") },
                    placeholder = { Text("Ex: Pets, Presentes") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // Payment Method Options (only relevant for Expenses)
        if (type == "DESPESA") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Método de Pagamento", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(paymentMethods.size) { index ->
                        val pMethod = paymentMethods[index]
                        val isSelected = selectedPaymentMethod == pMethod
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPaymentMethod = pMethod },
                            label = { Text(pMethod, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.secondary,
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.5.dp
                            ),
                            modifier = Modifier.testTag("chip_pay_$pMethod")
                        )
                    }
                }
            }
        }

        // Cash flow Status switch (Pago vs Pendente)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1.5f)) {
                    val label = if (type == "DESPESA") "Efetivado / Pago" else "Disponível / Recebido"
                    val subtitle = if (type == "DESPESA") 
                        "Marque se o débito já ocorreu em seu caixa." 
                    else "Marque se o valor já está em sua posse/conta."

                    Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = statusIsPaid,
                    onCheckedChange = { statusIsPaid = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = if (type == "RECEITA") Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                        checkedTrackColor = if (type == "RECEITA") Color(0xFF00E676).copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.testTag("status_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Confirm action buttons
        val submitBtnColor = if (type == "RECEITA") Color(0xFF00E676) else MaterialTheme.colorScheme.primary
        val submitBtnHoverColor = if (type == "RECEITA") Color(0xFF00B0FF) else MaterialTheme.colorScheme.primary
        
        Button(
            onClick = {
                val validatedTitle = title.trim().ifEmpty { 
                    if (type == "DESPESA") "Despesa Sem Título" else "Receita Sem Título" 
                }
                val rawAmount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
                
                val finalCategory = if (selectedCategory == "Outros" && customCategory.isNotEmpty()) {
                    customCategory.trim()
                } else {
                    selectedCategory
                }

                val finalStatus = if (type == "DESPESA") {
                    if (statusIsPaid) "Pago" else "Pendente"
                } else {
                    if (statusIsPaid) "Recebido" else "Pendente"
                }

                onAddSuccess(
                    type,
                    validatedTitle,
                    rawAmount,
                    dateSelected.timeInMillis,
                    finalCategory,
                    if (type == "DESPESA") selectedPaymentMethod else "",
                    finalStatus
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("tx_submit_btn"),
            colors = ButtonDefaults.buttonColors(
                containerColor = submitBtnColor,
                contentColor = if (type == "RECEITA") Color(0xFF060914) else MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("SALVAR LANÇAMENTO", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.sp)
        }
    }
}
