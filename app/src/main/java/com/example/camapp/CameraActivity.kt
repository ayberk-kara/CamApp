package com.example.camapp

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.camapp.databinding.ActivityCameraBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraActivity : AppCompatActivity() {

    private val binding: ActivityCameraBinding by lazy {
        ActivityCameraBinding.inflate(layoutInflater)
    }

    private lateinit var imageCapture: ImageCapture
    private lateinit var videoCapture: VideoCapture<Recorder>
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: androidx.camera.core.Camera
    private lateinit var cameraSelector: CameraSelector
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var thumbnailImageView: ImageView
    private lateinit var recordingTimer: Chronometer
    private var isRecording = false
    private var recording: Recording? = null
    private var isNoirFilterEnabled = false
    private var isSlowMotionEnabled = false
    private lateinit var qualitySpinner: Spinner
    private lateinit var textureView: TextureView
    private lateinit var cameraDevice: CameraDevice
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var cameraCaptureSession: CameraCaptureSession

    private val multiplePermissionId = 14
    private val multiplePermissionNameList = if (Build.VERSION.SDK_INT >= 33) {
        arrayListOf(
            Manifest.permission.CAMERA
        )
    } else {
        arrayListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        thumbnailImageView = findViewById(R.id.thumbnailIV)
        recordingTimer = findViewById(R.id.recordingTimer)
        qualitySpinner = findViewById(R.id.qualitySpinner)
        textureView = findViewById(R.id.textureView)

        if (checkMultiplePermission()) {
            startCameraX()
        }

        binding.noirIB.setOnClickListener {
            toggleNoirFilter()
        }

        binding.FlipIB.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            bindCameraUserCases()
        }

        binding.captureIB.setOnClickListener {
            takePhoto()
        }

        binding.FlashIB.setOnClickListener {
            setFlashIcon(camera)
        }

        binding.recordVideoIB.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                if (isSlowMotionEnabled) {
                    startSlowMotionRecording()
                } else {
                    startRecording()
                }
            }
        }



        thumbnailImageView.setOnClickListener {
            val mediaUri = it.tag as? Uri
            if (mediaUri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(mediaUri, if (mediaUri.toString().endsWith(".mp4")) "video/*" else "image/*")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this@CameraActivity, "No app found to view the media", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@CameraActivity, "No media available", Toast.LENGTH_SHORT).show()
            }
        }

        setupQualitySelector()

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                if (isSlowMotionEnabled) {
                    openCamera2()
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        }
    }

    private fun setupQualitySelector() {
        val qualities = arrayOf("UHD", "FHD", "HD")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, qualities)
        qualitySpinner.adapter = adapter
    }

    private fun getSelectedQuality(): Quality {
        return when (qualitySpinner.selectedItem.toString()) {
            "UHD" -> Quality.UHD
            "FHD" -> Quality.FHD
            "HD" -> Quality.HD
            else -> Quality.HD
        }
    }

    private fun toggleNoirFilter() {
        isNoirFilterEnabled = !isNoirFilterEnabled
        val message = if (isNoirFilterEnabled) {
            "Noir filter enabled"
        } else {
            "Noir filter disabled"
        }
        Toast.makeText(this@CameraActivity, message, Toast.LENGTH_SHORT).show()
    }

    private fun toggleSlowMotion() {
        isSlowMotionEnabled = !isSlowMotionEnabled
        val message: String
        if (isSlowMotionEnabled) {
            message = "Slow motion enabled"
            stopCameraX()
            startSlowMotionRecording()
        } else {
            message = "Slow motion disabled"
            stopSlowMotionRecording()
            startCameraX()
        }
        Toast.makeText(this@CameraActivity, message, Toast.LENGTH_SHORT).show()
    }

    private fun checkMultiplePermission(): Boolean {
        val listPermissionNeeded = arrayListOf<String>()
        for (permission in multiplePermissionNameList) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                listPermissionNeeded.add(permission)
            }
        }
        if (listPermissionNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                listPermissionNeeded.toTypedArray(),
                multiplePermissionId
            )
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == multiplePermissionId) {
            if (grantResults.isNotEmpty()) {
                var isGrant = true
                for (element in grantResults) {
                    if (element == PackageManager.PERMISSION_DENIED) {
                        isGrant = false
                    }
                }
                if (isGrant) {
                    startCameraX()
                } else {
                    var someDenied = false
                    for (permission in permissions) {
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                                this,
                                permission
                            )
                        ) {
                            if (ActivityCompat.checkSelfPermission(
                                    this,
                                    permission
                                ) == PackageManager.PERMISSION_DENIED
                            ) {
                                someDenied = true
                            }
                        }
                    }
                    if (someDenied) {
                        appSettingOpen(this)
                    } else {
                        warningPermissionDialog(this) { _: DialogInterface, which: Int ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE ->
                                    checkMultiplePermission()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startCameraX() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUserCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCameraX() {
        if (::cameraProvider.isInitialized) {
            cameraProvider.unbindAll()
        }
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        return if (abs(previewRatio - 4.0 / 3.0) <= abs(previewRatio - 16.0 / 9.0)) {
            AspectRatio.RATIO_4_3
        } else {
            AspectRatio.RATIO_16_9
        }
    }

    private fun bindCameraUserCases() {
        val screenAspectRatio = aspectRatio(
            binding.previewView.width,
            binding.previewView.height
        )
        val rotation = binding.previewView.display.rotation

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    screenAspectRatio,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            ).build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    getSelectedQuality(),
                    FallbackStrategy.higherQualityOrLowerThan(Quality.HD)
                )
            )
            .build()

        videoCapture = VideoCapture.withOutput(recorder)

        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()

            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, videoCapture
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setFlashIcon(camera: androidx.camera.core.Camera) {
        if (camera.cameraInfo.hasFlashUnit()) {
            if (camera.cameraInfo.torchState.value == 0) {
                camera.cameraControl.enableTorch(true)
                binding.FlashIB.setImageResource(R.drawable.flash_off)
            } else {
                camera.cameraControl.enableTorch(false)
                binding.FlashIB.setImageResource(R.drawable.flash_on)
            }
        } else {
            Toast.makeText(
                this@CameraActivity,
                "Flash is unavailable",
                Toast.LENGTH_LONG
            ).show()
            binding.FlashIB.isEnabled = false
        }
    }

    private fun applyNoirFilterToBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val filteredBitmap = Bitmap.createBitmap(width, height, bitmap.config)

        val canvas = Canvas(filteredBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter

        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return filteredBitmap
    }

    private fun getRotatedBitmap(filePath: String): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(filePath)
        var exif: ExifInterface? = null
        try {
            exif = ExifInterface(filePath)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun takePhoto() {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            Toast.makeText(this@CameraActivity, "External storage is not writable", Toast.LENGTH_LONG).show()
            return
        }

        val imageFolder = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM
            ), "CamApp"
        )
        if (!imageFolder.exists()) {
            if (!imageFolder.mkdirs()) {
                Toast.makeText(this@CameraActivity, "Failed to create directory", Toast.LENGTH_LONG).show()
                return
            }
        }

        val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            .format(System.currentTimeMillis()) + ".jpg"
        val imageFile = File(imageFolder, fileName)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = FileProvider.getUriForFile(
                        this@CameraActivity,
                        "${packageName}.fileprovider",
                        imageFile
                    )
                    val message = "Photo has been captured: $savedUri"
                    Toast.makeText(this@CameraActivity, message, Toast.LENGTH_LONG).show()

                    val rotatedBitmap = getRotatedBitmap(imageFile.absolutePath)

                    if (isNoirFilterEnabled && rotatedBitmap != null) {
                        val filteredBitmap = applyNoirFilterToBitmap(rotatedBitmap)

                        saveBitmapToFile(filteredBitmap, imageFile)
                    } else {
                        rotatedBitmap?.let {
                            saveBitmapToFile(it, imageFile)
                        }
                    }

                    thumbnailImageView.setImageURI(savedUri)
                    thumbnailImageView.tag = savedUri
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "Error: ${exception.message}", Toast.LENGTH_LONG).show()
                    exception.printStackTrace()
                }
            }
        )
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
    }

    private fun startRecording() {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            Toast.makeText(this@CameraActivity, "External storage is not writable", Toast.LENGTH_LONG).show()
            return
        }

        val videoFolder = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM
            ), "CamApp"
        )
        if (!videoFolder.exists()) {
            if (!videoFolder.mkdirs()) {
                Toast.makeText(this@CameraActivity, "Failed to create directory", Toast.LENGTH_LONG).show()
                return
            }
        }

        val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            .format(System.currentTimeMillis()) + ".mp4"
        val videoFile = File(videoFolder, fileName)

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), multiplePermissionId)
                Toast.makeText(this@CameraActivity, "Recording audio permission is required for video recording", Toast.LENGTH_LONG).show()
                return
            }

            recording = videoCapture.output
                .prepareRecording(this, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            recordingTimer.base = SystemClock.elapsedRealtime()
                            recordingTimer.start()
                            recordingTimer.visibility = View.VISIBLE
                            isRecording = true
                            binding.recordVideoIB.setImageResource(R.drawable.ic_stop)
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (!recordEvent.hasError()) {
                                val savedUri = FileProvider.getUriForFile(
                                    this@CameraActivity,
                                    "${packageName}.fileprovider",
                                    videoFile
                                )
                                val message = "Video has been saved: $savedUri"
                                Toast.makeText(this@CameraActivity, message, Toast.LENGTH_LONG).show()

                                setVideoThumbnail(savedUri)
                                thumbnailImageView.tag = savedUri
                            } else {
                                Toast.makeText(
                                    this@CameraActivity,
                                    "Video recording error: ${recordEvent.error}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            stopRecording()
                        }
                    }
                }
        } catch (e: SecurityException) {
            Toast.makeText(this@CameraActivity, "Permission denied: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        isRecording = false
        binding.recordVideoIB.setImageResource(R.drawable.ic_videocam)
        recordingTimer.stop()
        recordingTimer.visibility = View.GONE
    }

    private fun startSlowMotionRecording() {
        if (textureView.isAvailable) {
            openCamera2()
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCamera2()
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            }
        }
    }

    private fun stopSlowMotionRecording() {
        try {
            cameraCaptureSession.stopRepeating()
            cameraCaptureSession.abortCaptures()
            cameraDevice.close()
            mediaRecorder.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openCamera2() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCamera2Preview()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    runOnUiThread {
                        Toast.makeText(this@CameraActivity, "Kamera hatası: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }, null)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    private fun startCamera2Preview() {
        val surfaceTexture = textureView.surfaceTexture!!
        surfaceTexture.setDefaultBufferSize(1920, 1080)
        val previewSurface = Surface(surfaceTexture)

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        captureRequestBuilder.addTarget(previewSurface)

        cameraDevice.createCaptureSession(listOf(previewSurface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                cameraCaptureSession = session
                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                startSlowMotionRecordingSession()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, null)
    }

    private fun startSlowMotionRecordingSession() {
        val videoFolder = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM
            ), "CamApp"
        )
        if (!videoFolder.exists()) {
            videoFolder.mkdirs()
        }

        val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            .format(System.currentTimeMillis()) + "_slow_motion.mp4"
        val videoFile = File(videoFolder, fileName)

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(videoFile.absolutePath)
            setVideoEncodingBitRate(10000000)
            setVideoFrameRate(120)
            setVideoSize(1920, 1080)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }

        val recorderSurface = mediaRecorder.surface
        captureRequestBuilder.addTarget(recorderSurface)

        cameraCaptureSession.stopRepeating()
        cameraCaptureSession.abortCaptures()
        cameraDevice.createCaptureSession(
            listOf(Surface(textureView.surfaceTexture), recorderSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    try {
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                        mediaRecorder.start()
                        recordingTimer.base = SystemClock.elapsedRealtime()
                        recordingTimer.start()
                        recordingTimer.visibility = View.VISIBLE
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            null
        )
    }

    private fun setVideoThumbnail(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, uri)
        val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        runOnUiThread {
            thumbnailImageView.setImageBitmap(bitmap)
        }
    }
}
