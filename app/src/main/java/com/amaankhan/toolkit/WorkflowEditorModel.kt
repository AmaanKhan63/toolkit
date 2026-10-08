package com.amaankhan.toolkit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amaankhan.toolkit.documents.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*

class WorkflowEditorModel(application: Application): AndroidViewModel(application) {
    private val store = (application as ToolkitApplication).workflows
    val template = MutableStateFlow(WorkflowTemplate("My workflow", emptyList()))
    val saved = MutableStateFlow<List<WorkflowTemplate>>(emptyList())
    val notice = MutableStateFlow<String?>(null)
    init { viewModelScope.launch { try { saved.value = store.load() } catch (_: Exception) { notice.value = "Saved workflows could not be opened. Existing storage has been preserved." } } }
    fun rename(name: String) { template.update { it.copy(name = name.take(80)) } }
    fun add(operation: WorkflowOperation) { template.update { it.copy(steps = it.steps + WorkflowStep(operation)) } }
    fun change(step: WorkflowStep) { template.update { it.copy(steps = it.steps.map { old -> if (old.id == step.id) step else old }) } }
    fun remove(id: String) { template.update { it.copy(steps = it.steps.filter { old -> old.id != id }) } }
    fun move(index: Int, delta: Int) { template.update { template ->
        val list = template.steps.toMutableList(); val target = index + delta
        if (index in list.indices && target in list.indices) list.add(target, list.removeAt(index))
        template.copy(steps = list)
    } }
    fun select(value: WorkflowTemplate) { template.value = value; notice.value = null }
    fun save() { viewModelScope.launch { try { saved.value = store.save(template.value); notice.value = "Workflow saved. Passwords will be requested each run." } catch (e: Exception) { notice.value = e.message ?: "Could not save this workflow." } } }
    fun delete(id: String) { viewModelScope.launch { try { saved.value = store.remove(id); notice.value = "Saved workflow removed." } catch (e: Exception) { notice.value = "Could not remove the saved workflow." } } }
}
