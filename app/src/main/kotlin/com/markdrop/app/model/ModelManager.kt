package com.markdrop.app.model

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpException(
    val code: Int,
    override val message: String,
    val url: String
) : Exception(message)

data class PreValidationResult(
    val finalUrl: String,
    val contentLength: Long,
    val contentType: String?,
    val isAcceptRanges: Boolean
)

sealed class ModelDownloadState {
    object Idle : ModelDownloadState()
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val statusMessage: String? = null
    ) : ModelDownloadState()
    object Verifying : ModelDownloadState()
    data class Downloaded(val version: String, val fileSizeBytes: Long) : ModelDownloadState()
    data class Error(
        val message: String,
        val httpCode: Int? = null,
        val failingUrl: String? = null,
        val repoUrl: String? = null,
        val errorType: ErrorType = ErrorType.GENERIC
    ) : ModelDownloadState() {
        enum class ErrorType {
            UNAUTHORIZED, // 401
            GATED_ACCESS, // 403
            NOT_FOUND,    // 404
            PRE_VALIDATION, // Returned HTML or suspicious size
            CORRUPTED_ARCHIVE, // Corrupted zip header or incomplete bytes
            STORAGE,
            NETWORK,
            GENERIC
        }
    }
}

class ModelManager(
    private val context: Context
) {

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var activeConnection: HttpURLConnection? = null
    private var isCancelled = false

    private fun shouldForwardHfAuth(url: URL, hfToken: String?): Boolean {
        if (hfToken.isNullOrBlank()) return false
        val host = url.host.lowercase()
        val isHfHost = host == "huggingface.co" || host.endsWith(".huggingface.co") ||
                host == "hf.co" || host.endsWith(".hf.co")
        val query = url.query ?: ""
        val hasAmzAuth = query.contains("X-Amz-Signature", ignoreCase = true) ||
                query.contains("X-Amz-Credential", ignoreCase = true)
        return isHfHost && !hasAmzAuth
    }

    fun getModelFile(manifest: ModelManifest): File {
        val litertlmFile = File(modelsDir, "${manifest.modelId}.litertlm")
        if (litertlmFile.exists() && litertlmFile.length() > 0) return litertlmFile

        val taskFile = File(modelsDir, "${manifest.modelId}.task")
        if (taskFile.exists() && taskFile.length() > 0) return taskFile

        val assetPath = manifest.assetConfig.assetPath
        val ext = if (assetPath.endsWith(".litertlm", ignoreCase = true) || assetPath.contains(".litertlm?")) ".litertlm" else ".task"
        return File(modelsDir, "${manifest.modelId}$ext")
    }

    fun isValidZipHeader(file: File): Boolean = LiteRtArchiveValidator.isValidZipHeader(file)

    private val readinessCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun hasSentencePieceTokenizer(file: File): Boolean = LiteRtArchiveValidator.hasSentencePieceTokenizer(file)

    fun isModelReady(manifest: ModelManifest): Boolean {
        val file = getModelFile(manifest)
        if (!file.exists() || file.length() == 0L) {
            readinessCache[manifest.modelId] = false
            return false
        }
        val cached = readinessCache[manifest.modelId]
        if (cached != null) return cached

        val isArchive = file.name.endsWith(".task", ignoreCase = true) || file.name.endsWith(".litertlm", ignoreCase = true)
        val ready = if (isArchive) {
            val validation = LiteRtArchiveValidator.validate(file)
            validation.isValid
        } else {
            true
        }
        readinessCache[manifest.modelId] = ready
        return ready
    }

    fun getModelPath(manifest: ModelManifest): String = getModelFile(manifest).absolutePath

    fun checkLocalModel(manifest: ModelManifest) {
        val file = getModelFile(manifest)
        if (file.exists() && file.length() > 0) {
            val isArchive = file.name.endsWith(".task", ignoreCase = true) || file.name.endsWith(".litertlm", ignoreCase = true)
            if (isArchive) {
                val validation = LiteRtArchiveValidator.validate(file)
                if (!validation.isValid) {
                    _downloadState.value = ModelDownloadState.Error(
                        message = validation.errorMessage ?: "Archive validation failed: file is corrupted or not a valid LiteRT task archive.",
                        failingUrl = manifest.downloadUrl,
                        repoUrl = manifest.assetConfig.repoId,
                        errorType = ModelDownloadState.Error.ErrorType.CORRUPTED_ARCHIVE
                    )
                    return
                }
            }
            _downloadState.value = ModelDownloadState.Downloaded(
                version = manifest.version,
                fileSizeBytes = file.length()
            )
        } else if (_downloadState.value !is ModelDownloadState.Downloading) {
            _downloadState.value = ModelDownloadState.Idle
        }
    }

    fun getDeviceRamGb(): Double {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
    }

    fun isDeviceRamSufficient(manifest: ModelManifest): Boolean {
        return getDeviceRamGb() >= manifest.minRamGb
    }

    fun getAvailableDiskSpaceBytes(): Long {
        val stat = StatFs(context.filesDir.path)
        return stat.availableBytes
    }

    suspend fun preValidateDownloadUrl(
        targetUrl: String,
        hfToken: String? = null
    ): Result<PreValidationResult> = withContext(Dispatchers.IO) {
        try {
            var currentUrl = URL(targetUrl)
            var redirects = 0
            val maxRedirects = 10
            var conn: HttpURLConnection? = null

            while (redirects < maxRedirects) {
                val c = (currentUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 15000
                    readTimeout = 20000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Markdrop-Android-App")

                    if (shouldForwardHfAuth(currentUrl, hfToken)) {
                        setRequestProperty("Authorization", "Bearer ${hfToken!!.trim()}")
                    }
                }

                val status = c.responseCode
                if (status in 300..399) {
                    val location = c.getHeaderField("Location")
                    c.disconnect()
                    if (location.isNullOrBlank()) {
                        return@withContext Result.failure(HttpException(status, "Redirect without Location header", currentUrl.toString()))
                    }
                    currentUrl = URL(currentUrl, location)
                    redirects++
                    continue
                }

                if (status == 405 || status == 400) {
                    c.disconnect()
                    val getConn = (currentUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15000
                        readTimeout = 20000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "Markdrop-Android-App")
                        setRequestProperty("Range", "bytes=0-0")

                        if (shouldForwardHfAuth(currentUrl, hfToken)) {
                            setRequestProperty("Authorization", "Bearer ${hfToken!!.trim()}")
                        }
                    }
                    val getStatus = getConn.responseCode
                    if (getStatus in 300..399) {
                        val loc = getConn.getHeaderField("Location")
                        getConn.disconnect()
                        if (loc.isNullOrBlank()) {
                            return@withContext Result.failure(HttpException(getStatus, "Redirect without Location header", currentUrl.toString()))
                        }
                        currentUrl = URL(currentUrl, loc)
                        redirects++
                        continue
                    }
                    conn = getConn
                    break
                }

                conn = c
                break
            }

            if (conn == null) {
                return@withContext Result.failure(HttpException(0, "Too many redirects during pre-validation", targetUrl))
            }

            val code = conn.responseCode
            val contentType = conn.contentType ?: ""
            var length = conn.contentLengthLong

            val contentRange = conn.getHeaderField("Content-Range")
            if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                val totalStr = contentRange.substringAfterLast("/").trim()
                val parsedTotal = totalStr.toLongOrNull()
                if (parsedTotal != null && parsedTotal > 0) {
                    length = parsedTotal
                }
            }

            val acceptRanges = conn.getHeaderField("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
            conn.disconnect()

            if (code == 401) {
                return@withContext Result.failure(HttpException(401, "Hugging Face authentication failed (HTTP 401). Verify HF token.", targetUrl))
            }
            if (code == 403) {
                return@withContext Result.failure(HttpException(403, "Gated repository access required (HTTP 403). Accept license on Hugging Face.", targetUrl))
            }
            if (code == 404) {
                return@withContext Result.failure(HttpException(404, "Asset not found (HTTP 404).", targetUrl))
            }
            if (code !in 200..299) {
                return@withContext Result.failure(HttpException(code, "Server returned HTTP $code during pre-validation.", targetUrl))
            }

            if (contentType.contains("text/html", ignoreCase = true)) {
                return@withContext Result.failure(
                    HttpException(
                        code,
                        "Server returned an HTML page (Content-Type: $contentType) instead of binary weights. This typically indicates an authorization redirect or error page.",
                        targetUrl
                    )
                )
            }

            if (length in 1..50_000_000L) {
                return@withContext Result.failure(
                    HttpException(
                        code,
                        "File size ($length bytes) is suspiciously small for model weights (>50 MB expected). File may be corrupted or an error message.",
                        targetUrl
                    )
                )
            }

            Result.success(
                PreValidationResult(
                    finalUrl = currentUrl.toString(),
                    contentLength = length,
                    contentType = contentType,
                    isAcceptRanges = acceptRanges
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startDownload(
        manifest: ModelManifest,
        hfToken: String? = null
    ) {
        if (downloadJob?.isActive == true) return

        downloadJob = downloadScope.launch {
            val requiredBytes = manifest.fileSizeBytes + 500_000_000L
            if (getAvailableDiskSpaceBytes() < requiredBytes) {
                _downloadState.value = ModelDownloadState.Error(
                    message = "Insufficient storage space. At least ${(requiredBytes / (1024 * 1024 * 1024)) + 1} GB of free space required.",
                    errorType = ModelDownloadState.Error.ErrorType.STORAGE
                )
                return@launch
            }

            isCancelled = false
            val tempFile = File(modelsDir, "${manifest.modelId}.tmp")
            val currentTargetUrl = manifest.downloadUrl
            val targetRepoUrl = manifest.assetConfig.repoTreeUrl()

            _downloadState.value = ModelDownloadState.Downloading(
                progress = 0f,
                bytesDownloaded = 0L,
                totalBytes = manifest.fileSizeBytes,
                statusMessage = "Pre-validating download URL..."
            )

            val preVal = preValidateDownloadUrl(currentTargetUrl, hfToken)
            if (preVal.isFailure) {
                val ex = preVal.exceptionOrNull()
                val code = (ex as? HttpException)?.code
                val errType = when (code) {
                    401 -> ModelDownloadState.Error.ErrorType.UNAUTHORIZED
                    403 -> ModelDownloadState.Error.ErrorType.GATED_ACCESS
                    404 -> ModelDownloadState.Error.ErrorType.NOT_FOUND
                    else -> ModelDownloadState.Error.ErrorType.PRE_VALIDATION
                }
                _downloadState.value = ModelDownloadState.Error(
                    message = "Pre-Validation Failed: ${ex?.message ?: "Unknown error"}",
                    httpCode = code,
                    failingUrl = currentTargetUrl,
                    repoUrl = targetRepoUrl,
                    errorType = errType
                )
                return@launch
            }

            var totalBytes = preVal.getOrNull()?.contentLength?.takeIf { it > 0 } ?: manifest.fileSizeBytes
            var resumedBytes = 0L
            if (tempFile.exists()) {
                resumedBytes = tempFile.length()
            }

            try {
                var currentUrl = URL(currentTargetUrl)
                var connection: HttpURLConnection? = null
                var redirects = 0
                val maxRedirects = 10

                while (redirects < maxRedirects) {
                    val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "Markdrop-Android-App")

                        if (shouldForwardHfAuth(currentUrl, hfToken)) {
                            setRequestProperty("Authorization", "Bearer ${hfToken!!.trim()}")
                        }
                    }

                    if (resumedBytes > 0 && redirects == 0) {
                        conn.setRequestProperty("Range", "bytes=$resumedBytes-")
                    }

                    val status = conn.responseCode
                    if (status in 300..399) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (location.isNullOrBlank()) {
                            _downloadState.value = ModelDownloadState.Error(
                                message = "Redirect encountered without Location header (HTTP $status).",
                                httpCode = status,
                                failingUrl = currentUrl.toString(),
                                repoUrl = targetRepoUrl,
                                errorType = ModelDownloadState.Error.ErrorType.NETWORK
                            )
                            return@launch
                        }
                        currentUrl = URL(currentUrl, location)
                        redirects++
                        continue
                    }

                    connection = conn
                    break
                }

                if (connection == null) {
                    _downloadState.value = ModelDownloadState.Error(
                        message = "Too many redirects encountered while connecting to model server.",
                        failingUrl = currentTargetUrl,
                        repoUrl = targetRepoUrl,
                        errorType = ModelDownloadState.Error.ErrorType.NETWORK
                    )
                    return@launch
                }
                activeConnection = connection

                val responseCode = connection.responseCode

                if (responseCode == 401) {
                    _downloadState.value = ModelDownloadState.Error(
                        message = "Hugging Face authentication failed (HTTP 401). Your HF Access Token may be invalid or expired.",
                        httpCode = 401,
                        failingUrl = currentTargetUrl,
                        repoUrl = targetRepoUrl,
                        errorType = ModelDownloadState.Error.ErrorType.UNAUTHORIZED
                    )
                    return@launch
                }

                if (responseCode == 403) {
                    _downloadState.value = ModelDownloadState.Error(
                        message = "Gated Model Access Required (HTTP 403). You must accept the model license terms on Hugging Face before downloading.",
                        httpCode = 403,
                        failingUrl = currentTargetUrl,
                        repoUrl = targetRepoUrl,
                        errorType = ModelDownloadState.Error.ErrorType.GATED_ACCESS
                    )
                    return@launch
                }

                if (responseCode == 404) {
                    _downloadState.value = ModelDownloadState.Error(
                        message = "Model Asset Not Found (HTTP 404) on server. Please verify the model repository path.",
                        httpCode = 404,
                        failingUrl = currentTargetUrl,
                        repoUrl = targetRepoUrl,
                        errorType = ModelDownloadState.Error.ErrorType.NOT_FOUND
                    )
                    return@launch
                }

                if (responseCode !in 200..299) {
                    _downloadState.value = ModelDownloadState.Error(
                        message = "Server returned HTTP $responseCode: ${connection.responseMessage}",
                        httpCode = responseCode,
                        failingUrl = currentTargetUrl,
                        repoUrl = targetRepoUrl,
                        errorType = ModelDownloadState.Error.ErrorType.GENERIC
                    )
                    return@launch
                }

                val isPartial = responseCode == 206
                if (!isPartial && resumedBytes > 0) {
                    tempFile.delete()
                    resumedBytes = 0L
                }

                val contentLength = connection.contentLengthLong
                if (contentLength > 0) {
                    totalBytes = if (isPartial) contentLength + resumedBytes else contentLength
                }

                var downloadedBytes = resumedBytes
                var maxProgressSoFar = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile, isPartial).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var isFirstChunk = (resumedBytes == 0L)

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (isCancelled) {
                                _downloadState.value = ModelDownloadState.Idle
                                return@launch
                            }

                            if (isFirstChunk && bytesRead > 0) {
                                isFirstChunk = false
                                val previewLength = minOf(bytesRead, 512)
                                val preview = String(buffer, 0, previewLength, Charsets.UTF_8).trimStart()

                                if (preview.startsWith("version https://git-lfs.github.com/spec/v1")) {
                                    output.close()
                                    tempFile.delete()
                                    _downloadState.value = ModelDownloadState.Error(
                                        message = "Git-LFS pointer downloaded instead of binary — verify direct resolution URL",
                                        httpCode = 409,
                                        failingUrl = currentTargetUrl,
                                        repoUrl = targetRepoUrl,
                                        errorType = ModelDownloadState.Error.ErrorType.CORRUPTED_ARCHIVE
                                    )
                                    return@launch
                                }

                                val lower = preview.lowercase()
                                if (lower.startsWith("<!doctype html") || lower.startsWith("<html")) {
                                    output.close()
                                    tempFile.delete()
                                    _downloadState.value = ModelDownloadState.Error(
                                        message = "Server returned HTML error page instead of binary weights. Check repository authentication or permissions.",
                                        httpCode = 403,
                                        failingUrl = currentTargetUrl,
                                        repoUrl = targetRepoUrl,
                                        errorType = ModelDownloadState.Error.ErrorType.PRE_VALIDATION
                                    )
                                    return@launch
                                }
                            }

                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val fraction = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            if (fraction > maxProgressSoFar) {
                                maxProgressSoFar = fraction
                            }

                            _downloadState.value = ModelDownloadState.Downloading(
                                progress = maxProgressSoFar,
                                bytesDownloaded = downloadedBytes,
                                totalBytes = totalBytes,
                                statusMessage = "Downloading model weights..."
                            )
                        }
                    }
                }

                _downloadState.value = ModelDownloadState.Verifying
                if (tempFile.exists() && tempFile.length() > 0) {
                    if (totalBytes > 0 && downloadedBytes < totalBytes) {
                        tempFile.delete()
                        _downloadState.value = ModelDownloadState.Error(
                            message = "Incomplete download: received $downloadedBytes of $totalBytes bytes.",
                            failingUrl = currentTargetUrl,
                            repoUrl = targetRepoUrl,
                            errorType = ModelDownloadState.Error.ErrorType.CORRUPTED_ARCHIVE
                        )
                        return@launch
                    }

                    val ext = if (currentTargetUrl.contains(".litertlm", ignoreCase = true)) ".litertlm" else ".task"
                    val isArchive = ext == ".task" || ext == ".litertlm"

                    if (isArchive) {
                        val validation = LiteRtArchiveValidator.validate(tempFile)
                        if (!validation.isValid) {
                            tempFile.delete()
                            _downloadState.value = ModelDownloadState.Error(
                                message = validation.errorMessage ?: "Archive validation failed: file is corrupted or not a valid LiteRT task archive.",
                                failingUrl = currentTargetUrl,
                                repoUrl = targetRepoUrl,
                                errorType = ModelDownloadState.Error.ErrorType.CORRUPTED_ARCHIVE
                            )
                            return@launch
                        }
                    }

                    val finalModelFile = File(modelsDir, "${manifest.modelId}$ext")

                    val altExt = if (ext == ".litertlm") ".task" else ".litertlm"
                    val altFile = File(modelsDir, "${manifest.modelId}$altExt")
                    if (altFile.exists()) altFile.delete()

                    if (finalModelFile.exists()) finalModelFile.delete()
                    tempFile.renameTo(finalModelFile)

                    _downloadState.value = ModelDownloadState.Downloaded(
                        version = manifest.version,
                        fileSizeBytes = finalModelFile.length()
                    )
                } else {
                    _downloadState.value = ModelDownloadState.Error(
                        message = "Downloaded model file is empty.",
                        failingUrl = currentTargetUrl,
                        repoUrl = targetRepoUrl,
                        errorType = ModelDownloadState.Error.ErrorType.GENERIC
                    )
                }
            } catch (e: Exception) {
                if (!isCancelled) {
                    _downloadState.value = ModelDownloadState.Error(
                        message = e.localizedMessage ?: "Download failed due to network exception.",
                        failingUrl = currentTargetUrl,
                        repoUrl = targetRepoUrl,
                        errorType = ModelDownloadState.Error.ErrorType.NETWORK
                    )
                }
            } finally {
                activeConnection?.disconnect()
                activeConnection = null
            }
        }
    }

    fun cancelDownload() {
        isCancelled = true
        downloadJob?.cancel()
        downloadJob = null
        activeConnection?.disconnect()
        activeConnection = null
        _downloadState.value = ModelDownloadState.Idle
    }

    fun deleteModel(manifest: ModelManifest): Boolean {
        cancelDownload()
        readinessCache.remove(manifest.modelId)
        val taskFile = File(modelsDir, "${manifest.modelId}.task")
        val litertlmFile = File(modelsDir, "${manifest.modelId}.litertlm")
        val deleted1 = if (taskFile.exists()) taskFile.delete() else false
        val deleted2 = if (litertlmFile.exists()) litertlmFile.delete() else false
        val tmp = File(modelsDir, "${manifest.modelId}.tmp")
        if (tmp.exists()) tmp.delete()
        checkLocalModel(manifest)
        return deleted1 || deleted2
    }
}
