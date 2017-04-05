package edu.gemini.seqexec.web.client.components.sequence

import diode.react.ModelProxy
import edu.gemini.seqexec.model.Model.{Operator, Conditions, CloudCover, ImageQuality, SkyBackground, WaterVapor}
import edu.gemini.seqexec.web.client.semanticui.elements.dropdown.DropdownMenu
import edu.gemini.seqexec.web.client.semanticui.elements.label.Label
import edu.gemini.seqexec.web.client.semanticui.elements.input.InputEV
import edu.gemini.seqexec.web.client.model._
import edu.gemini.seqexec.web.client.services.SeqexecWebClient
import japgolly.scalajs.react.{BackendScope, Callback, ReactComponentB, ReactComponentU, TopNode}
import japgolly.scalajs.react.extra.{ExternalVar, TimerSupport}
import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.dom.html.Div

import scalaz.syntax.equal._
import scalaz.std.string._
import scalaz.std.option._
import scalaz.syntax.std.option._
import scalaz.Equal

import scala.concurrent.duration._

/**
  * Display to show headers per sequence
  */
object HeadersSideBar {
  case class Props(model: ModelProxy[HeaderSideBarReader])

  case class State(currentText: Option[String])

  object State {
    implicit val equals: Equal[State] = Equal.equalA[State]
  }

  class Backend(val $: BackendScope[Props, State]) extends TimerSupport {
    def updateState(value: String): Callback =
      $.modState(_.copy(currentText = Some(value)))

    def submitIfChanged: Callback =
      ($.state zip $.props) >>= {
        case (s, p) => Callback.when(s.currentText =/= p.model().operator)(Callback.empty) // We are not submitting until the backend properly update this property
      }

    def iqChanged(iq: ImageQuality): Callback =
      $.props >>= {_.model.dispatchCB(UpdateImageQuality(iq))}

    def ccChanged(i: CloudCover): Callback =
      $.props >>= {_.model.dispatchCB(UpdateCloudCover(i))}

    def sbChanged(sb: SkyBackground): Callback =
      $.props >>= {_.model.dispatchCB(UpdateSkyBackground(sb))}

    def wvChanged(wv: WaterVapor): Callback =
      $.props >>= {_.model.dispatchCB(UpdateWaterVapor(wv))}

    def render(p: Props, s: State): ReactTagOf[Div] = {
      val enabled = p.model().status.isLogged && p.model().status.anySelected

      val operatorEV = ExternalVar(s.currentText.getOrElse(""))(updateState)
      <.div(
        ^.cls := "ui raised secondary segment",
        <.h4("Headers"),
        <.div(
          ^.cls := "ui form",
          <.div(
            ^.cls := "required field",
            Label(Label.Props("Operator", "operator")),
            InputEV(InputEV.Props("operator", "operator",
              operatorEV,
              placeholder = "Operator...",
              disabled = !p.model().status.isLogged,
              onBlur = name => Callback.empty// >> p.operator.dispatchCB(UpdateOperator(name)))) TODO Enable when the backend accepts this property
            ))
          ),

          DropdownMenu(DropdownMenu.Props("Image Quality", p.model().conditions.iq.some, "Select", ImageQuality.all, disabled = !enabled, iqChanged)),
          DropdownMenu(DropdownMenu.Props("Cloud Cover", p.model().conditions.cc.some, "Select", CloudCover.all, disabled = !enabled, ccChanged)),
          DropdownMenu(DropdownMenu.Props("Water Vapor", p.model().conditions.wv.some, "Select", WaterVapor.all, disabled = !enabled, wvChanged)),
          DropdownMenu(DropdownMenu.Props("Sky Background", p.model().conditions.sb.some, "Select", SkyBackground.all, disabled = !enabled, sbChanged))
        )
      )
    }
  }

  private val component = ReactComponentB[Props]("HeadersSideBar")
    .initialState(State(None))
    .renderBackend[Backend]
    .configure(TimerSupport.install)
    .shouldComponentUpdate { f =>
      // If the state changes, don't update the UI
      f.$.state === f.nextState
    }
    // Every 2 seconds check if the field has changed and submit
    .componentDidMount(c => c.backend.setInterval(c.backend.submitIfChanged, 2.second))
    .build

  def apply(model: ModelProxy[HeaderSideBarReader]): ReactComponentU[Props, State, Backend, TopNode] =
    component(Props(model))
}
