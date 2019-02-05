package org.simple.clinic.widgets.qrcodescanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.FrameLayout
import com.budiyev.android.codescanner.AutoFocusMode
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.budiyev.android.codescanner.DecodeCallback
import com.budiyev.android.codescanner.ErrorCallback
import com.budiyev.android.codescanner.ScanMode
import com.google.zxing.BarcodeFormat
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.android.schedulers.AndroidSchedulers.*
import io.reactivex.rxkotlin.ofType
import org.simple.clinic.activity.TheActivity
import org.simple.clinic.router.screen.ActivityPermissionResult
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.util.RuntimePermissions
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.TheActivityLifecycle
import org.simple.clinic.widgets.UiEvent
import timber.log.Timber
import javax.inject.Inject

private const val REQUESTCODE_CAMERA_PERMISSION = 0
private const val CAMERA_PERMISSION = Manifest.permission.CAMERA

class QrCodeScannerView(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {

  @Inject
  lateinit var activity: TheActivity

  @Inject
  lateinit var lifecycle: Observable<TheActivityLifecycle>

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var controller: QrCodeScannerViewController

  private val scannerView = CodeScannerView(context).apply {
    isAutoFocusButtonVisible = false
    isFlashButtonVisible = false
    maskColor = Color.TRANSPARENT
    frameColor = Color.TRANSPARENT
  }

  private val codeScanner by lazy(LazyThreadSafetyMode.NONE) { CodeScanner(context, scannerView) }

  @SuppressLint("CheckResult")
  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }
    TheActivity.component.inject(this)
    addView(scannerView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    initializeCodeScanner()

    val screenDestroys = RxView.detaches(this).map { ScreenDestroyed() }

    Observable
        .merge(
            screenCreates(),
            screenDestroys,
            cameraPermissionChanges(),
            lifecycle)
        .compose(controller)
        .takeUntil(screenDestroys)
        .observeOn(mainThread())
        .subscribe { uiChange -> uiChange(this) }
  }

  private fun initializeCodeScanner() {
    codeScanner.apply {
      camera = CodeScanner.CAMERA_BACK
      formats = listOf(BarcodeFormat.QR_CODE)
      autoFocusMode = AutoFocusMode.SAFE
      scanMode = ScanMode.SINGLE
      isAutoFocusEnabled = true
      isFlashEnabled = false

      // Callbacks
      decodeCallback = DecodeCallback {
        Timber.i("Decode: ${it.barcodeFormat} - ${it.text}")
      }
      errorCallback = ErrorCallback {
        Timber.e(it)
      }
    }
  }

  private fun screenCreates(): Observable<UiEvent> = Observable.just(ScreenCreated())

  private fun cameraPermissionChanges(): Observable<UiEvent> {
    return screenRouter.streamScreenResults()
        .ofType<ActivityPermissionResult>()
        .filter { result -> result.requestCode == REQUESTCODE_CAMERA_PERMISSION }
        .map { RuntimePermissions.check(activity, CAMERA_PERMISSION) }
        .map(::QrCodeScannerCameraPermissionChanged)
  }

  fun requestCameraPermissions() {
    RuntimePermissions.request(activity, CAMERA_PERMISSION, REQUESTCODE_CAMERA_PERMISSION)
  }

  fun stopScanning() {
    codeScanner.releaseResources()
  }

  fun startScanning() {
    codeScanner.startPreview()
  }
}
