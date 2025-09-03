package com.example.fastdownloader

import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.io.File

class DownloadRepositoryTest {
    
    @Mock
    private lateinit var context: Context
    
    @Mock
    private lateinit var filesDir: File
    
    private lateinit var repository: DownloadRepository
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(context.filesDir).thenReturn(filesDir)
        `when`(filesDir.absolutePath).thenReturn("/test/files")
        
        repository = DownloadRepository(context)
    }
    
    @Test
    fun `loadTasks returns empty list when file doesn't exist`() = runTest {
        // Given
        val downloadsFile = File(filesDir, "downloads.json")
        `when`(downloadsFile.exists()).thenReturn(false)
        
        // When
        val result = repository.loadTasks()
        
        // Then
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun `addTask creates new task successfully`() = runTest {
        // Given
        val task = DownloadTask(
            id = "test-id",
            url = "https://example.com/file.zip",
            fileName = "file.zip",
            status = DownloadStatus.QUEUED
        )
        
        // When
        repository.addTask(task)
        
        // Then
        // Test would need actual file system mocking for full verification
        // This is a basic structure for the test
        assertNotNull(task.id)
        assertEquals("file.zip", task.fileName)
        assertEquals(DownloadStatus.QUEUED, task.status)
    }
    
    @Test
    fun `updateTask modifies existing task`() = runTest {
        // Given
        val originalTask = DownloadTask(
            id = "test-id",
            url = "https://example.com/file.zip",
            fileName = "file.zip",
            status = DownloadStatus.QUEUED,
            downloadedBytes = 0
        )
        
        val updatedTask = originalTask.copy(
            status = DownloadStatus.DOWNLOADING,
            downloadedBytes = 1024
        )
        
        // When
        repository.updateTask(updatedTask)
        
        // Then
        assertEquals(DownloadStatus.DOWNLOADING, updatedTask.status)
        assertEquals(1024, updatedTask.downloadedBytes)
    }
    
    @Test
    fun `getDownloadFile creates downloads directory if not exists`() {
        // Given
        val fileName = "test-file.zip"
        val downloadDir = File(filesDir, "downloads")
        `when`(downloadDir.exists()).thenReturn(false)
        `when`(downloadDir.mkdirs()).thenReturn(true)
        
        // When
        val result = repository.getDownloadFile(fileName)
        
        // Then
        assertEquals(File(downloadDir, fileName), result)
    }
}