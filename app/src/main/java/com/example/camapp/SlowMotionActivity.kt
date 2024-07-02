package com.example.camapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.arthenica.mobileffmpeg.FFmpeg
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class SlowMotionActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var recordVideoIB: ImageButton
    private lateinit var thumbnailImageView: ImageView
    private lateinit var bwFilterIB: ImageButton
    private lateinit var negativeEffectIB: ImageButton
    private lateinit var posterizeEffectIB: ImageButton
    private lateinit var aquaEffectIB: ImageButton
    private lateinit var solarizeEffectIB: ImageButton
    private lateinit var blackboardEffectIB: ImageButton
    private lateinit var grayscaleEffectIB: ImageButton
    private lateinit var vintageEffectIB: ImageButton
    private lateinit var slowMotionIB: ImageButton
    private var cameraDevice: CameraDevice? = null
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private var cameraCaptureSession: CameraCaptureSession? = null
    private lateinit var videoFile: File
    private var isBWFilterApplied = false
    private var isNegativeEffectApplied = false
    private var isPosterizeEffectApplied = false
    private var isAquaEffectApplied = false
    private var isSolarizeEffectApplied = false
    private var isBlackboardEffectApplied = false
    private var isGrayscaleEffectApplied = false
    private var isVintageEffectApplied = false
    private var isSlowMotionEnabled = false

    private val cameraManager: CameraManager by lazy {
        getSystemService(CAMERA_SERVICE) as CameraManager
    }
    private var isRecording = false

    private val cameraDeviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
            Toast.makeText(this@SlowMotionActivity, "Camera error: $error", Toast.LENGTH_LONG).show()
        }
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slow_motion)

        textureView = findViewById(R.id.textureView)
        recordVideoIB = findViewById(R.id.recordVideoIB)
        thumbnailImageView = findViewById(R.id.thumbnailIV)
        bwFilterIB = findViewById(R.id.bwFilterIB)
        negativeEffectIB = findViewById(R.id.negativeEffectIB)
        posterizeEffectIB = findViewById(R.id.posterizeEffectIB)
        aquaEffectIB = findViewById(R.id.aquaEffectIB)
        solarizeEffectIB = findViewById(R.id.solarizeEffectIB)
        blackboardEffectIB = findViewById(R.id.blackboardEffectIB)
        grayscaleEffectIB = findViewById(R.id.grayscaleEffectIB)
        vintageEffectIB = findViewById(R.id.vintageEffectIB)
        slowMotionIB = findViewById(R.id.slowMotionIB)

        textureView.surfaceTextureListener = textureListener

        recordVideoIB.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        thumbnailImageView.setOnClickListener {
            val mediaUri = it.tag as? Uri
            if (mediaUri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(mediaUri, "video/*")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this@SlowMotionActivity, "No app found to view the video", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@SlowMotionActivity, "No video available", Toast.LENGTH_SHORT).show()
            }
        }

        bwFilterIB.setOnClickListener {
            resetFilters()
            isBWFilterApplied = !isBWFilterApplied
            if (isBWFilterApplied) {
                applyBWFilter()
            } else {
                removeFilters()
            }
        }

        negativeEffectIB.setOnClickListener {
            resetFilters()
            isNegativeEffectApplied = !isNegativeEffectApplied
            if (isNegativeEffectApplied) {
                applyNegativeEffect()
            } else {
                removeFilters()
            }
        }

        posterizeEffectIB.setOnClickListener {
            resetFilters()
            isPosterizeEffectApplied = !isPosterizeEffectApplied
            if (isPosterizeEffectApplied) {
                applyPosterizeEffect()
            } else {
                removeFilters()
            }
        }

        aquaEffectIB.setOnClickListener {
            resetFilters()
            isAquaEffectApplied = !isAquaEffectApplied
            if (isAquaEffectApplied) {
                applyAquaEffect()
            } else {
                removeFilters()
            }
        }

        solarizeEffectIB.setOnClickListener {
            resetFilters()
            isSolarizeEffectApplied = !isSolarizeEffectApplied
            if (isSolarizeEffectApplied) {
                applySolarizeEffect()
            } else {
                removeFilters()
            }
        }

        blackboardEffectIB.setOnClickListener {
            resetFilters()
            isBlackboardEffectApplied = !isBlackboardEffectApplied
            if (isBlackboardEffectApplied) {
                applyBlackboardEffect()
            } else {
                removeFilters()
            }
        }

        grayscaleEffectIB.setOnClickListener {
            resetFilters()
            isGrayscaleEffectApplied = !isGrayscaleEffectApplied
            if (isGrayscaleEffectApplied) {
                applyGrayscaleEffect()
            } else {
                removeFilters()
            }
        }

        vintageEffectIB.setOnClickListener {
            resetFilters()
            isVintageEffectApplied = !isVintageEffectApplied
            if (isVintageEffectApplied) {
                applyVintageEffect()
            } else {
                removeFilters()
            }
        }

        slowMotionIB.setOnClickListener {
            isSlowMotionEnabled = !isSlowMotionEnabled
            if (isSlowMotionEnabled) {
                slowMotionIB.setImageResource(R.drawable.ic_slow_motion_active)
                slowMotionIB.setColorFilter(ContextCompat.getColor(this, R.color.yellow))
                Toast.makeText(this, "Slow motion enabled", Toast.LENGTH_SHORT).show()
            } else {
                slowMotionIB.setImageResource(R.drawable.ic_slow_motion)
                slowMotionIB.setColorFilter(ContextCompat.getColor(this, R.color.white))
                Toast.makeText(this, "Slow motion disabled", Toast.LENGTH_SHORT).show()
            }
        }


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), 1)
        }
    }

    private fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, cameraDeviceStateCallback, null)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun startCameraPreview() {
        try {
            val texture = textureView.surfaceTexture
            texture!!.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(texture)
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            applyCurrentFilter()

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    this@SlowMotionActivity.cameraCaptureSession = cameraCaptureSession
                    updatePreview()
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    Toast.makeText(this@SlowMotionActivity, "Configuration change", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun updatePreview() {
        if (cameraDevice == null) return
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        try {
            cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun startRecording() {
        if (cameraDevice == null || !textureView.isAvailable) return

        try {
            closePreviewSession()
            setupMediaRecorder()

            val texture = textureView.surfaceTexture
            texture!!.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(texture)
            val recorderSurface = mediaRecorder.surface

            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.addTarget(recorderSurface)
            applyCurrentFilter()

            cameraDevice!!.createCaptureSession(listOf(surface, recorderSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    updatePreview()
                    runOnUiThread {
                        recordVideoIB.setImageResource(R.drawable.ic_stop)
                        isRecording = true
                        mediaRecorder.start()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@SlowMotionActivity, "Failed", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun setupMediaRecorder() {
        mediaRecorder = MediaRecorder()
        try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                val videoFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "CamApp")
                if (!videoFolder.exists()) {
                    videoFolder.mkdirs()
                }
                val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(System.currentTimeMillis()) + if (isSlowMotionEnabled) "_slow_motion.mp4" else ".mp4"
                videoFile = File(videoFolder, fileName)
                setOutputFile(videoFile.absolutePath)
                setVideoEncodingBitRate(10000000)
                setVideoFrameRate(if (isSlowMotionEnabled) 120 else 30)
                setVideoSize(1920, 1080)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOrientationHint(90)
                prepare()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to prepare MediaRecorder: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            if (isRecording) {
                mediaRecorder.stop()
                isRecording = false
                mediaRecorder.reset()
                recordVideoIB.setImageResource(R.drawable.ic_videocam)
                startCameraPreview()

                if (isSlowMotionEnabled) {
                    val outputVideoFile = File(videoFile.parent, videoFile.nameWithoutExtension + "_slow.mp4")

                    processVideoForSlowMotion(videoFile.absolutePath, outputVideoFile.absolutePath) { processedUri ->
                        setVideoThumbnail(processedUri)
                    }
                } else {
                    val savedUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", videoFile)
                    setVideoThumbnail(savedUri)
                }
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to stop recording: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            mediaRecorder.release()
        }
    }



    private fun closePreviewSession() {
        cameraCaptureSession?.close()
        cameraCaptureSession = null
    }

    private fun setVideoThumbnail(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, uri)
        val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        runOnUiThread {
            thumbnailImageView.setImageBitmap(bitmap)
            thumbnailImageView.tag = uri
        }
    }

    private fun applyBWFilter() {
        if (cameraDevice == null || !textureView.isAvailable) return

        try {
            closePreviewSession()
            val texture = textureView.surfaceTexture
            texture!!.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(texture)

            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    try {
                        cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@SlowMotionActivity, "Configuration failed", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun applyNegativeEffect() {
        if (cameraDevice == null || !textureView.isAvailable) return

        try {
            closePreviewSession()
            val texture = textureView.surfaceTexture
            texture!!.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(texture)

            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    try {
                        cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@SlowMotionActivity, "Configuration failed", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun applyPosterizeEffect() {
        if (cameraDevice == null || !textureView.isAvailable) return

        try {
            closePreviewSession()
            val texture = textureView.surfaceTexture
            texture!!.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(texture)

            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    try {
                        cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@SlowMotionActivity, "Configuration failed", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun applyAquaEffect() {
        if (cameraDevice == null || !textureView.isAvailable) return

        try {
            closePreviewSession()
            val texture = textureView.surfaceTexture
            texture!!.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(texture)

            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_AQUA)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    try {
                        cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@SlowMotionActivity, "Configuration failed", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun applySolarizeEffect() {
        if (cameraDevice == null || !textureView.isAvailable) return

        try {
            closePreviewSession()
            val texture = textureView.surfaceTexture
            texture!!.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(texture)

            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    try {
                        cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@SlowMotionActivity, "Configuration failed", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun applyBlackboardEffect() {
        if (cameraDevice == null || !textureView.isAvailable) return

        try {
            closePreviewSession()
            val texture = textureView.surfaceTexture
            texture!!.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(texture)

            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    try {
                        cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@SlowMotionActivity, "Configuration failed", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun applyGrayscaleEffect() {
        if (cameraDevice == null || !textureView.isAvailable) return

        try {
            closePreviewSession()
            val texture = textureView.surfaceTexture
            texture!!.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(texture)

            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    try {
                        cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@SlowMotionActivity, "Configuration failed", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun applyVintageEffect() {
        if (cameraDevice == null || !textureView.isAvailable) return

        try {
            closePreviewSession()
            val texture = textureView.surfaceTexture
            texture!!.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(texture)

            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_SEPIA)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    try {
                        cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@SlowMotionActivity, "Configuration failed", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun removeFilters() {
        startCameraPreview()
    }

    private fun resetFilters() {
        isBWFilterApplied = false
        isNegativeEffectApplied = false
        isPosterizeEffectApplied = false
        isAquaEffectApplied = false
        isSolarizeEffectApplied = false
        isBlackboardEffectApplied = false
        isGrayscaleEffectApplied = false
        isVintageEffectApplied = false
    }

    private fun applyCurrentFilter() {
        when {
            isBWFilterApplied -> captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO)
            isNegativeEffectApplied -> captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE)
            isPosterizeEffectApplied -> captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE)
            isAquaEffectApplied -> captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_AQUA)
            isSolarizeEffectApplied -> captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE)
            isBlackboardEffectApplied -> captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD)
            isGrayscaleEffectApplied -> captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO)
            isVintageEffectApplied -> captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_SEPIA)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "You need to grant camera permission to use this feature", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        closePreviewSession()
        cameraDevice?.close()
        cameraDevice = null
    }

    override fun onResume() {
        super.onResume()
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = textureListener
        }
    }

    private fun processVideoForSlowMotion(inputPath: String, outputPath: String, callback: (Uri) -> Unit) {
        val command = arrayOf(
            "-i", inputPath,
            "-filter:v", "setpts=4.0*PTS",
            "-an",
            outputPath
        )

        FFmpeg.executeAsync(command) { executionId, returnCode ->
            if (returnCode == 0) {
                runOnUiThread {
                    val processedUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(outputPath))
                    callback(processedUri)
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Failed to process video", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}
