package com.example.fastdownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_DOWNLOAD = "START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "PAUSE_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "CANCEL_DOWNLOAD"
        
        const val EXTRA_DOWNLOAD_TASK = "DOWNLOAD_TASK"
    }
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private lateinit var repository: DownloadRepository
    private lateinit var notificationManager: NotificationManager
    private lateinit var okHttpClient: OkHttpClient
    
    private var currentDownloadJob: Job? = null
    private var currentTask: DownloadTask? = null
    
    override fun onCreate() {
        super.onCreate()
        
        repository = DownloadRepository(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra("url") ?: return START_NOT_STICKY
                val fileName = intent.getStringExtra("fileName") ?: "download_${System.currentTimeMillis()}"
                
                val task = DownloadTask(
                    url = url,
                    fileName = fileName,
                    status = DownloadStatus.QUEUED
                )
                
                startDownload(task)
            }
            ACTION_PAUSE_DOWNLOAD -> {
                pauseDownload()
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelDownload()
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun startDownload(task: DownloadTask) {
        currentTask = task
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification("Preparing download...", 0))
        
        currentDownloadJob = serviceScope.launch {
            try {
                // Save task to repository
                repository.addTask(task.copy(status = DownloadStatus.DOWNLOADING))
                
                val outputFile = repository.getDownloadFile(task.fileName)
                val downloader = SegmentedDownloader(
                    client = okHttpClient,
                    task = task,
                    outputFile = outputFile
                )
                
                val result = downloader.download { progress ->
                    // Update notification with progress
                    val notification = createNotification(
                        "Downloading ${task.fileName}",
                        progress.progressPercentage,
                        progress.speed
                    )
                    notificationManager.notify(NOTIFICATION_ID, notification)
                    
                    // Update task in repository
                    serviceScope.launch {
                        repository.updateTask(
                            task.copy(
                                downloadedBytes = progress.downloadedBytes,
                                totalBytes = progress.totalBytes,
                                status = DownloadStatus.DOWNLOADING
                            )
                        )
                    }
                }
                
                result.fold(
                    onSuccess = { success ->
                        if (success) {
                            // Download completed successfully
                            repository.updateTask(task.copy(status = DownloadStatus.COMPLETED))
                            
                            val completedNotification = createCompletedNotification(task.fileName)
                            notificationManager.notify(NOTIFICATION_ID, completedNotification)
                            
                        } else {
                            // Download failed
                            repository.updateTask(task.copy(status = DownloadStatus.FAILED))
                            
                            val failedNotification = createFailedNotification(task.fileName)
                            notificationManager.notify(NOTIFICATION_ID, failedNotification)
                        }
                    },
                    onFailure = { exception ->
                        // Download failed with exception
                        repository.updateTask(task.copy(status = DownloadStatus.FAILED))
                        
                        val failedNotification = createFailedNotification(
                            task.fileName,
                            exception.message
                        )
                        notificationManager.notify(NOTIFICATION_ID, failedNotification)
                    }
                )
                
            } catch (e: Exception) {
                e.printStackTrace()
                currentTask?.let { task ->
                    serviceScope.launch {
                        repository.updateTask(task.copy(status = DownloadStatus.FAILED))
                    }
                }
            } finally {
                stopForeground(false)
                stopSelf()
            }
        }
    }
    
    private fun pauseDownload() {
        currentDownloadJob?.cancel()
        currentTask?.let { task ->
            serviceScope.launch {
                repository.updateTask(task.copy(status = DownloadStatus.PAUSED))
            }
        }
    }
    
    private fun cancelDownload() {
        currentDownloadJob?.cancel()
        currentTask?.let { task ->
            serviceScope.launch {
                repository.updateTask(task.copy(status = DownloadStatus.CANCELLED))
                // Optionally delete partial file
                val file = repository.getDownloadFile(task.fileName)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(
        text: String, 
        progress: Int = 0,
        speed: Long = 0
    ): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FastDownloader")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
        
        if (progress > 0) {
            builder.setProgress(100, progress, false)
            
            if (speed > 0) {
                val speedText = formatSpeed(speed)
                builder.setSubText("$progress% • $speedText")
            } else {
                builder.setSubText("$progress%")
            }
        }
        
        return builder.build()
    }
    
    private fun createCompletedNotification(fileName: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Completed")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
    }
    
    private fun createFailedNotification(fileName: String, error: String? = null): Notification {
        val text = error?.let { "Failed: $it" } ?: "Download failed"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$fileName - $text")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
    }
    
    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "${bytesPerSecond} B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> "${"%.1f".format(bytesPerSecond / (1024.0 * 1024.0))} MB/s"
        }
    }
    
    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}