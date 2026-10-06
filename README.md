# iSuara: Offline ML Sign Language Interpreter

### 🚀 Try it Live

[![Download APK](https://img.shields.io/badge/Download_APK_V3.1.6-iSuara-2ea44f?style=for-the-badge&logo=android)](https://github.com/HongZhangLim/iSuara/releases/download/v3.1.6/iSuara-v3.1.6.apk)

_(Note: Ensure you allow "Install from unknown sources" on your Android device to install the prototype)._

## 1. Repository Overview & Team Introduction

Welcome to the official repository for **iSuara**, an Edge-AI application built to provide real-time, pocket-sized translation of sign language into Malay, English, Mandarin and Tamil. This repository contains the complete Android Studio project — native Kotlin UI, MediaPipe vision extractors, LiteRT inference pipeline, and the Gemini 3.8 Flash translation layer.

---

## 2. Project Overview

### Problem Statement

In Malaysia, there are **44,000 Deaf and hard-of-hearing individuals**, but only **60 certified interpreters** nationwide. Hiring a human interpreter costs up to RM150/hour and often requires a 3-day wait. As a result, 78% of Deaf individuals never encounter an interpreter when seeking specialized health services, and 70% fear visiting clinics alone due to the risk of being misunderstood. Writing on paper is not a viable substitute because sign language uses a different grammatical structure (Topic-Comment) than spoken Malay, making written text a challenging second language.

A mistranslation in a clinic is not a bad user experience — it is a harm. That constraint drove most of the engineering decisions below.

### SDG Alignment

iSuara is built to advance the United Nations Sustainable Development Goals:

- **SDG 10 (Reduced Inequalities) - Target 10.2:** Promotes universal social and economic inclusion by giving the Deaf community an independent, real-time voice.
- **SDG 4 (Quality Education) - Targets 4.5 & 4.a:** Empowers Deaf students to advocate for themselves and participate in inclusive learning environments.
- **SDG 8 (Decent Work & Economic Growth) - Target 8.5:** Breaks workplace communication barriers, allowing seamless idea contribution and productive employment.

### Short Description

iSuara is a real-time application that bridges the communication gap between the Deaf community and the hearing public. It uses on-device Edge Machine Learning to track 98 signs via the smartphone camera, then sends the detected glosses to Gemini 3.8 Flash, which restructures them into a natural sentence. The result is rendered in Malay, English, Mandarin and Tamil, and spoken aloud in whichever the user selects. A 3D avatar signs replies back, making the exchange a conversation rather than a one-way readout.

---

## 3. Key Features

- **Real-Time Vision Tracking:** Uses the standard smartphone camera to extract 3D skeletal data without requiring special gloves, depth cameras, or cloud video processing.
- **Speed-Invariant ML Recognition:** A custom AI model that adapts to any signing speed, successfully recognizing highly compressed, fast motion without dropping frames.
- **Two-Way Communication:** A 3D avatar signs spoken or typed replies back to the user from a library of 110 motion clips, so the hearing party can answer rather than only read.
- **Semantic AI Translator:** Overcomes the "Topic-Comment" syntax barrier of sign language by inferring hidden context and restructuring disjointed keywords into grammatical Malay.
- **Expression-Aware Register:** An on-device expression model (8 classes) shifts the translation's tone and the voice's pitch and rate — "sakit" said calmly and "sakit" said in distress should not sound identical.
- **Four-Language Output:** Every translation returns Malay, English, Mandarin and Tamil in a single request, so switching the display language afterwards is instant and costs nothing. Non-Malay selections keep the Malay visible and add the chosen language on a row beneath it.
- **Multilingual Text-To-Speech:** Speaks the selected language, falling back to Malay when a device lacks that voice — Mandarin and Tamil voice data is frequently absent on Malaysian retail devices.

---

## 4. Overview of Technologies Used

### Core Technologies

- **Gemini 3.8 Flash:** Acts as our cloud-based semantic brain, transforming raw sign glosses (e.g., "Market + I + Go") into natural sentences (e.g., "Saya pergi ke pasar") in one call that returns all four languages.
- **Android Studio & Kotlin Native:** The foundation of our zero-copy architecture, enabling direct access to camera hardware (CameraX) and the device's GPU without the bridge-latency of cross-platform frameworks.
- **MediaPipe:** Handles hardware-parallelized skeletal extraction (Pose on GPU, Hands on CPU) of 75 keypoints per frame.
- **LiteRT:** Runs our custom quantized BiLSTM model locally, taking only ~1-3ms per inference.
- **Android TTS:** The offline native engines used to execute the final audio output.
- **Google Colab:** Our primary environment for model training and evaluating quantitative analytics via Matplotlib.

### Other Supporting Tools

- **Jetpack Compose:** For building a modern, reactive, and overlay-driven UI.
- **ONNX Runtime:** Runs the on-device facial expression classifier.
- **Matplotlib:** Used extensively during the Colab training phase to plot cross-entropy loss, accuracy curves, and validate EMA smoothing ratios.

---

## 5. Implementation Details & Innovation

### System Architecture

iSuara utilizes a **Decoupled Edge-Cloud Pipeline**. Heavy visual processing (tracking and sign prediction) happens 100% offline on the Edge, ensuring zero-latency and total user privacy. The Cloud (Gemini 3.8 Flash) is triggered only for semantic translation of text payloads (<1KB) — no video ever leaves the device.

The cloud stage is a single Gemini call that returns the sentence in all four languages at once, so they stay mutually consistent. If the call fails, the app falls back to displaying and speaking the raw glosses.

### Workflow

1. **Capture:** CameraX captures 640x480 video at up to 60 FPS.
2. **Extract:** MediaPipe hardware parallelism tracks body pose (GPU) and dynamically crops hand regions (CPU).
3. **Normalize:** `FrameNormalizer` applies EMA smoothing and engineered features, expanding 258 raw landmarks into 780 features per frame.
4. **Predict:** BiLSTM model evaluates the temporal array to predict one of 98 signs natively on the GPU.
5. **Refine:** Gemini restructures the buffered sign tokens into conversational SVO Malay, plus English, Mandarin and Tamil.
6. **Output:** The app displays the sentence — Malay always, with the selected language beneath it — and speaks it aloud.

### Model Performance

The V3.1.5 model reaches **91.2% held-out test accuracy** at 1x speed with 1,374,883 parameters. Speed robustness comes from multi-speed temporal downsampling: 1.5x and 2x variants are generated by frame-skipping and hold-padding, so the same sign performed at different tempos maps to the same class.

---

## 6. Challenges Faced

- **Challenge 1: Model Regression & Overheating**
- _Problem:_ We initially used a Transformer model. It was too heavy for mobile, causing overheating and dropping framerates to 10 FPS.
- _Solution:_ We pivoted to a **Bidirectional LSTM with Dot Attention**. Reading the window in both directions also matters linguistically — several signs share an identical opening handshape and diverge only in the final frames, so a forward-only model commits too early. It runs in ~3ms on the GPU, bumping our framerate to a smooth 35+ FPS.

- **Challenge 2: High Tracking Latency**
- _Problem:_ Real-world conversations were slow because extracting 258 keypoints caused a bottleneck.
- _Solution:_ We implemented **Hardware Parallelism**. We split the MediaPipe workload to run the heavy body PoseLandmarker on the GPU while processing the HandLandmarker simultaneously on the CPU.

- **Challenge 3: Restrictive Usable Range**
- _Problem:_ Users had to stand rigidly within 50cm of the camera for hands to be recognized.
- _Solution:_ We built a **Dynamic Hand-Crop Strategy** combined with shoulder-width normalization. The app uses body wrist coordinates to artificially "zoom in" on the hands, extending our accurate tracking range to 1.5 meters (a 200% increase).

- **Challenge 4: Speed Skewing the Training Distribution**
- _Problem:_ After generating speed variants, 67% of training gradients came from those variants while validation and test only ever saw 1x speed — the model was optimizing for a distribution it was not judged on.
- _Solution:_ **Per-sample weighting** (1x speed at 2.0, variants at 1.0) rebalanced the effective gradient contribution to roughly 50/50, buying speed robustness without sacrificing 1x accuracy.

---

## 7. Installation & Setup

### 1. Prerequisites

- Android Studio Ladybug (2024.2) or newer
- JDK 17+
- Android device with API 26+ (Android 8.0)

### 2. Gemini API Key

Create a `local.properties` file in the project root (it is gitignored) and add your key from [Google AI Studio](https://aistudio.google.com/apikey):

```properties
GEMINI_API_KEY=your-gemini-key
# Optional extra keys, rotated through when one hits its rate limit:
# GEMINI_API_KEY_2=
# GEMINI_API_KEY_3=
```

The same key drives translation, the avatar's grammar step and the expressive voice.

_Translation works without this — the app falls back to showing and speaking the raw detected glosses._

### 3. Build & Run

```bash
./gradlew assembleDebug
# Or simply open the project in Android Studio and click Run
```

### 4. First Run Instructions

1. Grant camera permissions when prompted.
2. Point the camera at a person signing.
3. Detected words will appear and build in the bottom buffer.
4. Translation fires automatically after two seconds of stillness, or tap **Translate** to force it.
5. Use the language chip (**MS / EN / 中 / த**) to switch display and speech language. The choice persists across restarts.
6. Tap **Speak** to hear the sentence in the selected language.
7. Tap the bin icon to clear the current buffer.
