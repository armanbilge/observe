package edu.gemini.seqexec.web.client.components

import diode.data.{Empty, Pot}
import diode.react.ReactPot._
import diode.react._
import edu.gemini.seqexec.web.client.model._
import edu.gemini.seqexec.web.client.semanticui.elements.icon.Icon.{IconAttention, IconChevronLeft, IconChevronRight}
import edu.gemini.seqexec.web.client.semanticui.elements.icon.Icon.{IconCheckmark, IconCircleNotched}
import edu.gemini.seqexec.web.client.semanticui.elements.message.CloseableMessage
import edu.gemini.seqexec.web.client.services.HtmlConstants.{nbsp, iconEmpty}
import edu.gemini.seqexec.web.common.{SeqexecQueue, Sequence, SequenceState}
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scalacss.ScalaCssReact._
import scalaz.syntax.show._

object QueueTableBody {
  case class Props(queue: ModelProxy[Pot[SeqexecQueue]])

  // Minimum rows to display, pad with empty rows if needed
  val minRows = 5

  def emptyRow(k: String) = {
    <.tr(
      ^.key := k, // React requires unique keys
      <.td(iconEmpty),
      <.td(nbsp),
      <.td(nbsp),
      <.td(nbsp),
      <.td(
        SeqexecStyles.notInMobile,
        nbsp)
    )
  }

  def load(p: Props):Callback =
    // Request to load the queue if not present
    Callback.when(p.queue.value.isEmpty)(p.queue.dispatch(UpdatedQueue(Empty)))

  def showSequence(p: Props,s: Sequence):Callback =
    // Request to display the selected sequence
    p.queue.dispatch(SelectToDisplay(s))

  val component = ReactComponentB[Props]("QueueTableBody")
    .render_P( p =>
      <.tbody(
        // Render after data arrives
        p.queue().render( q =>
          q.queue.map(Some.apply).padTo(minRows, None).zipWithIndex.collect {
            case (Some(s), i) =>
              <.tr(
                ^.classSet(
                  "positive" -> (s.state == SequenceState.Completed),
                  "warning"  -> (s.state == SequenceState.Running),
                  "negative" -> (s.state == SequenceState.Error),
                  "negative" -> (s.state == SequenceState.Abort)
                ),
                ^.key := s"item.queue.$i",
                ^.onClick --> showSequence(p, s),
                <.td(
                  ^.cls := "collapsing",
                  s.state match {
                    case SequenceState.Completed                   => IconCheckmark
                    case SequenceState.Running                     => IconCircleNotched.copy(IconCircleNotched.p.copy(loading = true))
                    case SequenceState.Error | SequenceState.Abort => IconAttention
                    case _                                         => iconEmpty
                  }
                ),
                <.td(
                  ^.cls := "collapsing",
                  s.id
                ),
                <.td(s.state.shows + s.runningStep.map(u => s" ${u._1 + 1}/${u._2}").getOrElse("")),
                <.td(s.instrument),
                <.td(
                  SeqexecStyles.notInMobile,
                  s.error.map(e => <.p(IconAttention, s" $e")).getOrElse(<.p("-"))
                )
              )
            case (_, i) =>
              emptyRow(s"item.queue.$i")
          }
        ),
        // Render some rows when pending
        p.queue().renderPending(_ => (0 until minRows).map(i => emptyRow(s"item.queue.$i"))),
        // Render some rows even if it failed
        p.queue().renderFailed(_ => (0 until minRows).map(i => emptyRow(s"item.queue.$i")))
      )
    )
    .componentDidMount($ => load($.props))
    .build

  def apply(p: ModelProxy[Pot[SeqexecQueue]]) = component(Props(p))

}

/**
  * Shows a message when there is an error loading the queue
  */
object LoadingErrorMsg {
  case class Props(queue :ModelProxy[Pot[SeqexecQueue]])

  val component = ReactComponentB[Props]("LoadingErrorMessage")
    .stateless
    .render_P( p =>
      <.div(
        p.queue().renderFailed(_ =>
          CloseableMessage(CloseableMessage.Props(Some("Sorry, there was an error reading the queue from the server"), CloseableMessage.Style.Negative))
        )
      )
    )
    .build

  def apply(p: ModelProxy[Pot[SeqexecQueue]]) = component(Props(p))
}

/**
  * Component for the title of the queue area, including the search component
  */
object QueueAreaTitle {
  val component = ReactComponentB[Unit]("QueueAreaTitle")
    .stateless
    .render(_ =>
      TextMenuSegment("Queue",
        <.div(
          ^.cls := "right menu",
          ^.key := "queue.area.title",
          SeqexecCircuit.connect(_.searchResults)(SequenceSearch(_))
        )
      )
    ).build

  def apply() = component()
}

/**
  * Container for the queue table
  */
object QueueTableSection {
  val component = ReactComponentB[Unit]("QueueTableSection")
    .stateless
    .render( _ =>
      <.div(
        ^.cls := "segment",
        <.table(
          ^.cls := "ui selectable compact celled table unstackable",
          <.thead(
            <.tr(
              <.th(iconEmpty),
              <.th("Obs ID"),
              <.th("State"),
              <.th("Instrument"),
              <.th(
                SeqexecStyles.notInMobile,
                "Notes"
              )
            )
          ),
          SeqexecCircuit.connect(_.queue)(QueueTableBody(_))
        )
      )
    ).build

  def apply() = component()

}

/**
  * Displays the elements on the queue
  */
object QueueArea {
  case class Props(searchArea: ModelProxy[SectionVisibilityState])

  val component = ReactComponentB[Props]("QueueArea")
    .stateless
    .render_P(p =>
      <.div(
        ^.cls := "ui raised segments container",
        QueueAreaTitle(),
        <.div(
          ^.cls := "ui attached segment",
          <.div(
            ^.cls := "ui divided grid",
            <.div(
              ^.cls := "stretched row",
              <.div(
                ^.cls := "column",
                ^.classSet(
                  "ten wide"     -> (p.searchArea() == SectionOpen),
                  "sixteen wide" -> (p.searchArea() == SectionClosed)
                ),
                // Show a loading indicator if we are waiting for server data
                {
                  // Special equality check to avoid certain UI artifacts
                  implicit val eq = PotEq.seqexecQueueEq
                  SeqexecCircuit.connect(_.queue)(LoadingIndicator("Loading", _))
                },
                // If there was an error on the process display a message
                SeqexecCircuit.connect(_.queue)(LoadingErrorMsg(_)),
                QueueTableSection()
              ),
              p.searchArea() == SectionOpen ?= SequenceSearchResults() // Display the search area if open
            )
          )
        )
      )
    )
    .build

  def apply(p: ModelProxy[SectionVisibilityState]) = component(Props(p))

}
