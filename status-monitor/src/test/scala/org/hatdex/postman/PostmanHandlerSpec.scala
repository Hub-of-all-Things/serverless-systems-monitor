package org.hatdex.postman

import java.io.ByteArrayOutputStream

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.util.StringInputStream
import org.hatdex.serverless.aws.SNSClient
import org.hatdex.slack.SlackModels
import org.slf4j.{Logger, LoggerFactory}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

class PostmanHandlerSpec extends Specification with PostmanHandlerContext {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  "ProcessNewmanNotification" should {
    "Translate successful collection run to Slack message with attachments" in {
      val is = this.getClass.getClassLoader.getResourceAsStream("test-data/postmanSnsSuccessfulRun.json")
      val os = new ByteArrayOutputStream()

      handler.handle(is, os, contextMock)
      logger.info("Request handled")
      import org.hatdex.slack.SlackModels.messageJsonFormat
      val result = os.toString
      logger.info(s"Result: $result")
      val message = Json.parse(result).as[Seq[SlackModels.Message]]

      message.head.text must beMatching("Collection <.*> with .* requests and .* tests ran successfully")
    }

    "Translate errored collection run to Slack message with attachments" in {
      val is = this.getClass.getClassLoader.getResourceAsStream("test-data/postmanSnsErroredRun.json")
      val os = new ByteArrayOutputStream()

      handler.handle(is, os, contextMock)
      logger.info("Request handled")
      import org.hatdex.slack.SlackModels.messageJsonFormat
      val result = os.toString
      logger.info(s"Result: $result")
      val message = Json.parse(result).as[Seq[SlackModels.Message]]

      message.head.attachments.head.color must be equalTo "ed4b48"
      message.head.text must beMatching("Collection <.*> with .* requests and .* tests failed")
    }
  }
}

trait PostmanHandlerContext extends Scope with Mockito {

  val contextMock: Context = mock[Context]
  contextMock.getFunctionName returns "testFunctionName"
  contextMock.getAwsRequestId returns "requestid"
  contextMock.getRemainingTimeInMillis returns 10000

  val snsClientMock: SNSClient = mock[SNSClient]
  snsClientMock.publishSns(anyString) returns ""

  val handler: ProcessNewmanNotification = new ProcessNewmanNotification() {
    override val snsClient: SNSClient = snsClientMock
  }
}
