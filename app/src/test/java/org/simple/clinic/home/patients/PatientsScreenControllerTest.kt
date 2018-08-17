package org.simple.clinic.home.patients

import com.f2prateek.rx.preferences2.Preference
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.sync.SyncScheduler
import org.simple.clinic.user.UserSession
import org.simple.clinic.user.UserStatus
import org.simple.clinic.util.Just
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.TheActivityLifecycle
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit
import java.net.SocketTimeoutException

@RunWith(JUnitParamsRunner::class)
class PatientsScreenControllerTest {

  private val screen: PatientsScreen = mock()
  private val userSession = mock<UserSession>()
  private val approvalStatusApprovedAt = mock<Preference<Instant>>()
  private val hasUserDismissedApprovedStatus = mock<Preference<Boolean>>()
  private val syncScheduler = mock<SyncScheduler>()

  private val uiEvents: PublishSubject<UiEvent> = PublishSubject.create()
  private lateinit var controller: PatientsScreenController

  @Before
  fun setUp() {
    controller = PatientsScreenController(userSession, syncScheduler, approvalStatusApprovedAt, hasUserDismissedApprovedStatus)

    uiEvents
        .compose(controller)
        .subscribe { uiChange -> uiChange(screen) }
  }

  @Test
  fun `when new patient is clicked then patient search screen should open`() {
    uiEvents.onNext(NewPatientClicked())

    verify(screen).openNewPatientScreen()
  }

  @Test
  fun `when screen is created and the user is awaiting approval then the approval status should be checked`() {
    val user = PatientMocker.loggedInUser(status = UserStatus.WAITING_FOR_APPROVAL)
    whenever(userSession.loggedInUser()).thenReturn(Observable.just(Just(user)))
    whenever(userSession.refreshLoggedInUser()).thenReturn(Completable.complete())
    whenever(hasUserDismissedApprovedStatus.asObservable()).thenReturn(Observable.just(false))

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(TheActivityLifecycle.Resumed())
    uiEvents.onNext(TheActivityLifecycle.Resumed())

    verify(userSession, times(3)).refreshLoggedInUser()
  }

  @Test
  @Parameters(value = ["APPROVED_FOR_SYNCING", "DISAPPROVED_FOR_SYNCING"])
  fun `when screen is created and the user is not awaiting approval then the user's status should not be checked`(
      status: UserStatus
  ) {
    val user = PatientMocker.loggedInUser(status = status)
    whenever(userSession.loggedInUser()).thenReturn(Observable.just(Just(user)))
    whenever(userSession.refreshLoggedInUser()).thenReturn(Completable.never())
    whenever(approvalStatusApprovedAt.get()).thenReturn(Instant.now())
    whenever(hasUserDismissedApprovedStatus.asObservable()).thenReturn(Observable.just(false))

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(TheActivityLifecycle.Resumed())
    uiEvents.onNext(TheActivityLifecycle.Resumed())

    verify(userSession, never()).refreshLoggedInUser()
  }

  @Test
  fun `when the user is awaiting approval then the approval status should be shown`() {
    val user = PatientMocker.loggedInUser(status = UserStatus.WAITING_FOR_APPROVAL)
    whenever(userSession.loggedInUser()).thenReturn(Observable.just(Just(user)))
    whenever(userSession.refreshLoggedInUser()).thenReturn(Completable.never())
    whenever(hasUserDismissedApprovedStatus.asObservable()).thenReturn(Observable.just(false))

    uiEvents.onNext(ScreenCreated())

    verify(screen).showUserStatusAsWaiting()
  }

  @Test
  fun `when the user has been disapproved then the approval status shouldn't be shown`() {
    val user = PatientMocker.loggedInUser(status = UserStatus.DISAPPROVED_FOR_SYNCING)
    whenever(userSession.loggedInUser()).thenReturn(Observable.just(Just(user)))
    whenever(hasUserDismissedApprovedStatus.asObservable()).thenReturn(Observable.just(false))

    uiEvents.onNext(ScreenCreated())

    verify(screen).hideUserApprovalStatus()
  }

