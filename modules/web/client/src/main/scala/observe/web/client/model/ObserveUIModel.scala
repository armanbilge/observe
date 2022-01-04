// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import cats.Eq
import lucuma.core.util.Enumerated
import monocle.macros.Lenses
import observe.common.FixedLengthBuffer
import observe.model.Observer
import observe.model.UserDetails
import observe.web.client.model.SectionVisibilityState._
import seqexec.web.client.handlers.UserLoginFocus
import monocle.Getter

sealed trait SoundSelection extends Product with Serializable

object SoundSelection {
  case object SoundOn  extends SoundSelection
  case object SoundOff extends SoundSelection

  /** @group Typeclass Instances */
  implicit val SoundSelectionEnumerated: Enumerated[SoundSelection] =
    Enumerated.of(SoundOn, SoundOff)

  def flip: SoundSelection => SoundSelection =
    _ match {
      case SoundSelection.SoundOn  => SoundSelection.SoundOff
      case SoundSelection.SoundOff => SoundSelection.SoundOn
    }
}

/**
 * UI model, changes here will update the UI
 */
@Lenses
final case class ObserveUIModel(
  navLocation:        Pages.ObservePages,
  user:               Option[UserDetails],
  displayNames:       Map[String, String],
  loginBox:           SectionVisibilityState,
  globalLog:          GlobalLog,
  sequencesOnDisplay: SequencesOnDisplay,
  appTableStates:     AppTableStates,
  notification:       UserNotificationState,
  userPrompt:         UserPromptState,
  queues:             CalibrationQueues,
  obsProgress:        AllObservationsProgressState,
  sessionQueueFilter: SessionQueueFilter,
  sound:              SoundSelection,
  firstLoad:          Boolean
)

object ObserveUIModel {
  val Initial: ObserveUIModel = ObserveUIModel(
    Pages.Root,
    None,
    Map.empty,
    SectionClosed,
    GlobalLog(FixedLengthBuffer.unsafeFromInt(500), SectionClosed),
    SequencesOnDisplay.Empty,
    AppTableStates.Initial,
    UserNotificationState.Empty,
    UserPromptState.Empty,
    CalibrationQueues.Default,
    AllObservationsProgressState.Empty,
    SessionQueueFilter.NoFilter,
    SoundSelection.SoundOn,
    firstLoad = true
  )

  val userLoginFocus: Lens[ObserveUIModel, UserLoginFocus] =
    Lens[ObserveUIModel, UserLoginFocus](m => UserLoginFocus(m.user, m.displayNames))(n =>
      a => a.copy(user = n.user, displayNames = n.displayNames)
    )

  val unsafeUserPromptFocus: Lens[SeqexecUIModel, UserPromptFocus] =
    Lens[SeqexecUIModel, UserPromptFocus](m =>
      UserPromptFocus(m.userPrompt, m.user.flatMap(u => m.displayNames.get(u.username)))
    )(n => a => a.copy(userPrompt = n.user))

  implicit val eq: Eq[ObserveUIModel] =
    Eq.by(x =>
      (x.navLocation,
       x.user,
       x.displayNames,
       x.loginBox,
       x.globalLog,
       x.sequencesOnDisplay,
       x.appTableStates,
       x.notification,
       x.userPrompt,
       x.queues,
       x.sessionQueueFilter,
       x.sound,
       x.firstLoad
      )
    )

  val displayNameG: Getter[SeqexecUIModel, Option[String]] =
    Getter[SeqexecUIModel, Option[String]](x => x.user.flatMap(r => x.displayNames.get(r.username)))

}
