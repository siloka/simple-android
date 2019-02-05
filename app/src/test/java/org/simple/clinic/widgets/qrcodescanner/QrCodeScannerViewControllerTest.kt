package org.simple.clinic.widgets.qrcodescanner

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.util.RuntimePermissionResult
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.TheActivityLifecycle
import org.simple.clinic.widgets.UiEvent

@RunWith(JUnitParamsRunner::class)
class QrCodeScannerViewControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  val uiEvents = PublishSubject.create<UiEvent>()
  val view = mock<QrCodeScannerView>()

  val controller = QrCodeScannerViewController()

  @Before
  fun setUp() {
    uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(view) }
  }

  @Test
  fun `when the view is created, the camera permission must be requested`() {
    uiEvents.onNext(ScreenCreated())

    verify(view).requestCameraPermissions()
  }

  @Test
  fun `when the view is detached the camera preview must be stopped`() {
    uiEvents.onNext(ScreenDestroyed())

    verify(view).stopScanning()
  }

  @Test
  fun `when the activity is stopped the camera preview must be stopped`() {
    uiEvents.onNext(TheActivityLifecycle.Paused())

    verify(view).stopScanning()
  }

  @Test
  fun `when the activity is started and the camera permissions are granted, the camera preview must be started`() {
    uiEvents.onNext(TheActivityLifecycle.Resumed())
    uiEvents.onNext(QrCodeScannerCameraPermissionChanged(RuntimePermissionResult.DENIED))
    uiEvents.onNext(TheActivityLifecycle.Paused())
    uiEvents.onNext(QrCodeScannerCameraPermissionChanged(RuntimePermissionResult.GRANTED))
    uiEvents.onNext(QrCodeScannerCameraPermissionChanged(RuntimePermissionResult.NEVER_ASK_AGAIN))
    uiEvents.onNext(TheActivityLifecycle.Resumed())
    uiEvents.onNext(QrCodeScannerCameraPermissionChanged(RuntimePermissionResult.GRANTED))

    verify(view).startScanning()
  }

  @Test
  fun `when the screen is created and the camera permissions are granted, the camera preview must be started`() {
    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(QrCodeScannerCameraPermissionChanged(RuntimePermissionResult.DENIED))
    uiEvents.onNext(QrCodeScannerCameraPermissionChanged(RuntimePermissionResult.NEVER_ASK_AGAIN))
    uiEvents.onNext(QrCodeScannerCameraPermissionChanged(RuntimePermissionResult.GRANTED))

    verify(view).startScanning()
  }
}
