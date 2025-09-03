package com.example.fastdownloader

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fastdownloader.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: DownloadRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = DownloadRepository(this)
        
        setupUI()
        loadDownloadHistory()
    }
    
    private fun setupUI() {
        binding.apply {
            // Set default values
            urlEditText.setText("https://httpbin.org/bytes/10485760") // 10MB test file
            fileNameEditText.setText("test_file.bin")
            
            downloadButton.setOnClickListener {
                val url = urlEditText.text.toString().trim()
                val fileName = fileNameEditText.text.toString().trim()
                
                if (validateInput(url, fileName)) {
                    startDownload(url, fileName)
                }
            }
            
            clearHistoryButton.setOnClickListener {
                clearDownloadHistory()
            }
        }
    }
    
    private fun validateInput(url: String, fileName: String): Boolean {
        if (url.isEmpty()) {
            showToast("Please enter a URL")
            return false
        }
        
        if (!Patterns.WEB_URL.matcher(url).matches()) {
            showToast("Please enter a valid URL")
            return false
        }
        
        if (fileName.isEmpty()) {
            showToast("Please enter a file name")
            return false
        }
        
        // Check for valid file name (no path separators)
        if (fileName.contains("/") || fileName.contains("\\")) {
            showToast("File name cannot contain path separators")
            return false
        }
        
        return true
    }
    
    private fun startDownload(url: String, fileName: String) {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra("url", url)
            putExtra("fileName", fileName)
        }
        
        startForegroundService(intent)
        showToast("Download started: $fileName")
        
        // Clear input fields
        binding.urlEditText.setText("")
        binding.fileNameEditText.setText("")
        
        // Refresh history after a short delay
        binding.root.postDelayed({
            loadDownloadHistory()
        }, 1000)
    }
    
    private fun loadDownloadHistory() {
        lifecycleScope.launch {
            try {
                val tasks = repository.loadTasks()
                updateHistoryDisplay(tasks)
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to load download history")
            }
        }
    }
    
    private fun updateHistoryDisplay(tasks: List<DownloadTask>) {
        val historyText = if (tasks.isEmpty()) {
            "No downloads yet"
        } else {
            tasks.takeLast(5).reversed().joinToString("\n\n") { task ->
                val status = task.status.name.lowercase().replaceFirstChar { it.uppercase() }
                val progress = if (task.totalBytes > 0) {
                    val percentage = ((task.downloadedBytes * 100) / task.totalBytes).toInt()
                    " ($percentage%)"
                } else ""
                
                "📁 ${task.fileName}\n" +
                "🔗 ${task.url.take(50)}${if (task.url.length > 50) "..." else ""}\n" +
                "📊 Status: $status$progress"
            }
        }
        
        binding.historyTextView.text = historyText
    }
    
    private fun clearDownloadHistory() {
        lifecycleScope.launch {
            try {
                repository.saveTasks(emptyList())
                updateHistoryDisplay(emptyList())
                showToast("Download history cleared")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to clear history")
            }
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}