package com.example.ledger

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Backup {
    private fun dbFile(ctx: Context): File = ctx.getDatabasePath(AppDb.NAME)

    fun backupTo(ctx: Context, uri: Uri): Boolean = try {
        AppDb.get(ctx).openHelper.writableDatabase
        ctx.contentResolver.openOutputStream(uri)!!.use { out ->
            dbFile(ctx).inputStream().use { it.copyTo(out) }
        }
        true
    } catch (e: Exception) {
        false
    }

    fun restoreFrom(ctx: Context, uri: Uri): Boolean = try {
        val tmp = File(ctx.cacheDir, "restore.db")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
        val header = ByteArray(15)
        tmp.inputStream().use { it.read(header) }
        if (String(header) != "SQLite format 3") {
            false
        } else {
            AppDb.close()
            val f = dbFile(ctx)
            File(f.path + "-wal").delete()
            File(f.path + "-shm").delete()
            File(f.path + "-journal").delete()
            tmp.copyTo(f, overwrite = true)
            true
        }
    } catch (e: Exception) {
        false
    }

    fun restart(ctx: Context) {
        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(i)
        Runtime.getRuntime().exit(0)
    }

    fun shareFile(ctx: Context, f: File, mime: String, text: String? = null, pkg: String? = null) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
            if (pkg != null) setPackage(pkg)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareDb(ctx: Context) {
        AppDb.get(ctx).openHelper.writableDatabase
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "ledger-backup.db")
        dbFile(ctx).copyTo(f, overwrite = true)
        shareFile(ctx, f, "application/octet-stream")
    }
}
