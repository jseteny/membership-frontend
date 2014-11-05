import sbt._
import play._

object Dependencies {

  val identity = "3.42"

  //libraries
  val identityCookie = "com.gu.identity" %% "identity-cookie" % identity
  val identityModel = "com.gu.identity" %% "identity-model" % identity
  val identityTestUsers = "com.gu" %% "identity-test-users" % "0.4"
  val scalaUri = "com.netaporter" %% "scala-uri" % "0.4.1"
  val membershipCommon = "com.gu" %% "membership-common" % "0.36"
  val contentAPI = "com.gu" %% "content-api-client" % "3.5"
  val playWS = PlayImport.ws
  val playFilters = PlayImport.filters

  //projects
  val frontendDependencies = Seq(identityCookie, identityModel, identityTestUsers, scalaUri,
    membershipCommon, contentAPI, playWS, playFilters)
}
