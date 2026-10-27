package io.github.xhr666.opendictionary.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import io.github.xhr666.opendictionary.data.UserDb

data class ImportResult(val imported: Int, val withLyrics: Int, val errors: Int, val message: String)

/** 通过 SAF 文件夹导入音乐与同名 .lrc；支持音频/视频容器。 */
object MediaImporter {
    private val audioExt = setOf("mp3", "m4a", "aac", "ogg", "wav", "flac", "mp4", "m4v", "mkv", "webm", "opus")

    fun importTree(context: Context, treeUri: Uri, onProgress: (String) -> Unit = {}): ImportResult {
        val db = UserDb.get(context)
        val files = mutableListOf<Pair<String, Uri>>()
        collect(context, treeUri, files, depth = 0)
        val byBase = files.groupBy { it.first.substringBeforeLast('.', it.first).lowercase() }
        var imported = 0; var withLyrics = 0; var errors = 0
        byBase.forEach { (base, list) ->
            val audio = list.firstOrNull { it.first.substringAfterLast('.', "").lowercase() in audioExt } ?: return@forEach
            val lrc = list.firstOrNull { it.first.substringAfterLast('.', "").equals("lrc", true) }
            onProgress("导入 ${audio.first}")
            try {
                val duration = runCatching {
                    MediaMetadataRetriever().use { r ->
                        r.setDataSource(context, audio.second)
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    }
                }.getOrDefault(0L)
                val title = audio.first.substringBeforeLast('.')
                val id = db.addMedia(audio.second.toString(), audio.first, title, "", duration, lrc != null)
                if (lrc != null) {
                    val raw = context.contentResolver.openInputStream(lrc.second)?.bufferedReader()?.use { it.readText() } ?: ""
                    val doc = LrcParser.parse(raw)
                    db.setMediaLyrics(id, doc.lines, doc.words)
                    withLyrics++
                }
                imported++
            } catch (e: Exception) { errors++ }
        }
        return ImportResult(imported, withLyrics, errors, "导入 $imported 个媒体，$withLyrics 个带歌词" + if (errors > 0) "，$errors 个失败" else "")
    }

    private fun collect(context: Context, treeUri: Uri, out: MutableList<Pair<String, Uri>>, depth: Int = 0, parentDocumentId: String? = null) {
        if (depth > 4) return
        val parent = parentDocumentId ?: DocumentsContract.getTreeDocumentId(treeUri)
        val childUri = try { DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent) } catch (e: Exception) { return }
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        context.contentResolver.query(childUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collect(context, treeUri, out, depth + 1, docId)
                } else out.add(name to uri)
            }
        }
    }
}