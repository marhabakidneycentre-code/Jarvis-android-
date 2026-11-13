package com.jarvis.assistant

import android.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private lateinit var listenButton: Button
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        status.text = if (results[Manifest.permission.RECORD_AUDIO] == true) {
            "Permissions set. Tap Listen, then say Jarvis and a command."
        } else {
            "Microphone was denied. Voice control needs it."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        status = TextView(this).apply {
            text = "Jarvis needs these permissions before it can start."
            textSize = 18f
        }
        val allowButton = Button(this).apply {
            text = "Allow and continue"
            setOnClickListener { askPermissions() }
        }
        listenButton = Button(this).apply {
            text = "Listen"
            setOnClickListener { toggleListening() }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(status)
            addView(allowButton)
            addView(listenButton)
        })
    }

    override fun onInit(code: Int) {
        ttsReady = code == TextToSpeech.SUCCESS
        tts?.language = Locale.US
    }

    private fun askPermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions.launch(needed.toTypedArray())
    }

    private fun toggleListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Allow permissions first."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition is not available on this phone."
            return
        }
        if (listening) {
            recognizer?.stopListening()
            return
        }
        startListening()
    }

    private fun startListening() {
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "Listening..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                finishListening()
                status.text = "Didn't catch that. Tap Listen to try again."
            }
            override fun onResults(results: Bundle?) {
                finishListening()
                val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                handleSpeech(said)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
        }
        listening = true
        listenButton.text = "Stop"
        recognizer?.startListening(intent)
    }

    private fun finishListening() {
        listening = false
        listenButton.text = "Listen"
    }

    private fun handleSpeech(said: String) {
        val lower = said.lowercase().trim()
        if (!lower.startsWith("jarvis")) {
            status.text = "Heard: \"$said\". Start with Jarvis."
            return
        }
        val command = lower.removePrefix("jarvis").trim()
        val reply = when {
            command.isEmpty() -> "Yes?"
            Regex("\\b(hi|hello|hey)\\b").containsMatchIn(command) -> "Hello. How can I help?"
            command.contains("time") -> "It is " + SimpleDateFormat("h:mm a", Locale.US).format(Date()) + "."
            else -> "I don't know that command yet."
        }
        status.text = reply
        speak(reply)
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
