package services

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.Play.current
import play.api.libs.json.Reads
import play.api.libs.ws.{WSResponse, WS}
import play.api.Logger

import com.gu.membership.salesforce.{Member, Tier}

import model.Stripe._
import model.StripeDeserializer._
import configuration.Config

trait StripeService {
  def get[A <: StripeObject](endpoint: String)(implicit reads: Reads[A]): Future[A]
  def post[A <: StripeObject](endpoint: String, data: Map[String, Seq[String]])(implicit reads: Reads[A]): Future[A]
  def delete[A <: StripeObject](endpoint: String)(implicit reads: Reads[A]): Future[A]

  private def extract[A <: StripeObject](response: WSResponse)(implicit reads: Reads[A]): A = {
    response.json.asOpt[A].getOrElse {
      throw (response.json \ "error").asOpt[Error].getOrElse(Error("internal", "Unable to extract object", None, None))
    }
  }

  object Charge {
    def create(amount: Int, currency: String, card: String, description: String): Future[Charge] = {
      post[Charge]("charges", Map(
        "amount" -> Seq(amount.toString),
        "currency" -> Seq(currency),
        "card" -> Seq(card),
        "description" -> Seq(description)
      ))
    }
  }

  object Customer {
    def create(email: String, card: String): Future[Customer] =
      post[Customer]("customers", Map("email" -> Seq(email), "card" -> Seq(card)))

    def read(customerId: String): Future[Customer] =
      get[Customer](s"customers/$customerId")

    def updateCard(customerId: String, card: String): Future[Customer] =
      post[Customer](s"customers/$customerId", Map("card" -> Seq(card)))
  }

  object Subscription {
    def create(customerId: String, planId: String): Future[Subscription] =
      post[Subscription](s"customers/$customerId/subscriptions", Map("plan" -> Seq(planId)))

    def delete(customerId: String, subscriptionId: String): Future[Subscription] =
      StripeService.this.delete[Subscription](s"customers/$customerId/subscriptions/$subscriptionId?at_period_end=true")

    def update(customerId: String, subscriptionId: String, planId: String, card: String): Future[Subscription] =
      post[Subscription](s"customers/$customerId/subscriptions/$subscriptionId", Map("plan" -> Seq(planId), "card" -> Seq(card)))
  }

  object Events {
    val eventHandlers = Map(
      "customer.subscription.deleted" -> customerSubscriptionDeleted _
    )

    def customerSubscriptionDeleted(event: Event) {
      val subscription = event.extract[Subscription]
      // TODO: This whole section will soon be deleted when we switch to Zuora
      MemberRepository.get(subscription.customer, Member.Keys.CUSTOMER_ID).foreach {
        _.filter(_.optedIn).map { member =>
          MemberRepository.upsert(member.identityId, Map(Member.Keys.TIER -> Tier.Friend.toString))
        }
      }
    }

    def handle(event: Event) {
      Logger.debug(s"Got event ${event.`type`}")
      eventHandlers.get(event.`type`).map(_(event))
    }
  }
}

object StripeService extends StripeService {
  val apiURL = Config.stripeApiURL
  val apiAuthHeader = ("Authorization", s"Bearer ${Config.stripeApiSecret}")

  def get[A <: StripeObject](endpoint: String)(implicit reads: Reads[A]): Future[A] =
    WS.url(s"$apiURL/$endpoint").withHeaders(apiAuthHeader).get().map(extract[A])

  def post[A <: StripeObject](endpoint: String, data: Map[String, Seq[String]])(implicit reads: Reads[A]): Future[A] =
    WS.url(s"$apiURL/$endpoint").withHeaders(apiAuthHeader).post(data).map(extract[A])

  def delete[A <: StripeObject](endpoint: String)(implicit reads: Reads[A]): Future[A] =
    WS.url(s"$apiURL/$endpoint").withHeaders(apiAuthHeader).delete().map(extract[A])
}
