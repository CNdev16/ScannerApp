package com.example.scannerapp

import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import coil.load
import com.example.scannerapp.databinding.ActivityMainBinding
import com.example.scannerapp.utils.BitmapUtils
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val openGalleryLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia(), ::onImagePicked)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.buttonOpenGallery.setOnClickListener {
            openGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun onImagePicked(uri: Uri?) {
        if (uri == null) return

        try {
            val `is` = contentResolver.openInputStream(uri) ?: return
            val rotationDegree = ExifInterface(`is`).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val bitmap = BitmapUtils.getBitmapFromUri(contentResolver, uri) ?: return
            val bitmapRotate = BitmapUtils.rotateBitmap(bitmap, exifToDegrees(rotationDegree))

            with(bitmapRotate) {
                run(::displayImageBitmap)
                run(::decodeQrFromBitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun displayImageBitmap(bitmap: Bitmap) {
        binding.imageViewPreview.load(bitmap)
    }

    private fun decodeQrFromBitmap(bitmap: Bitmap) {
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)

            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val luminanceSource = RGBLuminanceSource(width, height, pixels)
            val bBitmap = BinaryBitmap(HybridBinarizer(luminanceSource))
            val reader = QRCodeReader()
            val result = reader.decode(bBitmap)

            binding.textViewResult.text = "QR Result: ${result.text}"
        } catch (e: Exception) {
            e.printStackTrace()
            binding.textViewResult.text = "QR Result: ${e.localizedMessage}"
        }
    }

    private fun exifToDegrees(exifOrientation: Int): Int {
        return when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }
}
