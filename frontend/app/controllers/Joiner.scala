package controllers

import play.api.mvc.Controller
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import actions.{ MemberAction, AuthenticatedAction }
import model.Tier
import services.StripeService

trait Joiner extends Controller {

  def tierList = CachedAction { implicit request =>
    Ok(views.html.joiner.tierList())
  }

  def friend() = CachedAction { implicit request =>
    Ok(views.html.joiner.tier.friend())
  }

  def partner() = CachedAction { implicit request =>
    Ok(views.html.joiner.tier.partner())
  }

  def patron() = CachedAction { implicit request =>
    Ok(views.html.joiner.tier.patron())
  }

  def paymentPartner() = AuthenticatedAction { implicit request =>
    Ok(views.html.joiner.payment.paymentForm(Tier.Partner, 15))
  }

  def paymentPatron() = AuthenticatedAction { implicit request =>
    Ok(views.html.joiner.payment.paymentForm(Tier.Patron, 60))
  }

  def thankyouFriend() = AuthenticatedAction { implicit request =>
    Ok(views.html.joiner.thankyou.friend())
  }

  def thankyouPartner() = MemberAction.async { implicit request =>
    StripeService.Customer.read(request.member.customerId).map { customer =>
      val response = for {
        subscription <- customer.subscriptions.data.headOption
        card <- customer.cards.data.headOption
      } yield Ok(views.html.joiner.thankyou.partner(subscription, card))

      response.getOrElse(NotFound)
    }
  }

  def thankyouPatron() = MemberAction.async { implicit request =>
    StripeService.Customer.read(request.member.customerId).map { customer =>
      val response = for {
        subscription <- customer.subscriptions.data.headOption
        card <- customer.cards.data.headOption
      } yield Ok(views.html.joiner.thankyou.partner(subscription, card))

      response.getOrElse(NotFound)
    }
  }

}

object Joiner extends Joiner