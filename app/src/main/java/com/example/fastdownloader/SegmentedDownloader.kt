package com.example.fastdownloader

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

class SegmentedDownloader(
    private val client: OkHttpClient,
    private val task: DownloadTask,
    private val outputFile: File,
    private val segmentCount: Int = 6
) {
    
    private val segmentProgress = LongArray(segmentCount) { 0L }
    private var lastProgressTime = 0L
    private var lastDownloadedBytes = 0L
    
    suspend fun download(
        progressCallback: (DownloadProgress) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // First, check if server supports range requests
            val headResponse = client.newCall(
                Request.Builder()
                    .url(task.url)
                    .head()
                    .build()
            ).execute()
            
            if (!headResponse.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to get file info: ${headResponse.code}")
                )
            }
            
            val contentLength = headResponse.header("Content-Length")?.toLongOrNull()
                ?: return@withContext Result.failure(Exception("Unknown file size"))
                
            val acceptRanges = headResponse.header("Accept-Ranges") == "bytes"
            
            if (!acceptRanges || contentLength <= 0) {
                // Fall back to single-threaded download
                return@withContext downloadSingleThreaded(contentLength, progressCallback)
            }
            
            // Prepare file
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.setLength(contentLength)
            }
            
            // Calculate segments
            val segmentSize = contentLength / segmentCount
            val segments = mutableListOf<DownloadSegment>()
            
            for (i in 0 until segmentCount) {
                val start = i * segmentSize
                val end = if (i == segmentCount - 1) contentLength - 1 else start + segmentSize - 1
                segments.add(DownloadSegment(i, start, end))
            }
            
            // Download segments concurrently
            val jobs = segments.map { segment ->
                async {
                    downloadSegment(segment, contentLength, progressCallback)
                }
            }
            
            // Wait for all segments to complete
            val results = jobs.awaitAll()
            val allSuccessful = results.all { it }
            
            Result.success(allSuccessful)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun downloadSegment(
        segment: DownloadSegment,
        totalBytes: Long,
        progressCallback: (DownloadProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            var currentPos = segment.start
            val bufferSize = 64 * 1024 // 64KB buffer
            
            while (currentPos <= segment.end) {
                val endPos = min(currentPos + bufferSize - 1, segment.end)
                
                val request = Request.Builder()
                    .url(task.url)
                    .addHeader("Range", "bytes=$currentPos-$endPos")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful || response.code != 206) {
                        throw Exception("Range request failed: ${response.code}")
                    }
                    
                    val responseBody = response.body
                        ?: throw Exception("Empty response body")
                    
                    val bytes = responseBody.bytes()
                    
                    // Write to file at correct position
                    synchronized(this@SegmentedDownloader) {
                        RandomAccessFile(outputFile, "rw").use { raf ->
                            raf.seek(currentPos)
                            raf.write(bytes)
                        }
                        
                        // Update progress
                        segmentProgress[segment.index] += bytes.size
                        val totalDownloaded = segmentProgress.sum()
                        
                        // Calculate speed (every second)
                        val currentTime = System.currentTimeMillis()
                        var speed = 0L
                        if (currentTime - lastProgressTime >= 1000) {
                            speed = ((totalDownloaded - lastDownloadedBytes) * 1000) / 
                                    (currentTime - lastProgressTime)
                            lastProgressTime = currentTime
                            lastDownloadedBytes = totalDownloaded
                        }
                        
                        progressCallback(
                            DownloadProgress(
                                taskId = task.id,
                                downloadedBytes = totalDownloaded,
                                totalBytes = totalBytes,
                                speed = speed
                            )
                        )
                    }
                    
                    currentPos += bytes.size
                }
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private suspend fun downloadSingleThreaded(
        totalBytes: Long,
        progressCallback: (DownloadProgress) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(task.url)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Download failed: ${response.code}")
                    )
                }
                
                val responseBody = response.body
                    ?: return@withContext Result.failure(Exception("Empty response body"))
                
                outputFile.outputStream().use { fileOutput ->
                    val buffer = ByteArray(64 * 1024)
                    var downloadedBytes = 0L
                    var bytesRead: Int
                    
                    responseBody.byteStream().use { input ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            fileOutput.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            
                            progressCallback(
                                DownloadProgress(
                                    taskId = task.id,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            )
                        }
                    }
                }
            }
            
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private data class DownloadSegment(
    val index: Int,
    val start: Long,
    val end: Long
)