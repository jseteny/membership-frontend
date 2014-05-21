package controllers

import play.api.mvc.{ Action, Controller }
import actions.AuthenticatedAction

trait Joiner extends Controller {

  def tierList = Action {
    Ok(views.html.joiner.tierList())
  }

  def friend() = Action {
    Ok(views.html.joiner.tier.friend())
  }

  def partner() = Action {
    Ok(views.html.joiner.tier.partner())
  }

  def patron() = Action {
    Ok(views.html.joiner.tier.patron())
  }

  def paymentFriend() = AuthenticatedAction {
    Ok(views.html.joiner.payment.friend())
  }

  def paymentPartner() = AuthenticatedAction {
    Ok(views.html.joiner.payment.partner())
  }

  def paymentPatron() = AuthenticatedAction {
    Ok(views.html.joiner.payment.patron())
  }

  def thankyouFriend() = AuthenticatedAction {
    Ok(views.html.joiner.thankyou.friend())
  }

  def thankyouPartner() = AuthenticatedAction {
    Ok(views.html.joiner.thankyou.partner())
  }

  def thankyouPatron() = AuthenticatedAction {
    Ok(views.html.joiner.thankyou.patron())
  }

}

object Joiner extends Joiner