package online.draran.billing.core.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import online.draran.billing.core.database.BillingDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup file = zip with the SQLite database and a small manifest.
 * - Manual export/import goes through the system file picker (Google Drive, pen drive, etc.).
 * - A daily automatic copy is kept in app storage (last [KEEP] files).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: BillingDatabase,
) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val _lastBackup = MutableStateFlow(prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 })

    /** Epoch millis of the last successful backup (manual or automatic), or null. */
    val lastBackup: StateFlow<Long?> = _lastBackup.asStateFlow()

    private val autoDir: File get() = File(context.filesDir, "backups").apply { mkdirs() }

    fun suggestedFileName(): String =
        "kallaa-petti-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".zip"

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { write(it) } ?: error("Cannot open the selected file")
        markBackedUp()
    }

    suspend fun importFrom(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { restore(it) } ?: error("Cannot open the selected file")
    }

    suspend fun restoreAuto(file: File) = withContext(Dispatchers.IO) { file.inputStream().use { restore(it) } }

    fun autoBackups(): List<File> = autoDir.listFiles { f -> f.name.endsWith(".zip") }?.sortedByDescending { it.lastModified() }.orEmpty()

    /** Writes a daily copy when the last backup is older than 24 hours. Returns true if one was made. */
    suspend fun autoBackupIfDue(now: Long = System.currentTimeMillis()): Boolean {
        val last = autoBackups().firstOrNull()?.lastModified() ?: 0L
        if (now - last < DAY_MS) return false
        saveCopy()
        return true
    }

    /** Saves a copy in app storage now, keeping the newest [KEEP]. */
    suspend fun saveCopy() = withContext(Dispatchers.IO) {
        val file = File(autoDir, suggestedFileName().replace(".zip", "-" + System.currentTimeMillis() % 100_000 + ".zip"))
        file.outputStream().use { write(it) }
        autoBackups().drop(KEEP).forEach { it.delete() }
        markBackedUp()
    }

    private fun markBackedUp(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST, now).apply()
        _lastBackup.value = now
    }

    internal fun write(out: OutputStream) {
        // Flush the write-ahead log into the main file so one file holds everything
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val dbFile = context.getDatabasePath(BillingDatabase.NAME)
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write("app=Modern Kallaa Petti\nschema=1\ncreated=${System.currentTimeMillis()}\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DB_ENTRY))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    internal fun restore(input: InputStream) {
        val temp = File(context.cacheDir, "restore.db")
        var manifestOk = false
        var dbOk = false
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    MANIFEST -> manifestOk = zip.readBytes().decodeToString().contains("Kallaa Petti")
                    DB_ENTRY -> {
                        temp.outputStream().use { zip.copyTo(it) }
                        dbOk = temp.inputStream().use { String(it.readNBytes(15)) } == "SQLite format 3"
                    }
                }
            }
        }
        if (!manifestOk || !dbOk) {
            temp.delete()
            error("This is not a Modern Kallaa Petti backup file")
        }
        db.close()
        val dbFile = context.getDatabasePath(BillingDatabase.NAME)
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        temp.copyTo(dbFile, overwrite = true)
        temp.delete()
    }

    companion object {
        private const val KEY_LAST = "last_backup"
        private const val MANIFEST = "manifest.txt"
        private const val DB_ENTRY = "database.db"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        const val KEEP = 7
    }
}
