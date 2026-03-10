package com.supaphone.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.supaphone.app.data.BackendConfig
import com.supaphone.app.data.EdgeFunctionsClient
import com.supaphone.app.data.SecureStorage
import com.supaphone.app.diagnostics.AppLog
import com.supaphone.app.ui.components.BrandLogo
import com.supaphone.app.ui.theme.SupaPhoneColors
import com.supaphone.app.ui.theme.SupaPhoneTheme
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val PAIRING_CODE_LENGTH = 6

@Composable
fun PairingScreen(
    onPaired: (deviceId: String, secret: String) -> Unit
) {
    val colors = SupaPhoneTheme.colors
    val dialogActionColor = if (colors.textMain == Color.White) Color.White else MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(1) }
    val tabs = listOf("QR Scan", "Enter Code")

    var codeDigits by remember { mutableStateOf(List(PAIRING_CODE_LENGTH) { "" }) }
    var codeHelper by remember {
        mutableStateOf("Enter the 6-digit code from your browser extension.")
    }
    var codeError by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var qrScannerRestartKey by remember { mutableIntStateOf(0) }
    var showQrRefreshButton by remember { mutableStateOf(false) }
    var cameraPermissionRequestedOnce by remember { mutableStateOf(false) }
    var cameraPermissionPermanentlyDenied by remember { mutableStateOf(false) }
    var showCameraPermissionDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostActivity = context.findActivity()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun refreshCameraPermissionState(trigger: String) {
        val currentlyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        hasCameraPermission = currentlyGranted
        cameraPermissionPermanentlyDenied = !currentlyGranted &&
            cameraPermissionRequestedOnce &&
            hostActivity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(hostActivity, Manifest.permission.CAMERA)

        if (currentlyGranted) {
            showQrRefreshButton = true
            showCameraPermissionDialog = false
        }

        AppLog.i(
            "PAIR_CAMERA_STATE",
            "trigger=$trigger granted=$currentlyGranted blocked=$cameraPermissionPermanentlyDenied"
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionRequestedOnce = true
        hasCameraPermission = granted
        AppLog.i("PAIR_CAMERA_RESULT", if (granted) "granted" else "denied")
        if (granted) {
            qrScannerRestartKey += 1
            showQrRefreshButton = true
            codeError = false
            codeHelper = "Camera access granted. Point camera at the QR code."
            showCameraPermissionDialog = false
            cameraPermissionPermanentlyDenied = false
        }
        if (!granted) {
            showQrRefreshButton = false
            codeError = false
            codeHelper = "Camera permission denied. Use Enter Code to pair."
            cameraPermissionPermanentlyDenied =
                hostActivity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(hostActivity, Manifest.permission.CAMERA)
            showCameraPermissionDialog = selectedTab == 0
            AppLog.w("PAIR_CAMERA_DENIED", "fallback=enter_code")
        }
    }

    fun requestCameraPermissionFromUser(source: String) {
        cameraPermissionRequestedOnce = true
        AppLog.i("PAIR_CAMERA_REQUEST", "source=$source")
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun openAppCameraSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { error ->
                AppLog.e("PAIR_CAMERA_SETTINGS_OPEN_FAIL", "reason=${error.message}", error)
            }
    }

    LaunchedEffect(Unit) {
        AppLog.i("PAIR_SCREEN_OPEN")
    }

    LaunchedEffect(selectedTab) {
        AppLog.d("PAIR_TAB_CHANGED", if (selectedTab == 0) "qr_scan" else "enter_code")
        if (selectedTab == 0 && !hasCameraPermission) {
            refreshCameraPermissionState("qr_tab_selected")
            showCameraPermissionDialog = true
        }
    }

    DisposableEffect(lifecycleOwner, selectedTab) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME || selectedTab != 0) {
                return@LifecycleEventObserver
            }

            refreshCameraPermissionState("resume")
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val enteredDigitCount = codeDigits.count { it.length == 1 }
    val isCodeComplete = enteredDigitCount == PAIRING_CODE_LENGTH
    val codeValue = codeDigits.joinToString("")

    fun fillCodeDigitsFromString(value: String) {
        val digits = value.filter { it.isDigit() }.take(PAIRING_CODE_LENGTH)
        if (digits.length != PAIRING_CODE_LENGTH) return
        codeDigits = digits.map { it.toString() }
        codeError = false
        codeHelper = "Code progress: ${digits.length}/6"
    }

    fun updateCodeDigit(index: Int, value: String) {
        codeDigits = codeDigits.toMutableList().also { it[index] = value }
        codeError = false
        codeHelper = if (codeDigits.all { it.isEmpty() }) {
            "Enter the 6-digit code from your browser extension."
        } else {
            "Code progress: ${codeDigits.count { it.isNotEmpty() }}/6"
        }
    }

    fun submitPairing(codeInput: String, source: String) {
        if (isSubmitting) return

        val normalizedCode = codeInput.filter { it.isDigit() }.take(PAIRING_CODE_LENGTH)
        if (normalizedCode.length != PAIRING_CODE_LENGTH) {
            codeError = true
            codeHelper = "Please enter all 6 digits before connecting."
            return
        }

        if (!BackendConfig.isConfigured()) {
            codeError = true
            codeHelper = "Service configuration missing. Please check setup and try again."
            AppLog.w("PAIR_CODE_SUBMIT_FAIL", "source=$source reason=backend_not_configured")
            return
        }

        val phoneClientId = SecureStorage.getOrCreateClientInstanceId(context)
        val phoneClientSecret = SecureStorage.getOrCreateClientAuthSecret(context)
        val phoneLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        isSubmitting = true
        codeError = false
        codeHelper = if (source == "qr") "QR detected. Connecting..." else "Connecting..."
        AppLog.i("PAIR_CODE_SUBMIT_START", "digits=6 source=$source")

        scope.launch {
            val result = EdgeFunctionsClient.completePairing(
                code = normalizedCode,
                phoneClientId = phoneClientId,
                phoneClientSecret = phoneClientSecret,
                phoneLabel = phoneLabel
            )
            isSubmitting = false

            if (result.ok) {
                codeError = false
                codeHelper = "Pairing successful."
                AppLog.i("PAIR_CODE_SUBMIT_OK", "client=$phoneClientId source=$source")
                onPaired(phoneClientId, result.pairLinkId ?: "paired")
            } else {
                codeError = true
                codeHelper = result.error ?: "Pairing failed. Please try again."
                AppLog.w("PAIR_CODE_SUBMIT_FAIL", "source=$source reason=${result.error ?: "unknown"}")
                if (source == "qr") {
                    selectedTab = 1
                }
            }
        }
    }

    if (showCameraPermissionDialog && selectedTab == 0 && !hasCameraPermission) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionDialog = false },
            title = {
                Text("Camera Access Needed")
            },
            text = {
                Text(
                    if (cameraPermissionPermanentlyDenied) {
                        "Camera access is required to scan QR for pairing. Permission is currently blocked. Open settings to enable camera permission, or pair using the 6-digit code."
                    } else {
                        "Camera access is required to scan QR for pairing. Allow camera access to continue, or pair using the 6-digit code."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCameraPermissionDialog = false
                        if (cameraPermissionPermanentlyDenied) {
                            openAppCameraSettings()
                        } else {
                            requestCameraPermissionFromUser("qr_dialog")
                        }
                    }
                ) {
                    Text(
                        text = if (cameraPermissionPermanentlyDenied) "Open Settings" else "Allow Camera",
                        color = dialogActionColor
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCameraPermissionDialog = false
                        selectedTab = 1
                        codeError = false
                        codeHelper = "Enter the 6-digit code from your browser extension."
                    }
                ) {
                    Text(
                        text = "Use 6-digit Code",
                        color = dialogActionColor
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(colors.bgSurface, colors.bgBase)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        BrandLogo(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(56.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Connect your browser extension",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMain,
        )

        Spacer(Modifier.height(32.dp))

        val selectorContainerShape = RoundedCornerShape(12.dp)
        val selectorItemShape = RoundedCornerShape(9.dp)
        val selectorInnerPadding = 4.dp
        val selectorItemSpacing = 4.dp
        val selectorItemHorizontalPadding = 18.dp
        val selectorItemVerticalPadding = 8.dp

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = selectorContainerShape,
            color = colors.bgElevated,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(selectorInnerPadding),
                horizontalArrangement = Arrangement.spacedBy(selectorItemSpacing)
            ) {
                tabs.forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        shape = selectorItemShape,
                        color = if (isSelected) colors.primary else Color.Transparent,
                        shadowElevation = 0.dp,
                        onClick = {
                            selectedTab = index
                            if (index == 0 && !hasCameraPermission) {
                                refreshCameraPermissionState("qr_tab_click")
                                showCameraPermissionDialog = true
                            }
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = selectorItemHorizontalPadding,
                                vertical = selectorItemVerticalPadding
                            ),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (index == 0) Icons.Default.QrCodeScanner else Icons.Default.Dialpad,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isSelected) Color.White else colors.textMain,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) Color.White else colors.textMain,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (selectedTab == 0) {
                QrScannerContent(
                    colors = colors,
                    hasCameraPermission = hasCameraPermission,
                    onRequestCameraPermission = {
                        if (cameraPermissionPermanentlyDenied) {
                            openAppCameraSettings()
                        } else {
                            requestCameraPermissionFromUser("qr_content")
                        }
                    },
                    cameraPermissionPermanentlyDenied = cameraPermissionPermanentlyDenied,
                    onUseCodePairing = { selectedTab = 1 },
                    isSubmitting = isSubmitting,
                    showRefreshButton = showQrRefreshButton,
                    onRefreshScanner = {
                        qrScannerRestartKey += 1
                        codeError = false
                        codeHelper = "Scanner refreshed. Point camera at QR code."
                        AppLog.i("PAIR_QR_REFRESH", "source=manual_button")
                    },
                    onCodeDetected = { scannedCode ->
                        AppLog.i("PAIR_QR_CODE_DETECTED", "digits=6")
                        fillCodeDigitsFromString(scannedCode)
                        submitPairing(scannedCode, source = "qr")
                    },
                    scannerRestartKey = qrScannerRestartKey
                )
            } else {
                CodeEntryContent(
                    colors = colors,
                    codeDigits = codeDigits,
                    helperMessage = codeHelper,
                    helperIsError = codeError,
                    onCodeDigitChange = ::updateCodeDigit,
                )
            }
        }

        Button(
            onClick = {
                if (selectedTab == 0) {
                    selectedTab = 1
                    codeError = false
                    codeHelper = "Enter the 6-digit code from your browser extension."
                    return@Button
                }
                submitPairing(codeValue, source = "manual")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = Color.White,
                disabledContainerColor = colors.bgSurfaceHover,
                disabledContentColor = Color.White
            ),
            enabled = if (selectedTab == 0) !isSubmitting else isCodeComplete && !isSubmitting,
        ) {
            if (selectedTab != 0) {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = if (isSubmitting) "Connecting..." else if (selectedTab == 0) "Enter Code Instead" else "Connect with Code",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun QrScannerContent(
    colors: SupaPhoneColors,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    cameraPermissionPermanentlyDenied: Boolean,
    onUseCodePairing: () -> Unit,
    isSubmitting: Boolean,
    showRefreshButton: Boolean,
    onRefreshScanner: () -> Unit,
    onCodeDetected: (String) -> Unit,
    scannerRestartKey: Int
) {
    if (!hasCameraPermission) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = colors.textMuted
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Camera access is needed to scan QR.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMain,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (cameraPermissionPermanentlyDenied) {
                    "Camera permission is blocked. Open settings to enable camera, or pair with the 6-digit code instead."
                } else {
                    "If you deny permission, pair with the 6-digit code instead."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRequestCameraPermission,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White
                )
            ) {
                Text(if (cameraPermissionPermanentlyDenied) "Open Settings" else "Allow Camera")
            }
            TextButton(onClick = onUseCodePairing) {
                Text("Enter Code Instead", color = colors.textMain)
            }
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scannerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "laser",
    )
    var lastDetectedCode by remember { mutableStateOf("") }
    var lastDetectedAtMs by remember { mutableLongStateOf(0L) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.fillMaxSize()) {
                QrCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    enabled = !isSubmitting,
                    restartKey = scannerRestartKey,
                    onRawQrValue = { raw ->
                        val code = extractPairCodeFromQr(raw) ?: return@QrCameraPreview
                        val now = System.currentTimeMillis()
                        val isDuplicate = code == lastDetectedCode && now - lastDetectedAtMs < 1800L
                        if (isDuplicate || isSubmitting) {
                            return@QrCameraPreview
                        }
                        lastDetectedCode = code
                        lastDetectedAtMs = now
                        onCodeDetected(code)
                    }
                )
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .size(40.dp)
                        .border(
                            width = 4.dp,
                            color = colors.primary,
                            shape = RoundedCornerShape(topStart = 24.dp)
                        )
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(40.dp)
                        .border(
                            width = 4.dp,
                            color = colors.primary,
                            shape = RoundedCornerShape(topEnd = 24.dp)
                        )
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .size(40.dp)
                        .border(
                            width = 4.dp,
                            color = colors.primary,
                            shape = RoundedCornerShape(bottomStart = 24.dp)
                        )
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(40.dp)
                        .border(
                            width = 4.dp,
                            color = colors.primary,
                            shape = RoundedCornerShape(bottomEnd = 24.dp)
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .offset(y = (10 + 220 * scannerOffset).dp)
                        .background(colors.primary)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (isSubmitting) {
                "QR detected. Connecting..."
            } else {
                "Point camera at the QR code\non your browser extension"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        if (showRefreshButton) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRefreshScanner,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text("Refresh")
            }
        }
    }
}

