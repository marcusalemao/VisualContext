package io.alema.facecore

/** One face found in a picture: its descriptor (128-d face-api or 512-d ArcFace) and box in pixels. */
class Face(val descriptor: FloatArray, val x: Float, val y: Float, val w: Float, val h: Float, val score: Float)
