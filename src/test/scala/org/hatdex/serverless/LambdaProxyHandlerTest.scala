package org.hatdex.serverless

import java.io.ByteArrayOutputStream

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.util.StringInputStream
import org.hatdex.serverless.aws.{AnyContent, LambdaProxyHandler, LambdaProxyHandlerAsync}
import org.slf4j.{Logger, LoggerFactory}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.{Format, Json}

import scala.concurrent.Future
import scala.io.Source
import scala.util.Try

trait ProxyLambdaSpecContext extends Scope with Mockito {
  object DataJsonProtocol {
    case class Ping(inputMsg: String)
    case class Pong(outputMsg: String)
    implicit val pingFormat: Format[Ping] = Json.format[Ping]
    implicit val pongFormat: Format[Pong] = Json.format[Pong]
  }

  import DataJsonProtocol._

  class EmptyPingHandler extends LambdaProxyHandler[AnyContent, Pong] {
    override protected def handle(c: Context): Try[Pong] = {
      logger.info("Handling contentless Ping")
      Try(Pong("pong"))
    }
  }

  class EmptyAsyncHandler extends LambdaProxyHandlerAsync[AnyContent, Pong] {
    override protected def handle(c: Context): Future[Pong] = {
      logger.info("Handling async Ping")
      Future {
        Thread.sleep(1000)
        Pong("pong")
      }
    }
  }

  class PingPongHandler extends LambdaProxyHandler[Ping, Pong] {
    override protected def handle(ping: Ping, context: Context): Try[Pong] = {
      logger.info("Handling Ping")
      Try(Pong(ping.inputMsg.reverse))
    }
  }

  class PingPongContextlessHandler extends LambdaProxyHandler[Ping, Pong] {
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


class ProxyLambdaSpec extends Specification with ProxyLambdaSpecContext with Mockito {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  "Lambda Proxy Handler with no content" should {
    "handle request and response classes with no body" in {
      val s = Source.fromResource("proxyInput-empty.json").mkString

      val is = new StringInputStream(s)
      val os = new ByteArrayOutputStream()

      new EmptyPingHandler().handle(is, os, contextMock)
      logger.info("Request handled")
      val result = os.toString
      logger.info(s"Result: $result")

      result must startWith("{")
      result must contain(""""body":"{\"outputMsg\":\"pong\"}"""")
      result must endWith("}")
    }

    "handle request with asynchrony" in {
      val s = Source.fromResource("proxyInput-empty.json").mkString

      val is = new StringInputStream(s)
      val os = new ByteArrayOutputStream()

      new EmptyAsyncHandler().handle(is, os, contextMock)
      logger.info("Request handled")
      val result = os.toString
      logger.info(s"Result: $result")

      result must startWith("{")
      result must contain(""""body":"{\"outputMsg\":\"pong\"}"""")
      result must endWith("}")
    }
  }

  "Lambda Proxy Handler" should {
    "handle request and response classes with body of case classes and proxy request info" in {
      val s = Source.fromResource("proxyInput-case-class.json").mkString

      val is = new StringInputStream(s)
      val os = new ByteArrayOutputStream()

      new PingPongHandler().handle(is, os, contextMock)
      logger.info("Request handled")
      val result = os.toString
      logger.info(s"Result: $result")

      result must startWith("{")
      result must contain(""""body":"{\"outputMsg\":\"gnip\"}"""")
      result must endWith("}")
    }

    "handle request and response classes with body of case classes" in {
      val s = Source.fromResource("proxyInput-case-class.json").mkString

      val is = new StringInputStream(s)
      val os = new ByteArrayOutputStream()

      new PingPongContextlessHandler().handle(is, os, contextMock)
      logger.info("Request handled")
      val result = os.toString
      logger.info(s"Result: $result")

      result must startWith("{")
      result must contain(""""body":"{\"outputMsg\":\"gnip\"}"""")
      result must endWith("}")
    }
  }
}
