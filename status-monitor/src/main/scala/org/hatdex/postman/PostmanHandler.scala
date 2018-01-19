package org.hatdex.postman

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.MessageAttributeValue
import org.hatdex.postman.SlackModels.Message
import org.hatdex.serverless.aws.{AnyContent, LambdaHandler, LambdaProxyHandlerAsync}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{Format, JsValue, Json}
import play.api.libs.ws.JsonBodyReadables
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

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

object PostmanCollectionModels {

  case class CollectionInfo(
    id: String,
    name: String,
    schema: String)

  case class CollectionItem(
    id: String,
    name: String,
    request: JsValue,
    response: Seq[JsValue])

  case class PostmanCollection(
    item: Seq[CollectionItem],
    info: CollectionInfo)

  case class Stat (
    total: Int,
    pending: Int,
    failed: Int)

  case class RunStats (
    iterations: Stat,
    items: Stat,
    scripts: Stat,
    prerequests: Stat,
    requests: Stat,
    tests: Stat,
    assertions: Stat,
    testScripts: Stat,
    prerequestScripts: Stat)

  case class RunTimings(
    responseAverage: Int,
    started: ZonedDateTime,
    completed: ZonedDateTime)

  case class RunTransfers(
    responseTotal: Long)

  case class PostmanCollectionRun(
    stats: RunStats,
    timings: RunTimings,
    transfers: RunTransfers,
    failures: Seq[JsValue],
    error: Option[JsValue])

  case class PostmanCollectionRunResult(
    collection: PostmanCollection,
    run: PostmanCollectionRun)

  implicit val collectionInfoJsonFormat: Format[CollectionInfo] = Json.format[CollectionInfo]
  implicit val collectionItemJsonFormat: Format[CollectionItem] = Json.format[CollectionItem]
  implicit val postmanCollectionJsonFormat: Format[PostmanCollection] = Json.format[PostmanCollection]
  implicit val statJsonFormat: Format[Stat] = Json.format[Stat]
  implicit val runStatsJsonFormat: Format[RunStats] = Json.format[RunStats]
  implicit val runTimingsJsonFormat: Format[RunTimings] = Json.format[RunTimings]
  implicit val runTransferJsonFormat: Format[RunTransfers] = Json.format[RunTransfers]
  implicit val postmanCollectionRunFormat: Format[PostmanCollectionRun] = Json.format[PostmanCollectionRun]
  implicit val postmanCollectionRunResult: Format[PostmanCollectionRunResult] = Json.format[PostmanCollectionRunResult]

}

object SlackModels {

  case class AttachmentField(
    title: Option[String],
    value: Option[String],
    short: Boolean)

  case class MessageAttachment(
    color: String,
    fields: Seq[AttachmentField],
    fallback: String)

  case class Message(
    text: String,
    attachments: Seq[MessageAttachment])

  implicit val attachmentFieldJsonFormat: Format[AttachmentField] = Json.format[AttachmentField]
  implicit val messageAttachmentJsonFormat: Format[MessageAttachment] = Json.format[MessageAttachment]
  implicit val messageJsonFormat: Format[Message] = Json.format[Message]

}

class ProcessNewmanNotification extends LambdaHandler[PostmanCollectionModels.PostmanCollectionRunResult, Message] with JsonBodyReadables {
  override implicit val executionContext: ExecutionContext = Client.executionContext

  import SlackModels._
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

  override protected def handle(runResult: PostmanCollectionModels.PostmanCollectionRunResult, context: Context): Try[Message] = {
    logger.info(s"Handling request ${context.getAwsRequestId}")
    val stats = runResult.run.stats
    val message = Message(
      s"""
         | Collection <https://documenter.getpostman.com/collection/view/${runResult.collection.info.id}|${runResult.collection.info.name}>
         | with ${stats.requests.total} requests and ${stats.tests.total} tests ran successfully""".stripMargin,
      Seq(MessageAttachment(
        "36a64f",
        Seq(
          AttachmentField(Some("Status"), Some("Success"), short = true),
          AttachmentField(Some("Tests Passed"), Some(s"${stats.tests.total - stats.tests.failed - stats.tests.pending} of ${stats.tests.total}"), short = true),
          AttachmentField(Some("Total Response Time"), Some(s"${ChronoUnit.MILLIS.between(runResult.run.timings.completed, runResult.run.timings.started)} ms"), short = true),
          AttachmentField(Some("Total Requests"), Some(s"${stats.requests.total}"), short = true),
          AttachmentField(Some("Errors"), Some(s"${runResult.run.failures.length}"), short = true)
        ),
        "[no preview available]"
      ))
    )

    val snsClient = new SNSClient(Client.awsRegion, "")

    Try(snsClient.publishSns(Json.toJson(message).toString))
        .map(messageId => logger.info(s"Published ${Json.toJson(message).toString} with message id $messageId"))
        .map(_ => message)
  }
}

class SNSClient(region: String, topicArn: String) {
  def publishSns(message: String): String = {
    val snsClient = AmazonSNSClientBuilder.standard()
      .withRegion(region)
      .build()


    import com.amazonaws.services.sns.model.PublishRequest
    val publishRequest = new PublishRequest(topicArn, message)

    val publishResult = snsClient.publish(publishRequest)

    publishResult.getMessageId
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

}

object Client {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContext = system.dispatcher
  val wsClient = StandaloneAhcWSClient()
  val postmanApiKey: String = sys.env.getOrElse("POSTMAN_API_KEY", "")
  val awsRegion: String = sys.env.getOrElse("AWS_DEFAULT_REGION", "eu-west-1")
  val snsTopic: String = sys.env("SNS_TOPIC")
  logger.info(s"Running with POSTMAN API Key $postmanApiKey")
}