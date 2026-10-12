package com.kotosay.app.avatar.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import com.kotosay.app.avatar.engine.AvatarPoseState
import com.kotosay.app.avatar.engine.KinematicSolver
import com.kotosay.app.avatar.engine.MotionPlayer
import com.kotosay.app.avatar.model.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Draws the signing avatar from primitives: a young East Asian woman with a
 * short layered brown cut, a dark plaid vest over a teal long-sleeved shirt,
 * against a light studio backdrop.
 *
 * Only the look lives here. Every joint position comes from [AvatarPoseState]
 * exactly as before, so signing motion is unaffected by anything in this file.
 */
class AvatarGLRenderer(
    val motionPlayer: MotionPlayer,
    val orbitController: TouchOrbitController = TouchOrbitController()
) : GLSurfaceView.Renderer {

    companion object {
        val COLOR_SKIN = floatArrayOf(0.890f, 0.735f, 0.625f, 1.0f)       // Light warm East Asian tone
        val COLOR_LIPS = floatArrayOf(0.780f, 0.240f, 0.255f, 1.0f)       // Soft red
        val COLOR_EYE_WHITE = floatArrayOf(0.960f, 0.950f, 0.935f, 1.0f)
        val COLOR_IRIS = floatArrayOf(0.170f, 0.095f, 0.060f, 1.0f)       // Dark brown
        val COLOR_LASH = floatArrayOf(0.090f, 0.060f, 0.050f, 1.0f)
        val COLOR_CATCHLIGHT = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)
        val COLOR_BROW = floatArrayOf(0.250f, 0.150f, 0.090f, 1.0f)
        val COLOR_HAIR = floatArrayOf(0.400f, 0.225f, 0.120f, 1.0f)       // Chestnut brown
        val COLOR_HAIR_LIGHT = floatArrayOf(0.520f, 0.305f, 0.165f, 1.0f) // Layer highlights
        val COLOR_SHIRT = floatArrayOf(0.110f, 0.430f, 0.430f, 1.0f)      // Teal
        val COLOR_VEST = floatArrayOf(0.255f, 0.160f, 0.120f, 1.0f)       // Dark brown plaid base
        val COLOR_BUTTON = floatArrayOf(0.100f, 0.070f, 0.055f, 1.0f)
        val COLOR_TROUSERS = floatArrayOf(0.150f, 0.155f, 0.180f, 1.0f)

        // Specular strength per material.
        const val SPEC_MATTE = 0.04f
        const val SPEC_SKIN = 0.14f
        const val SPEC_HAIR = 0.30f
        const val SPEC_WET = 0.55f
    }

    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var programId = 0
    private var uMVPMatrixHandle = 0
    private var uMVMatrixHandle = 0
    private var uColorHandle = 0
    private var uLightPosHandle = 0
    private var uSpecularHandle = 0
    private var uPatternHandle = 0
    private var aPositionHandle = 0
    private var aNormalHandle = 0

    // Mesh primitives
    private lateinit var sphereMesh: MeshData
    private lateinit var cylinderMesh: MeshData
    private lateinit var headMesh: MeshData
    private lateinit var torsoMesh: MeshData
    private lateinit var vNeckMesh: MeshData

    private var lastFrameTimeNanos = 0L

    private val vertexShaderCode = """
        uniform mat4 u_MVPMatrix;
        uniform mat4 u_MVMatrix;
        attribute vec4 a_Position;
        attribute vec3 a_Normal;
        varying vec3 v_Position;
        varying vec3 v_Normal;
        varying vec3 v_Object;

        void main() {
            v_Position = vec3(u_MVMatrix * a_Position);
            v_Normal = normalize(mat3(u_MVMatrix) * a_Normal);
            v_Object = a_Position.xyz;
            gl_Position = u_MVPMatrix * a_Position;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform vec4 u_Color;
        uniform vec3 u_LightPos;
        uniform float u_Specular;
        uniform float u_Pattern;
        varying vec3 v_Position;
        varying vec3 v_Normal;
        varying vec3 v_Object;

        void main() {
            vec3 normal = normalize(v_Normal);
            vec3 lightVector = normalize(u_LightPos - v_Position);
            vec3 viewDir = normalize(-v_Position);

            // Soft key light, wrapped so the terminator does not go harsh.
            float NdotL = dot(normal, lightVector);
            float key = clamp((NdotL + 0.35) / 1.35, 0.0, 1.0);

            // Cool fill from the opposite side.
            vec3 fillDir = normalize(vec3(-1.0, -0.2, 0.9));
            float fill = max(dot(normal, fillDir), 0.0) * 0.16;

            float lighting = 0.42 + key * 0.52 + fill;

            vec3 base = u_Color.rgb;

            // Woven plaid in object space, so it stays on the cloth as the camera moves.
            if (u_Pattern > 0.5) {
                float bandX = step(0.86, fract(v_Object.x * 6.0));
                float bandY = step(0.86, fract(v_Object.y * 11.0));
                float fineX = step(0.95, fract(v_Object.x * 18.0 + 0.5));
                float fineY = step(0.95, fract(v_Object.y * 33.0 + 0.5));
                base *= 1.0 + 0.22 * max(bandX, bandY) - 0.10 * max(fineX, fineY);
            }

            // Blinn-Phong highlight.
            vec3 halfVec = normalize(lightVector + viewDir);
            float spec = pow(max(dot(normal, halfVec), 0.0), 28.0) * u_Specular;

            // Faint warm rim from the backdrop.
            float rim = 1.0 - max(dot(viewDir, normal), 0.0);
            rim = smoothstep(0.65, 1.0, rim) * 0.10;

            vec3 finalColor = base * lighting + vec3(spec) + vec3(1.0, 0.97, 0.94) * rim;
            gl_FragColor = vec4(finalColor, u_Color.a);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.925f, 0.935f, 0.945f, 1.0f) // Light studio backdrop
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        // AvatarMeshBuilder winds its outward faces clockwise; without this the
        // far inner side of every shape is what gets drawn.
        GLES20.glFrontFace(GLES20.GL_CW)

        // Compile shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        programId = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        uMVPMatrixHandle = GLES20.glGetUniformLocation(programId, "u_MVPMatrix")
        uMVMatrixHandle = GLES20.glGetUniformLocation(programId, "u_MVMatrix")
        uColorHandle = GLES20.glGetUniformLocation(programId, "u_Color")
        uLightPosHandle = GLES20.glGetUniformLocation(programId, "u_LightPos")
        uSpecularHandle = GLES20.glGetUniformLocation(programId, "u_Specular")
        uPatternHandle = GLES20.glGetUniformLocation(programId, "u_Pattern")
        aPositionHandle = GLES20.glGetAttribLocation(programId, "a_Position")
        aNormalHandle = GLES20.glGetAttribLocation(programId, "a_Normal")

        // Build geometric primitives
        sphereMesh = AvatarMeshBuilder.createUnitSphere(24, 24)
        cylinderMesh = AvatarMeshBuilder.createUnitCylinder(24)
        headMesh = AvatarMeshBuilder.createMannequinHead(32, 32)
        torsoMesh = AvatarMeshBuilder.createHumanoidTorso(20, 32)
        vNeckMesh = buildVNeckMesh()

        lastFrameTimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / if (height > 0) height.toFloat() else 1f
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val deltaSec = if (lastFrameTimeNanos > 0L) ((nowNanos - lastFrameTimeNanos) / 1_000_000_000.0f).coerceIn(0.001f, 0.1f) else 0.016f
        lastFrameTimeNanos = nowNanos

        val pose = motionPlayer.advanceTime(deltaSec)

        val eye = orbitController.computeEyePosition()
        Matrix.setLookAtM(
            viewMatrix, 0,
            eye[0], eye[1], eye[2],
            orbitController.targetX, orbitController.targetY, orbitController.targetZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        GLES20.glUseProgram(programId)

        val lightPosInModel = floatArrayOf(1.6f, 3.2f, 3.6f, 1.0f)
        val lightPosInEye = FloatArray(4)
        Matrix.multiplyMV(lightPosInEye, 0, viewMatrix, 0, lightPosInModel, 0)
        GLES20.glUniform3f(uLightPosHandle, lightPosInEye[0], lightPosInEye[1], lightPosInEye[2])

        renderAvatar(pose)
    }

    private fun renderAvatar(pose: AvatarPoseState) {
        val h = Vec3(pose.headPosition.x, 1.33f, pose.headPosition.z)

        drawBody()
        drawHead(h)
        drawArm(pose.leftShoulder, pose.leftElbow, pose.leftWrist)
        drawArm(pose.rightShoulder, pose.rightElbow, pose.rightWrist)
        pose.leftHandPoints?.let { drawHand(it) } ?: drawRestHand(pose.leftElbow, pose.leftWrist)
        pose.rightHandPoints?.let { drawHand(it) } ?: drawRestHand(pose.rightElbow, pose.rightWrist)
    }

    // ── Body: teal shirt, plaid vest, trousers ──
    private fun drawBody() {
        // Trousers peeking below the vest hem.
        drawScaled(cylinderMesh, Vec3(0f, 0.53f, 0f), 0.135f, 0.30f, 0.082f, COLOR_TROUSERS, SPEC_MATTE)

        // Teal shirt torso, with a low yoke closing the top.
        drawScaled(torsoMesh, Vec3(0f, 0.90f, 0f), 0.160f, 0.50f, 0.110f, COLOR_SHIRT, SPEC_MATTE)
        drawSphere(Vec3(0f, 1.130f, -0.005f), 1f, COLOR_SHIRT, SPEC_MATTE, 0.160f, 0.030f, 0.095f)

        // Vest over it, a touch larger, running up to the shoulders.
        drawScaled(torsoMesh, Vec3(0f, 0.890f, 0f), 0.166f, 0.48f, 0.116f, COLOR_VEST, SPEC_MATTE, pattern = true)

        // V-neck showing the shirt underneath: a flat wedge just proud of the vest.
        drawScaled(vNeckMesh, Vec3(0f, 1.122f, 0.0995f), 0.046f, 0.078f, 1f, COLOR_SHIRT, SPEC_MATTE)

        // Shirt collar ring around the neck.
        drawCylinderBetween(Vec3(0f, 1.125f, -0.010f), Vec3(0f, 1.170f, -0.010f), 0.054f, COLOR_SHIRT, SPEC_MATTE)

        // Vest buttons.
        for (y in floatArrayOf(0.985f, 0.915f, 0.845f)) {
            drawSphere(Vec3(0f, y, 0.095f), 0.0065f, COLOR_BUTTON, SPEC_WET)
        }

        // Neck.
        drawCylinderBetween(Vec3(0f, 1.13f, -0.012f), Vec3(0f, 1.25f, -0.012f), 0.040f, COLOR_SKIN, SPEC_SKIN)
    }

    // ── Head: face, features, short layered hair ──
    private fun drawHead(h: Vec3) {
        // Skull with a tapered jaw.
        drawScaled(headMesh, h, 0.105f, 0.130f, 0.115f, COLOR_SKIN, SPEC_SKIN)

        // Ears.
        for (side in floatArrayOf(-1f, 1f)) {
            drawSphere(Vec3(h.x + side * 0.102f, h.y - 0.008f, h.z - 0.006f), 1f, COLOR_SKIN, SPEC_SKIN, 0.008f, 0.022f, 0.015f)
        }

        drawEyes(h)

        // Brows: soft and straight.
        for (side in floatArrayOf(-1f, 1f)) {
            drawSphere(Vec3(h.x + side * 0.036f, h.y + 0.034f, h.z + 0.102f), 1f, COLOR_BROW, SPEC_MATTE, 0.020f, 0.0028f, 0.006f)
        }

        // Small, low-bridged nose: a soft ridge and a rounded tip.
        drawSphere(Vec3(h.x, h.y - 0.022f, h.z + 0.104f), 1f, COLOR_SKIN, SPEC_SKIN, 0.0070f, 0.012f, 0.009f)
        drawSphere(Vec3(h.x, h.y - 0.032f, h.z + 0.108f), 0.0065f, COLOR_SKIN, SPEC_SKIN)

        // Lips: thin upper, fuller lower, meeting in a soft line.
        drawSphere(Vec3(h.x, h.y - 0.0615f, h.z + 0.089f), 1f, COLOR_LIPS, SPEC_WET, 0.0140f, 0.0040f, 0.0072f)
        drawSphere(Vec3(h.x, h.y - 0.0665f, h.z + 0.0875f), 1f, COLOR_LIPS, SPEC_WET, 0.0130f, 0.0055f, 0.0080f)

        drawHair(h)
    }

    private fun drawEyes(h: Vec3) {
        for (side in floatArrayOf(-1f, 1f)) {
            val x = h.x + side * 0.034f
            val y = h.y + 0.004f
            // Set forward enough that the skull does not swallow the upper half.
            val z = h.z + 0.1055f
            // Almond-shaped white.
            drawSphere(Vec3(x, y, z), 1f, COLOR_EYE_WHITE, SPEC_WET, 0.0155f, 0.0078f, 0.0082f)
            // Large dark iris.
            drawSphere(Vec3(x, y - 0.0004f, z + 0.0045f), 1f, COLOR_IRIS, SPEC_WET, 0.0068f, 0.0068f, 0.0050f)
            // Catchlight.
            drawSphere(Vec3(x + 0.0026f, y + 0.0024f, z + 0.0095f), 0.0014f, COLOR_CATCHLIGHT, SPEC_MATTE)
            // Fine upper lash line along the top edge.
            drawSphere(Vec3(x, y + 0.0072f, z + 0.0012f), 1f, COLOR_LASH, SPEC_MATTE, 0.0165f, 0.0013f, 0.0075f)
        }
    }

    private fun drawHair(h: Vec3) {
        // Base cap over the crown and back, set back so the face stays clear.
        drawSphere(Vec3(h.x, h.y + 0.030f, h.z - 0.030f), 1f, COLOR_HAIR, SPEC_HAIR, 0.116f, 0.120f, 0.128f)

        // Nape.
        drawSphere(Vec3(h.x, h.y - 0.030f, h.z - 0.058f), 1f, COLOR_HAIR, SPEC_HAIR, 0.098f, 0.075f, 0.065f)

        // Sides over the tops of the ears.
        for (side in floatArrayOf(-1f, 1f)) {
            drawSphere(Vec3(h.x + side * 0.097f, h.y + 0.032f, h.z - 0.008f), 1f, COLOR_HAIR, SPEC_HAIR, 0.022f, 0.046f, 0.048f)
            drawSphere(Vec3(h.x + side * 0.090f, h.y + 0.010f, h.z + 0.030f), 1f, COLOR_HAIR_LIGHT, SPEC_HAIR, 0.016f, 0.032f, 0.020f)
        }

        // Crown volume: one smooth mass so the cut reads as a single shape.
        drawSphere(Vec3(h.x, h.y + 0.085f, h.z - 0.005f), 1f, COLOR_HAIR, SPEC_HAIR, 0.104f, 0.062f, 0.105f)

        // Side-swept fringe: long, flat locks tilted so they fall across the
        // forehead to one side, all ending above the brows.
        // dx, dy, dz, half-sizes x, y, z, tilt in degrees.
        val fringe = arrayOf(
            floatArrayOf(-0.062f, 0.074f, 0.070f, 0.030f, 0.014f, 0.016f, -35f),
            floatArrayOf(-0.034f, 0.068f, 0.090f, 0.038f, 0.013f, 0.015f, -25f),
            floatArrayOf(0.000f, 0.064f, 0.097f, 0.040f, 0.013f, 0.014f, -18f),
            floatArrayOf(0.036f, 0.059f, 0.090f, 0.036f, 0.013f, 0.014f, -12f),
            floatArrayOf(0.064f, 0.054f, 0.072f, 0.026f, 0.015f, 0.016f, -6f),
            floatArrayOf(-0.016f, 0.080f, 0.090f, 0.044f, 0.012f, 0.016f, -22f),
            floatArrayOf(0.024f, 0.075f, 0.092f, 0.040f, 0.012f, 0.015f, -14f),
        )
        fringe.forEachIndexed { i, f ->
            val color = if (i % 2 == 0) COLOR_HAIR else COLOR_HAIR_LIGHT
            drawScaled(
                sphereMesh, Vec3(h.x + f[0], h.y + f[1], h.z + f[2]),
                f[3], f[4], f[5], color, SPEC_HAIR, rotZ = f[6],
            )
        }
    }

    // ── Arms: long teal sleeves ending at the wrist ──
    private fun drawArm(shoulder: Vec3, elbow: Vec3, wrist: Vec3) {
        drawSphere(shoulder, 0.044f, COLOR_SHIRT, SPEC_MATTE)
        drawCylinderBetween(shoulder, elbow, 0.041f, COLOR_SHIRT, SPEC_MATTE)
        drawSphere(elbow, 0.039f, COLOR_SHIRT, SPEC_MATTE)

        // Sleeve stops a little short of the wrist; skin shows beyond the cuff.
        val cuff = elbow + (wrist - elbow) * 0.86f
        drawCylinderBetween(elbow, cuff, 0.035f, COLOR_SHIRT, SPEC_MATTE)
        drawCylinderBetween(cuff, wrist, 0.024f, COLOR_SKIN, SPEC_SKIN)
        drawSphere(wrist, 0.023f, COLOR_SKIN, SPEC_SKIN)
    }

    // ── Relaxed hand for when the motion has no hand data, such as the idle pose ──
    private fun drawRestHand(elbow: Vec3, wrist: Vec3) {
        val dir = (wrist - elbow).normalize()
        val knuckles = wrist + dir * 0.055f
        val tips = wrist + dir * 0.090f
        drawCylinderBetween(wrist, knuckles, 0.020f, COLOR_SKIN, SPEC_SKIN)
        drawSphere(knuckles, 0.020f, COLOR_SKIN, SPEC_SKIN)
        drawCylinderBetween(knuckles, tips, 0.016f, COLOR_SKIN, SPEC_SKIN)
        drawSphere(tips, 0.016f, COLOR_SKIN, SPEC_SKIN)
    }

    // ── Hands: solid palm webbing plus rounded fingers ──
    private fun drawHand(pts: List<Vec3>) {
        if (pts.size < 21) return

        // Size everything off the hand itself so it matches whatever scale the motion uses.
        val palmLength = pts[0].distanceTo(pts[9])
        val fingerR = (palmLength * 0.085f).coerceIn(0.0050f, 0.0095f)
        val palmR = (palmLength * 0.16f).coerceIn(0.0080f, 0.0170f)

        // Palm: thick links between wrist and knuckles, filling the gaps.
        val palmLinks = arrayOf(
            intArrayOf(0, 5), intArrayOf(0, 9), intArrayOf(0, 13), intArrayOf(0, 17),
            intArrayOf(5, 9), intArrayOf(9, 13), intArrayOf(13, 17), intArrayOf(1, 5),
            intArrayOf(1, 9), intArrayOf(5, 13), intArrayOf(9, 17), intArrayOf(0, 1),
        )
        for ((i, j) in palmLinks) {
            drawCylinderBetween(pts[i], pts[j], palmR, COLOR_SKIN, SPEC_SKIN)
        }
        for (i in intArrayOf(0, 1, 5, 9, 13, 17)) {
            drawSphere(pts[i], palmR, COLOR_SKIN, SPEC_SKIN)
        }

        // Fingers, tapering towards the tips.
        KinematicSolver.HAND_BONES.forEach { (i, j) ->
            if (i == 0) return@forEach // Covered by the palm.
            val taper = if (j % 4 == 0) 0.85f else 1f
            drawCylinderBetween(pts[i], pts[j], fingerR * taper, COLOR_SKIN, SPEC_SKIN)
            drawSphere(pts[j], fingerR * taper, COLOR_SKIN, SPEC_SKIN)
        }
    }

    private fun drawScaled(
        mesh: MeshData,
        pos: Vec3,
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float,
        color: FloatArray,
        specular: Float,
        pattern: Boolean = false,
        rotZ: Float = 0f
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, pos.x, pos.y, pos.z)
        if (rotZ != 0f) Matrix.rotateM(modelMatrix, 0, rotZ, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, scaleX, scaleY, scaleZ)
        drawMesh(mesh, color, specular, pattern)
    }

    private fun drawSphere(
        pos: Vec3,
        radius: Float,
        color: FloatArray,
        specular: Float,
        scaleX: Float = 1.0f,
        scaleY: Float = 1.0f,
        scaleZ: Float = 1.0f
    ) {
        drawScaled(sphereMesh, pos, radius * scaleX, radius * scaleY, radius * scaleZ, color, specular)
    }

    private fun drawCylinderBetween(p1: Vec3, p2: Vec3, radius: Float, color: FloatArray, specular: Float) {
        val dist = p1.distanceTo(p2)
        if (dist < 0.001f) return

        val midX = (p1.x + p2.x) * 0.5f
        val midY = (p1.y + p2.y) * 0.5f
        val midZ = (p1.z + p2.z) * 0.5f

        val dir = (p2 - p1).normalize()

        val angleDeg = Math.toDegrees(acos(dir.y.toDouble().coerceIn(-1.0, 1.0))).toFloat()
        val axisX = dir.z
        val axisZ = -dir.x
        val axisLen = sqrt(axisX * axisX + axisZ * axisZ)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, midX, midY, midZ)

        if (axisLen > 0.0001f) {
            Matrix.rotateM(modelMatrix, 0, angleDeg, axisX / axisLen, 0f, axisZ / axisLen)
        } else if (dir.y < 0f) {
            Matrix.rotateM(modelMatrix, 0, 180f, 1f, 0f, 0f)
        }

        Matrix.scaleM(modelMatrix, 0, radius, dist, radius)
        drawMesh(cylinderMesh, color, specular, pattern = false)
    }

    private fun drawMesh(mesh: MeshData, color: FloatArray, specular: Float, pattern: Boolean) {
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0)

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVMatrixHandle, 1, false, mvMatrix, 0)
        GLES20.glUniform4fv(uColorHandle, 1, color, 0)
        GLES20.glUniform1f(uSpecularHandle, specular)
        GLES20.glUniform1f(uPatternHandle, if (pattern) 1f else 0f)

        mesh.vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, mesh.vertexBuffer)
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        mesh.normalBuffer.position(0)
        GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, 0, mesh.normalBuffer)
        GLES20.glEnableVertexAttribArray(aNormalHandle)

        mesh.indexBuffer.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indexBuffer)
    }

    /**
     * A flat downward-pointing triangle facing +Z: top edge from x=-1 to x=1 at
     * y=0, point at y=-1. Wound clockwise to match [GLES20.GL_CW] front faces.
     */
    private fun buildVNeckMesh(): MeshData {
        val vertices = floatArrayOf(-1f, 0f, 0f, 1f, 0f, 0f, 0f, -1f, 0f)
        val normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)
        val indices = shortArrayOf(0, 1, 2)
        val vBuf = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vBuf.put(vertices).position(0)
        val nBuf = ByteBuffer.allocateDirect(normals.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        nBuf.put(normals).position(0)
        val iBuf = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        iBuf.put(indices).position(0)
        return MeshData(vBuf, nBuf, iBuf, indices.size)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
