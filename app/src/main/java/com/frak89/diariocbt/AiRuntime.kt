package com.frak89.diariocbt

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.runBlocking

/** All calls come from AiEngine's single background thread. No network use during inference. */
object AiRuntime {
    @JvmStatic
    fun generate(context: Context, modelPath: String, prompt: String): String = runBlocking {
        var lastError: Throwable? = null
        for (useGpu in listOf(true, false)) {
            var engine: Engine? = null
            var initialized = false
            try {
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = if (useGpu) Backend.GPU() else Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
                engine = Engine(config)
                engine.initialize()
                initialized = true
                val response = engine.createConversation().use { conversation ->
                    conversation.sendMessage(prompt).toString()
                }
                return@runBlocking response.toString().trim()
            } catch (error: Throwable) {
                lastError = error
            } finally {
                if (initialized) {
                    try { engine?.close() } catch (_: Exception) {}
                }
            }
        }
        throw IllegalStateException("LiteRT-LM GPU and CPU inference unavailable", lastError)
    }
}
