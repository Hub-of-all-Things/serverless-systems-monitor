package org.hatdex.slack

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.lambda.runtime.Context
import SlackModels.messageJsonFormat
import org.hatdex.serverless
import org.hatdex.serverless.aws.EventJsonProtocol.eventReads
import org.hatdex.serverless.aws.{Event, LambdaHandlerAsync}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.libs.ws.{DefaultBodyWritables, JsonBodyReadables}

import scala.concurrent.{ExecutionContext, Future}

class PostMessageHandler extends LambdaHandlerAsync[Event[SlackModels.Message], Seq[SlackModels.Message]] with JsonBodyReadables with DefaultBodyWritables {
  override implicit val executionContext: ExecutionContext = Client.executionContext
  import SlackModels._

  override protected def handle(event: Event[SlackModels.Message], context: Context): Future[Seq[SlackModels.Message]] = {
    logger.info(s"Handling request $event to post to slack")
    val published = event.Records flatMap { record =>
      record.Sns map { sns =>
        postMessage(sns.Message)
      }
    }

    Future.sequence(published)
  }

  def postMessage(message: SlackModels.Message): Future[Message] = {
    Client.wsClient.url("https://slack.com/api/chat.postMessage")
      .post(Map[String, Seq[String]](
        "token" -> Seq(Client.slackApiKey),
        "channel" -> Seq(Client.slackChannel),
        "text" -> Seq(message.text),
        "attachments"-> Seq(Json.toJson(message.attachments).toString)
      ))
      .map(_ => message)
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
