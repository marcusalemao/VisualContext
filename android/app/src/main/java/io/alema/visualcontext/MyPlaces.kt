package io.alema.visualcontext

import android.content.Context
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject

/**
 * The user's own places ("Casa", "Trabalho", "Pop Vegan"), saved in the account (Marcus, 08/11): GPS indoors drifts to
 * the next street, so a named spot with a radius beats the street address. Cached here; refreshed at each Live start.
 */
object MyPlaces {
    data class P(val id: String, val name: String, val kind: String, val lat: Double, val lng: Double, val radius: Int)
    @Volatile private var list: List<P> = emptyList()
    private var loadedAt = 0L

    fun refresh(ctx: Context, force: Boolean = false) {
        if (!force && System.currentTimeMillis() - loadedAt < 10 * 60_000 && list.isNotEmpty()) return
        val r = Backend.post(ctx, JSONObject().put("action", "places")) ?: return
        val a = runCatching { JSONArray(r) }.getOrNull() ?: return
        list = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { P(it.optString("id"), it.optString("name"), it.optString("kind"), it.optDouble("lat"), it.optDouble("lng"), it.optInt("radius_m", 120)) }
        loadedAt = System.currentTimeMillis()
    }
    fun all() = list
    /** A moment's place as he named it ("Pop Vegan"), with the street after; the street alone when no name fits. */
    fun label(e: org.json.JSONObject): String {
        val addr = e.optString("place")
        if (!e.has("lat") || e.isNull("lat") || !e.has("lng") || e.isNull("lng")) return addr
        val loc = Location("vc").apply { latitude = e.optDouble("lat"); longitude = e.optDouble("lng") }
        val p = runCatching { at(loc) }.getOrNull() ?: return addr
        return if (addr.isBlank() || addr == p.name) p.name else "${p.name} (${addr})"
    }

    /** The saved place this location falls in (radius + the GPS's own uncertainty, up to 60 m). */
    fun at(loc: Location): P? = list.map { p -> val r = FloatArray(1); Location.distanceBetween(loc.latitude, loc.longitude, p.lat, p.lng, r); p to r[0] }
        .filter { (p, d) -> d <= p.radius + minOf(if (loc.hasAccuracy()) loc.accuracy else 30f, 60f) }.minByOrNull { it.second }?.first

    fun save(ctx: Context, name: String, kind: String, loc: Location, address: String?): Boolean {
        val r = Backend.post(ctx, JSONObject().put("action", "save_place").put("name", name).put("kind", kind).put("lat", loc.latitude).put("lng", loc.longitude).put("radius_m", 120).put("address", address))
        if (r != null) refresh(ctx, force = true)
        return r != null
    }
    fun delete(ctx: Context, id: String) { Backend.post(ctx, JSONObject().put("action", "delete_place").put("id", id)); refresh(ctx, force = true) }

    const val TOOL = "marcar_lugar"
    fun tool(): JSONObject = JSONObject().put("name", TOOL)
        .put("description", "Dá nome ao lugar onde o usuário está agora, para a memória: \"aqui é minha casa\", \"estou no trabalho\", \"isso aqui é o Pop Vegan\". Também use com a resposta dele quando você perguntar onde ele está.")
        .put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()
            .put("nome", JSONObject().put("type", "STRING").put("description", "Casa, Trabalho, ou o nome do lugar"))
            .put("tipo", JSONObject().put("type", "STRING").put("enum", JSONArray().put("casa").put("trabalho").put("frequente").put("outro"))))
            .put("required", JSONArray().put("nome").put("tipo")))

    /**
     * When the current spot has no name, a one-line hint for the Live: ask once, after answering, whether he is at home,
     * at work or at one of the named places nearby. Asked at most once a week per spot (~150 m).
     */
    fun askHint(ctx: Context, loc: Location): String? {
        if (at(loc) != null) return null
        val prefs = ctx.getSharedPreferences("visualcontext", Context.MODE_PRIVATE)
        val cell = "%.3f,%.3f".format(java.util.Locale.US, loc.latitude, loc.longitude) // ~110 m
        val asked = prefs.getLong("asked_place_$cell", 0L)
        if (System.currentTimeMillis() - asked < 7 * 86_400_000L) return null
        prefs.edit().putLong("asked_place_$cell", System.currentTimeMillis()).apply()
        val near = try { Where.nearby(loc, 80, 3).map { "${it.name} (${it.kind}, ${it.dist} m)" } } catch (_: Exception) { emptyList() }
        val hasHome = list.any { it.kind == "casa" }; val hasWork = list.any { it.kind == "trabalho" }
        val opts = listOfNotNull(if (!hasHome) "em casa" else null, if (!hasWork) "no trabalho" else null) + near
        return "O lugar onde ele está agora ainda não tem nome na memória" +
            (if (opts.isNotEmpty()) "; opções prováveis: " + opts.joinToString("; ") else "") +
            ". Nesta conversa, depois de responder a primeira coisa que ele pedir, pergunte UMA vez, bem curto, onde ele está (por exemplo \"aliás, você está em casa?\") e chame marcar_lugar com a resposta. Nunca pergunte antes de ele falar. Se ele não quiser dizer, não insista."
    }
}
