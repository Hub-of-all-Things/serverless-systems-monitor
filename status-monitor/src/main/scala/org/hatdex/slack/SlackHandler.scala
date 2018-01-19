package org.hatdex.slack

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.lambda.runtime.Context
import org.hatdex.postman.SlackModels
import org.hatdex.serverless.aws.{AnyContent, LambdaHandlerAsync}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{DefaultBodyWritables, JsonBodyReadables}
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.{ExecutionContext, Future}

class PostMessageHandler extends LambdaHandlerAsync[SlackModels.Message, AnyContent] with JsonBodyReadables with DefaultBodyWritables {
  override implicit val executionContext: ExecutionContext = Client.executionContext
  import SlackModels._

  override protected def handle(message: Message, context: Context): Future[AnyContent] = {
    logger.info(s"Handling request $message to post to slack")
    Client.wsClient.url("https://slack.com/api/chat.postMessage")
      .post(Map[String, Seq[String]](
        "token" -> Seq(Client.slackApiKey),
        "channel" -> Seq(Client.slackChannel),
        "text" -> Seq(message.text),
        "attachments"-> Seq(Json.toJson(message.attachments).toString)
      ))
      .map { response =>
        logger.info(s"Response to request ${context.getAwsRequestId}: ${response.body[JsValue]}")
        ""
      }
  }
}

object Client {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContext = system.dispatcher
  val wsClient = StandaloneAhcWSClient()
  val slackApiKey: String = sys.env("SLACK_API_KEY")
  val slackChannel: String = sys.env("SLACK_CHANNEL")
  logger.info(s"Running with SLACK API Key $slackApiKey")
}
