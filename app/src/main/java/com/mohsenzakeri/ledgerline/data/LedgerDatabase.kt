package com.mohsenzakeri.ledgerline.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val retailer: String,
    val receiptTotal: Double? = null
)

@Entity(tableName = "expense_entries")
data class ExpenseEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val amount: Double,
    val category: String,
    val secondaryCategory: String = "",
    val necessity: Int? = null,
    val details: String = "",
    val moreInformation: String = "",
    // Kept for importing and exporting legacy CSV rows; not shown in new-entry UI.
    val legacyPerson: String = ""
)

@Dao
interface LedgerDao {
    @Insert suspend fun insertPurchase(purchase: PurchaseEntity): Long
    @Insert suspend fun insertEntries(entries: List<ExpenseEntryEntity>)
    @Query("SELECT * FROM purchases ORDER BY date DESC, id DESC")
    fun observePurchases(): Flow<List<PurchaseEntity>>
    @Query("SELECT * FROM expense_entries WHERE purchaseId = :purchaseId ORDER BY id")
    fun observeEntries(purchaseId: Long): Flow<List<ExpenseEntryEntity>>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expense_entries")
    fun observeTotalSpent(): Flow<Double>
}

@Database(entities = [PurchaseEntity::class, ExpenseEntryEntity::class], version = 1, exportSchema = false)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile private var INSTANCE: LedgerDatabase? = null
        fun get(context: Context): LedgerDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                LedgerDatabase::class.java,
                "ledgerline.db"
            ).build().also { INSTANCE = it }
        }
    }
}
