package com.fourpixell.facerecognition.registerfacesection


import android.app.Activity
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLES11Ext
import java.nio.ByteBuffer
import java.nio.ByteOrder

//UI
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RegisterFaceScreen(
    scanType: String,
    viewModel: RegisterNewFaceViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saveResult) {
        when (uiState.saveResult) {
            SaveResult.SUCCESS -> {
                snackbarHostState.showSnackbar("Face data saved successfully")
                viewModel.consumeSaveResult()
            }
            SaveResult.FAILURE -> {
                snackbarHostState.showSnackbar("Error: Couldn't save face data")
            }
            null -> {}
        }
    }

    // Requesting camera permission
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (scanType == "register") "Register Face" else "Verify Face",
                        fontSize = 24.sp
                    )
                })
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (cameraPermissionState.status.isGranted) {
                ARCoreFaceView(
                    onFaceDataUpdated = { latestFaceData ->
                        viewModel.onFaceUpdated(latestFaceData)
                    }
                )
            } else {
                Text("Camera permissions are required.",
                    modifier = Modifier.align(Alignment.Center))
            }

            if (scanType == "register") {
                Button(
                    onClick = { viewModel.saveFaceData() },
                    enabled = uiState.isFaceDetected,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                ) {
                    Text("Save Face Data")
                }
            }
        }
    }
}

// AR View
@Composable
fun ARCoreFaceView(
    onFaceDataUpdated: (FloatBuffer?) -> Unit
) {
    //to get access
    val context = LocalContext.current
    //to track when the app starts/ends/pauses
    val lifecycleOwner = LocalLifecycleOwner.current
    //to allow background tasks
    val coroutineScope = rememberCoroutineScope()

    //to store the AR session
    var arSession by remember { mutableStateOf<Session?>(null) }
    //to remember that the app is asked to install ARCore.
    var installRequested by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    //create an object to draw the AR view
    val renderer = remember { ARCoreFaceRenderer(context, onFaceDataUpdated) }

    //Create camera view
    val glSurfaceView = remember { //glSurfaceView is used for 3D graphics
        GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true //keeps graphics when app pauses
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY //update the view continuously
        }
    }

    LaunchedEffect(Unit) {
        if (arSession != null) return@LaunchedEffect // initiates AR session if there's no session
        try {
            val activity = context as? Activity ?: return@LaunchedEffect
            if (ArCoreApk.getInstance().requestInstall(activity, !installRequested) // check ARCore is installed. if not show the user to install.
                == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true
                return@LaunchedEffect
            }

            val session = withContext(Dispatchers.IO) { //create AR session on background
                Session(context, setOf(Session.Feature.FRONT_CAMERA)).apply { // defines the front camera to be used.
                    val filter = CameraConfigFilter(this)
                        .setFacingDirection(CameraConfig.FacingDirection.FRONT) //find the front camera
                    cameraConfig = getSupportedCameraConfigs(filter).first() //takes the first detected front camera
                    configure(Config(this).apply { //set the session for 3D mesh detection
                        augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
                    })
                }
            }
            arSession = session //saves the session
            renderer.setSession(session)

            //Error handling
        } catch (e: Exception) {
            errorMessage = when (e) {
                is UnavailableApkTooOldException -> "Please update ARCore."
                is UnavailableSdkTooOldException -> "Please update this app."
                is UnavailableDeviceNotCompatibleException -> "This device does not support AR."
                is UnavailableUserDeclinedInstallationException -> "ARCore installation is required."
                is CameraNotAvailableException -> "Camera not available. Restart app."
                else -> "Failed to create AR session: ${e.message}"
            }
            Log.e("ARCoreFaceView", "Error creating AR session", e)
        }
    }

    DisposableEffect(lifecycleOwner, arSession) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { //when app resumes starts camera and ar session
                    glSurfaceView.onResume()
                    arSession?.resume()
                }
                Lifecycle.Event.ON_PAUSE -> { //when app pauses stops camera and ar session
                    arSession?.pause()
                    glSurfaceView.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) } //cleans the observer
    }

    DisposableEffect(Unit) {
        onDispose {
            val sessionToClose = arSession
            coroutineScope.launch(Dispatchers.IO) {
                sessionToClose?.close()
                Log.d("ARCoreFaceView", "AR Session closed.")
            }
        }
    } //closes the ar session properly as a background thread

    //Displays the body
    Box(Modifier.fillMaxSize()) {
        if (arSession != null) {
            AndroidView({ glSurfaceView }, modifier = Modifier.fillMaxSize())
        } else {
            val message = errorMessage ?: if (installRequested)
                "Please install ARCore and restart the app."
            else "Initializing AR session..."
            Text(message, modifier = Modifier.align(Alignment.Center))
        }
    }
}

