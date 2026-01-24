package com.thirutricks.tllplayer.infrastructure

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Interface for comprehensive logging with context tracking.
 */
interface MenuLogger {
    fun log(level: Int, tag: String, message: String, throwable: Throwable? = null)
    fun logWithContext(level: Int, tag: String, message: String, context: Map<String, Any>, throwable: Throwable? = null)
    fun setDebugMode(enabled: Boolean)
    fun isDebugMode(): Boolean
    fun getLogHistory(maxEntries: Int = 100): List<LogEntry>
    fun clearHistory()
}

/**
 * Represents a single log entry with full context information.
 */
data class LogEntry(
    val timestamp: Long,
    val level: Int,
    val tag: String,
    val message: String,
    val context: Map<String, Any>,
    val throwable: Throwable?,
    val threadName: String
) {
    fun getLevelName(): String {
        return when (level) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            Log.ASSERT -> "ASSERT"
            else -> "UNKNOWN"
        }
    }
    
    fun getFormattedTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * Default implementation of MenuLogger with comprehensive logging capabilities.
 */
class DefaultMenuLogger : MenuLogger {
    
    private var debugMode = false
    private val logHistory = ConcurrentLinkedQueue<LogEntry>()
    private val maxHistorySize = 1000
    
    override fun log(level: Int, tag: String, message: String, throwable: Throwable?) {
        logWithContext(level, tag, message, emptyMap(), throwable)
    }
    
    override fun logWithContext(level: Int, tag: String, message: String, context: Map<String, Any>, throwable: Throwable?) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            context = context,
            throwable = throwable,
            threadName = Thread.currentThread().name
        )
        
        // Add to history
        addToHistory(entry)
        
        // Format message with context
        val formattedMessage = formatMessage(message, context)
        
        // Log to Android Log system
        when (level) {
            Log.VERBOSE -> if (debugMode) Log.v(tag, formattedMessage, throwable)
            Log.DEBUG -> if (debugMode) Log.d(tag, formattedMessage, throwable)
            Log.INFO -> Log.i(tag, formattedMessage, throwable)
            Log.WARN -> Log.w(tag, formattedMessage, throwable)
            Log.ERROR -> Log.e(tag, formattedMessage, throwable)
            Log.ASSERT -> Log.wtf(tag, formattedMessage, throwable)
        }
        
        // Additional debug logging if enabled
        if (debugMode && level >= Log.INFO) {
            logDebugInfo(entry)
        }
    }
    
    override fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
        log(Log.INFO, "MenuLogger", "Debug mode ${if (enabled) "enabled" else "disabled"}")
    }
    
    override fun isDebugMode(): Boolean = debugMode
    
    override fun getLogHistory(maxEntries: Int): List<LogEntry> {
        return logHistory.toList().takeLast(maxEntries)
    }
    
    override fun clearHistory() {
        logHistory.clear()
        log(Log.INFO, "MenuLogger", "Log history cleared")
    }
    
    private fun formatMessage(message: String, context: Map<String, Any>): String {
        return if (context.isEmpty()) {
            message
        } else {
            val contextString = context.entries.joinToString(", ") { "${it.key}=${it.value}" }
            "$message [Context: $contextString]"
        }
    }
    
    private fun addToHistory(entry: LogEntry) {
        logHistory.offer(entry)
        
        // Maintain history size limit
        while (logHistory.size > maxHistorySize) {
            logHistory.poll()
        }
    }
    
    private fun logDebugInfo(entry: LogEntry) {
        val debugInfo = buildString {
            append("Thread: ${entry.threadName}")
            append(" | Time: ${entry.getFormattedTimestamp()}")
            append(" | Level: ${entry.getLevelName()}")
            
            if (entry.context.isNotEmpty()) {
                append(" | Context: ${entry.context}")
            }
            
            // Add memory info for error and warning logs
            if (entry.level >= Log.WARN) {
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                append(" | Memory: ${usedMemory / 1024 / 1024}MB used")
            }
        }
        
        Log.d("${entry.tag}_DEBUG", debugInfo)
    }
    
    /**
     * Get logging statistics for monitoring and debugging.
     */
    fun getLoggingStatistics(): LoggingStatistics {
        val entries = logHistory.toList()
        val now = System.currentTimeMillis()
        val recentEntries = entries.filter { now - it.timestamp < 300_000 } // Last 5 minutes
        
        return LoggingStatistics(
            totalEntries = entries.size,
            recentEntries = recentEntries.size,
            entriesByLevel = entries.groupBy { it.level }.mapValues { it.value.size },
            entriesByTag = entries.groupBy { it.tag }.mapValues { it.value.size },
            errorCount = entries.count { it.level >= Log.ERROR },
            warningCount = entries.count { it.level == Log.WARN }
        )
    }
    
    /**
     * Export log history as formatted text for debugging.
     */
    fun exportLogHistory(): String {
        return buildString {
            appendLine("=== Menu System Log History ===")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Debug Mode: $debugMode")
            appendLine("Total Entries: ${logHistory.size}")
            appendLine()
            
            logHistory.forEach { entry ->
                appendLine("${entry.getFormattedTimestamp()} [${entry.getLevelName()}] ${entry.tag}: ${entry.message}")
                if (entry.context.isNotEmpty()) {
                    appendLine("  Context: ${entry.context}")
                }
                if (entry.throwable != null) {
                    appendLine("  Exception: ${entry.throwable.message}")
                }
                appendLine("  Thread: ${entry.threadName}")
                appendLine()
            }
        }
    }
}

/**
 * Statistics about logging activity for monitoring and debugging.
 */
data class LoggingStatistics(
    val totalEntries: Int,
    val recentEntries: Int,
    val entriesByLevel: Map<Int, Int>,
    val entriesByTag: Map<String, Int>,
    val errorCount: Int,
    val warningCount: Int
)

/**
 * Utility functions for common logging patterns.
 */
object LoggingUtils {
    
    fun createPerformanceContext(operationName: String, startTime: Long): Map<String, Any> {
        return mapOf(
            "operation" to operationName,
            "duration_ms" to (System.currentTimeMillis() - startTime),
            "thread" to Thread.currentThread().name
        )
    }
    
    fun createErrorContext(error: Throwable, additionalInfo: Map<String, Any> = emptyMap()): Map<String, Any> {
        return mapOf(
            "error_type" to (error::class.simpleName ?: "Unknown"),
            "error_message" to (error.message ?: "Unknown error"),
            "stack_trace" to error.stackTrace.take(5).joinToString(" | ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        ) + additionalInfo
    }
    
    fun createDataContext(dataType: String, dataSize: Int, isValid: Boolean): Map<String, Any> {
        return mapOf(
            "data_type" to dataType,
            "data_size" to dataSize,
            "is_valid" to isValid,
            "timestamp" to System.currentTimeMillis()
        )
    }
    
    fun createResourceContext(resourceType: String, resourceCount: Int, memoryUsage: Long): Map<String, Any> {
        return mapOf(
            "resource_type" to resourceType,
            "resource_count" to resourceCount,
            "memory_usage_mb" to (memoryUsage / 1024 / 1024),
            "available_memory_mb" to (Runtime.getRuntime().freeMemory() / 1024 / 1024)
        )
    }
}