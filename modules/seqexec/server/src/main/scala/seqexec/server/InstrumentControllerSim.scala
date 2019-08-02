// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server

import cats.Show
import cats.effect.Timer
import cats.effect.Sync
import cats.implicits._
import fs2.Stream
import gov.aps.jca.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import mouse.all._
import org.log4s.getLogger
import seqexec.model.dhs.ImageFileId
import seqexec.model.enum.ObserveCommandResult
import seqexec.server.SeqexecFailure.SeqexecException
import seqexec.server.InstrumentSystem.ElapsedTime
import squants.time.{Seconds, Time}

import scala.annotation.tailrec

sealed trait InstrumentControllerSim[F[_]] {
  def observe(fileId: ImageFileId, expTime: Time): F[ObserveCommandResult]

  def applyConfig[C: Show](config: C): F[Unit]

  def stopObserve: F[Unit]

  def abortObserve: F[Unit]

  def endObserve: F[Unit]

  def pauseObserve: F[Unit]

  def resumePaused: F[ObserveCommandResult]

  def stopPaused: F[ObserveCommandResult]

  def abortPaused: F[ObserveCommandResult]

  def observeCountdown(total: Time, elapsed: ElapsedTime): Stream[F, Progress]

}

object InstrumentControllerSim {
  private final class InstrumentControllerSimImpl[F[_]](name: String, useTimeout: Boolean)(implicit val F: Sync[F], T: Timer[F]) extends InstrumentControllerSim[F] {
    private val Log = getLogger

    private val stopFlag = new AtomicBoolean(false)
    private val abortFlag = new AtomicBoolean(false)
    private val pauseFlag = new AtomicBoolean(false)
    private val remainingTime = new AtomicInteger(0)

    private val tic = 200

    private val ReadoutDelay = Seconds(5)
    private val ConfigurationDelay = Seconds(5)
    private val StopObserveDelay = Seconds(1.5)

    @tailrec
    private def observeTic(stop: Boolean, abort: Boolean, pause: Boolean, remain: Int, timeout: Option[Int]): F[ObserveCommandResult] =
      if(remain < tic) {
        Log.info(s"Simulate $name observation completed")
        ObserveCommandResult.Success.pure[F].widen
      } else if(stop) ObserveCommandResult.Stopped.pure[F].widen
        else if(abort) ObserveCommandResult.Aborted.pure[F].widen
        else if(pause) {
          remainingTime.set(remain)
          ObserveCommandResult.Paused.pure[F].widen
        }
        else if(timeout.exists(_<= 0)) F.raiseError(SeqexecException(new TimeoutException()))
        else {
          Thread.sleep(tic.toLong)
          observeTic(stopFlag.get, abortFlag.get, pauseFlag.get, remain - tic, timeout.map(_ - tic))
        }

    def observe(fileId: ImageFileId, expTime: Time): F[ObserveCommandResult] = Sync[F].delay {
      Log.info(s"Simulate taking $name observation with label $fileId")
      pauseFlag.set(false)
      stopFlag.set(false)
      abortFlag.set(false)
      val totalTime = (expTime + ReadoutDelay).toMilliseconds.toInt
      remainingTime.set(totalTime)
      totalTime
    }.flatMap {totalTime =>
      observeTic(stop = false, abort = false, pause = false, totalTime, useTimeout.option(totalTime + 2 * tic))
    }

    def applyConfig[C: Show](config: C): F[Unit] = F.delay {
      Log.info(s"Simulate applying $name configuration ${config.show}")
      Thread.sleep(ConfigurationDelay.toMilliseconds.toLong)
    }

    def stopObserve: F[Unit] = F.delay {
      Log.info(s"Simulate stopping $name exposure")
      Thread.sleep(StopObserveDelay.toMilliseconds.toLong)
      stopFlag.set(true)
    }

    def abortObserve: F[Unit] = F.delay {
      Log.info(s"Simulate aborting $name exposure")
      abortFlag.set(true)
    }

    def endObserve: F[Unit] = F.delay {
      Log.info(s"Simulate sending endObserve to $name")
    }

    def pauseObserve: F[Unit] = F.delay {
      Log.info(s"Simulate pausing $name exposure")
      pauseFlag.set(true)
    }

    def resumePaused: F[ObserveCommandResult] = Sync[F].delay {
      Log.info(s"Simulate resuming $name observation")
      pauseFlag.set(false) } *>
      observeTic(stop = false, abort = false, pause = false, remainingTime.get,
        useTimeout.option(remainingTime.get + 2 * tic))

    def stopPaused: F[ObserveCommandResult] = Sync[F].delay {
      Log.info(s"Simulate stopping $name paused observation")
      pauseFlag.set(false) } *>
      observeTic(stop = true, abort = false, pause = false, 1000, None)

    def abortPaused: F[ObserveCommandResult] = Sync[F].delay {
      Log.info(s"Simulate aborting $name paused observation")
      pauseFlag.set(false) } *>
      observeTic(stop = false, abort = true, pause = false, 1000, None)

    def observeCountdown(total: Time, elapsed: ElapsedTime): Stream[F, Progress] =
      ProgressUtil.countdown[F](total, elapsed.self)

  }

  def apply[F[_]: Sync: Timer](name: String): InstrumentControllerSim[F] = new InstrumentControllerSimImpl[F](name, false)
  def withTimeout[F[_]: Sync: Timer](name: String): InstrumentControllerSim[F] = new InstrumentControllerSimImpl[F](name, true)

}
