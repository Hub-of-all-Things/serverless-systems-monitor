package org.hatdex.postman

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SNSEvent.MessageAttribute
import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.{AmazonSNSClient, AmazonSNSClientBuilder}
import org.hatdex.serverless.aws.{AnyContent, LambdaProxyHandlerAsync}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.JsonBodyReadables
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/*
 Slack travis notification template:

 Build <%{build_url}|#%{build_number}> (<%{compare_url}|%{commit}>) of %{repository}@%{branch} by %{author} %{result} in %{duration}"
 Build %{commit}: %{commit_message}

 Postman attachments:

 "attachments": [{
    "color": "36a64f",
    "fields": [
        { "title": "Status", "value": "Success", "short": true },
        { "title": "Tests Passed", "value": "8 of 8", "short": true },
        { "title": "Total Response Time", "value": "3248 ms", "short": true },
        { "title": "Total Requests", "value": "7", "short": true },
        { "title": "Errors", "value": "0", "short": true },
        { "title": "Warnings", "value": "0", "short": true } ],
    "fallback": "[no preview available]"
  }]

 */

class GetStatusHandler extends LambdaProxyHandlerAsync[AnyContent, JsValue] with JsonBodyReadables {
  override implicit val executionContext: ExecutionContext = Client.executionContext

  override protected def handle(context: Context): Future[JsValue] = {
    logger.info(s"Handling request ${context.getAwsRequestId}")
    Client.wsClient.url("https://api.getpostman.com/monitors/110376-1e7f097f-462c-40d0-bc16-5f5acc939cb2")
      .withHttpHeaders("X-Api-Key" -> Client.postmanApiKey)
      .get()
      .map { response =>
        logger.info(s"Response to request ${context.getAwsRequestId}")
        response.body[JsValue]
      }
  }

  protected def publishSns: Unit = {
    val snsClient = AmazonSNSClientBuilder.standard()
      .withRegion(Regions.EU_WEST_1.getName)
      .build()


    import com.amazonaws.services.sns.model.PublishRequest
    import com.amazonaws.services.sns.model.PublishResult
    val msg = "My text published to SNS topic with email endpoint"
    val messageAttributes = Map[String, MessageAttributeValue](
      attribute("hello", "key")
    )
    val publishRequest = new PublishRequest("", msg)
      .withMessageAttributes(messageAttributes.asJava)

    val publishResult = snsClient.publish(publishRequest)
    //print MessageId of message published to SNS topic
    System.out.println("MessageId - " + publishResult.getMessageId)
  }

  private def attribute(attributeName: String, attributeValue: JsValue): (String, MessageAttributeValue) = {
    val messageAttributeValue = new MessageAttributeValue()
      .withDataType("String")
      .withStringValue(attributeValue.toString)
    (attributeName, messageAttributeValue)
  }

  private def attribute(attributeName: String, attributeValue: String): (String, MessageAttributeValue) = {
    attribute(attributeName, Json.toJson(attributeValue))
  }


}

object Client {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContext = system.dispatcher
  val wsClient = StandaloneAhcWSClient()
  val postmanApiKey: String = sys.env.getOrElse("POSTMAN_API_KEY", "")
  logger.info(s"Running with POSTMAN API Key $postmanApiKey")
}