package io.alema.visualcontext

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Where the user is (phone GPS) and the important places nearby (OpenStreetMap via Overpass,
 * the same source as the web prototype). Only coordinates go out, never a picture.
 */
object Where {
    data class Place(val name: String, val kind: String, val lat: Double, val lon: Double, val dist: Int, val bearing: Int)

    @SuppressLint("MissingPermission")
    fun location(ctx: Context): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (_: Exception) { null } }
            .maxByOrNull { it.time }
    }

    fun address(ctx: Context, loc: Location): String? = try {
        @Suppress("DEPRECATION")
        Geocoder(ctx, Locale("pt", "BR")).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let { a ->
            listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(", ").ifEmpty { a.featureName ?: "" } +
                (a.subLocality?.let { " · $it" } ?: "")
        }
    } catch (_: Exception) { null }

    private val MIRRORS = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")

    /** Named points of interest within [radius] m, nearest first. Blocking. */
    fun nearby(loc: Location, radius: Int = 300, max: Int = 8): List<Place> {
        // Named places people talk about: food and drink first (Marcus, 07/11: Rua Avanhandava's famous restaurants were missed),
        // culture, landmarks, shops with a brand. Ask for many, keep the nearest; notable ones (Wikipedia/Wikidata) first.
        val a = "(around:$radius,${loc.latitude},${loc.longitude})"
        val q = """[out:json][timeout:15];(nwr$a["name"]["tourism"];nwr$a["name"]["historic"];nwr$a["name"]["leisure"~"park|garden|stadium"];
            nwr$a["name"]["amenity"~"restaurant|cafe|bar|pub|fast_food|ice_cream|library|theatre|cinema|arts_centre|university|college|hospital|pharmacy|bank|place_of_worship|marketplace|nightclub"];
            nwr$a["name"]["shop"]["brand"];nwr$a["name"]["building"]["wikidata"];);out center 80;""".trimIndent()
        for (m in MIRRORS) {
            try {
                val c = (URL("$m?data=" + URLEncoder.encode(q, "UTF-8")).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 18000 }
                if (c.responseCode !in 200..299) continue
                val els = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("elements") ?: continue
                return (0 until els.length()).mapNotNull { i ->
                    val e = els.optJSONObject(i) ?: return@mapNotNull null
                    val lat = if (e.has("lat")) e.optDouble("lat") else e.optJSONObject("center")?.optDouble("lat") ?: return@mapNotNull null
                    val lon = if (e.has("lon")) e.optDouble("lon") else e.optJSONObject("center")?.optDouble("lon") ?: return@mapNotNull null
                    val t = e.optJSONObject("tags") ?: return@mapNotNull null
                    val name = t.optString("name").ifEmpty { return@mapNotNull null }
                    val kind = listOf("tourism", "amenity", "leisure", "historic", "shop", "building").firstNotNullOfOrNull { k -> t.optString(k).takeIf { it.isNotEmpty() } } ?: "lugar"
                    val r = FloatArray(2); Location.distanceBetween(loc.latitude, loc.longitude, lat, lon, r)
                    val notable = t.has("wikidata") || t.has("wikipedia")
                    Place(name, kind.replace('_', ' '), lat, lon, r[0].toInt() - (if (notable) 150 else 0), ((r[1] + 360) % 360).toInt())
                }.sortedBy { it.dist }.take(max).map { p -> val r = FloatArray(2); Location.distanceBetween(loc.latitude, loc.longitude, p.lat, p.lon, r); p.copy(dist = r[0].toInt()) }
            } catch (_: Exception) { }
        }
        return emptyList()
    }

    fun asJson(ctx: Context): JSONObject {
        val loc = location(ctx) ?: return JSONObject().put("erro", "sem localização ainda; o GPS do celular precisa estar ligado")
        val o = JSONObject().put("endereco", address(ctx, loc) ?: "").put("precisao_m", loc.accuracy.toInt())
        val arr = JSONArray()
        nearby(loc).forEach { arr.put(JSONObject().put("nome", it.name).put("tipo", it.kind).put("distancia_m", it.dist)) }
        return o.put("lugares_perto", arr)
    }
}
