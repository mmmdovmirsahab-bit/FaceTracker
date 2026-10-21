package com.facetrack.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.facetrack.camera.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
    )
    private var trackingEnabled = true
    private var faceLostCount = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.CAMERA] == true) startCamera()
        else b.status.text = "Camera permission required. Allow it in Settings and retry."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        cameraExecutor = Executors.newSingleThreadExecutor()
        b.trackingSwitch.setOnCheckedChangeListener { _, checked ->
            trackingEnabled = checked
            b.overlay.visibility = if (checked) View.VISIBLE else View.INVISIBLE
            if (!checked) b.overlay.clearFace()
        }
        b.recordButton.setOnClickListener { if (recording == null) startRecording() else stopRecording() }
        if (hasPermissions()) startCamera() else permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    private fun hasPermissions() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(b.preview.surfaceProvider)
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD, QualitySelector.FALLBACK_STRATEGY_LOWER))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy ->
                    val mediaImage = proxy.image
                    if (mediaImage == null) { proxy.close(); return@setAnalyzer }
                    val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                    detector.process(image)
                        .addOnSuccessListener(cameraExecutor) { faces ->
                            if (trackingEnabled && faces.isNotEmpty()) {
                                faceLostCount = 0
                                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
                                b.overlay.post {
                                    b.overlay.setFace(face.boundingBox, proxy.width, proxy.height, proxy.imageInfo.rotationDegrees)
                                    b.status.text = "Face detected · tracking locally"
                                }
                            } else {
                                faceLostCount++
                                if (faceLostCount > 5) b.overlay.post {
                                    b.overlay.clearFace()
                                    b.status.text = if (trackingEnabled) "Searching for face" else "Tracking paused"
                                }
                            }
                        }
                        .addOnFailureListener(cameraExecutor) {
                            b.status.post { b.status.text = "Face detection unavailable" }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis, videoCapture)
                b.status.text = "Front camera ready · on-device detection"
            } catch (e: Exception) {
                b.status.text = "Could not start camera: ${e.localizedMessage ?: "unknown error"}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val capture = videoCapture ?: run { b.status.text = "Camera is not ready"; return }
        val name = "FaceTrack_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FaceTrack")
        }
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values).build()
        var pending = capture.output.prepareRecording(this, output)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    b.recordButton.text = "■  Stop recording"
                    b.timer.text = "RECORDING"
                    b.status.text = "Recording to Movies/FaceTrack"
                }
                is VideoRecordEvent.Status -> {
                    val seconds = event.recordingStats.recordedDurationNanos / 1_000_000_000
                    b.timer.text = String.format(Locale.US, "● REC  %02d:%02d", seconds / 60, seconds % 60)
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    b.recordButton.text = "●  Start recording"
                    b.timer.text = "READY"
                    b.status.text = if (!event.hasError()) "Saved to Movies/FaceTrack" else "Recording failed: ${event.error}"
                }
            }
        }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        b.status.text = "Finalizing video…"
    }

    override fun onDestroy() {
        recording?.stop()
        cameraExecutor.shutdown()
        detector.close()
        super.onDestroy()
    }
}