  @Test
  @Parameters("true", "false")
  fun `when the user has been approved within the last 24h then the approval status should be shown`(
      hasUserDismissedStatus: Boolean
  ) {
    val user = PatientMocker.loggedInUser(status = UserStatus.APPROVED_FOR_SYNCING)
    whenever(userSession.loggedInUser()).thenReturn(Observable.just(Just(user)))
    whenever(approvalStatusApprovedAt.get()).thenReturn(Instant.now().minus(23, ChronoUnit.HOURS))
    whenever(hasUserDismissedApprovedStatus.asObservable()).thenReturn(Observable.just(hasUserDismissedStatus))

    uiEvents.onNext(ScreenCreated())

    if (hasUserDismissedStatus) {
      verify(screen, never()).showUserStatusAsApproved()
    } else {
      verify(screen).showUserStatusAsApproved()
    }
  }

  @Test
  @Parameters("true", "false")
  fun `when the user was approved earlier than 24h then the approval status should not be shown`(
      hasUserDismissedStatus: Boolean
  ) {
    val user = PatientMocker.loggedInUser(status = UserStatus.APPROVED_FOR_SYNCING)
    whenever(userSession.loggedInUser()).thenReturn(Observable.just(Just(user)))
    whenever(userSession.refreshLoggedInUser()).thenReturn(Completable.complete())
    whenever(approvalStatusApprovedAt.get()).thenReturn(Instant.now().minus(25, ChronoUnit.HOURS))
    whenever(hasUserDismissedApprovedStatus.asObservable()).thenReturn(Observable.just(hasUserDismissedStatus))

    uiEvents.onNext(ScreenCreated())

    verify(screen, never()).showUserStatusAsApproved()
  }

  @Test
  fun `when checking the user's status fails with any error then the error should be silently swallowed`() {
    val user = PatientMocker.loggedInUser(status = UserStatus.WAITING_FOR_APPROVAL)
    whenever(userSession.loggedInUser()).thenReturn(Observable.just(Just(user)))
    whenever(userSession.refreshLoggedInUser()).thenReturn(Completable.error(SocketTimeoutException()))
    whenever(hasUserDismissedApprovedStatus.asObservable()).thenReturn(Observable.just(false))

    uiEvents.onNext(ScreenCreated())

    verify(userSession).refreshLoggedInUser()
    verify(approvalStatusApprovedAt).set(any())
  }

  @Test
  fun `when the user dismisses the approved status then the status should be hidden`() {
    val user = PatientMocker.loggedInUser(status = UserStatus.APPROVED_FOR_SYNCING)
    whenever(userSession.loggedInUser()).thenReturn(Observable.just(Just(user)))
    whenever(approvalStatusApprovedAt.get()).thenReturn(Instant.now().minus(23, ChronoUnit.HOURS))
    whenever(hasUserDismissedApprovedStatus.asObservable()).thenReturn(Observable.just(false))

    uiEvents.onNext(ScreenCreated())
    uiEvents.onNext(UserApprovedStatusDismissed())

    verify(hasUserDismissedApprovedStatus).set(true)
  }

  @Test
  @Parameters(
      "WAITING_FOR_APPROVAL, false",
      "APPROVED_FOR_SYNCING, true",
      "DISAPPROVED_FOR_SYNCING, false")
  fun `when user is refreshed then patient data should be synced depending on the new approval status`(
      statusAfterRefresh: UserStatus,
      shouldSync: Boolean
  ) {
    whenever(userSession.loggedInUser())
        .thenReturn(Observable.just(Just(PatientMocker.loggedInUser(status = UserStatus.WAITING_FOR_APPROVAL))))
        .thenReturn(Observable.just(Just(PatientMocker.loggedInUser(status = statusAfterRefresh))))

    whenever(userSession.refreshLoggedInUser()).thenReturn(Completable.complete())
    whenever(hasUserDismissedApprovedStatus.asObservable()).thenReturn(Observable.just(false))
    whenever(approvalStatusApprovedAt.get()).thenReturn(Instant.now())
    whenever(syncScheduler.syncImmediately()).thenReturn(Completable.complete())

    uiEvents.onNext(ScreenCreated())

    verify(userSession).refreshLoggedInUser()

    if (shouldSync) {
      verify(syncScheduler).syncImmediately()
    } else {
      verify(syncScheduler, never()).syncImmediately()
    }
  }
}