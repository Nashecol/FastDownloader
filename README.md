# FastDownloader

A modern Android download manager with multi-connection segmented downloading, built with Kotlin, Coroutines, and OkHttp. This project demonstrates legal and ethical implementation of fast downloading techniques.

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.20-blue.svg)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)

## Features

- **🚀 Multi-Connection Downloads**: Split files into segments for faster downloading
- **📱 Material Design 3**: Modern UI with adaptive theming (light/dark)
- **🔄 Background Downloads**: Foreground service ensures downloads continue
- **📊 Real-time Progress**: Live progress tracking with speed indicators
- **💾 Persistent Storage**: Download history and resume capability
- **🛡️ Robust Error Handling**: Automatic retries and graceful failure handling
- **🎯 Clean Architecture**: MVVM pattern with repository pattern
- **🧪 Unit Tested**: Comprehensive test coverage

## Screenshots

| Main Interface | Download Progress | Download History |
|:---:|:---:|:---:|
| ![Main](https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?w=200&h=400&fit=crop) | ![Progress](https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=200&h=400&fit=crop) | ![History](https://images.unsplash.com/photo-1460925895917-afdab827c52f?w=200&h=400&fit=crop) |

## Architecture

```
FastDownloader/
├── app/
│   ├── src/main/java/com/example/fastdownloader/
│   │   ├── MainActivity.kt              # UI layer
│   │   ├── DownloadService.kt           # Foreground service
│   │   ├── SegmentedDownloader.kt       # Core downloading logic
│   │   ├── DownloadRepository.kt        # Data persistence
│   │   └── Models.kt                    # Data models
│   └── src/test/                        # Unit tests
├── build.gradle                         # Project configuration
└── README.md
```

## Technical Implementation

### Multi-Connection Downloading

The app implements segmented downloading by:

1. **Range Request Detection**: Checks if server supports `Accept-Ranges: bytes`
2. **File Segmentation**: Splits files into configurable segments (default: 6)
3. **Concurrent Downloads**: Uses Kotlin Coroutines for parallel segment downloads
4. **File Assembly**: Writes segments directly to correct file positions using `RandomAccessFile`

```kotlin
// Simplified core logic
val segmentSize = fileSize / segmentCount
val jobs = segments.map { segment ->
    async {
        downloadSegment(segment.start, segment.end)
    }
}
jobs.awaitAll()
```

### Background Processing

- **Foreground Service**: Ensures downloads continue when app is backgrounded
- **Notification Updates**: Real-time progress in notification panel
- **Proper Lifecycle**: Handles service creation/destruction correctly

### Data Persistence

- **JSON Storage**: Simple file-based storage using kotlinx.serialization
- **Download History**: Tracks completed, failed, and cancelled downloads
- **Resume Support**: Architecture supports resume (implementation can be extended)

## Installation & Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/fastdownloader-android.git
   cd fastdownloader-android
   ```

2. **Open in Android Studio**:
   - Open Android Studio
   - Choose "Open an existing project"
   - Select the cloned directory

3. **Build and run**:
   - Connect an Android device or start an emulator
   - Click Run (Ctrl+R / Cmd+R)

## Requirements

- **Android API 24+** (Android 7.0)
- **Kotlin 1.9.20+**
- **Internet permission** for downloads
- **Foreground service permission** for background downloads

## Usage

1. **Enter URL**: Paste the download URL in the input field
2. **Set Filename**: Choose a name for the downloaded file
3. **Start Download**: Tap "Start Download" button
4. **Monitor Progress**: Check notification panel for real-time updates
5. **View History**: See recent downloads in the app

### Testing URLs

For testing purposes, you can use:
- `https://httpbin.org/bytes/10485760` (10MB test file)
- `https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf`

## Testing

Run the unit tests:

```bash
./gradlew test
```

The project includes tests for:
- `SegmentedDownloader` with MockWebServer
- `DownloadRepository` data operations
- Range request handling
- Error scenarios

## Performance Features

### Speed Optimizations

- **Concurrent Connections**: Multiple simultaneous downloads
- **Optimal Buffer Size**: 64KB buffers balance memory and performance
- **Direct File Writing**: No intermediate buffering
- **Connection Reuse**: OkHttp connection pooling

### Memory Management

- **Streaming Downloads**: No full-file memory loading
- **Bounded Buffers**: Fixed-size read/write operations
- **Proper Resource Cleanup**: Automatic stream/connection closing

## Legal & Ethical Notice

⚠️ **Important**: This project is for **educational purposes only**. Please ensure you:

- Only download content you have permission to access
- Respect website terms of service and robots.txt
- Don't overload servers with excessive concurrent connections
- Comply with copyright laws and regulations
- Use responsibly and ethically

## Limitations & Future Enhancements

### Current Limitations

- No resume support after app restart (architecture supports it)
- Limited retry mechanisms
- Basic error reporting
- No bandwidth throttling

### Planned Improvements

- [ ] **Resume Downloads**: Persistent segment tracking
- [ ] **Retry Logic**: Exponential backoff for failed segments
- [ ] **Speed Limiting**: User-configurable bandwidth limits
- [ ] **Queue Management**: Multiple simultaneous downloads
- [ ] **Advanced UI**: Progress bars, pause/resume buttons
- [ ] **File Verification**: Checksum validation
- [ ] **ETag Support**: Avoid re-downloading unchanged files

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests if applicable
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### Development Guidelines

- Follow Android and Kotlin best practices
- Maintain clean architecture principles
- Add unit tests for new features
- Update documentation as needed
- Ensure legal and ethical compliance

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Dependencies

- **AndroidX**: Core Android libraries
- **Material Design 3**: Modern UI components
- **Kotlin Coroutines**: Asynchronous programming
- **OkHttp**: HTTP client for networking
- **kotlinx.serialization**: JSON serialization
- **JUnit & MockWebServer**: Testing framework

## Resources

- [OkHttp Documentation](https://square.github.io/okhttp/)
- [Android Foreground Services](https://developer.android.com/guide/components/foreground-services)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Material Design 3](https://m3.material.io/)

## Acknowledgments

- Inspired by Advanced Download Manager (ADM)
- Built with modern Android development practices
- Thanks to the open-source community

---

**Disclaimer**: This software is provided as-is for educational purposes. The authors are not responsible for any misuse or legal issues arising from its use. Please download responsibly and respect content creators' rights.