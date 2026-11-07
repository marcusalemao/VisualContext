package io.alema.visualcontext

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Spec 1n: where a note goes besides the memory. Google Keep and Samsung Notes (the two most used on
 * Android) take a note through "share", with no login; none lets an app write silently, so the phone shows
 * one notification and a tap puts the note in the chosen app. Prescriptions stay out unless turned on.
 */
object NotesOut {
    const val KEEP = "com.google.android.keep"
    const val SAMSUNG = "com.samsung.android.app.notes"
    private const val CH = "notes_out"

    fun installed(ctx: Context, pkg: String) = try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }

    /** "keep" | "samsung" | "ask" | "none". Default: Keep when installed, else Samsung Notes, else ask. */
    fun target(ctx: Context): String = prefs(ctx).getString("notes_app", null)
        ?: if (installed(ctx, KEEP)) "keep" else if (installed(ctx, SAMSUNG)) "samsung" else "ask"
    fun setTarget(ctx: Context, t: String) { prefs(ctx).edit().putString("notes_app", t).apply() }
    fun health(ctx: Context) = prefs(ctx).getBoolean("notes_health", false)
    fun setHealth(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("notes_health", on).apply() }

    fun offer(ctx: Context, title: String, text: String, sensitive: Boolean) {
        val t = target(ctx)
        if (t == "none" || (sensitive && !health(ctx))) return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TITLE, title).putExtra(Intent.EXTRA_TEXT, text)
        val pkg = when (t) { "keep" -> KEEP; "samsung" -> SAMSUNG; else -> null }
        val intent = if (pkg != null && installed(ctx, pkg)) send.setPackage(pkg) else Intent.createChooser(send, "Guardar a nota em…")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val app = when (pkg) { KEEP -> "Google Keep"; SAMSUNG -> "Samsung Notes"; else -> "seu app de notas" }
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH, "Notas para o seu app", NotificationManager.IMPORTANCE_DEFAULT))
            val pi = PendingIntent.getActivity(ctx, title.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(title.hashCode(), Notification.Builder(ctx, CH).setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("Guardado: $title").setContentText("Toque para pôr no $app").setContentIntent(pi).setAutoCancel(true).build())
        } catch (_: SecurityException) { }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE)
}
