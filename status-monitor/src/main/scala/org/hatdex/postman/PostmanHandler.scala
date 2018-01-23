package org.hatdex.postman

import java.time.temporal.ChronoUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.lambda.runtime.Context
import org.hatdex.postman.PostmanCollectionModels.postmanCollectionRunResult
import org.hatdex.serverless.aws.EventJsonProtocol.eventReads
import org.hatdex.serverless.aws.{Event, LambdaHandler, SNSClient}
import org.hatdex.slack.SlackModels.{Message, messageJsonFormat}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyReadables
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

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

class ProcessNewmanNotification
  extends LambdaHandler[Event[PostmanCollectionModels.PostmanCollectionRunResult], Seq[Message]] with JsonBodyReadables {
  override implicit val executionContext: ExecutionContext = Client.executionContext

  import org.hatdex.slack.SlackModels._
  val snsClient: SNSClient = new SNSClient(Client.awsRegion, Client.snsTopic)

  /*
   *
   * Example Slack message structure:
   *
   * {
        "text": "Monitor <https://monitor.getpostman.com/monitors/1e7f097f-462c-40d0-bc16-5f5acc939cb2?job=1e7fd31d-4ea9-4e20-a4ac-e7bb9c2c503e|DEX State monitor> on Collection <https://documenter.getpostman.com/collection/view/110376-cd628c63-58cb-51d8-b983-4ac1e44e94c5|DEX State> with 7 requests and 8 tests ran successfully",
        "bot_id": "B8MFKA5DZ",
        "attachments": [{
          "id": 1,
          "color": "36a64f",
          "fields": [
            { "title": "Status", "value": "Success", "short": true },
            { "title": "Tests Passed", "value": "8 of 8", "short": true },
            { "title": "Total Response Time", "value": "3196 ms", "short": true },
            { "title": "Total Requests", "value": "7", "short": true },
            { "title": "Errors", "value": "0", "short": true },
            { "title": "Warnings", "value": "0", "short": true }
          ],
          "fallback": "[no preview available]"
        }],
        "type": "message",
        "subtype": "bot_message",
        "ts": "1516377758.000039"
     },
   */

  override protected def handle(event: Event[PostmanCollectionModels.PostmanCollectionRunResult], context: Context): Try[Seq[Message]] = {
    val published = event.Records flatMap { record =>
      record.Sns map { sns =>
        val notification = buildSlackNotification(sns.Message)
        publishMessage(snsClient, notification)
      }
    }

    published.foldLeft(Try(Seq[Message]()))({
      case (Success(s), Success(m)) => Success(s :+ m)
      case (Success(_), Failure(e)) => Failure(e)
      case (Failure(e), _) => Failure(e)
    })
  }

  def buildSlackNotification(runResult: PostmanCollectionModels.PostmanCollectionRunResult): Message = {
    val stats = runResult.run.stats
    val errored: Boolean = runResult.run.error.isDefined || runResult.run.failures.nonEmpty
    Message(
      s"""Collection ${runResult.collection.info.name}
         |with ${stats.requests.total} requests and ${stats.tests.total} tests
         |${if (errored) "failed" else { "ran successfully"}}""".stripMargin.replaceAll("\n", " "),
      Seq(MessageAttachment(
        if (errored) { "ed4b48" } else { "36a64f" },
        Seq(
          AttachmentField(Some("Status"), if (errored) { Some("Failure") } else { Some("Success") }, short = true),
          AttachmentField(Some("Tests Passed"), Some(s"${stats.tests.total - stats.tests.failed - stats.tests.pending} of ${stats.tests.total}"), short = true),
          AttachmentField(Some("Total Response Time"), runResult.run.timings.completed.map(completed => s"${ChronoUnit.MILLIS.between(runResult.run.timings.started, completed)} ms"), short = true),
          AttachmentField(Some("Total Requests"), Some(s"${stats.requests.total}"), short = true),
          AttachmentField(Some("Errors"), Some(s"${runResult.run.failures.length}"), short = true)
        ),
        "[no preview available]"
      ))
    )
  }

  def publishMessage(snsClient: SNSClient, message: Message): Try[Message] = {
    Try(snsClient.publishSns(Json.toJson(message).toString))
      .map(messageId => logger.info(s"Published ${Json.toJson(message).toString} with message id $messageId"))
      .map(_ => message)
  }
}



//class GetStatusHandler extends LambdaProxyHandlerAsync[AnyContent, JsValue] with JsonBodyReadables {
//  override implicit val executionContext: ExecutionContext = Client.executionContext
//
//  override protected def handle(context: Context): Future[JsValue] = {
//    logger.info(s"Handling request ${context.getAwsRequestId}")
//    Client.wsClient.url("https://api.getpostman.com/monitors/110376-1e7f097f-462c-40d0-bc16-5f5acc939cb2")
//      .withHttpHeaders("X-Api-Key" -> Client.postmanApiKey)
//      .get()
//      .map { response =>
//        logger.info(s"Response to request ${context.getAwsRequestId}")
//        response.body[JsValue]
//      }
//  }
//
//}

object Client {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContext = system.dispatcher
  val wsClient = StandaloneAhcWSClient()
  lazy val awsRegion: String = sys.env.getOrElse("AWS_DEFAULT_REGION", "eu-west-1")
  lazy val snsTopic: String = sys.env.getOrElse("SNS_TOPIC", "UNDEFINED")
  logger.info(s"Initialised with SNS_TOPIC $snsTopic")
}