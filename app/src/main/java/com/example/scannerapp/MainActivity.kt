package com.example.scannerapp

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.scannerapp.analyzer.ZXingAnalyzer
import com.example.scannerapp.databinding.ActivityMainBinding
import com.example.scannerapp.utils.BitmapUtils
import com.google.common.util.concurrent.ListenableFuture
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val TAG = MainActivity::class.java.simpleName

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService

    private val openGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia(), ::onImagePicked)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        setupView()
        startCameraScan()
    }

    private fun setupView() {
        binding.buttonOpenGallery.setOnClickListener {
            openGalleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun startCameraScan() {
        if (ContextCompat.checkSelfPermission(
                this,
                CAMERA_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openCameraScanner()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(CAMERA_PERMISSION),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }


    private fun openCameraScanner() {
        Log.d(TAG, "openCameraScanner: ")
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider?) {
        if (isDestroyed || isFinishing) return

        cameraProvider?.unbindAll()

        val preview = Preview.Builder()
            .build()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(binding.previewView.width, binding.previewView.height))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(cameraExecutor, ZXingAnalyzer() { result ->
            runOnUiThread {
                imageAnalysis.clearAnalyzer()
                cameraProvider?.unbindAll()

                showDialogResult(result) {
                    bindPreview(cameraProvider)
                }
            }
        })
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)
        cameraProvider?.bindToLifecycle(this, cameraSelector, imageAnalysis, preview)
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

    private fun showDialogResult(result: String, action: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle("Result")
            .setMessage("QR Result: $result")
            .setCancelable(false)
            .setNegativeButton("OK") { _, _ ->
                action?.invoke()
            }
            .create()
            .show()
    }

    private fun displayImageBitmap(bitmap: Bitmap) {
        //binding.imageViewPreview.load(bitmap)
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

            showDialogResult(result.text)
        } catch (e: Exception) {
            e.printStackTrace()

            showDialogResult("Error")
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCameraScanner()
            } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    CAMERA_PERMISSION
                )
            ) {
                AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Can't open camera. Please check your permission")
                    .setNegativeButton("Go to setting") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri: Uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivityForResult(intent, CAMERA_PERMISSION_REQUEST_CODE)
                    }
                    .create()
                    .show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openCameraScanner()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
}
