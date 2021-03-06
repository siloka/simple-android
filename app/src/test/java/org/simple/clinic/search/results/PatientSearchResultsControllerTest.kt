package org.simple.clinic.search.results

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.patient.OngoingNewPatientEntry
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.patient.PatientSearchResult
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.widgets.UiEvent

@RunWith(JUnitParamsRunner::class)
class PatientSearchResultsControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val screen: PatientSearchResultsScreen = mock()
  private val patientRepository: PatientRepository = mock()
  private val userSession: UserSession = mock()
  private val facilityRepository: FacilityRepository = mock()

  private lateinit var controller: PatientSearchResultsController
  private val uiEvents = PublishSubject.create<UiEvent>()

  val currentFacility = PatientMocker.facility()

  @Before
  fun setUp() {
    RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }

    val user = PatientMocker.loggedInUser()
    whenever(userSession.requireLoggedInUser()).thenReturn(Observable.just(user))
    whenever(facilityRepository.currentFacility(user)).thenReturn(Observable.just(currentFacility))

    controller = PatientSearchResultsController(patientRepository, userSession, facilityRepository)
    uiEvents.compose(controller).subscribe { uiChange -> uiChange(screen) }
  }

  @Test
  fun `when screen is created then patients matching the search query should be shown`() {
    val searchResults = listOf(PatientMocker.patientSearchResult(), PatientMocker.patientSearchResult())
    whenever(patientRepository.search(any())).thenReturn(Observable.just(searchResults))

    uiEvents.onNext(PatientSearchResultsScreenCreated(PatientSearchResultsScreenKey("name")))

    verify(patientRepository).search("name")
    verify(screen).updateSearchResults(searchResults, currentFacility)
    verify(screen).setEmptyStateVisible(false)
  }

  @Test
  fun `when screen is created and no matching patients are available then the empty state should be shown`() {
    val emptyResults = listOf<PatientSearchResult>()
    whenever(patientRepository.search(any())).thenReturn(Observable.just(emptyResults))

    uiEvents.onNext(PatientSearchResultsScreenCreated(PatientSearchResultsScreenKey("name")))

    verify(patientRepository).search("name")
    verify(screen).updateSearchResults(emptyResults, currentFacility)
    verify(screen).setEmptyStateVisible(true)
  }

  @Test
  fun `when a patient search result is clicked, the patient's summary screen should be opened`() {
    val searchResult = PatientMocker.patientSearchResult()
    uiEvents.onNext(PatientSearchResultClicked(searchResult))

    verify(screen).openPatientSummaryScreen(searchResult.uuid)
  }

  @Test
  @Parameters(value = [
    "Abhay",
    "Vinod"])
  fun `when create new patient is clicked then patient entry screen should be opened with prefilled name`(
      name: String
  ) {
    whenever(patientRepository.search(any())).thenReturn(Observable.just(listOf()))

    val preFilledEntry = OngoingNewPatientEntry(OngoingNewPatientEntry.PersonalDetails(
        fullName = name,
        dateOfBirth = null,
        age = null,
        gender = null))
    whenever(patientRepository.saveOngoingEntry(preFilledEntry)).thenReturn(Completable.complete())

    uiEvents.onNext(PatientSearchResultsScreenCreated(PatientSearchResultsScreenKey(name)))
    uiEvents.onNext(CreateNewPatientClicked())

    verify(patientRepository).saveOngoingEntry(preFilledEntry)
    verify(screen).openPatientEntryScreen()
  }
}
