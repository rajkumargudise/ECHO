package echo.player.platform.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import echo.player.domain.AddRootResult
import echo.player.domain.MusicRoot
import echo.player.domain.MusicRootStatus
import echo.player.domain.MusicRootStore
import echo.player.domain.MusicRoots

data class MusicRootInfo(val root: MusicRoot, val status: MusicRootStatus)

sealed interface PickFolderResult {
    data class Added(val root: MusicRoot) : PickFolderResult
    /** The picked folder was already in the list; [root] is that existing entry (its grant is refreshed). */
    data class Duplicate(val root: MusicRoot) : PickFolderResult

    /** The picked location is not a folder on device storage that MediaStore can map (for example a cloud provider). */
    data object Unsupported : PickFolderResult
}

/**
 * Android side of the music-folder system. Folders are chosen with the system folder picker
 * (ACTION_OPEN_DOCUMENT_TREE) and the read grant is persisted, so ECHO can tell when a grant is revoked or a
 * folder disappears. The grant is only used for that status check: library contents still come from
 * MediaStore, filtered by these folders, so no broad storage permission is needed.
 */
class MusicRootManager(
    context: Context,
    private val store: MusicRootStore = AndroidMusicRootStore(context),
) {
    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    fun roots(): List<MusicRoot> = store.load()

    fun rootInfos(): List<MusicRootInfo> = roots().map { MusicRootInfo(it, statusOf(it)) }

    /** Only reachable folders contribute tracks; revoked or missing ones wait to be fixed in the folder screen. */
    fun activeRoots(): List<MusicRoot> = rootInfos().filter { it.status == MusicRootStatus.AVAILABLE }.map { it.root }

    fun statusOf(root: MusicRoot): MusicRootStatus {
        val tree = root.treeUri?.let(Uri::parse)
        val granted = tree != null && hasPersistedReadGrant(tree)
        val exists = granted && folderExists(tree!!)
        return MusicRoots.statusOf(root, hasPersistedGrant = granted, folderExists = exists)
    }

    fun addDefaultMusicFolder(): AddRootResult {
        val result = MusicRoots.add(roots(), MusicRoots.DEFAULT_MUSIC)
        if (result is AddRootResult.Added) store.save(result.roots)
        return result
    }

    fun addFromTree(treeUri: Uri): PickFolderResult {
        if (treeUri.authority != EXTERNAL_STORAGE_AUTHORITY) return PickFolderResult.Unsupported
        val root = try {
            MusicRoots.fromTreeDocumentId(DocumentsContract.getTreeDocumentId(treeUri), treeUri.toString())
        } catch (e: IllegalArgumentException) {
            null
        } ?: return PickFolderResult.Unsupported

        try {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not persist access to $treeUri", e)
            return PickFolderResult.Unsupported
        }

        return when (val result = MusicRoots.add(roots(), root)) {
            is AddRootResult.Added -> {
                store.save(result.roots)
                PickFolderResult.Added(root)
            }
            AddRootResult.Duplicate -> {
                // Same folder picked again: refresh its stored grant instead of leaving a stale one.
                store.save(roots().map { if (it.key == root.key) root else it })
                PickFolderResult.Duplicate(root)
            }
        }
    }

    fun remove(root: MusicRoot) {
        store.save(MusicRoots.remove(roots(), root))
        root.treeUri?.let { uri ->
            try {
                resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Already gone, which is the state we wanted.
            }
        }
    }

    private fun hasPersistedReadGrant(treeUri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }

    private fun folderExists(treeUri: Uri): Boolean = try {
        val folder = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        resolver.query(folder, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    } catch (e: Exception) {
        false
    }

    companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val TAG = "EchoMusicRoots"

        /** Where the folder picker should start: the standard Music folder. */
        fun musicFolderPickerStart(): Uri =
            DocumentsContract.buildTreeDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:Music")
    }
}