private fun extractPairCodeFromQr(rawValue: String): String? {
    val raw = rawValue.trim()
    if (raw.isEmpty()) {
        return null
    }

    if (raw.startsWith("supaphone://", ignoreCase = true)) {
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        val codeFromParam = uri
            ?.getQueryParameter("code")
            ?.filter { it.isDigit() }
            ?.take(PAIRING_CODE_LENGTH)
        if (codeFromParam != null && codeFromParam.length == PAIRING_CODE_LENGTH) {
            return codeFromParam
        }
    }

    val simplePattern = Regex("^\\d{3}-?\\d{3}$")
    if (simplePattern.matches(raw)) {
        return raw.filter { it.isDigit() }.take(PAIRING_CODE_LENGTH)
    }
    return null
}

@Composable
private fun QrCameraPreview(
    modifier: Modifier,
    enabled: Boolean,
    restartKey: Int,
    onRawQrValue: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    DisposableEffect(lifecycleOwner, enabled, restartKey) {
        if (!enabled) {
            onDispose {
                // no-op
            }
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            val mainExecutor = ContextCompat.getMainExecutor(context)

            val bindCamera = Runnable {
                runCatching {
                    val cameraProvider = providerFuture.get()
                    val preview = Preview.Builder()
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                val rawValue = barcodes
                                    .firstOrNull { !it.rawValue.isNullOrBlank() }
                                    ?.rawValue
                                    ?.trim()
                                    .orEmpty()
                                if (rawValue.isNotEmpty()) {
                                    onRawQrValue(rawValue)
                                }
                            }
                            .addOnFailureListener { error ->
                                AppLog.w("PAIR_QR_SCAN_FAIL", error.message ?: "scan_error")
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                    AppLog.d("PAIR_QR_CAMERA_BOUND")
                }.onFailure { error ->
                    AppLog.e("PAIR_QR_CAMERA_BIND_FAIL", "reason=${error.message}", error)
                }
            }

            providerFuture.addListener(bindCamera, mainExecutor)

            onDispose {
                runCatching {
                    providerFuture.get().unbindAll()
                    AppLog.d("PAIR_QR_CAMERA_UNBOUND")
                }
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun CodeEntryContent(
    colors: SupaPhoneColors,
    codeDigits: List<String>,
    helperMessage: String,
    helperIsError: Boolean,
    onCodeDigitChange: (index: Int, digit: String) -> Unit,
) {
    val focusRequesters = remember { List(PAIRING_CODE_LENGTH) { FocusRequester() } }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val preferredBoxWidth = 48.dp
            val minBoxWidth = 28.dp
            val preferredSpacing = 8.dp
            val minSpacing = 4.dp
            val rowHorizontalPadding = 8.dp
            val availableWidth = maxWidth - rowHorizontalPadding

            val separatorWidth = 10.dp
            val computedBoxWidth = ((availableWidth - separatorWidth - (preferredSpacing * PAIRING_CODE_LENGTH)) / PAIRING_CODE_LENGTH)
                .coerceIn(minBoxWidth, preferredBoxWidth)
            val computedSpacing = ((availableWidth - separatorWidth - (computedBoxWidth * PAIRING_CODE_LENGTH)) / PAIRING_CODE_LENGTH)
                .coerceIn(minSpacing, preferredSpacing)
            val boxHeight = (computedBoxWidth * 1.2f).coerceIn(40.dp, 58.dp)
            val digitFontSize = (computedBoxWidth.value * 0.56f).coerceIn(14f, 24f).sp
            val codeTextStyle = MaterialTheme.typography.titleLarge.copy(
                fontSize = digitFontSize,
                lineHeight = (digitFontSize.value * 1.05f).sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(computedSpacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                codeDigits.forEachIndexed { index, digit ->
                    if (index == 3) {
                        Text(
                            text = "-",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = (digitFontSize.value * 0.9f).sp
                            ),
                            color = colors.textMuted,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    DigitCodeField(
                        value = digit,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isDigit() }
                            val nextDigit = filtered.takeLast(1)

                            onCodeDigitChange(index, nextDigit)

                            if (nextDigit.isNotEmpty() && index < focusRequesters.lastIndex) {
                                focusRequesters[index + 1].requestFocus()
                            }
                            if (nextDigit.isEmpty() && digit.isNotEmpty() && index > 0) {
                                focusRequesters[index - 1].requestFocus()
                            }
                        },
                        colors = colors,
                        modifier = Modifier
                            .width(computedBoxWidth)
                            .height(boxHeight)
                            .focusRequester(focusRequesters[index]),
                        textStyle = codeTextStyle,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = helperMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = if (helperIsError) colors.danger else colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DigitCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    colors: SupaPhoneColors,
    modifier: Modifier = Modifier,
    textStyle: TextStyle,
) {
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = textStyle.copy(color = colors.textMain),
        cursorBrush = SolidColor(colors.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bgElevated, RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = if (isFocused) colors.primary else colors.bgSurfaceHover,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                innerTextField()
            }
        },
    )
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
