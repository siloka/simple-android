package org.simple.clinic.widgets.qrcodescanner

import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.ofType
import org.simple.clinic.ReplayUntilScreenIsDestroyed
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.util.RuntimePermissionResult
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.TheActivityLifecycle
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

typealias Ui = QrCodeScannerView
typealias UiChange = (Ui) -> Unit

class QrCodeScannerViewController @Inject constructor() : ObservableTransformer<UiEvent, UiChange> {

  override fun apply(events: Observable<UiEvent>): ObservableSource<UiChange> {
    val replayedEvents = ReplayUntilScreenIsDestroyed(events)
        .compose(ReportAnalyticsEvents())
        .replay()

    return Observable.merge(
        requestCameraPermission(replayedEvents),
        startScanningForQrCode(replayedEvents),
        stopScanningForQrCode(replayedEvents)
    )
  }

  private fun requestCameraPermission(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<ScreenCreated>()
        .map { { ui: Ui -> ui.requestCameraPermissions() } }
  }

  private fun startScanningForQrCode(events: Observable<UiEvent>): Observable<UiChange> {
    val activityLifecycleEvents = events
        .ofType<TheActivityLifecycle>()
        .mergeWith(
            events
                .ofType<ScreenCreated>()
                .map { TheActivityLifecycle.Resumed() })

    val permissionResultChanges = events
        .ofType<QrCodeScannerCameraPermissionChanged>()

    return Observables.combineLatest(activityLifecycleEvents, permissionResultChanges)
        .distinctUntilChanged()
        .filter { (lifecycleEvent, _) -> lifecycleEvent is TheActivityLifecycle.Resumed }
        .filter { (_, permissionResultChange) -> permissionResultChange.result == RuntimePermissionResult.GRANTED }
        .map { { ui: Ui -> ui.startScanning() } }
  }

  private fun stopScanningForQrCode(events: Observable<UiEvent>): Observable<UiChange> {
    return Observable
        .merge(
            events.ofType<ScreenDestroyed>(),
            events.ofType<TheActivityLifecycle.Paused>()
        )
        .map { { ui: Ui -> ui.stopScanning() } }
  }
}
