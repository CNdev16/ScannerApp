package com.example.scannerapp.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri

object BitmapUtils {
    fun getBitmapFromUri(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        return try {
            val `is` = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(`is`)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun rotateBitmap(bitmap: Bitmap, rotateDegree: Int): Bitmap {
        return when {
            rotateDegree == 0 -> bitmap
            (rotateDegree % 90) != 0 -> bitmap
            else -> {
                val matrix = Matrix().apply {
                    val degree = if (rotateDegree % 90 == 0) 90 else rotateDegree
                    postRotate(degree.toFloat())
                }

                Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )
            }
        }
    }
}
