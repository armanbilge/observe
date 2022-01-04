// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.circuit

import cats.Eq
import monocle.Getter
import monocle.macros.Lenses
import observe.model._
import ebserve.web.client.model._

@Lenses
final case class HeaderSideBarFocus(
  status:      ClientStatus,
  conditions:  Conditions,
  operator:    Option[Operator],
  displayName: Option[String]
)

object HeaderSideBarFocus {
  implicit val eq: Eq[HeaderSideBarFocus] =
    Eq.by(x => (x.status, x.conditions, x.operator, x.displayName))

  val headerSideBarG: Getter[ObserveAppRootModel, HeaderSideBarFocus] =
    Getter[ObserveAppRootModel, HeaderSideBarFocus] { c =>
      val clientStatus = ClientStatus(c.uiModel.user, c.ws)
      val displayName  = c.uiModel.user.flatMap(u => c.uiModel.displayNames.get(u.username))
      HeaderSideBarFocus(clientStatus, c.sequences.conditions, c.sequences.operator, displayName)
    }
}

final case class UserLoginFocus(user: Option[UserDetails], displayNames: Map[String, String]) {
  val displayName: Option[String] = user.flatMap(u => displayNames.get(u.username))
}

object UserLoginFocus {
  implicit val eqUserLoginFocus: Eq[UserLoginFocus] = Eq.by(u => (u.user, u.displayNames))
}

@Lenses
final case class UserPromptFocus(user: UserPromptState, displayName: Option[String])

object UserPromptFocus {
  implicit val eqUserPromptFocus: Eq[UserPromptFocus] = Eq.by(u => (u.user, u.displayName))
}
