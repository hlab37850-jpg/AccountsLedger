package com.example.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class LedgerVM(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    val dao: LedgerDao = db.dao()

    val rows = dao.accountRows().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories = dao.categories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val currencies = dao.currencies().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun entries(accountId: Long) = dao.entries(accountId)

    fun addAccount(
        name: String, phone: String, catId: Long, curId: Long, ceiling: Double,
        amount: Double, debit: Boolean, date: String, details: String
    ) {
        viewModelScope.launch {
            val id = dao.insertAccount(Account(name = name, phone = phone, categoryId = catId, currencyId = curId, ceiling = ceiling))
            if (amount > 0) dao.insertEntry(Entry(accountId = id, amount = amount, isDebit = debit, date = date, details = details))
        }
    }

    fun addEntry(accountId: Long, amount: Double, debit: Boolean, date: String, details: String) {
        viewModelScope.launch {
            dao.insertEntry(Entry(accountId = accountId, amount = amount, isDebit = debit, date = date, details = details))
        }
    }

    fun deleteEntry(id: Long) { viewModelScope.launch { dao.deleteEntry(id) } }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            dao.deleteEntriesOf(id)
            dao.deleteAccount(id)
        }
    }

    fun editAccount(id: Long, name: String, phone: String) { viewModelScope.launch { dao.editAccount(id, name, phone) } }
    fun moveAccount(id: Long, cat: Long) { viewModelScope.launch { dao.moveAccount(id, cat) } }

    fun addCategory(name: String) {
        viewModelScope.launch {
            val pos = (categories.value.maxOfOrNull { it.position } ?: -1) + 1
            dao.insertCategory(Category(name = name, position = pos))
        }
    }

    fun renameCategory(id: Long, name: String) { viewModelScope.launch { dao.renameCategory(id, name) } }

    fun swapCategories(a: Category, b: Category) {
        viewModelScope.launch {
            if (a.position == b.position) {
                dao.setCategoryPos(a.id, b.position + 1)
            } else {
                dao.setCategoryPos(a.id, b.position)
                dao.setCategoryPos(b.id, a.position)
            }
        }
    }

    /** result: true if deleted */
    fun deleteCategory(id: Long, result: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (categories.value.size <= 1 || dao.countInCategory(id) > 0) {
                result(false)
            } else {
                dao.deleteCategory(id)
                result(true)
            }
        }
    }

    fun addCurrency(name: String) { viewModelScope.launch { dao.insertCurrency(Currency(name = name)) } }
    fun renameCurrency(id: Long, name: String) { viewModelScope.launch { dao.renameCurrency(id, name) } }

    fun deleteCurrency(id: Long, result: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (currencies.value.size <= 1 || dao.countInCurrency(id) > 0) {
                result(false)
            } else {
                dao.deleteCurrency(id)
                result(true)
            }
        }
    }

    /** Annual closing: collapse every account's entries into a single carried-forward balance. */
    fun closeYear(date: String, label: String, done: () -> Unit) {
        viewModelScope.launch {
            db.withTransaction {
                for (a in rows.value) {
                    dao.deleteEntriesOf(a.id)
                    val net = a.balance
                    if (net != 0.0) {
                        dao.insertEntry(Entry(accountId = a.id, amount = abs(net), isDebit = net < 0, date = date, details = label))
                    }
                }
            }
            done()
        }
    }
}
