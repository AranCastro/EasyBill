package online.draran.billing.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
/** A restore that failed after the running database was closed: the app must be restarted to use the data again. */
class RestoreFailedException(message: String, val needsRestart: Boolean, cause: Throwable? = null) : Exception(message, cause)

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

    /** Copies kept in app storage, newest first: daily copies and the copies taken just before a restore. */
    fun autoBackups(): List<File> = autoDir.listFiles { f -> f.name.endsWith(".zip") }?.sortedByDescending { it.lastModified() }.orEmpty()

    /** True for the copy taken automatically just before a restore. */
    fun isBeforeRestore(file: File): Boolean = file.name.startsWith(BEFORE_RESTORE)

    /** Writes a daily copy when the last daily copy is older than 24 hours. Returns true if one was made. */
    suspend fun autoBackupIfDue(now: Long = System.currentTimeMillis()): Boolean {
        val last = autoBackups().firstOrNull { !isBeforeRestore(it) }?.lastModified() ?: 0L
        if (now - last < DAY_MS) return false
        saveCopy()
        return true
    }

    /** Saves a copy in app storage now, keeping the newest [KEEP]. */
    suspend fun saveCopy() = withContext(Dispatchers.IO) {
        val file = File(autoDir, suggestedFileName().replace(".zip", "-" + System.currentTimeMillis() % 100_000 + ".zip"))
        try {
            file.outputStream().use { write(it) }
        } catch (e: Exception) {
            file.delete() // never leave a half-written copy in the list
            throw e
        }
        autoBackups().filterNot(::isBeforeRestore).drop(KEEP).forEach { it.delete() }
        markBackedUp()
    }

    private fun markBackedUp(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST, now).apply()
        _lastBackup.value = now
    }

    internal fun write(out: OutputStream) {
        val sqlite = db.openHelper.writableDatabase
        // Fold the write-ahead log into the main file first; best effort, a busy reader can hold it back
        runCatching { sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
        val dbFile = context.getDatabasePath(BillingDatabase.NAME)
        val walFile = File(dbFile.path + "-wal")
        // Holding the write lock stops any save from changing the files while they are copied;
        // the log is copied too, so whatever the checkpoint could not fold in is still in the backup.
        sqlite.beginTransaction()
        try {
            writeZip(out, dbFile, walFile.takeIf { it.exists() && it.length() > 0 })
        } finally {
            sqlite.endTransaction()
        }
    }

    private fun writeZip(out: OutputStream, dbFile: File, walFile: File?) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write("app=Modern Kallaa Petti\nschema=${BillingDatabase.VERSION}\ncreated=${System.currentTimeMillis()}\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DB_ENTRY))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            if (walFile != null) {
                zip.putNextEntry(ZipEntry(WAL_ENTRY))
                walFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            // Logo and signature images
            File(context.filesDir, BrandingManager.DIR).listFiles()?.filter { it.isFile }?.forEach { f ->
                zip.putNextEntry(ZipEntry(BRANDING_PREFIX + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Replaces the database and images with the ones in a backup. Nothing is touched
     * until the backup's database passes an integrity and version check; the old
     * database is kept as a "before-restore" copy in the list of automatic copies, so a
     * wrong restore can be undone from the same screen.
     */
    internal fun restore(input: InputStream) {
        val temp = File(context.cacheDir, "restore.db")
        // Leftovers of an earlier, interrupted restore must not be mixed into this one
        listOf("", "-wal", "-shm", "-journal").forEach { File(temp.path + it).delete() }
        var manifestOk = false
        var dbOk = false
        val branding = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        MANIFEST -> manifestOk = zip.readBytes().decodeToString().contains("Kallaa Petti")
                        DB_ENTRY -> {
                            temp.outputStream().use { zip.copyTo(it) }
                            dbOk = true
                        }
                        // Recent changes not yet folded into the main file; SQLite reads them on open
                        WAL_ENTRY -> File(temp.path + "-wal").outputStream().use { zip.copyTo(it) }
                        else -> if (entry.name.startsWith(BRANDING_PREFIX)) {
                            val name = File(entry.name).name // never trust paths inside a zip
                            if (name == BrandingManager.LOGO || name == BrandingManager.SIGNATURE) branding[name] = zip.readBytes()
                        }
                    }
                }
            }
            if (!manifestOk || !dbOk) error("This is not a Modern Kallaa Petti backup file")
            checkDatabase(temp)

            val dbFile = context.getDatabasePath(BillingDatabase.NAME)
            val staged = File(dbFile.parentFile, "restore-staging.db")
            var closed = false
            try {
                // Everything that can fail for lack of space happens before the live database is touched
                temp.copyTo(staged, overwrite = true)
                val safetyDir = File(context.filesDir, "backups").apply { mkdirs() }
                db.close()
                closed = true
                if (dbFile.exists()) {
                    // A new, complete copy each time (a partial one is deleted), so restoring a second wrong
                    // file cannot overwrite the only copy of the original data. The newest three are kept.
                    // Saved as an ordinary backup zip, so it shows in the list and can be restored like any other.
                    val safety = File(safetyDir, "$BEFORE_RESTORE${System.currentTimeMillis()}.zip")
                    try {
                        safety.outputStream().use { writeZip(it, dbFile, File(dbFile.path + "-wal").takeIf { w -> w.exists() && w.length() > 0 }) }
                    } catch (e: Exception) {
                        safety.delete()
                        throw e
                    }
                    safetyDir.listFiles { f -> f.name.startsWith(BEFORE_RESTORE) }?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
                }
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
                // Same folder, so the swap is one atomic rename: the database is the old one or the new one.
                // If the rename fails the old file is untouched, so there is nothing to put back.
                check(staged.renameTo(dbFile)) { "Could not replace the database" }
            } catch (e: Exception) {
                // After close() the running app holds a closed database: tell the caller to restart it
                throw RestoreFailedException(e.message ?: "Could not restore the backup", needsRestart = closed, cause = e)
            } finally {
                staged.delete()
            }

            // Images come last and cannot fail the restore: the database is already replaced
            runCatching {
                val brandingDir = File(context.filesDir, BrandingManager.DIR)
                val fresh = File(context.filesDir, BrandingManager.DIR + ".new").apply { deleteRecursively(); mkdirs() }
                branding.forEach { (name, bytes) -> File(fresh, name).writeBytes(bytes) }
                brandingDir.deleteRecursively()
                if (!fresh.renameTo(brandingDir)) {
                    brandingDir.mkdirs()
                    fresh.listFiles()?.forEach { it.copyTo(File(brandingDir, it.name), overwrite = true) }
                    fresh.deleteRecursively()
                }
            }
        } finally {
            temp.delete()
            File(temp.path + "-wal").delete()
            File(temp.path + "-shm").delete()
            File(temp.path + "-journal").delete()
        }
    }

    /** Opens the backup's database (a temporary copy): it must be intact, from this or an older app version, and have our tables. */
    private fun checkDatabase(file: File) {
        val sqlite = try {
            // Read-write on this throwaway copy: the integrity check of the search index (FTS) needs to write
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (e: Exception) {
            error("This is not a Modern Kallaa Petti backup file")
        }
        try {
            val integrity = sqlite.rawQuery("PRAGMA integrity_check", null).use { if (it.moveToFirst()) it.getString(0) else "" }
            check(integrity == "ok") { "The backup file is damaged" }
            check(sqlite.version <= BillingDatabase.VERSION) { "This backup was made by a newer version of the app. Update the app, then restore it." }
            val hasBusiness = sqlite.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'business'", null).use { it.moveToFirst() }
            check(sqlite.version >= 1 && hasBusiness) { "This is not a Modern Kallaa Petti backup file" }
            // Fold a log from the backup into the main file: only the main file is moved into place
            sqlite.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            // Not a database at all ("file is not a database"): give the same plain message
            error("This is not a Modern Kallaa Petti backup file")
        } finally {
            sqlite.close()
        }
    }

    companion object {
        private const val KEY_LAST = "last_backup"
        private const val MANIFEST = "manifest.txt"
        private const val DB_ENTRY = "database.db"
        private const val WAL_ENTRY = "database.db-wal"
        private const val BEFORE_RESTORE = "before-restore-"
        private const val BRANDING_PREFIX = "branding/"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        const val KEEP = 7
    }
}
