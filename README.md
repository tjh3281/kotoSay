# kotoSay: A Two-Way Sign Language Interpreter in Your Pocket

**kotoSay：ポケットに入る双方向AI手話通訳**

### 🚀 Try it Live

[![Download APK](https://img.shields.io/badge/Download_APK_v1.0.0-kotoSay-2ea44f?style=for-the-badge&logo=android)](https://github.com/tjh3281/kotoSay/releases/download/v1.0.0-on-publish/kotoSay_v1.0.0.apk)
[![Repository](https://img.shields.io/badge/GitHub-tjh3281%2FkotoSay-181717?style=for-the-badge&logo=github)](https://github.com/tjh3281/kotoSay)

_(Note: Ensure you allow "Install from unknown sources" on your Android device to install the prototype)._

## 1. Repository Overview & Team Introduction

Deaf people sign to a phone; kotoSay translates sign and emotion into voice and a 3D avatar.

スマートフォンで使える双方向AI手話通訳アプリ。日本手話（JSL）とマレーシア手話（MSL）を読み取って音声にし、相手の返事は3Dアバターが手話で返すので、ろう者が第一言語のまま働き、学び、助けを求められます。

This repository contains the complete Android Studio project — native Kotlin UI, MediaPipe vision extractors, LiteRT inference pipeline, Gemini translation layer and the 3D signing avatar.

**Team Name:** sudo rm -rf /

Our team unites experts of various domains. Sign linguistics expert **Shawn** architects sign grammar and 3D avatars, while ML engineer **Zayn** models our core AI. Software engineer **Jason** deploys these innovations into a robust MVP. Driven by user impact, **Zoey** serves as our community liaison to connect directly with target users, as **Edmund** guides business strategy to anchor revenue and key partnerships.

---

## 2. Project Overview

### Problem Statement

Sign language has long been denied, mocked as _te-mane_ ("monkey imitation"). Sign language is the Deaf's first and only native language. Instead of providing translation, a bank refused to let a Deaf customer open an account alone; Yokohama police refused to take a statement from a Deaf person. We interviewed Deaf taxi driver Mr. Ong, who was assaulted when he was signing. He was intimidated into dropping his report after the police refused to provide an interpreter.

### SDG Alignment

kotoSay is built to advance the United Nations Sustainable Development Goals:

- **SDG 10 (Reduced Inequalities) - Target 10.2:** Promotes universal social and economic inclusion by giving the Deaf community an independent, real-time voice.
- **SDG 4 (Quality Education) - Targets 4.5 & 4.a:** Empowers Deaf students to advocate for themselves and participate in inclusive learning environments.
- **SDG 8 (Decent Work & Economic Growth) - Target 8.5:** Breaks workplace communication barriers, allowing seamless idea contribution and productive employment.

### Short Description

kotoSay is an AI interpreter for Japanese and Malaysian Sign Language. Operating fully offline, it decodes signs and facial expressions instantly. Gemini then synthesizes contextualized, emotionally expressive audio in five languages. When hearing individuals reply via text, a lifelike 3D avatar signs back using sign grammar. Addressing critical WFD and WASLI gaps, kotoSay empowers the Deaf community to communicate autonomously in high-stakes settings — from banks to job interviews.

### Validation

We evaluated kotoSay with 8 MSL researchers at UKM, identifying limitations in fast and distant signs. We then refined the model to achieve a 1.5 m functional range during rapid signing. A Deaf worker at the RC Deaf Missions (RCDM) café validated the upgrades, praising its accelerated speed, instant voice, and expanded range. RCDM's co-founder is impressed and expressed strong interest in a long-term co-creation partnership to expand kotoSay into a feasible Relay Interpreting Services platform.

### Revenue Model

Our main plan puts kotoSay on a Deaf user's phone for **¥190 a month** after a two-month free trial. Families and employers can buy it, and Japan's 2.7% disability hiring rule gives employers a reason to.

- **Why pay:** free interpreters must be booked in advance; kotoSay works the moment a conversation starts.
- **Why the price works:** serving even a heavy user costs us under ¥100 a month, about half the price.

### Market Competition

Sign language AI became a real market in 2026 with Google's SL2T. Asia has about 40 million Deaf signers. At ¥190 a month each, that is ¥90 billion a year.

Deaf people need three things: their own sign language, anytime, answered in sign. Each rival misses one:

| Option | What it misses |
|---|---|
| Interpreters | Need booking |
| SoftBank's SureTalk | Needs a paid counter |
| UD Talk | Needs typing |
| Google SL2T | Cannot sign back |
| **kotoSay** | **Meets all three** |

### Growth Strategy

- **Phase 1 (Q3 2027):** Expand vocabulary to 300+ JSL/MSL signs, launch pilots with regional Deaf associations, incorporate and secure institutional growth capital.
- **Phase 2 (Q4 2028):** Launch a pay-as-you-go API, enabling banks, clinics, and taxis to integrate kotoSay natively.
- **Phase 3 (Q1 2030):** Scale regionally by training our core ML pipeline across 11 Asian sign languages.

### Achievements

kotoSay is a production-ready Android app powered by our proprietary 1.4M-parameter sign language ML model. Trained on 39.2k clips, it achieves an ultra-low 3 ms latency. Awarded **1st Runner-Up at Google KitaHack and MUBA 2026**, we are scaling globally via elite international research partnerships.

---

## 3. Key Features

- **Real-Time Vision Tracking:** Uses the standard smartphone camera to extract 3D skeletal data without requiring special gloves, depth cameras, or cloud video processing.
- **Speed-Invariant ML Recognition:** A custom AI model that adapts to any signing speed, successfully recognizing highly compressed, fast motion without dropping frames.
- **Two-Way Communication:** A 3D avatar signs typed replies back to the user from a library of 110 motion clips, so the hearing party can answer rather than only read. The sign sequence and its grammar reasoning are shown in the user's chosen language.
- **Semantic AI Translator:** Overcomes the "Topic-Comment" syntax barrier of sign language by inferring hidden context and restructuring disjointed keywords into grammatical sentences.
- **Expression-Aware Register:** An on-device expression model (8 classes) shifts the translation's tone and the voice's pitch and rate — "sakit" said calmly and "sakit" said in distress should not sound identical.
- **Five-Language Output:** Every translation returns Japanese, Malay, English, Mandarin and Tamil in a single request, so switching the display language afterwards is instant and costs nothing. Japanese is always the headline, with the chosen language on a row beneath it (English by default).
- **Multilingual Text-To-Speech:** Speaks the chosen language, falling back to Japanese and then Malay when a device lacks that voice.

---

## 4. Overview of Technologies Used

### Core Technologies

- **Gemini (3.5 Flash Lite → 3.1 Flash Lite → 3.6 Flash):** Acts as our cloud-based semantic brain, transforming raw sign glosses (e.g., "Market + I + Go") into natural sentences in one call that returns all five languages. Models are tried in that order, and an overloaded model is skipped immediately.
- **Android Studio & Kotlin Native:** The foundation of our architecture, enabling direct access to camera hardware (CameraX) without the bridge-latency of cross-platform frameworks.
- **MediaPipe:** Handles hardware-parallelized skeletal extraction (Pose on GPU, Hands on CPU) of 75 keypoints per frame.
- **LiteRT:** Runs our custom Bidirectional LSTM sign model locally on the CPU (XNNPACK).
- **Android TTS:** The offline native engines used to execute the final audio output.
- **Google Colab:** Our primary environment for model training and evaluating quantitative analytics via Matplotlib.

### Other Supporting Tools

- **Jetpack Compose:** For building a modern, reactive, and overlay-driven UI.
- **ONNX Runtime:** Runs the on-device facial expression classifier.
- **OpenGL ES:** Renders the 3D signing avatar.
- **Matplotlib:** Used extensively during the Colab training phase to plot cross-entropy loss, accuracy curves, and validate EMA smoothing ratios.

---

## 5. Implementation Details & Innovation

### System Architecture

kotoSay utilizes a **Decoupled Edge-Cloud Pipeline**. Heavy visual processing (tracking and sign prediction) happens 100% offline on the Edge, ensuring low latency and total user privacy. The Cloud (Gemini) is triggered only for semantic translation of text payloads (<1KB) — no video ever leaves the device.

The cloud stage is a single Gemini call that returns the sentence in all five languages at once, so they stay mutually consistent. If every model fails, the app falls back to displaying and speaking the detected signs word by word in the chosen language.

### Workflow

1. **Capture:** CameraX captures 640x480 video at up to 60 FPS.
2. **Extract:** MediaPipe hardware parallelism tracks body pose (GPU) and dynamically crops hand regions (CPU).
3. **Normalize:** `FrameNormalizer` applies EMA smoothing and engineered features, expanding 258 raw landmarks into 780 features per frame.
4. **Predict:** The Bidirectional LSTM model evaluates the temporal array to predict one of 98 signs on the CPU.
5. **Refine:** Gemini restructures the buffered sign tokens into a natural sentence in Japanese, Malay, English, Mandarin and Tamil.
6. **Output:** The app displays the sentence — Japanese always, with the chosen language beneath it — and speaks it aloud.

### Model Performance

The V3.1.5 model reaches **91.2% held-out test accuracy** at 1x speed with 1,374,883 parameters. Speed robustness comes from multi-speed temporal downsampling: 1.5x and 2x variants are generated by frame-skipping and hold-padding, so the same sign performed at different tempos maps to the same class.

---

## 6. Challenges Faced

- **Challenge 1: Model Regression & Overheating**
- _Problem:_ We initially used a Transformer model. It was too heavy for mobile, causing overheating and dropping framerates to 10 FPS.
- _Solution:_ We pivoted to a **Bidirectional LSTM with Dot Attention**. Reading the window in both directions also matters linguistically — several signs share an identical opening handshape and diverge only in the final frames, so a forward-only model commits too early. This bumped our framerate to a smooth 35+ FPS.

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

The same key drives translation and the avatar's grammar step.

_Translation works without this — the app falls back to showing and speaking the detected signs word by word._

### 3. Build & Run

```bash
./gradlew assembleDebug
# Or simply open the project in Android Studio and click Run
```

### 4. First Run Instructions

1. Grant camera permissions when prompted.
2. Point the camera at a person signing.
3. Detected words will appear and build in the sentence box.
4. Translation fires automatically after a few seconds of stillness, or tap **Translate** to force it.
5. Use the language chip (**EN / 日 / MS / 中 / த**) to choose the language shown beneath the Japanese. The choice persists across restarts.
6. Tap the play button to hear the sentence in the chosen language.
7. Tap the bin icon to clear the current buffer.
8. Switch to the **Text → Sign** tab to type a reply and watch the 3D avatar sign it.
