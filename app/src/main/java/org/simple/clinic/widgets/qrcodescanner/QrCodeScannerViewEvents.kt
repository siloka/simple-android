package org.simple.clinic.widgets.qrcodescanner

import org.simple.clinic.util.RuntimePermissionResult
import org.simple.clinic.widgets.UiEvent

data class QrCodeScannerCameraPermissionChanged(val result: RuntimePermissionResult) : UiEvent {
  override val analyticsName = "Scan QR Code:Camera Permission:$result"
}
