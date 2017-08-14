// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem

import cats._
import cats.implicits._
import cats.effect.Sync
import doobie.imports._
import gem.dao._
import gem.enum._
import gem.util.Lens, Lens._

final class Service[M[_]: Monad] private (private val xa: Transactor[M], val log: Log[M], val user: User[ProgramRole]) {

  /**
   * Construct a program that yields a list of `Program` whose name or id contains the given
   * substring (case-insensitive), up to a provided maximum length.
   */
  def queryProgramsByName(substr: String, max: Int): M[List[Program[Nothing]]] =
    log.log(user, s"""queryProgramsByName("$substr", $max)""") {
      ProgramDao.selectBySubstring(s"%$substr%", max).transact(xa)
    }

  /**
   * Construct a program that attempts to change the user's password, yielding `true` on success.
   */
  def changePassword(oldPassword: String, newPassword: String): M[Boolean] =
    log.log(user, "changePassword(***, ***)") {
      UserDao.changePassword(user.id, oldPassword, newPassword).transact(xa)
    }

}

object Service {

  object L {
    def user[M[_]: Monad]: Service[M] @> User[ProgramRole] = Lens((a, b) => new Service(a.xa, a.log, b), _.user)
  }

  def apply[M[_]: Monad](xa: Transactor[M], log: Log[M], user: User[ProgramRole]): Service[M] =
    new Service(xa, log, user)

  /**
   * Construct a program that verifies a user's id and password and returns a `Service`.
   */
  def tryLogin[M[_]: Sync](
    user: User.Id, pass: String, xa: Transactor[M], log: Log[M]
  ): M[Option[Service[M]]] =
    xa.trans.apply(UserDao.selectUserʹ(user, pass)).map {
      case None    => Option.empty[Service[M]]
      case Some(u) => Some(Service[M](xa, log, u))
    }

  /**
   * Like `tryLogin`, but for previously-authenticated users.
   */
  def service[M[_]: Sync](
    user: User.Id, xa: Transactor[M], log: Log[M]
  ): M[Option[Service[M]]] =
    xa.trans.apply(UserDao.selectUser(user)).map {
      case None    => Option.empty[Service[M]]
      case Some(u) => Some(Service[M](xa, log, u))
    }

}
