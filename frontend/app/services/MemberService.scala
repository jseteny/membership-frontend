package services

import com.gu.identity.model.User
import com.gu.membership.salesforce.Member.Keys
import com.gu.membership.salesforce._
import configuration.Config
import controllers.IdentityRequest
import forms.MemberForm._
import model.Eventbrite.{EBDiscount, EBEvent}
import model.Stripe.Card
import monitoring.{MemberMetrics, IdentityApiMetrics}
import play.api.Logger
import utils.ScheduledTask

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

case class MemberServiceError(s: String) extends Throwable {
  override def getMessage: String = s
}

trait MemberService {
  def commonData(user: User, formData: JoinForm, tier: Tier.Tier) = Map(
    Keys.EMAIL -> user.getPrimaryEmailAddress,
    Keys.FIRST_NAME -> formData.name.first,
    Keys.LAST_NAME -> formData.name.last,
    Keys.OPT_IN -> true,
    Keys.TIER -> tier.toString,
    Keys.MAILING_POSTCODE -> formData.deliveryAddress.postCode,
    Keys.ALLOW_MEMBERSHOP_MAIL -> true
  ) ++
    formData.marketingChoices.thirdParty.map( Keys.ALLOW_THIRD_PARTY_EMAIL -> _) ++
    formData.marketingChoices.gnm.map( Keys.ALLOW_GU_RELATED_MAIL -> _)



  def createFriend(user: User, formData: FriendJoinForm, identityRequest: IdentityRequest): Future[String] = {
    formData.password.map(updateUserPassword(user, _ , identityRequest))
    for {
      memberId <- MemberRepository.upsert(user.id, commonData(user: User, formData, Tier.Friend))
      subscription <- SubscriptionService.createFriendSubscription(memberId, formData.name, formData.deliveryAddress)
      identityResponse <- IdentityService.updateUserFieldsBasedOnJoining(user, formData, identityRequest)
    } yield {
      Logger.info(s"Identity status response: ${identityResponse.status.toString} for user ${user.id}")
      IdentityApiMetrics.putUpdateUserDetailsResponse(identityResponse.status)
      MemberMetrics.putSignUp(Tier.Friend)
      memberId.account
    }
  }

  def createPaidMember(user: User, formData: PaidMemberJoinForm, identityRequest: IdentityRequest): Future[String] = {
    formData.password.map(updateUserPassword(user, _ , identityRequest))

    for {
      customer <- StripeService.Customer.create(user.id, formData.payment.token)

      updatedData = commonData(user, formData, formData.tier) ++ Map(
        Keys.CUSTOMER_ID -> customer.id,
        Keys.DEFAULT_CARD_ID -> customer.card.id
      )
      memberId <- MemberRepository.upsert(user.id, updatedData)
      subscription <- SubscriptionService.createPaidSubscription(memberId, customer, formData.tier,
        formData.payment.annual, formData.name, formData.deliveryAddress)
      identityResponse <- IdentityService.updateUserFieldsBasedOnJoining(user, formData, identityRequest)
    } yield {
      Logger.info(s"Identity status response for fields update: ${identityResponse.status.toString} for user ${user.id}")
      IdentityApiMetrics.putUpdateUserDetailsResponse(identityResponse.status)
      MemberMetrics.putSignUp(formData.tier)
      memberId.account
    }
  }

  private def updateUserPassword(user: User, password: String, identityRequest: IdentityRequest) {
    for (identityResponse <- IdentityService.updateUserPassword(password, identityRequest))
    yield {
      Logger.info(s"Identity status response for password update: ${identityResponse.status.toString} for user ${user.id}")
      IdentityApiMetrics.putPasswordUpdateResponse(identityResponse.status)
    }
  }


  def createDiscountForMember(member: Member, event: EBEvent): Future[Option[EBDiscount]] = {
    // code should be unique for each user/event combination
    if (member.tier >= Tier.Partner) {
      EventbriteService.createOrGetDiscount(event.id, DiscountCode.generate(s"${member.identityId}_${event.id}")).map(Some(_))
    } else Future.successful(None)
  }

  def updateDefaultCard(member: PaidMember, token: String): Future[Card] = {
    for {
      customer <- StripeService.Customer.updateCard(member.stripeCustomerId, token)
      memberId <- MemberRepository.upsert(member.identityId, Map(Keys.DEFAULT_CARD_ID -> customer.card.id))
    } yield customer.card
  }

  def cancelSubscription(member: Member): Future[String] = {
    val newTier = if (member.tier == Tier.Friend) Tier.None else member.tier

    for {
      subscription <- SubscriptionService.cancelSubscription(member.salesforceAccountId, member.tier == Tier.Friend)
      _ <- MemberRepository.upsert(member.identityId, Map(Keys.OPT_IN -> false, Keys.TIER -> newTier.toString))
    } yield {
      MemberMetrics.putCancel(newTier)
      ""
    }
  }

  def downgradeSubscription(member: Member, tier: Tier.Tier): Future[String] = {
    for {
      _ <- SubscriptionService.downgradeSubscription(member.salesforceAccountId, tier, false)
    } yield {
      MemberMetrics.putDowngrade(tier)
      ""
    }
  }

  // TODO: this currently only handles free -> paid
  def upgradeSubscription(member: FreeMember, user: User, tier: Tier.Tier, form: PaidMemberChangeForm, identityRequest: IdentityRequest): Future[String] = {
    for {
      customer <- StripeService.Customer.create(user.id, form.payment.token)
      _ <- SubscriptionService.createPaymentMethod(member.salesforceAccountId, customer)
      subscription <- SubscriptionService.upgradeSubscription(member.salesforceAccountId, tier, form.payment.annual)
      memberId <- MemberRepository.upsert(
        member.identityId,
        Map(
          Keys.TIER -> tier.toString,
          Keys.CUSTOMER_ID -> customer.id,
          Keys.DEFAULT_CARD_ID -> customer.card.id
        )
      )
      identity <- IdentityService.updateUserFieldsBasedOnUpgrade(user, form, identityRequest)
    } yield {
      MemberMetrics.putUpgrade(tier)
      memberId.account
    }
  }
}

object MemberService extends MemberService

object MemberRepository extends MemberRepository with ScheduledTask[Authentication] {
  val initialValue = Authentication("", "")
  val initialDelay = 0.seconds
  val interval = 2.hours

  def refresh() = salesforce.getAuthentication

  val salesforce = new Scalaforce {
    val consumerKey = Config.salesforceConsumerKey
    val consumerSecret = Config.salesforceConsumerSecret

    val apiURL = Config.salesforceApiUrl
    val apiUsername = Config.salesforceApiUsername
    val apiPassword = Config.salesforceApiPassword
    val apiToken = Config.salesforceApiToken

    def authentication: Authentication = agent.get()
  }
}
