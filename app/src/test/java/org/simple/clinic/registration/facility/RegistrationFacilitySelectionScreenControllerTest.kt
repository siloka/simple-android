package org.simple.clinic.registration.facility

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.simple.clinic.facility.Facility
import org.simple.clinic.facility.FacilityPullResult
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.facility.FacilitySync
import org.simple.clinic.facility.change.FacilitiesUpdateType.FIRST_UPDATE
import org.simple.clinic.facility.change.FacilitiesUpdateType.SUBSEQUENT_UPDATE
import org.simple.clinic.facility.change.FacilityListItem
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.registration.RegistrationScheduler
import org.simple.clinic.user.OngoingRegistrationEntry
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.Instant
import java.util.UUID

class RegistrationFacilitySelectionScreenControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val uiEvents = PublishSubject.create<UiEvent>()!!
  private val screen = mock<RegistrationFacilitySelectionScreen>()
  private val facilitySync = mock<FacilitySync>()
  private val facilityRepository = mock<FacilityRepository>()
  private val registrationScheduler = mock<RegistrationScheduler>()
  private val userSession = mock<UserSession>()

  private lateinit var controller: RegistrationFacilitySelectionScreenController

  @Before
  fun setUp() {
    controller = RegistrationFacilitySelectionScreenController(facilitySync, facilityRepository, userSession, registrationScheduler)

    uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(screen) }
  }

  @Test
  fun `when screen is started then facilities should be fetched if they are empty`() {
    val facilities = emptyList<Facility>()
    whenever(facilityRepository.facilities()).thenReturn(Observable.just(facilities))
    whenever(facilityRepository.recordCount()).thenReturn(Observable.just(facilities.size))
    whenever(facilitySync.pullWithResult()).thenReturn(Single.just(FacilityPullResult.Success()))

    uiEvents.onNext(ScreenCreated())

    verify(screen).showProgressIndicator()
    verify(facilitySync).pullWithResult()
    verify(screen).hideProgressIndicator()
  }

  @Test
  fun `when screen is started then facilities should not be fetched if they are already available`() {
    val facilities = listOf(PatientMocker.facility())
    whenever(facilityRepository.facilities()).thenReturn(Observable.just(facilities))
    whenever(facilityRepository.recordCount()).thenReturn(Observable.just(facilities.size))
    whenever(facilitySync.pullWithResult()).thenReturn(Single.just(FacilityPullResult.Success()))

    uiEvents.onNext(ScreenCreated())

    verify(screen, never()).showProgressIndicator()
    verify(facilitySync, never()).pullWithResult()
    verify(screen, never()).hideProgressIndicator()
  }

  @Test
  fun `when search query is changed then the query should be used for fetching filtered facilities`() {
    val facilities = listOf(
        PatientMocker.facility(name = "Facility 1"),
        PatientMocker.facility(name = "Facility 2"))
    whenever(facilityRepository.facilities(any())).thenReturn(Observable.just(facilities))
    whenever(facilityRepository.recordCount()).thenReturn(Observable.just(facilities.size))
    whenever(facilitySync.pullWithResult()).thenReturn(Single.just(FacilityPullResult.Success()))

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(RegistrationFacilitySearchQueryChanged(query = "F"))
    uiEvents.onNext(RegistrationFacilitySearchQueryChanged(query = "Fa"))
    uiEvents.onNext(RegistrationFacilitySearchQueryChanged(query = "Fac"))

    verify(facilityRepository).facilities("F")
    verify(facilityRepository).facilities("Fa")
    verify(facilityRepository).facilities("Fac")
  }

  @Test
  fun `when fetching facilities fails then an error should be shown`() {
    whenever(facilityRepository.facilities()).thenReturn(Observable.just(emptyList()))
    whenever(facilityRepository.recordCount()).thenReturn(Observable.just(0))
    whenever(facilitySync.pullWithResult())
        .thenReturn(Single.just(FacilityPullResult.UnexpectedError()))
        .thenReturn(Single.just(FacilityPullResult.NetworkError()))

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(RegistrationFacilitySearchQueryChanged(query = ""))
    uiEvents.onNext(RegistrationFacilitySelectionRetryClicked())

    verify(screen).showNetworkError()
    verify(screen).showUnexpectedError()
  }

  @Test
  fun `when retry is clicked then the error should be cleared and facilities should be fetched again`() {
    whenever(facilityRepository.facilities()).thenReturn(Observable.just(emptyList()))
    whenever(facilitySync.pullWithResult()).thenReturn(Single.just(FacilityPullResult.Success()))

    uiEvents.onNext(RegistrationFacilitySelectionRetryClicked())

    verify(screen).hideError()
    verify(screen).showProgressIndicator()
    verify(facilitySync).pullWithResult()
    verify(screen).hideProgressIndicator()
  }

  @Test
  fun `when facilities are received then their UI models for facility list should be created`() {
    val facility1 = PatientMocker.facility(name = "Facility 1")
    val facility2 = PatientMocker.facility(name = "Facility 2")
    val facilities = listOf(facility1, facility2)
    whenever(facilityRepository.facilities()).thenReturn(Observable.just(facilities, facilities))
    whenever(facilityRepository.recordCount()).thenReturn(Observable.just(facilities.size, facilities.size))

    val searchQuery = ""

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(RegistrationFacilitySearchQueryChanged(searchQuery))

    val facility1ListItem = FacilityListItem.Builder.build(facility1, searchQuery)
    val facility2ListItem = FacilityListItem.Builder.build(facility2, searchQuery)

    verify(screen).updateFacilities(listOf(facility1ListItem, facility2ListItem), FIRST_UPDATE)
    verify(screen).updateFacilities(listOf(facility1ListItem, facility2ListItem), SUBSEQUENT_UPDATE)
  }

  @Test
  fun `when a facility is clicked then the ongoing entry should be updated with selected facility and the user should be logged in`() {
    val ongoingEntry = OngoingRegistrationEntry(
        uuid = UUID.randomUUID(),
        phoneNumber = "1234567890",
        fullName = "Ashok",
        pin = "1234",
        pinConfirmation = "5678",
        createdAt = Instant.now())
    whenever(userSession.ongoingRegistrationEntry()).thenReturn(Single.just(ongoingEntry))
    whenever(userSession.saveOngoingRegistrationEntry(any())).thenReturn(Completable.complete())
    whenever(userSession.loginFromOngoingRegistrationEntry()).thenReturn(Completable.complete())
    whenever(registrationScheduler.schedule()).thenReturn(Completable.complete())

    val facility1 = PatientMocker.facility(name = "Hoshiarpur", uuid = UUID.randomUUID())
    uiEvents.onNext(RegistrationFacilityClicked(facility1))

    val inOrder = inOrder(userSession, registrationScheduler, screen)
    inOrder.verify(userSession).loginFromOngoingRegistrationEntry()
    inOrder.verify(registrationScheduler).schedule()
    inOrder.verify(screen).openHomeScreen()
    verify(userSession).saveOngoingRegistrationEntry(ongoingEntry.copy(facilityIds = listOf(facility1.uuid)))
  }

  @Test
  fun `search field should only be shown when facilities are available`() {
    whenever(facilityRepository.recordCount()).thenReturn(Observable.just(0, 10))
    whenever(facilitySync.pullWithResult()).thenReturn(Single.never())

    uiEvents.onNext(ScreenCreated())

    val inOrder = inOrder(screen)
    inOrder.verify(screen).showToolbarWithoutSearchField()
    inOrder.verify(screen).showToolbarWithSearchField()
  }
}
