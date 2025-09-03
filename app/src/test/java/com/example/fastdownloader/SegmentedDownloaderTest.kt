package com.example.fastdownloader

import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class SegmentedDownloaderTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var tempFile: File
    
    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        
        tempFile = File.createTempFile("test_download", ".tmp")
        tempFile.deleteOnExit()
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }
    
    @Test
    fun `download succeeds with range support`() = runTest {
        // Given
        val testContent = "Hello, World! This is a test file for downloading.".repeat(100)
        val contentLength = testContent.toByteArray().size.toLong()
        
        // Mock HEAD request
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Length", contentLength.toString())
                .addHeader("Accept-Ranges", "bytes")
        )
        
        // Mock range requests
        repeat(6) { segmentIndex ->
            val segmentSize = contentLength / 6
            val start = segmentIndex * segmentSize
            val end = if (segmentIndex == 5) contentLength - 1 else start + segmentSize - 1
            val segmentContent = testContent.substring(start.toInt(), (end + 1).toInt())
            
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Range", "bytes $start-$end/$contentLength")
                    .setBody(segmentContent)
            )
        }
        
        val task = DownloadTask(
            id = "test-task",
            url = mockWebServer.url("/test-file").toString(),
            fileName = "test-file.txt"
        )
        
        val downloader = SegmentedDownloader(
            client = client,
            task = task,
            outputFile = tempFile,
            segmentCount = 6
        )
        
        var progressCallbackCalled = false
        
        // When
        val result = downloader.download { progress ->
            progressCallbackCalled = true
            assertTrue("Downloaded bytes should be positive", progress.downloadedBytes >= 0)
            assertTrue("Total bytes should be positive", progress.totalBytes > 0)
            assertTrue("Progress should not exceed 100%", progress.progressPercentage <= 100)
        }
        
        // Then
        assertTrue("Download should succeed", result.isSuccess)
        assertTrue("Progress callback should be called", progressCallbackCalled)
        assertTrue("Downloaded file should exist", tempFile.exists())
        assertEquals("File size should match", contentLength, tempFile.length())
    }
    
    @Test
    fun `download falls back to single-threaded when ranges not supported`() = runTest {
        // Given
        val testContent = "Simple test content"
        
        // Mock HEAD request without range support
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Length", testContent.length.toString())
                // No Accept-Ranges header
        )
        
        // Mock single GET request
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(testContent)
        )
        
        val task = DownloadTask(
            id = "test-task",
            url = mockWebServer.url("/test-file").toString(),
            fileName = "test-file.txt"
        )
        
        val downloader = SegmentedDownloader(
            client = client,
            task = task,
            outputFile = tempFile
        )
        
        // When
        val result = downloader.download { _ -> }
        
        // Then
        assertTrue("Download should succeed", result.isSuccess)
        assertEquals("File content should match", testContent, tempFile.readText())
    }
    
    @Test
    fun `download fails when server returns error`() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
        )
        
        val task = DownloadTask(
            id = "test-task",
            url = mockWebServer.url("/nonexistent-file").toString(),
            fileName = "test-file.txt"
        )
        
        val downloader = SegmentedDownloader(
            client = client,
            task = task,
            outputFile = tempFile
        )
        
        // When
        val result = downloader.download { _ -> }
        
        // Then
        assertTrue("Download should fail", result.isFailure)
    }
    
    @Test
    fun `download handles unknown content length`() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                // No Content-Length header
        )
        
        val task = DownloadTask(
            id = "test-task",
            url = mockWebServer.url("/test-file").toString(),
            fileName = "test-file.txt"
        )
        
        val downloader = SegmentedDownloader(
            client = client,
            task = task,
            outputFile = tempFile
        )
        
        // When
        val result = downloader.download { _ -> }
        
        // Then
        assertTrue("Download should fail with unknown content length", result.isFailure)
    }
}