// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gmos

import cats.Show
import cats.effect.{ Async, Ref, Temporal }
import cats.syntax.all._
import fs2.Stream
import org.typelevel.log4cats.Logger
import monocle.Optional
import monocle.macros.Lenses
import monocle.std.option.some
import observe.model.GmosParameters.NsCyclesI
import observe.model.NSSubexposure
import observe.model.ObserveStage
import observe.model.dhs.ImageFileId
import observe.model.enum.NodAndShuffleStage._
import observe.model.enum.ObserveCommandResult
import observe.server.InstrumentControllerSim
import observe.server.InstrumentSystem.ElapsedTime
import observe.server.ObsProgress
import observe.server.Progress
import observe.server.ProgressUtil.countdown
import observe.server.RemainingTime
import observe.server.gmos.GmosController.Config.NSConfig
import observe.server.gmos.GmosController.GmosConfig
import observe.server.gmos.GmosController.NorthTypes
import observe.server.gmos.GmosController.SiteDependentTypes
import observe.server.gmos.GmosController.SouthTypes
import shapeless.tag
import squants.Time
import squants.time.TimeConversions._

/**
 * Keep track of the current execution state
 */
@Lenses
final case class NSCurrent(
  fileId:        ImageFileId,
  totalCycles:   Int,
  exposureCount: Int,
  expTime:       Time
) {
  def lastSubexposure: Boolean =
    (exposureCount + 1) === totalCycles * NsSequence.length

  def firstSubexposure: Boolean = exposureCount === 0

  val cycle: Int = exposureCount / NsSequence.length

  val stageIndex: Int = exposureCount % NsSequence.length
}

object NSCurrent {
  implicit val showNSCurrent: Show[NSCurrent] = Show.show { a =>
    s"NS State: file=${a.fileId}, cycle=${a.cycle + 1}, stage=${NsSequence.toList
      .lift(a.exposureCount % NsSequence.length)
      .getOrElse("Unknown")}/${(a.exposureCount % NsSequence.length) + 1}, subexposure=${a.exposureCount + 1}, expTime=${a.expTime}"
  }
}

/**
 * Used to keep the internal state of NS
 */
@Lenses
final case class NSObsState(config: NSConfig, current: Option[NSCurrent])

object NSObsState {
  def fromConfig(c: NSConfig): NSObsState =
    NSObsState(c, None)

  val Zero: NSObsState = NSObsState(NSConfig.NoNodAndShuffle, None)

  val fileId: Optional[NSObsState, ImageFileId] =
    NSObsState.current.andThen(some[NSCurrent]).andThen(NSCurrent.fileId)

  val exposureCount: Optional[NSObsState, Int] =
    NSObsState.current.andThen(some[NSCurrent]).andThen(NSCurrent.exposureCount)

  val expTime: Optional[NSObsState, Time] =
    NSObsState.current.andThen(some[NSCurrent]).andThen(NSCurrent.expTime)

}

