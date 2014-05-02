package model

import play.api.libs.json._
import play.api.test.PlaySpecification
import scala.io.Source

class EventbriteDeserializerTest extends PlaySpecification {

  "EventbriteDeserializer" should {

    "should deserialize event json" in {
      import EventbriteDeserializer._ // class under test

      val event = Source.fromFile("test/resources/event-1.json").mkString
      val eventJson: JsValue = Json.parse(event)
      val ebResponse = eventJson.as[EBResponse]
      ebResponse.events.head.name.text === "Chris' big time jamboree"
    }
  }

}