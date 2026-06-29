package com.example.safeshe2

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class ScreamDetectionActivity : AppCompatActivity() {
    private lateinit var mainVoiceRecognitionSwitch: Switch
    private lateinit var startListeningSwitch: Switch
    private lateinit var statusTextView: TextView
    private lateinit var confidenceTextView: TextView
    private lateinit var alertTextView: TextView
    private lateinit var sensitivitySeekBar: SeekBar
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var tflite: Interpreter? = null
    private var isInterpreterInitialized = false
    private val handler = Handler(Looper.getMainLooper())
    private val recordedAudioData = mutableListOf<Float>()
    private var energyThreshold = 0.1f
    private var zcrThreshold = 0.1f
    private var peakThreshold = 0.41f
    private val SCREAM_CONFIDENCE_THRESHOLD = 0.6f
    private val SCREAM_ENERGY_THRESHOLD = 0.6f
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var isScreamDetected = false
    private var recordingJob: Job? = null
    private var currentAttempt = 0
    private val MAX_ATTEMPTS = 3
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val SAMPLE_RATE = 22050
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 1024
        private const val RECORDING_DURATION_MS = 10000L // 10 seconds
        private const val COOLDOWN_DURATION_MS = 5000L // 5 seconds
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val TAG = "ScreamDetectionActivity"

        // Base values for sensitivity calculation
        private const val BASE_ENERGY_THRESHOLD = 0.1f
        private const val BASE_ZCR_THRESHOLD = 0.1f
        private const val BASE_PEAK_THRESHOLD = 0.41f
        private const val MAX_SENSITIVITY_MULTIPLIER = 2.0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scream_detection)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Initialize views
        mainVoiceRecognitionSwitch = findViewById(R.id.mainVoiceRecognitionSwitch)
        startListeningSwitch = findViewById(R.id.startListeningSwitch)
        statusTextView = findViewById(R.id.statusTextView)
        confidenceTextView = findViewById(R.id.confidenceTextView)
        alertTextView = findViewById(R.id.alertTextView)
        sensitivitySeekBar = findViewById(R.id.sensitivitySeekBar)

        // Initialize TFLite model
        initializeInterpreter()

        // Set up switch listeners
        setupSwitchListeners()

        // Set up red zone listener
        setupRedZoneListener()

        // Set up sensitivity slider
        setupSensitivitySlider()
    }

    private fun setupSensitivitySlider() {
        // Calculate initial progress that matches our default thresholds (50% = default values)
        val initialProgress = calculateProgressFromThresholds()
        sensitivitySeekBar.progress = initialProgress

        sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateThresholdsFromProgress(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun calculateProgressFromThresholds(): Int {
        // Calculate an average progress based on all three thresholds
        val energyProgress = ((energyThreshold / BASE_ENERGY_THRESHOLD) - 0.5f) * 100 / (MAX_SENSITIVITY_MULTIPLIER - 0.5f)
        val zcrProgress = ((zcrThreshold / BASE_ZCR_THRESHOLD) - 0.5f) * 100 / (MAX_SENSITIVITY_MULTIPLIER - 0.5f)
        val peakProgress = ((peakThreshold / BASE_PEAK_THRESHOLD) - 0.5f) * 100 / (MAX_SENSITIVITY_MULTIPLIER - 0.5f)

        // Return average of all three as progress (0-100)
        return ((energyProgress + zcrProgress + peakProgress) / 3).toInt().coerceIn(0, 100)
    }

    private fun updateThresholdsFromProgress(progress: Int) {
        // Convert progress (0-100) to multiplier (0.5-2.0)
        val multiplier = 0.5f + (progress / 100f) * (MAX_SENSITIVITY_MULTIPLIER - 0.5f)

        // Update all thresholds based on multiplier
        energyThreshold = BASE_ENERGY_THRESHOLD * multiplier
        zcrThreshold = BASE_ZCR_THRESHOLD * multiplier
        peakThreshold = BASE_PEAK_THRESHOLD * multiplier

        Log.d(TAG, "Thresholds updated - Energy: $energyThreshold, ZCR: $zcrThreshold, Peak: $peakThreshold")
    }

    private fun setupSwitchListeners() {
        mainVoiceRecognitionSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                statusTextView.text = "System Ready"
                startListeningSwitch.isEnabled = true
                if (!isScreamDetected) {
                    startListeningSwitch.isChecked = true
                    startRecordingCycle()
                }
            } else {
                statusTextView.text = "System Disabled"
                startListeningSwitch.isChecked = false
                startListeningSwitch.isEnabled = false
                stopRecording()
                cancelRecordingJob()
                // Reset scream status when main switch is turned off
                isScreamDetected = false
                updateScreamStatus(false)
            }
        }

        startListeningSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && mainVoiceRecognitionSwitch.isChecked) {
                startRecordingCycle()
            } else {
                stopRecording()
            }
        }
    }

    private fun startRecordingCycle() {
        if (recordingJob?.isActive == true) return

        recordingJob = coroutineScope.launch {
            while (currentAttempt < MAX_ATTEMPTS && mainVoiceRecognitionSwitch.isChecked && !isScreamDetected) {
                currentAttempt++
                statusTextView.text = "Recording attempt $currentAttempt of $MAX_ATTEMPTS"

                // Start recording
                startRecording()
                delay(RECORDING_DURATION_MS)

                // Stop recording and analyze
                stopRecording()
                val hasScream = analyzeRecordedAudio()

                if (hasScream) {
                    isScreamDetected = true
                    updateScreamStatus(true)
                    startListeningSwitch.isChecked = false
                    break
                }

                // Cooldown period
                if (currentAttempt < MAX_ATTEMPTS && !isScreamDetected) {
                    statusTextView.text = "Cooldown period..."
                    startListeningSwitch.isChecked = false
                    delay(COOLDOWN_DURATION_MS)
                    if (mainVoiceRecognitionSwitch.isChecked) {
                        startListeningSwitch.isChecked = true
                    }
                }
            }

            if (!isScreamDetected && currentAttempt >= MAX_ATTEMPTS) {
                // No scream detected in all attempts
                mainVoiceRecognitionSwitch.isChecked = false
                statusTextView.text = "No scream detected in $MAX_ATTEMPTS attempts"
            }

            currentAttempt = 0
        }
    }

    private fun cancelRecordingJob() {
        recordingJob?.cancel()
        recordingJob = null
    }

    private fun analyzeRecordedAudio(): Boolean {
        if (recordedAudioData.isEmpty()) {
            Toast.makeText(this, "No audio recorded", Toast.LENGTH_SHORT).show()
            return false
        }

        val floatData = recordedAudioData.toFloatArray()
        val hasVoice = checkVoiceActivity(floatData)

        if (hasVoice) {
            val features = extractFeatures(floatData)
            val output = runInference(features)

            // Calculate energy and ZCR
            val energy = sqrt(floatData.map { it * it }.average())
            val zeroCrossings = (1 until floatData.size).count { i -> floatData[i] * floatData[i - 1] < 0 }
            val zeroCrossingRate = zeroCrossings.toFloat() / floatData.size
            val peakEnergy = floatData.map { abs(it) }.maxOrNull() ?: 0f

            // Calculate confidence
            val modelConfidence = if (output[0] + output[1] > 0) {
                // Normalize outputs to get probability distribution
                val normalizedScream = output[0] / (output[0] + output[1])
                val normalizedNonScream = output[1] / (output[0] + output[1])
                // Use the probability of scream as confidence
                normalizedScream
            } else {
                0f
            }
            val featureConfidence = if (energy > energyThreshold && zeroCrossingRate > zcrThreshold) 0.7f else 0.3f
            val peakConfidence = if (peakEnergy > peakThreshold) 0.8f else 0.2f
            val combinedConfidence = (modelConfidence + featureConfidence + peakConfidence) / 3

            val confidencePercentage = (combinedConfidence * 100).toInt()

            // Only detect scream if combined confidence is above 60%
            val isScream = combinedConfidence > 0.6f

            // Update UI
            runOnUiThread {
                confidenceTextView.text = "Confidence: $confidencePercentage%"
                alertTextView.text = if (isScream) {
                    "Scream detected!\nEnergy: ${String.format("%.2f", energy)}\nZCR: ${String.format("%.2f", zeroCrossingRate)}\nPeak: ${String.format("%.2f", peakEnergy)}\nModel: ${String.format("%.2f", modelConfidence)}\nCombined: ${String.format("%.2f", combinedConfidence)}"
                } else {
                    "No scream detected\nEnergy: ${String.format("%.2f", energy)}\nZCR: ${String.format("%.2f", zeroCrossingRate)}\nPeak: ${String.format("%.2f", peakEnergy)}\nModel: ${String.format("%.2f", modelConfidence)}\nCombined: ${String.format("%.2f", combinedConfidence)}"
                }
            }

            return isScream
        }

        return false
    }

    private fun checkVoiceActivity(audioData: FloatArray): Boolean {
        val energy = sqrt(audioData.map { it * it }.average())
        val zeroCrossings = (1 until audioData.size).count { i ->
            audioData[i] * audioData[i - 1] < 0
        }
        val zeroCrossingRate = zeroCrossings.toFloat() / audioData.size

        return energy > energyThreshold && zeroCrossingRate > zcrThreshold
    }

    private fun extractFeatures(signal: FloatArray): FloatArray {
        val mfccs = extractMFCCs(signal)
        val spectralContrast = extractSpectralContrast(signal)
        val zeroCrossings = extractZeroCrossingRate(signal)

        return FloatArray(mfccs.size + spectralContrast.size + zeroCrossings.size).also { result ->
            var offset = 0
            mfccs.forEachIndexed { index, value -> result[offset + index] = value }
            offset += mfccs.size
            spectralContrast.forEachIndexed { index, value -> result[offset + index] = value }
            offset += spectralContrast.size
            zeroCrossings.forEachIndexed { index, value -> result[offset + index] = value }
        }
    }

    private fun extractMFCCs(signal: FloatArray): FloatArray {
        val frameSize = 2048
        val hopSize = 512
        val nFrames = (signal.size - frameSize) / hopSize + 1
        val mfccs = FloatArray(13)

        repeat(nFrames) { frameIndex ->
            val frameStart = frameIndex * hopSize
            val frameEnd = frameStart + frameSize
            val frame = signal.slice(frameStart until frameEnd).toFloatArray()

            val windowedFrame = frame.mapIndexed { index, value ->
                value * (0.5f - 0.5f * Math.cos(2.0 * Math.PI * index / (frameSize - 1))).toFloat()
            }.toFloatArray()

            val powerSpectrum = calculatePowerSpectrum(windowedFrame)
            powerSpectrum.forEachIndexed { index, value -> mfccs[index] += value }
        }

        return mfccs.map { it / nFrames }.toFloatArray()
    }

    private fun calculatePowerSpectrum(frame: FloatArray): FloatArray {
        val n = frame.size
        return FloatArray(12) { bandIndex ->
            frame.map { it * it }.sum() / n
        }
    }

    private fun extractSpectralContrast(signal: FloatArray): FloatArray {
        val frameSize = 2048
        val hopSize = 512
        val nFrames = (signal.size - frameSize) / hopSize + 1
        val contrast = FloatArray(6)

        repeat(nFrames) { frameIndex ->
            val frameStart = frameIndex * hopSize
            val frameEnd = frameStart + frameSize
            val frame = signal.slice(frameStart until frameEnd).toFloatArray()

            val powerSpectrum = calculatePowerSpectrum(frame)

            repeat(6) { bandIndex ->
                val start = bandIndex * 2
                val end = start + 2
                if (end <= powerSpectrum.size) {
                    val bandValues = powerSpectrum.slice(start until end)
                    val max = bandValues.maxOrNull() ?: 0f
                    val min = bandValues.minOrNull() ?: 0f
                    contrast[bandIndex] += max - min
                }
            }
        }

        return contrast.map { it / nFrames }.toFloatArray()
    }

    private fun extractZeroCrossingRate(signal: FloatArray): FloatArray {
        val crossings = (1 until signal.size).count { i ->
            signal[i] * signal[i - 1] < 0
        }
        return floatArrayOf(crossings.toFloat() / signal.size)
    }

    private fun runInference(features: FloatArray): FloatArray {
        if (!isInterpreterInitialized) {
            initializeInterpreter()
        }

        if (tflite == null) {
            Log.e(TAG, "TFLite interpreter is null")
            return floatArrayOf(0f, 0f)
        }

        try {
            val inputShape = tflite?.getInputTensor(0)?.shape() ?: intArrayOf(1, 21)
            val inputSize = inputShape.reduce { acc, i -> acc * i }

            val inputBuffer = ByteBuffer.allocateDirect(inputSize * 4)
                .order(ByteOrder.nativeOrder())

            val paddedFeatures = if (features.size < inputSize) {
                FloatArray(inputSize) { i -> if (i < features.size) features[i] else 0f }
            } else {
                features.copyOf(inputSize)
            }

            paddedFeatures.forEach { inputBuffer.putFloat(it) }
            inputBuffer.rewind()

            val outputBuffer = ByteBuffer.allocateDirect(2 * 4)
                .order(ByteOrder.nativeOrder())

            tflite?.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            val output = FloatArray(2)
            output[0] = outputBuffer.float
            output[1] = outputBuffer.float

            val sum = output[0] + output[1]
            if (sum > 0) {
                output[0] /= sum
                output[1] /= sum
            }

            return output
        } catch (e: Exception) {
            Log.e(TAG, "Error running inference: ${e.message}")
            return floatArrayOf(0f, 0f)
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true
        recordedAudioData.clear()
        statusTextView.text = "Listening..."
        confidenceTextView.text = ""
        alertTextView.text = ""

        // Process audio chunks during recording
        handler.post(recordingRunnable)
    }

    private val recordingRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                processAudioChunk()
                handler.postDelayed(this, 100) // Process every 100ms
            }
        }
    }

    private fun processAudioChunk() {
        val audioData = ShortArray(BUFFER_SIZE)
        val readSize = audioRecord?.read(audioData, 0, BUFFER_SIZE) ?: 0

        if (readSize > 0) {
            val floatData = FloatArray(readSize)
            for (i in 0 until readSize) {
                floatData[i] = audioData[i].toFloat() / Short.MAX_VALUE
            }
            recordedAudioData.addAll(floatData.toList())
        }
    }

    private fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        handler.removeCallbacks(recordingRunnable)

        // Analyze the recorded audio
        analyzeRecordedAudio()

        startListeningSwitch.isChecked = false
        statusTextView.text = "Stopped"
    }

    private fun updateScreamStatus(isScream: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.reference.child("users").child(userId)

        userRef.child("IsScream").setValue(isScream)
            .addOnSuccessListener {
                Log.d(TAG, "Scream status updated: $isScream")
                if (isScream) {
                    // Update emergency status in CommunityActivity
                    userRef.child("IsInPanic").setValue(true)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update scream status: ${e.message}")
            }
    }

    private fun initializeInterpreter() {
        if (!isInterpreterInitialized) {
            try {
                tflite = Interpreter(loadModelFile())
                isInterpreterInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing TFLite interpreter: ${e.message}")
                Toast.makeText(this, "Error loading TFLite model: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assets.openFd("scream_model2.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun setupRedZoneListener() {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.reference.child("users").child(userId)

        userRef.child("IsInRedZone").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isInRedZone = snapshot.getValue(Boolean::class.java) ?: false

                if (isInRedZone) {
                    // Automatically turn on voice recognition when in red zone
                    runOnUiThread {
                        if (!mainVoiceRecognitionSwitch.isChecked) {
                            mainVoiceRecognitionSwitch.isChecked = true
                            Toast.makeText(
                                this@ScreamDetectionActivity,
                                "Voice recognition activated due to red zone entry",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error reading red zone status: ${error.message}")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelRecordingJob()
        stopRecording()
        try {
            tflite?.close()
            tflite = null
            isInterpreterInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TFLite interpreter: ${e.message}")
        }
        coroutineScope.cancel()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startRecording()
                } else {
                    Toast.makeText(this, "Audio recording permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}