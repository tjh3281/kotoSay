package com.kotosay.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.kotosay.app.emotion.EmotionClassifier
import com.kotosay.app.emotion.EmotionTracker
import com.kotosay.app.ml.SignPredictor
import com.kotosay.app.service.GeminiClient
import com.kotosay.app.service.GeminiTranslator
import com.kotosay.app.service.SpeechRouter
import com.kotosay.app.service.LanguagePreference
import com.kotosay.app.service.Language
import com.kotosay.app.service.Translator
import com.kotosay.app.service.TtsService
import com.kotosay.app.ui.BottomTabBar
import com.kotosay.app.ui.CameraScreen

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var signPredictor: SignPredictor? = null
    private var translator: Translator? = null
    private var languagePreference: LanguagePreference? = null
    private var ttsService: TtsService? = null
    private var speechRouter: SpeechRouter? = null
    private var emotionTracker: EmotionTracker? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            initAndShow()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            initAndShow()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initAndShow() {
        // ── Initialize ML & services ──
        // Expression recognition is an enrichment, so it is built first and
        // separately: if the ONNX model fails to load, the tracker stays null
        // and the app runs exactly as it did before the feature existed.
        emotionTracker = try {
            EmotionTracker(EmotionClassifier(this))
        } catch (e: Exception) {
            Log.w(TAG, "Emotion model unavailable, continuing without it: ${e.message}")
            null
        }

        try {
            signPredictor = SignPredictor(this, emotionTracker)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init SignPredictor", e)
            Toast.makeText(this, "Model load failed: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // Translator (optional — needs a Gemini key in local.properties).
        translator = if (GeminiClient.isConfigured) {
            GeminiTranslator()
        } else {
            Log.w(TAG, "No GEMINI_API_KEY — translate feature disabled")
            null
        }

        val tts = TtsService(this)
        ttsService = tts
        // The UI speaks only through the router, which prefers the expressive
        // cloud voice and silently falls back to [tts] when it is unavailable.
        speechRouter = SpeechRouter(tts)
        languagePreference = LanguagePreference(this)

        // ── Compose UI ──
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    val predictor = signPredictor
                    val speech = speechRouter

                    if (predictor != null && speech != null) {
                        val languagePrefs = languagePreference
                        var currentTab by remember { mutableIntStateOf(0) } // 0 = Camera, 1 = 3D Avatar

                        Scaffold(
                            bottomBar = {
                                BottomTabBar(
                                    selected = currentTab,
                                    onSelect = { currentTab = it },
                                )
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    // The bottom bar already covers the system
                                    // navigation bar; consuming it here keeps
                                    // screens' imePadding from lifting by it twice.
                                    .consumeWindowInsets(innerPadding)
                            ) {
                                if (currentTab == 0) {
                                    CameraScreen(
                                        signPredictor = predictor,
                                        translator = translator,
                                        speech = speech,
                                        emotionTracker = emotionTracker,
                                        initialLanguage = languagePrefs?.get() ?: Language.DEFAULT,
                                        onLanguageChange = { languagePrefs?.set(it) }
                                    )
                                } else {
                                    com.kotosay.app.avatar.ui.AvatarPlayerScreen(
                                        initialLanguage = languagePrefs?.get() ?: Language.DEFAULT,
                                        onLanguageChange = { languagePrefs?.set(it) }
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Failed to initialize ML models",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        signPredictor?.close()
        speechRouter?.stop()
        ttsService?.close()
        // Closes the ONNX session and stops the inference thread.
        emotionTracker?.close()
    }
}
