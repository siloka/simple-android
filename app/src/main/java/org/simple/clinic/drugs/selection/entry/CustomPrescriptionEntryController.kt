package org.simple.clinic.drugs.selection.entry

import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.withLatestFrom
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.drugs.PrescriptionRepository
import org.simple.clinic.util.nullIfBlank
import org.simple.clinic.widgets.UiEvent
import javax.inject.Inject

private typealias Ui = CustomPrescriptionEntrySheet
private typealias UiChange = (Ui) -> Unit

const val DOSAGE_PLACEHOLDER = "mg"

class CustomPrescriptionEntryController @Inject constructor(
    private val prescriptionRepository: PrescriptionRepository
) : ObservableTransformer<UiEvent, UiChange> {

  override fun apply(events: Observable<UiEvent>): ObservableSource<UiChange> {
    val replayedEvents = events.compose(ReportAnalyticsEvents()).replay().refCount()

    return Observable.mergeArray(
        toggleSaveButton(replayedEvents),
        saveNewPrescriptionsAndDismiss(replayedEvents),
        updatePrescriptionAndDismiss(replayedEvents),
        showDefaultDosagePlaceholder(replayedEvents),
        updateSheetTitle(replayedEvents),
        toggleRemoveButton(replayedEvents),
        prefillPrescription(replayedEvents),
        removePrescription(replayedEvents),
        closeSheetWhenPrescriptionIsDeleted(replayedEvents))
  }

  private fun toggleSaveButton(events: Observable<UiEvent>): Observable<UiChange> {
    return events
        .ofType<CustomPrescriptionDrugNameTextChanged>()
        .map { it.name.isNotBlank() }
        .distinctUntilChanged()
        .map { canBeSaved -> { ui: Ui -> ui.setSaveButtonEnabled(canBeSaved) } }
  }

  private fun saveNewPrescriptionsAndDismiss(events: Observable<UiEvent>): Observable<UiChange> {
    val patientUuids = events
        .ofType<CustomPrescriptionSheetCreated>()
        .filter { it.openAs is OpenAs.New }
        .map { it.openAs as OpenAs.New }
        .map { it.patientUuid }
        .take(1)

    val nameChanges = events
        .ofType<CustomPrescriptionDrugNameTextChanged>()
        .map { it.name }

    val dosageChanges = events
        .ofType<CustomPrescriptionDrugDosageTextChanged>()
        .map { it.dosage }

    val saveClicks = events
        .ofType<SaveCustomPrescriptionClicked>()

    return Observables
        .combineLatest(saveClicks, patientUuids, nameChanges, dosageChanges) { _, uuid, name, dosage -> Triple(uuid, name, dosage) }
        .flatMap { (patientUuid, name, dosage) ->
          prescriptionRepository
              .savePrescription(patientUuid, name, dosage.nullIfBlank(), rxNormCode = null, isProtocolDrug = false)
              .andThen(Observable.just({ ui: Ui -> ui.finish() }))
        }
  }

  private fun updatePrescriptionAndDismiss(events: Observable<UiEvent>): Observable<UiChange> {
    val prescribedDrugs = events
        .ofType<CustomPrescriptionSheetCreated>()
        .filter { it.openAs is OpenAs.Update }
        .map { it.openAs as OpenAs.Update }
        .flatMap { prescriptionRepository.prescription(it.prescribedDrugUuid) }
        .take(1)

    val nameChanges = events
        .ofType<CustomPrescriptionDrugNameTextChanged>()
        .map { it.name }

    val dosageChanges = events
        .ofType<CustomPrescriptionDrugDosageTextChanged>()
        .map { it.dosage }

    val saveClicks = events
        .ofType<SaveCustomPrescriptionClicked>()

    return Observables.combineLatest(prescribedDrugs, nameChanges, dosageChanges, saveClicks) { prescribedDrug, name, dosage, _ -> Triple(prescribedDrug, name, dosage) }
        .flatMap { (prescribedDrug, name, dosage) ->
          prescriptionRepository.updatePrescription(prescribedDrug.copy(name = name, dosage = dosage))
              .andThen(Observable.just({ ui: Ui -> ui.finish() }))
        }
  }

  /**
   * The dosage field shows a default text as "mg". When it is focused, the cursor will
   * by default be moved to the end. This will force the user to either move the cursor
   * to the end manually or delete everything and essentially making the placeholder
   * useless. As a workaround, we move the cursor to the starting again.
   */
  private fun showDefaultDosagePlaceholder(events: Observable<UiEvent>): Observable<UiChange> {
    val dosageTextChanges = events
        .ofType<CustomPrescriptionDrugDosageTextChanged>()
        .map { it.dosage }

    val dosageFocusChanges = events
        .ofType<CustomPrescriptionDrugDosageFocusChanged>()
        .map { it.hasFocus }

    val setPlaceholder = Observables.combineLatest(dosageFocusChanges, dosageTextChanges)
        .filter { (hasFocus, text) -> hasFocus && text.isBlank() }
        .take(1)
        .map { { ui: Ui -> ui.setDrugDosageText(DOSAGE_PLACEHOLDER) } }

    val resetPlaceholder = Observables.combineLatest(dosageFocusChanges, dosageTextChanges)
        .filter { (hasFocus, text) -> !hasFocus && text.trim() == DOSAGE_PLACEHOLDER }
        .map { { ui: Ui -> ui.setDrugDosageText("") } }

    val moveCursorToStart = Observables.combineLatest(dosageFocusChanges, dosageTextChanges)
        .filter { (hasFocus, text) -> hasFocus && text.trim() == DOSAGE_PLACEHOLDER }
        .map { { ui: Ui -> ui.moveDrugDosageCursorToBeginning() } }

    return Observable.merge(setPlaceholder, resetPlaceholder, moveCursorToStart)
  }

  private fun updateSheetTitle(events: Observable<UiEvent>): Observable<UiChange> {
    val openAsStream = events
        .ofType<CustomPrescriptionSheetCreated>()
        .map { it.openAs }

    val showEnterNewPrescription = openAsStream
        .filter { it is OpenAs.New }
        .map { { ui: Ui -> ui.showEnterNewPrescriptionTitle() } }

    val showEditPrescription = openAsStream
        .filter { it is OpenAs.Update }
        .map { { ui: Ui -> ui.showEditPrescriptionTitle() } }

    return showEnterNewPrescription.mergeWith(showEditPrescription)
  }

  private fun toggleRemoveButton(events: Observable<UiEvent>): Observable<UiChange> {
    val openAsStream = events
        .ofType<CustomPrescriptionSheetCreated>()
        .map { it.openAs }

    val hideRemoveButton = openAsStream
        .filter { it is OpenAs.New }
        .map { { ui: Ui -> ui.hideRemoveButton() } }

    val showRemoveButton = openAsStream
        .filter { it is OpenAs.Update }
        .map { { ui: Ui -> ui.showRemoveButton() } }

    return showRemoveButton.mergeWith(hideRemoveButton)
  }

  private fun prefillPrescription(events: Observable<UiEvent>): Observable<UiChange> {
    val openAsUpdate = events
        .ofType<CustomPrescriptionSheetCreated>()
        .filter { it.openAs is OpenAs.Update }
        .map { it.openAs as OpenAs.Update }

    return openAsUpdate
        .flatMap { prescriptionRepository.prescription(it.prescribedDrugUuid).take(1) }
        .map {
          { ui: Ui ->
            ui.setMedicineName(it.name)
            ui.setDosage(it.dosage)
          }
        }
  }

  private fun removePrescription(events: Observable<UiEvent>): Observable<UiChange> {
    val openAsUpdate = events
        .ofType<CustomPrescriptionSheetCreated>()
        .filter { it.openAs is OpenAs.Update }
        .map { it.openAs as OpenAs.Update }
        .map { it.prescribedDrugUuid }
        .take(1)

    return events
        .ofType<RemoveCustomPrescriptionClicked>()
        .withLatestFrom(openAsUpdate)
        .map { (_, prescribedDrugUuid) -> { ui: Ui -> ui.showConfirmRemoveMedicineDialog(prescribedDrugUuid) } }
  }

  private fun closeSheetWhenPrescriptionIsDeleted(events: Observable<UiEvent>): Observable<UiChange> {
    val prescribedDrugUuuids = events
        .ofType<CustomPrescriptionSheetCreated>()
        .filter { it.openAs is OpenAs.Update }
        .map { it.openAs as OpenAs.Update }
        .map { it.prescribedDrugUuid }

    return prescribedDrugUuuids
        .flatMap(prescriptionRepository::prescription)
        .filter { it.isDeleted }
        .take(1)
        .map { { ui: Ui -> ui.finish() } }
  }
}