object GmosControllerSim {
  def apply[F[_]: Temporal, T <: SiteDependentTypes](
    sim:      InstrumentControllerSim[F],
    nsConfig: Ref[F, NSObsState]
  ): GmosController[F, T] =
    new GmosController[F, T] {
      override def observe(
        fileId:  ImageFileId,
        expTime: Time
      ): F[ObserveCommandResult] =
        nsConfig.modify {
          case s @ NSObsState(NSConfig.NoNodAndShuffle, _)                =>
            (s, s)
          case s @ NSObsState(NSConfig.NodAndShuffle(cycles, _, _, _), _) =>
            // Initialize the current state
            val update =
              NSObsState.current.replace(NSCurrent(fileId, cycles, 0, expTime).some)
            (update(s), update(s))
        } >>= {
          case NSObsState(NSConfig.NodAndShuffle(_, _, _, _), Some(curr)) =>
            sim.log(s"Simulate Gmos N&S observation ${curr.show}") *>
              // Initial N&S obs
              sim.observe(fileId, expTime).as(ObserveCommandResult.Paused)
          case NSObsState(_, _)                                           =>
            sim.observe(fileId, expTime) // Regular observation
        }

      override def applyConfig(config: GmosConfig[T]): F[Unit] =
        nsConfig.set(NSObsState.fromConfig(config.ns)) *> // Keep the state of NS Config
          sim.applyConfig(config)

      override def stopObserve: F[Unit] = sim.stopObserve

      override def abortObserve: F[Unit] = sim.abortObserve

      override def endObserve: F[Unit] = sim.endObserve

      override def pauseObserve: F[Unit] = sim.pauseObserve

      override def resumePaused(expTime: Time): F[ObserveCommandResult] =
        nsConfig.modify {
          case s @ NSObsState(NSConfig.NodAndShuffle(_, _, _, _), Some(curr))
              if !curr.lastSubexposure =>
            // We should keep track of where on a N&S Sequence are we
            // Let's just increase the exposure counter
            val upd = NSObsState.exposureCount.modify(_ + 1)
            (upd(s), upd(s))
          case s =>
            (s, s)
        } >>= {
          case NSObsState(NSConfig.NodAndShuffle(_, _, _, _), Some(curr))
              if !curr.lastSubexposure =>
            sim.log(s"Next Nod ${curr.show}") *>
              sim.observe(curr.fileId, expTime).as(ObserveCommandResult.Paused)
          case NSObsState(NSConfig.NodAndShuffle(_, _, _, _), Some(curr)) if curr.lastSubexposure =>
            sim.log(s"Final Nod ${curr.show}") *>
              sim.observe(curr.fileId, expTime)
          case _                                                                                  =>
            // Regular observation
            sim.resumePaused
        }

      override def stopPaused: F[ObserveCommandResult] = sim.stopPaused

      override def abortPaused: F[ObserveCommandResult] = sim.abortPaused

      private def classicObserveProgress(
        total:   Time,
        elapsed: ElapsedTime
      ): Stream[F, Progress] = sim.observeCountdown(total, elapsed)

      private def nsObserveProgress(
        total:   Time,
        elapsed: ElapsedTime,
        curr:    NSCurrent
      ): Stream[F, Progress] = (
        if (curr.firstSubexposure)
          Stream.emit(ObsProgress(total, RemainingTime(total), ObserveStage.Preparing)) ++
            countdown[F](total, elapsed.self)
        else if (curr.lastSubexposure)
          countdown[F](total, elapsed.self) ++
            Stream.emit(ObsProgress(total, RemainingTime(0.0.seconds), ObserveStage.ReadingOut))
        else countdown[F](total, elapsed.self)
      ).map { p =>
        val sub = NSSubexposure(tag[NsCyclesI][Int](curr.totalCycles),
                                tag[NsCyclesI][Int](curr.cycle),
                                curr.stageIndex
        )
        p.toNSProgress(sub.getOrElse(NSSubexposure.Zero))
      }

      override def observeProgress(
        total:   Time,
        elapsed: ElapsedTime
      ): Stream[F, Progress] =
        Stream.eval(nsConfig.get).flatMap {
          case NSObsState(NSConfig.NodAndShuffle(_, _, _, _), Some(curr)) =>
            nsObserveProgress(total, elapsed, curr)
          case _                                                          => classicObserveProgress(total, elapsed)
        }

      override def nsCount: F[Int] = nsConfig.get.map(_.current.foldMap(_.exposureCount))
    }

  def south[F[_]: Async: Logger]: F[GmosController[F, SouthTypes]] =
    (Ref.of(NSObsState.Zero), InstrumentControllerSim[F](s"GMOS South")).mapN { (nsConfig, sim) =>
      GmosControllerSim[F, SouthTypes](sim, nsConfig)
    }

  def north[F[_]: Async: Logger]: F[GmosController[F, NorthTypes]] =
    (Ref.of(NSObsState.Zero), InstrumentControllerSim[F](s"GMOS North")).mapN { (nsConfig, sim) =>
      GmosControllerSim[F, NorthTypes](sim, nsConfig)
    }
}
