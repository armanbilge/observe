package edu.gemini.seqexec.engine

import scalaz._
import Scalaz._

/**
 * Flag to indicate whether the global execution is `Running` or `Waiting`.
 */
sealed trait Status

object Status {
  case object Waiting   extends Status
  case object Completed extends Status
  case object Running   extends Status
}
/**
  * This is the top level state exposed by the `Engine`. This is what gets
  * generated whenever it needs to react to an `Event`.
  */
sealed trait QState {

  /**
    * Returns a new `State` where the next pending `Sequence` is been made the
    * current `Sequence` under execution and the previous current `Sequence` is
    * placed in the completed `Queue`.
    *
    * If the current `Sequence` has `Step`s not completed or there are no more
    * pending `Seqeunce`s it returns `None`.
    */
  val next: Option[QState]

  val status: Status

  val pending: List[Sequence[Action]]

  /**
    * Current Execution
    */
  val current: Execution

  val done: List[Sequence[Result]]

  /**
    * Given an index of a current `Action` it replaces such `Action` with the
    * `Result` and returns the new modified `State`.
    *
    * If the index doesn't exist, the new `State` is returned unmodified.
    */
  def mark(i: Int)(r: Result): QState

  /**
    * Unzip `State`. This creates a single `Queue` with either completed `Sequence`s
    * or pending `Sequence`s.
    */
  val toQueue: Queue[Action \/ Result]

}

object QState {

  val status: QState @> Status =
    // `QState` doesn't provide `.copy`
    Lens.lensu(
      (qs, s) => (
        qs match {
          // TODO: Isn't there a better way to write this?
          case Initial(st, _) => Initial(st, s)
          case Zipper(st, _) => Zipper(st, s)
          case Final(st, _) => Final(st, s)
        }
      ),
      _.status
    )

  /**
    * Initialize a `State` passing a `Queue` of pending `Sequence`s.
    */
  // TODO: Make this function `apply`?
  def init(q: Queue[Action]): QState = Initial(q, Status.Waiting)


  /**
    * Initial `State`. This doesn't have any `Sequence` under execution, there are
    * only pending `Step`s.
    *
    */
  case class Initial(queue: Queue[Action], status: Status) extends QState { self =>

    val next: Option[QState] =
      Queue.Zipper.currentify(queue).map(Zipper(_, status))

    val pending: List[Sequence[Action]] = queue.sequences

    val current: Execution = Execution.empty

    val done: List[Sequence[Result]] = Nil

    def mark(i: Int)(r: Result): QState = self

    val toQueue: Queue[Action \/ Result] = queue.map(_.left)

  }

  /**
    * This is the `State` in Zipper mode, which means is under execution.
    *
    */
  case class Zipper(zipper: Queue.Zipper, status: Status) extends QState { self =>

    val next: Option[QState] = zipper.next match {
      // Last execution
      case None    => zipper.uncurrentify.map(Final(_, status))
      case Some(x) => Some(Zipper(x, status))
    }

    /**
      * Current Execution
      */
    val current: Execution =
      // Queue
      zipper
        // Sequence
        .focus
        // Step
        .focus
        // Execution
        .focus

    val pending: List[Sequence[Action]] = zipper.pending

    val done: List[Sequence[Result]] = zipper.done

    def mark(i: Int)(r: Result): QState = {

      val zipper: Zipper @> Queue.Zipper =
        Lens.lensu((qs, z) => qs.copy(zipper = z), _.zipper)

      val current: Zipper @> Execution = zipper >=> Queue.Zipper.current

      current.mod(_.mark(i)(r), self)

    }

    val toQueue: Queue[Action \/ Result] =
      Queue(
        done.map(_.map(_.right)) ++
        List(zipper.focus.toSequence) ++
        pending.map(_.map(_.left))
      )

  }

  /**
    * Final `State`. This doesn't have any `Sequence` under execution, there are
    * only completed `Step`s.
    *
    */
  case class Final(queue: Queue[Result], status: Status) extends QState { self =>

    val next: Option[QState] = None

    val current: Execution = Execution.empty

    val pending: List[Sequence[Action]] = Nil

    val done: List[Sequence[Result]] = queue.sequences

    def mark(i: Int)(r: Result): QState = self

    val toQueue: Queue[Action \/ Result] = queue.map(_.right)

  }

}
