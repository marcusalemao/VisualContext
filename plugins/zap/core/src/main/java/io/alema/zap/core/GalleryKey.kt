package io.alema.zap.core

/**
 * A photo or video the wearer may attach, named so every layer can tell where
 * it came from without asking Android: "g:o:v:123" = from the glasses (o),
 * a video (v), MediaStore id 123. "c" is the phone's own camera roll, "i" a photo.
 */
data class GalleryKey(val fromGlasses: Boolean, val video: Boolean, val id: Long) {
    override fun toString() = "g:${if (fromGlasses) "o" else "c"}:${if (video) "v" else "i"}:$id"

    companion object {
        fun parse(key: String): GalleryKey? {
            val parts = key.split(':')
            if (parts.size != 4 || parts[0] != "g") return null
            val id = parts[3].toLongOrNull() ?: return null
            if (parts[1] !in setOf("o", "c") || parts[2] !in setOf("v", "i")) return null
            return GalleryKey(fromGlasses = parts[1] == "o", video = parts[2] == "v", id = id)
        }

        fun isVideo(key: String?) = key?.let(::parse)?.video == true
    }
}
