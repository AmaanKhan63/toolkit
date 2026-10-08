package com.amaankhan.toolkit.data

import android.content.Context
import android.util.AtomicFile
import com.amaankhan.toolkit.documents.WorkflowTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File

class WorkflowStore(context: Context) {
    private val storage = AtomicFile(File(context.filesDir, "workflows.json"))
    private val lock = Mutex()
    private fun read(): List<WorkflowTemplate> {
        if (!storage.baseFile.exists()) return emptyList()
        require(storage.baseFile.length() <= 1024 * 1024) { "Workflow storage is too large to open." }
        val values = JSONArray(String(storage.readFully(), Charsets.UTF_8))
        return (0 until values.length()).map { WorkflowTemplate.fromJson(values.getJSONObject(it)) }
    }
    suspend fun load(): List<WorkflowTemplate> = withContext(Dispatchers.IO) { lock.withLock { read() } }
    suspend fun save(template: WorkflowTemplate): List<WorkflowTemplate> = withContext(Dispatchers.IO) {
        lock.withLock { val values = read().filter { it.id != template.id } + template; require(values.size <= 100) { "Save up to 100 workflows." }; write(values); values }
    }
    suspend fun remove(id: String): List<WorkflowTemplate> = withContext(Dispatchers.IO) { lock.withLock { read().filter { it.id != id }.also { write(it) } } }
    private fun write(values: List<WorkflowTemplate>) {
        val output = storage.startWrite()
        try {
            output.write(JSONArray().apply { values.forEach { put(it.toJson()) } }.toString().toByteArray(Charsets.UTF_8))
            storage.finishWrite(output)
        } catch (error: Throwable) { storage.failWrite(output); throw error }
    }
}
