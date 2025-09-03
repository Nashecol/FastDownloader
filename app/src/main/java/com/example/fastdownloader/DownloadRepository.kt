package com.example.fastdownloader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class DownloadRepository(private val context: Context) {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val downloadsFile = File(context.filesDir, "downloads.json")
    
    suspend fun loadTasks(): List<DownloadTask> = withContext(Dispatchers.IO) {
        try {
            if (!downloadsFile.exists()) {
                return@withContext emptyList()
            }
            
            val content = downloadsFile.readText()
            if (content.isBlank()) {
                return@withContext emptyList()
            }
            
            json.decodeFromString<List<DownloadTask>>(content)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun saveTasks(tasks: List<DownloadTask>) = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(tasks)
            downloadsFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun addTask(task: DownloadTask) = withContext(Dispatchers.IO) {
        val currentTasks = loadTasks().toMutableList()
        currentTasks.add(task)
        saveTasks(currentTasks)
    }
    
    suspend fun updateTask(updatedTask: DownloadTask) = withContext(Dispatchers.IO) {
        val currentTasks = loadTasks().toMutableList()
        val index = currentTasks.indexOfFirst { it.id == updatedTask.id }
        if (index != -1) {
            currentTasks[index] = updatedTask
            saveTasks(currentTasks)
        }
    }
    
    suspend fun removeTask(taskId: String) = withContext(Dispatchers.IO) {
        val currentTasks = loadTasks().toMutableList()
        currentTasks.removeAll { it.id == taskId }
        saveTasks(currentTasks)
    }
    
    fun getDownloadFile(fileName: String): File {
        val downloadDir = File(context.filesDir, "downloads")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        return File(downloadDir, fileName)
    }
}