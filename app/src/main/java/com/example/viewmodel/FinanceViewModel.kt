package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.FinanceDatabase
import com.example.data.Transaction
import com.example.data.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class FinanceViewModel(
    application: Application,
    private val repository: TransactionRepository
) : AndroidViewModel(application) {

    // Filter states
    val selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH)) // 0 to 11
    val selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedCategory = MutableStateFlow("Todas")

    // Read notification alert ids
    val readNotificationIds = MutableStateFlow<Set<String>>(emptySet())

    // Focused transaction ID (used for deep linking/jumping straight to specific transactions)
    val focusedTransactionId = MutableStateFlow<Int?>(null)

    // Transactions list from repository
    val allTransactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Dynamic, comprehensive category list fetched from preset values + database entries
    val categoriesList: StateFlow<List<String>> = allTransactions
        .map { transactions ->
            val preset = listOf("Alimentação", "Moradia", "Transporte", "Lazer", "Saúde", "Serviços", "Educação", "Vestuário", "Salário", "Investimentos", "Trabalho Extra", "Prêmio", "Outros")
            val dbCategories = transactions.map { it.category }.distinct()
            (listOf("Todas") + (preset + dbCategories).distinct().filter { it.isNotBlank() }.sorted()).toList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf("Todas")
        )

    // Filtered transactions for the currently selected month, year and category
    val filteredTransactions: StateFlow<List<Transaction>> = combine(
        allTransactions,
        selectedMonth,
        selectedYear,
        selectedCategory
    ) { transactions, month, year, category ->
        transactions.filter { transaction ->
            val cal = Calendar.getInstance().apply { timeInMillis = transaction.dateMillis }
            val matchDate = cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year
            val matchCategory = category == "Todas" || transaction.category.equals(category, ignoreCase = true)
            matchDate && matchCategory
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Statistics for the currently selected month
    val monthlyStats: StateFlow<MonthlyStats> = filteredTransactions
        .combine(selectedMonth) { transactions, month ->
            val totalIncome = transactions.filter { it.type == "RECEITA" }.sumOf { it.amount }
            val totalExpense = transactions.filter { it.type == "DESPESA" }.sumOf { it.amount }
            val balance = totalIncome - totalExpense

            // Group expenses by category
            val categoryExpenses = transactions
                .filter { it.type == "DESPESA" }
                .groupBy { it.category }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            // Group expenses by payment method
            val paymentMethodExpenses = transactions
                .filter { it.type == "DESPESA" }
                .groupBy { it.paymentMethod }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            // Filter pending expenses versus paid
            val paidExpenses = transactions.filter { it.type == "DESPESA" && it.status == "Pago" }.sumOf { it.amount }
            val pendingExpenses = transactions.filter { it.type == "DESPESA" && it.status == "Pendente" }.sumOf { it.amount }

            // Filter pending income versus received
            val receivedIncome = transactions.filter { it.type == "RECEITA" && it.status == "Recebido" }.sumOf { it.amount }
            val pendingIncome = transactions.filter { it.type == "RECEITA" && it.status == "Pendente" }.sumOf { it.amount }

            MonthlyStats(
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                balance = balance,
                categoryExpenses = categoryExpenses,
                paymentExpenses = paymentMethodExpenses,
                paidExpenses = paidExpenses,
                pendingExpenses = pendingExpenses,
                receivedIncome = receivedIncome,
                pendingIncome = pendingIncome,
                monthIndex = month
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MonthlyStats()
        )

    // Data for the 12-month overview chart reflecting selected category as well
    val yearlyOverview: StateFlow<List<MonthlyBarData>> = combine(
        allTransactions,
        selectedYear,
        selectedCategory
    ) { transactions, year, category ->
        val monthNames = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
        
        List(12) { monthIndex ->
            val monthTransactions = transactions.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
                val matchDate = cal.get(Calendar.MONTH) == monthIndex && cal.get(Calendar.YEAR) == year
                val matchCategory = category == "Todas" || it.category.equals(category, ignoreCase = true)
                matchDate && matchCategory
            }
            val income = monthTransactions.filter { it.type == "RECEITA" }.sumOf { it.amount }
            val expense = monthTransactions.filter { it.type == "DESPESA" }.sumOf { it.amount }
            
            MonthlyBarData(
                monthLabel = monthNames[monthIndex],
                income = income,
                expense = expense
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active Intelligent Financial Notifications
    val notifications: StateFlow<List<FinanceNotification>> = combine(
        allTransactions,
        selectedMonth,
        selectedYear,
        readNotificationIds
    ) { transactions, month, year, readIds ->
        val list = mutableListOf<FinanceNotification>()
        
        // Filter transactions for the selected month & year
        val currentMonthTxs = transactions.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            cal.get(Calendar.MONTH) == month && cal.get(Calendar.YEAR) == year
        }
        
        // 1. Pending Expenses
        val pendingExps = currentMonthTxs.filter { it.type == "DESPESA" && it.status == "Pendente" }
        pendingExps.forEach { tx ->
            val id = "pending_exp_${tx.id}"
            list.add(
                FinanceNotification(
                    id = id,
                    title = "Despesa Pendente",
                    message = "A despesa '${tx.title}' no valor de R$ ${String.format(Locale("pt", "BR"), "%,.2f", tx.amount)} está pendente de pagamento.",
                    type = "WARNING",
                    timestamp = tx.dateMillis,
                    isRead = readIds.contains(id)
                )
            )
        }
        
        // 2. Received Income
        val receivedIncomes = currentMonthTxs.filter { it.type == "RECEITA" && it.status == "Recebido" }
        receivedIncomes.forEach { tx ->
            val id = "rec_inc_${tx.id}"
            list.add(
                FinanceNotification(
                    id = id,
                    title = "Receita Recebida",
                    message = "A receita '${tx.title}' de R$ ${String.format(Locale("pt", "BR"), "%,.2f", tx.amount)} foi recebida com sucesso!",
                    type = "SUCCESS",
                    timestamp = tx.dateMillis,
                    isRead = readIds.contains(id)
                )
            )
        }
        
        // 3. Negative Monthly Balances vs Positive Balances
        val totalInc = currentMonthTxs.filter { it.type == "RECEITA" }.sumOf { it.amount }
        val totalExp = currentMonthTxs.filter { it.type == "DESPESA" }.sumOf { it.amount }
        val balance = totalInc - totalExp
        if (balance < 0.0 && currentMonthTxs.isNotEmpty()) {
            val id = "negative_balance_${month}_${year}"
            list.add(
                FinanceNotification(
                    id = id,
                    title = "Aviso de Balanço Negativo",
                    message = "Atenção: Suas despesas superaram suas receitas em R$ ${String.format(Locale("pt", "BR"), "%,.2f", -balance)}!",
                    type = "ERROR",
                    timestamp = System.currentTimeMillis(),
                    isRead = readIds.contains(id)
                )
            )
        } else if (balance > 1500.0) {
            val id = "positive_balance_${month}_${year}"
            list.add(
                FinanceNotification(
                    id = id,
                    title = "Saldo Saudável",
                    message = "Parabéns! Você acumulou um saldo positivo de R$ ${String.format(Locale("pt", "BR"), "%,.2f", balance)} neste período.",
                    type = "SUCCESS",
                    timestamp = System.currentTimeMillis(),
                    isRead = readIds.contains(id)
                )
            )
        }
        
        // 4. Alert for high category spends
        val categoryExpenses = currentMonthTxs
            .filter { it.type == "DESPESA" }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
        
        val highestExpenseCategory = categoryExpenses.maxByOrNull { it.value }
        if (highestExpenseCategory != null && highestExpenseCategory.value > 800.0) {
            val id = "high_category_expense_${highestExpenseCategory.key}_${month}"
            list.add(
                FinanceNotification(
                    id = id,
                    title = "Alerta de Gastos Elevados",
                    message = "Seus gastos com '${highestExpenseCategory.key}' atingiram R$ ${String.format(Locale("pt", "BR"), "%,.2f", highestExpenseCategory.value)}. Considere moderar saídas.",
                    type = "WARNING",
                    timestamp = System.currentTimeMillis(),
                    isRead = readIds.contains(id)
                )
            )
        }

        list.sortByDescending { it.timestamp }
        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Database CRUD Operations
    private fun triggerImmediateNotificationCheck() {
        val oneTimeWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.ui.FinanceNotificationWorker>().build()
        androidx.work.WorkManager.getInstance(getApplication()).enqueue(oneTimeWorkRequest)
    }

    fun addTransaction(
        type: String,
        title: String,
        amount: Double,
        dateMillis: Long,
        category: String,
        paymentMethod: String,
        status: String
    ) {
        viewModelScope.launch {
            repository.insert(
                Transaction(
                    type = type,
                    title = title,
                    amount = amount,
                    dateMillis = dateMillis,
                    category = category,
                    paymentMethod = paymentMethod,
                    status = status
                )
            )
            triggerImmediateNotificationCheck()
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.update(transaction)
            triggerImmediateNotificationCheck()
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.delete(transaction)
        }
    }

    // Set month, year or category
    fun setMonth(monthIndex: Int) {
        selectedMonth.value = monthIndex
    }

    fun setYear(year: Int) {
        selectedYear.value = year
    }

    fun setCategory(category: String) {
        selectedCategory.value = category
    }

    fun focusTransaction(txId: Int) {
        val tx = allTransactions.value.find { it.id == txId }
        if (tx != null) {
            val cal = Calendar.getInstance().apply { timeInMillis = tx.dateMillis }
            selectedMonth.value = cal.get(Calendar.MONTH)
            selectedYear.value = cal.get(Calendar.YEAR)
            selectedCategory.value = "Todas"
            focusedTransactionId.value = tx.id
        }
    }

    fun clearFocusedTransaction() {
        focusedTransactionId.value = null
    }

    fun markNotificationAsRead(id: String) {
        readNotificationIds.value = readNotificationIds.value + id
    }

    fun markAllNotificationsAsRead() {
        val currentIds = notifications.value.map { it.id }.toSet()
        readNotificationIds.value = readNotificationIds.value + currentIds
    }

    // Prepopulate database with realistic sample transactions if database is empty so user starts with data
    fun addSampleDataIfEmpty() {
        viewModelScope.launch {
            // Check if any transactions exist, if empty add some sample data
            // To be called from composable/MainActivity
        }
    }

    fun addSampleDataDirectly() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            
            // Generate some data from Jan to Jun/Jul for testing and beautiful initial charts!
            val samples = listOf(
                // Janeiro
                Transaction(type = "RECEITA", title = "Salário", amount = 5000.0, dateMillis = getTimeForMonth(Calendar.JANUARY, 5, currentYear), category = "Salário", paymentMethod = "Pix", status = "Recebido"),
                Transaction(type = "DESPESA", title = "Aluguel", amount = 1200.0, dateMillis = getTimeForMonth(Calendar.JANUARY, 10, currentYear), category = "Moradia", paymentMethod = "Boleto", status = "Pago"),
                Transaction(type = "DESPESA", title = "Supermercado", amount = 450.0, dateMillis = getTimeForMonth(Calendar.JANUARY, 15, currentYear), category = "Alimentação", paymentMethod = "Cartão de Crédito", status = "Pago"),
                Transaction(type = "DESPESA", title = "Cinema", amount = 80.0, dateMillis = getTimeForMonth(Calendar.JANUARY, 20, currentYear), category = "Lazer", paymentMethod = "Pix", status = "Pago"),
                
                // Fevereiro
                Transaction(type = "RECEITA", title = "Salário", amount = 5000.0, dateMillis = getTimeForMonth(Calendar.FEBRUARY, 5, currentYear), category = "Salário", paymentMethod = "Pix", status = "Recebido"),
                Transaction(type = "RECEITA", title = "Freela Design", amount = 800.0, dateMillis = getTimeForMonth(Calendar.FEBRUARY, 18, currentYear), category = "Trabalho Extra", paymentMethod = "Pix", status = "Recebido"),
                Transaction(type = "DESPESA", title = "Aluguel", amount = 1200.0, dateMillis = getTimeForMonth(Calendar.FEBRUARY, 10, currentYear), category = "Moradia", paymentMethod = "Boleto", status = "Pago"),
                Transaction(type = "DESPESA", title = "Manutenção Carro", amount = 350.0, dateMillis = getTimeForMonth(Calendar.FEBRUARY, 12, currentYear), category = "Transporte", paymentMethod = "Cartão de Crédito", status = "Pago"),
                Transaction(type = "DESPESA", title = "Supermercado", amount = 500.0, dateMillis = getTimeForMonth(Calendar.FEBRUARY, 15, currentYear), category = "Alimentação", paymentMethod = "Cartão de Crédito", status = "Pago"),

                // Março
                Transaction(type = "RECEITA", title = "Salário", amount = 5000.0, dateMillis = getTimeForMonth(Calendar.MARCH, 5, currentYear), category = "Salário", paymentMethod = "Pix", status = "Recebido"),
                Transaction(type = "DESPESA", title = "Aluguel", amount = 1200.0, dateMillis = getTimeForMonth(Calendar.MARCH, 10, currentYear), category = "Moradia", paymentMethod = "Boleto", status = "Pago"),
                Transaction(type = "DESPESA", title = "Supermercado", amount = 600.0, dateMillis = getTimeForMonth(Calendar.MARCH, 15, currentYear), category = "Alimentação", paymentMethod = "Cartão de Crédito", status = "Pago"),
                Transaction(type = "DESPESA", title = "Restaurantes", amount = 220.0, dateMillis = getTimeForMonth(Calendar.MARCH, 22, currentYear), category = "Alimentação", paymentMethod = "Pix", status = "Pago"),
                Transaction(type = "DESPESA", title = "Curso Online", amount = 150.0, dateMillis = getTimeForMonth(Calendar.MARCH, 25, currentYear), category = "Educação", paymentMethod = "Cartão de Crédito", status = "Pago"),

                // Abril
                Transaction(type = "RECEITA", title = "Salário", amount = 5000.0, dateMillis = getTimeForMonth(Calendar.APRIL, 5, currentYear), category = "Salário", paymentMethod = "Pix", status = "Recebido"),
                Transaction(type = "DESPESA", title = "Aluguel", amount = 1200.0, dateMillis = getTimeForMonth(Calendar.APRIL, 10, currentYear), category = "Moradia", paymentMethod = "Boleto", status = "Pago"),
                Transaction(type = "DESPESA", title = "Supermercado", amount = 480.0, dateMillis = getTimeForMonth(Calendar.APRIL, 15, currentYear), category = "Alimentação", paymentMethod = "Cartão de Crédito", status = "Pago"),
                Transaction(type = "DESPESA", title = "Gasolina", amount = 180.0, dateMillis = getTimeForMonth(Calendar.APRIL, 18, currentYear), category = "Transporte", paymentMethod = "Pix", status = "Pago"),

                // Maio
                Transaction(type = "RECEITA", title = "Salário", amount = 5000.0, dateMillis = getTimeForMonth(Calendar.MAY, 5, currentYear), category = "Salário", paymentMethod = "Pix", status = "Recebido"),
                Transaction(type = "RECEITA", title = "Venda Celular", amount = 1200.0, dateMillis = getTimeForMonth(Calendar.MAY, 12, currentYear), category = "Outros", paymentMethod = "Pix", status = "Recebido"),
                Transaction(type = "DESPESA", title = "Aluguel", amount = 1200.0, dateMillis = getTimeForMonth(Calendar.MAY, 10, currentYear), category = "Moradia", paymentMethod = "Boleto", status = "Pago"),
                Transaction(type = "DESPESA", title = "Supermercado", amount = 520.0, dateMillis = getTimeForMonth(Calendar.MAY, 15, currentYear), category = "Alimentação", paymentMethod = "Cartão de Crédito", status = "Pago"),
                Transaction(type = "DESPESA", title = "Concerto Computador", amount = 450.0, dateMillis = getTimeForMonth(Calendar.MAY, 20, currentYear), category = "Lazer", paymentMethod = "Pix", status = "Pago"),
                Transaction(type = "DESPESA", title = "Assinatura Netflix", amount = 55.0, dateMillis = getTimeForMonth(Calendar.MAY, 28, currentYear), category = "Lazer", paymentMethod = "Cartão de Crédito", status = "Pago"),

                // Junho (Current Month in metadata)
                Transaction(type = "RECEITA", title = "Salário", amount = 5500.0, dateMillis = getTimeForMonth(Calendar.JUNE, 5, currentYear), category = "Salário", paymentMethod = "Pix", status = "Recebido"),
                Transaction(type = "RECEITA", title = "Rendimento Poupança", amount = 150.0, dateMillis = getTimeForMonth(Calendar.JUNE, 10, currentYear), category = "Investimentos", paymentMethod = "Pix", status = "Recebido"),
                Transaction(type = "DESPESA", title = "Aluguel", amount = 1200.0, dateMillis = getTimeForMonth(Calendar.JUNE, 5, currentYear), category = "Moradia", paymentMethod = "Boleto", status = "Pago"),
                Transaction(type = "DESPESA", title = "Compra de Roupas", amount = 340.0, dateMillis = getTimeForMonth(Calendar.JUNE, 8, currentYear), category = "Vestuário", paymentMethod = "Cartão de Crédito", status = "Pago"),
                Transaction(type = "DESPESA", title = "Supermercado", amount = 590.0, dateMillis = getTimeForMonth(Calendar.JUNE, 12, currentYear), category = "Alimentação", paymentMethod = "Cartão de Crédito", status = "Pago"),
                Transaction(type = "DESPESA", title = "Energia Elétrica", amount = 220.0, dateMillis = getTimeForMonth(Calendar.JUNE, 18, currentYear), category = "Serviços", paymentMethod = "Pix", status = "Pendente"),
                Transaction(type = "DESPESA", title = "Academia Mensal", amount = 110.0, dateMillis = getTimeForMonth(Calendar.JUNE, 20, currentYear), category = "Saúde", paymentMethod = "Cartão de Crédito", status = "Pago"),
                Transaction(type = "DESPESA", title = "Combustível", amount = 150.0, dateMillis = getTimeForMonth(Calendar.JUNE, 22, currentYear), category = "Transporte", paymentMethod = "Pix", status = "Pago"),
                Transaction(type = "DESPESA", title = "Dentista", amount = 300.0, dateMillis = getTimeForMonth(Calendar.JUNE, 28, currentYear), category = "Saúde", paymentMethod = "Boleto", status = "Pendente")
            )
            
            for (item in samples) {
                repository.insert(item)
            }
        }
    }

    private fun getTimeForMonth(month: Int, day: Int, year: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

// Stats Holder
data class MonthlyStats(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
    val categoryExpenses: Map<String, Double> = emptyMap(),
    val paymentExpenses: Map<String, Double> = emptyMap(),
    val paidExpenses: Double = 0.0,
    val pendingExpenses: Double = 0.0,
    val receivedIncome: Double = 0.0,
    val pendingIncome: Double = 0.0,
    val monthIndex: Int = 0
)

// Data class for 12 months bar charts
data class MonthlyBarData(
    val monthLabel: String,
    val income: Double,
    val expense: Double
)

// Factory for construction DI
class FinanceViewModelFactory(
    private val application: Application,
    private val repository: TransactionRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FinanceViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Data class for Notifications Alerts
data class FinanceNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: String, // "INFO", "SUCCESS", "WARNING", "ERROR"
    val timestamp: Long,
    val isRead: Boolean = false
)
