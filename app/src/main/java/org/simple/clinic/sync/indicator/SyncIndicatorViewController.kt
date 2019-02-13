package org.simple.clinic.sync.indicator

import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

typealias Ui = SyncIndicatorView
typealias UiChange = (Ui) -> Unit

class SyncIndicatorViewController @Inject constructor(
) : ObservableTransformer<UiEvent, UiChange> {

  override fun apply(events: Observable<UiEvent>): ObservableSource<UiChange> {
    val replayedEvents = events.compose(ReportAnalyticsEvents()).replay(1).refCount()

    return Observable.never()
  }
}
