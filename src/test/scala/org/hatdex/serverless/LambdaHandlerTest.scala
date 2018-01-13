package org.hatdex.serverless

import java.io.ByteArrayOutputStream

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.util.StringInputStream
import org.hatdex.serverless.aws.{AnyContent, LambdaHandler, LambdaHandlerAsync}
import org.slf4j.{Logger, LoggerFactory}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.{Format, Json}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait LambdaSpecContext extends Scope with Mockito {
  object DataJsonProtocol {
    case class Ping(inputMsg: String)
    case class Pong(outputMsg: String)
    implicit val pingFormat: Format[Ping] = Json.format[Ping]
    implicit val pongFormat: Format[Pong] = Json.format[Pong]
  }

  import DataJsonProtocol._

  class EmptyPingHandler extends LambdaHandler[AnyContent, Pong]() {
    override protected def handle(i: AnyContent, c: Context): Try[Pong] = {
      logger.info("Handling contentless Ping")
      Try(Pong("pong"))
    }
  }

  class EmptyAsyncHandler extends LambdaHandlerAsync[AnyContent, Pong]() {
    implicit val executionContext: ExecutionContext = ExecutionContext.global

    override protected def handle(i: AnyContent, c: Context): Future[Pong] = {
      logger.info("Handling async Ping")
      Future {
        Thread.sleep(1000)
        Pong("pong")
      }
    }
  }

  class PingPongHandler extends LambdaHandler[Ping, Pong]() {
    override protected def handle(ping: Ping, context: Context): Try[Pong] = {
      logger.info("Handling Ping")
      Try(Pong(ping.inputMsg.reverse))
    }
  }

  val contextMock: Context = mock[Context]
  contextMock.getFunctionName returns "testFunctionName"
  contextMock.getAwsRequestId returns "requestid"
  contextMock.getRemainingTimeInMillis returns 10000

}


class LambdaSpec extends Specification with LambdaSpecContext with Mockito {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  "Lambda Handler with no content" should {
    "handle request and response" in {
      val s = "'hello'"

      val is = new StringInputStream(s)
      val os = new ByteArrayOutputStream()

      new EmptyPingHandler().handle(is, os, contextMock)
      logger.info("Request handled")
      val result = os.toString
      logger.info(s"Result: $result")

      result must contain("""{"outputMsg":"pong"}""")
    }

    "handle request with asynchrony" in {
      val s = "hello"

      val is = new StringInputStream(s)
      val os = new ByteArrayOutputStream()

      new EmptyAsyncHandler().handle(is, os, contextMock)
      logger.info("Request handled")
      val result = os.toString
      logger.info(s"Result: $result")

      result must contain("""{"outputMsg":"pong"}""")
    }

    "handle request and response classes with body of case classes" in {
      import DataJsonProtocol._
      val s = Json.toJson(Ping("ping")).toString

      val is = new StringInputStream(s)
      val os = new ByteArrayOutputStream()

      new PingPongHandler().handle(is, os, contextMock)
      logger.info("Request handled")
      val result = os.toString
      logger.info(s"Result: $result")

      result must contain("""{"outputMsg":"gnip"}""")
    }
  }
}
