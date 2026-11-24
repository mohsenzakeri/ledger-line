package com.mohsenzakeri.ledgerline.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.Transaction
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
    val legacyPerson: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val paymentMethod: String = ""
)

data class CategoryTotal(val category: String, val total: Double)

@Dao
interface LedgerDao {
    @Insert suspend fun insertPurchase(purchase: PurchaseEntity): Long
    @Insert suspend fun insertEntries(entries: List<ExpenseEntryEntity>)
    @Query("SELECT * FROM purchases WHERE id = :id LIMIT 1")
    suspend fun getPurchase(id: Long): PurchaseEntity?
    @Query("SELECT * FROM expense_entries WHERE purchaseId = :purchaseId ORDER BY id")
    suspend fun getEntries(purchaseId: Long): List<ExpenseEntryEntity>
    @Transaction
    suspend fun importCsvRows(rows: List<Pair<Map<String, String>, Double>>) {
        rows.forEach { (row, amount) ->
            val id = insertPurchase(PurchaseEntity(date = row.getValue("Date").trim(),
                retailer = row.getValue("Retailer"), receiptTotal = amount))
            insertEntries(listOf(ExpenseEntryEntity(purchaseId = id, amount = amount,
                category = row.getValue("Category"), secondaryCategory = row.getValue("Secondary-Category"),
                necessity = row.getValue("Necessity").trim().toIntOrNull(),
                details = row.getValue("Details"), moreInformation = row.getValue("More Information"),
                legacyPerson = row["Person"].orEmpty(), paymentMethod = row["Payment Method"].orEmpty())))
        }
    }
    @Query("SELECT * FROM purchases ORDER BY date DESC, id DESC")
    suspend fun getAllPurchases(): List<PurchaseEntity>
    @Query("DELETE FROM expense_entries WHERE purchaseId = :purchaseId")
    suspend fun deleteEntries(purchaseId: Long)
    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deletePurchaseRow(id: Long)
    @Transaction suspend fun deletePurchase(id: Long) { deleteEntries(id); deletePurchaseRow(id) }
    @Transaction suspend fun replacePurchase(purchase: PurchaseEntity, entries: List<ExpenseEntryEntity>) {
        updatePurchase(purchase); deleteEntries(purchase.id); insertEntries(entries)
    }
    @androidx.room.Update suspend fun updatePurchase(purchase: PurchaseEntity)
    @Query("SELECT * FROM expense_entries ORDER BY id")
    suspend fun getAllEntries(): List<ExpenseEntryEntity>
    @Query("SELECT category, SUM(amount) AS total FROM expense_entries GROUP BY category ORDER BY total DESC")
    fun observeCategoryTotals(): Flow<List<CategoryTotal>>
    @Query("SELECT * FROM purchases ORDER BY date DESC, id DESC")
    fun observePurchases(): Flow<List<PurchaseEntity>>
    @Query("SELECT * FROM expense_entries WHERE purchaseId = :purchaseId ORDER BY id")
    fun observeEntries(purchaseId: Long): Flow<List<ExpenseEntryEntity>>
    @Query("SELECT * FROM expense_entries ORDER BY id DESC")
    fun observeAllEntries(): Flow<List<ExpenseEntryEntity>>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expense_entries")
    fun observeTotalSpent(): Flow<Double>
}

@Database(entities = [PurchaseEntity::class, ExpenseEntryEntity::class], version = 2, exportSchema = false)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile private var INSTANCE: LedgerDatabase? = null
        fun get(context: Context): LedgerDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                LedgerDatabase::class.java,
                "ledgerline.db"
            ).addMigrations(object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE expense_entries ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT ''")
                }
            }).build().also { INSTANCE = it }
        }
    }
}
