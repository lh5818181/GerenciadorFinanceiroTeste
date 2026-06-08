package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "RECEITA" or "DESPESA"
    val title: String,
    val amount: Double,
    val dateMillis: Long,
    val category: String, // Saved category or tags
    val paymentMethod: String, // "Dinheiro", "Cartão de Crédito", "Cartão de Débito", "Pix", "Boleto", etc.
    val status: String // "Pago", "Pendente" for expenses; "Recebido", "Pendente" for income
)
