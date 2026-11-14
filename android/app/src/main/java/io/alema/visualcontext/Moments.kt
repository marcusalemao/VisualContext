package io.alema.visualcontext

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Moments going to the memory. Offline (no signal, no token yet) they wait in a small local file and go
 * with the next one. Blocking: call from a worker thread.
 */
object Moments {
    private const val FILE = "momentos-pendentes.jsonl"
    @Synchronized
    fun send(ctx: Context, body: JSONObject) {
        val f = File(ctx.filesDir, FILE)
        val queue = (if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()) + body.toString()
        val left = queue.filter { Backend.post(ctx, JSONObject(it)) == null }
        if (left.isEmpty()) f.delete() else f.writeText(left.takeLast(200).joinToString("\n") + "\n")
    }
}
