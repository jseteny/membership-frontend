package controllers

import scala.concurrent.Future

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import services.{MemberService, EventbriteService}
import actions.AuthenticatedAction
import model.Eventbrite.EBError

import com.netaporter.uri.dsl._

trait Event extends Controller {

  val eventService: EventbriteService
  val memberService: MemberService

  def details(id: String) = CachedAction.async {
    eventService.getEvent(id)
      .map(event => Ok(views.html.event.page(event)))
      .recover { case error: EBError if error.status_code == NOT_FOUND => NotFound }
  }

  def list = CachedAction {
    Ok(views.html.event.list(eventService.getLiveEvents))
  }

  def listFilteredBy(urlTagText: String) = CachedAction {
    val tag = urlTagText.replace('-', ' ')
    Ok(views.html.event.list(eventService.getEventsTagged(tag)))
  }

  def buy(id: String) = AuthenticatedAction.async { implicit request =>
    for {
      event <- eventService.getEvent(id)
      discountSeq <- Future.sequence(memberService.createEventDiscount(request.user.id, event).toSeq)
      discountOpt = discountSeq.headOption.filter(discount => discount.quantity_available > discount.quantity_sold)
    } yield Found(event.url ? ("discount" -> discountOpt.map(_.code)))
  }
}

object Event extends Event {
  val eventService = EventbriteService
  val memberService = MemberService
}