// Renderer
class ARCoreFaceRenderer(
    private val context: Context,
    private val onFaceDataUpdated: (FloatBuffer?) -> Unit
) : GLSurfaceView.Renderer {

    private var session: Session? = null  //stores the ARCore session data
    private val TAG = "ARCoreFaceRenderer" //a label for debugging
    private val backgroundRenderer = BackgroundRenderer() //Shows the camera live video

    fun setSession(session: Session?) {
        this.session = session
    }

    //prepares the background for camera view
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        // get display size
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // call with screen size
        backgroundRenderer.createOnGlThread()

    }

    //when device is rotated to lands
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        session?.setDisplayGeometry(0, width, height)
    }


    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT) //clear the screen
        val currentSession = session ?: return
        try {
            currentSession.setCameraTextureName(backgroundRenderer.textureId) //connects the live camera view to background to display
            val frame = currentSession.update() //updates with the latest frames
            backgroundRenderer.draw(frame) //actually display the video

            val faces = frame.getUpdatedTrackables(AugmentedFace::class.java) //all the faces detected
            val trackingFace = faces.firstOrNull { it.trackingState == TrackingState.TRACKING } //first face that tracked properly

            // Call directly
            onFaceDataUpdated(trackingFace?.meshVertices)

        } catch (_: SessionPausedException) {
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available during onDrawFrame", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during onDrawFrame", e)
        }
    }
}

//Background Renderer
class BackgroundRenderer {
    private val VEX_SHADER = """
        attribute vec4 a_Position;
        attribute vec2 a_TexCoord;
        varying vec2 v_TexCoord;
        void main() {
            v_TexCoord = a_TexCoord;
            gl_Position = a_Position;
        }
    """.trimIndent()

    private val FRAG_SHADER = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 v_TexCoord;
        uniform samplerExternalOES s_Texture;
        void main() {
            gl_FragColor = texture2D(s_Texture, v_TexCoord);
        }
    """.trimIndent()

    private var quadVertices: FloatBuffer? = null
    private var quadTexCoord: FloatBuffer? = null
    private var quadTexCoordTransformed: FloatBuffer? = null
    private var quadProgram = 0
    var textureId = -1
        private set
    private var positionAttrib = 0
    private var texCoordAttrib = 0
    private var textureUniform = 0

    fun createOnGlThread() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)

        val vexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, VEX_SHADER)
        val fragShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, FRAG_SHADER)

        quadProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vexShader)
            GLES20.glAttachShader(it, fragShader)
            GLES20.glLinkProgram(it)
        }
        GLES20.glUseProgram(quadProgram)
        positionAttrib = GLES20.glGetAttribLocation(quadProgram, "a_Position")
        texCoordAttrib = GLES20.glGetAttribLocation(quadProgram, "a_TexCoord")
        textureUniform = GLES20.glGetUniformLocation(quadProgram, "s_Texture")

        // Fullscreen quad (always fits)
        val QUAD_COORDS = floatArrayOf(
            -1.0f, -1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f,
            1.0f, -1.0f, 0.0f,
            1.0f,  1.0f, 0.0f
        )

        // Flipped vertically for correct front camera orientation
        val QUAD_TEXCOORDS = floatArrayOf(
            0f, 0f,   // bottom-left
            0f, 1f,   // top-left
            1f, 0f,   // bottom-right
            1f, 1f    // top-right
        )

        quadVertices = ByteBuffer.allocateDirect(QUAD_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(QUAD_COORDS)
                position(0)
            }

        quadTexCoord = ByteBuffer.allocateDirect(QUAD_TEXCOORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(QUAD_TEXCOORDS)
                position(0)
            }

        quadTexCoordTransformed = ByteBuffer.allocateDirect(QUAD_TEXCOORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(QUAD_TEXCOORDS) // init with defaults
                position(0)
            }
    }

    fun draw(frame: Frame) {
        // Update texture coordinates if display geometry changed
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quadTexCoord, Coordinates2d.TEXTURE_NORMALIZED,
                quadTexCoordTransformed
            )
        }

        // If quadTexCoordTransformed is still empty, fall back to default coords
        val texCoords = if (quadTexCoordTransformed != null && quadTexCoordTransformed!!.limit() > 0) {
            quadTexCoordTransformed
        } else {
            quadTexCoord
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUseProgram(quadProgram)
        GLES20.glUniform1i(textureUniform, 0)

        // Position of the square
        GLES20.glVertexAttribPointer(positionAttrib, 3, GLES20.GL_FLOAT, false, 0, quadVertices)

        // ✅ Always use the corrected texture coordinates
        GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, texCoords)

        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glEnableVertexAttribArray(texCoordAttrib)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDisableVertexAttribArray(texCoordAttrib)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun loadGLShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e("BackgroundRenderer", "Error compiling shader: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
        }
        return shader
    }
}