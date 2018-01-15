package todos

import java.util.UUID

import awscala.Region
import awscala.dynamodbv2.{DynamoDB, Table, cond}
import com.amazonaws.services.lambda.runtime.Context
import org.hatdex.serverless.aws.{AnyContent, LambdaProxyHandler, LambdaProxyHandlerAsync}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._

import scala.util.{Random, Try}

package postman {
  import akka.actor.ActorSystem
  import akka.stream.ActorMaterializer
  import play.api.libs.ws.JsonBodyReadables
  import play.api.libs.ws.ahc.StandaloneAhcWSClient

  import scala.concurrent.{ExecutionContext, Future}

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
    logger.info(s"Running with POSTMAN API Key $postmanApiKey")
  }
}

package travis {
  import akka.actor.ActorSystem
  import akka.stream.ActorMaterializer
  import play.api.libs.ws._
  import play.api.libs.ws.ahc._
  import play.api.libs.ws.ahc.StandaloneAhcWSClient
  import DefaultBodyReadables._
  import play.api.libs.ws.JsonBodyWritables._

  import scala.concurrent.Future
  import scala.util.{ Failure, Success }

  class GetStatusHandler extends LambdaProxyHandlerAsync[AnyContent, JsValue] {

    override implicit val executionContext = Client.executionContext
    override protected def handle(context: Context): Future[JsValue] = {
      import play.api.libs.ws.JsonBodyReadables._

      Client.wsClient.url("https://api.travis-ci.org/repo/Hub-of-all-Things%2FHAT2.0/branch/master")
        .withHttpHeaders(
          "Authorization" -> "token 8Ior5kc9vSLT7APMbhlJEw",
          "Cache-Control" -> "no-cache",
          "Travis-API-Version" -> "3")
        .get()
        .map { response =>
          response.body[JsValue]
        }
    }
  }

  object Client {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher
    val wsClient = StandaloneAhcWSClient()
  }

}

package handler {

  object DataJsonProtocol {

    case class Ping(inputMsg: String)

    case class Pong(outputMsg: String)

    case class CreateRequest(body: String)

    implicit val pingFormat: Format[Ping] = Json.format[Ping]
    implicit val pongFormat: Format[Pong] = Json.format[Pong]
    implicit val createRequestFormat: Format[CreateRequest] = Json.format[CreateRequest]
  }

  import todos.handler.DataJsonProtocol._

  class GetAllHandler extends LambdaProxyHandler[AnyContent, List[String]] {
    implicit protected val dynamoDB: DynamoDB = Handler.dynamoDB

    override protected def handle(context: Context): Try[List[String]] = {
      logger.info(s"${Handler.containerId} Handling GetAll")
      Try {
        val results = Handler.table.scan(Seq("item" -> cond.isNotNull)).toList
        results.flatMap(res => res.attributes.filter(_.name == "item").map(attr => attr.value.s.get))
      }
    }
  }

  class CreateHandler extends LambdaProxyHandler[CreateRequest, String] {
    implicit protected val dynamoDB: DynamoDB = Handler.dynamoDB

    override protected def handle(input: CreateRequest, context: Context): Try[String] = {
      logger.info(s"${Handler.containerId} Handling Create")
      Try {
        val id = UUID.randomUUID().toString
        Handler.table.put(id, "item" -> input.body)
        id
      }
    }
  }

  class PingPongHandler extends LambdaProxyHandler[Ping, Pong] {
    override protected def handle(ping: Ping, context: Context): Try[Pong] = {
      logger.info(s"${Handler.containerId} Handling Ping")
      Try(Pong(ping.inputMsg.reverse))
    }
  }

}


object Handler {
  lazy val dynamoDB: DynamoDB = DynamoDB.at(Region.EU_WEST_1)

  lazy val table: Table = dynamoDB.table("todos").get

  val containerId: String = Random.alphanumeric.take(5).mkString
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  logger.info(s"Started container $containerId")

//  import scala.concurrent.ExecutionContext.Implicits.global
//  val system = ActorSystem("actorSystem")
//  system.scheduler.schedule(1.second, 5.seconds) {
//    logger.info(s"$containerId Still alive")
//  }
//
//  Runtime.getRuntime.addShutdownHook(
//    MonitorableThreadFactory("monitoring-thread-factory", daemonic = false, Some(Thread.currentThread().getContextClassLoader))
//    .newThread(new Runnable {
//      override def run(): Unit = {
//        logger.info(s"Closing down container $containerId")
//      }
//    }))
}
