package com.example.ledger

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int = 0
)

@Entity(tableName = "currencies")
data class Currency(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val categoryId: Long,
    val currencyId: Long,
    val ceiling: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val amount: Double,
    val isDebit: Boolean,
    val date: String,
    val details: String = ""
)

data class AccountRow(
    val id: Long,
    val name: String,
    val phone: String,
    val categoryId: Long,
    val currencyId: Long,
    val ceiling: Double,
    val createdAt: Long,
    val debit: Double,
    val credit: Double
) {
    val balance: Double get() = credit - debit
}

data class EntryRow(
    val accountId: Long,
    val accountName: String,
    val categoryId: Long,
    val currencyId: Long,
    val amount: Double,
    val isDebit: Boolean,
    val date: String,
    val details: String
)

@Dao
interface LedgerDao {
    @Query(
        "SELECT a.id AS id, a.name AS name, a.phone AS phone, a.categoryId AS categoryId, " +
            "a.currencyId AS currencyId, a.ceiling AS ceiling, a.createdAt AS createdAt, " +
            "COALESCE(SUM(CASE WHEN e.isDebit = 1 THEN e.amount ELSE 0 END), 0) AS debit, " +
            "COALESCE(SUM(CASE WHEN e.isDebit = 0 THEN e.amount ELSE 0 END), 0) AS credit " +
            "FROM accounts a LEFT JOIN entries e ON e.accountId = a.id GROUP BY a.id"
    )
    fun accountRows(): Flow<List<AccountRow>>

    @Query("SELECT * FROM categories ORDER BY position, id")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM currencies ORDER BY id")
    fun currencies(): Flow<List<Currency>>

    @Query("SELECT * FROM entries WHERE accountId = :id ORDER BY date DESC, id DESC")
    fun entries(id: Long): Flow<List<Entry>>

    @Query(
        "SELECT e.accountId AS accountId, a.name AS accountName, a.categoryId AS categoryId, " +
            "a.currencyId AS currencyId, e.amount AS amount, e.isDebit AS isDebit, e.date AS date, " +
            "e.details AS details FROM entries e JOIN accounts a ON a.id = e.accountId ORDER BY e.date, e.id"
    )
    suspend fun allEntryRows(): List<EntryRow>

    @Query("SELECT DISTINCT accountId FROM entries WHERE details LIKE '%' || :q || '%'")
    suspend fun searchEntryAccounts(q: String): List<Long>

    @Insert suspend fun insertCategory(c: Category): Long
    @Insert suspend fun insertCurrency(c: Currency): Long
    @Insert suspend fun insertAccount(a: Account): Long
    @Insert suspend fun insertEntry(e: Entry): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String)

    @Query("UPDATE categories SET position = :pos WHERE id = :id")
    suspend fun setCategoryPos(id: Long, pos: Int)

    @Query("UPDATE currencies SET name = :name WHERE id = :id")
    suspend fun renameCurrency(id: Long, name: String)

    @Query("UPDATE accounts SET name = :name, phone = :phone WHERE id = :id")
    suspend fun editAccount(id: Long, name: String, phone: String)

    @Query("UPDATE accounts SET categoryId = :cat WHERE id = :id")
    suspend fun moveAccount(id: Long, cat: Long)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("DELETE FROM currencies WHERE id = :id")
    suspend fun deleteCurrency(id: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("DELETE FROM entries WHERE accountId = :id")
    suspend fun deleteEntriesOf(id: Long)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT COUNT(*) FROM accounts WHERE categoryId = :id")
    suspend fun countInCategory(id: Long): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE currencyId = :id")
    suspend fun countInCurrency(id: Long): Int
}

@Database(
    entities = [Category::class, Currency::class, Account::class, Entry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        const val NAME = "ledger.db"

        @Volatile
        private var instance: AppDb? = null

        fun get(c: Context): AppDb = instance ?: synchronized(this) {
            instance ?: build(c).also { instance = it }
        }

        fun close() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        private fun build(c: Context): AppDb =
            Room.databaseBuilder(c.applicationContext, AppDb::class.java, NAME)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT INTO categories(name, position) VALUES ('عام', 0)")
                        db.execSQL("INSERT INTO categories(name, position) VALUES ('العملاء', 1)")
                        db.execSQL("INSERT INTO categories(name, position) VALUES ('الموردين', 2)")
                        db.execSQL("INSERT INTO currencies(name) VALUES ('محلي')")
                        db.execSQL("INSERT INTO currencies(name) VALUES ('أجنبي')")
                    }
                })
                .build()
    }
}
