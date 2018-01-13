package todos

import java.util.UUID

import akka.dispatch.MonitorableThreadFactory
import awscala.Region
import awscala.dynamodbv2.{DynamoDB, Table, cond}
import com.amazonaws.services.lambda.runtime.Context
import org.hatdex.serverless.aws.{AnyContent, LambdaProxyHandler}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._

import scala.util.{Random, Try}

package handler {

  object DataJsonProtocol {

    case class Ping(inputMsg: String)

    case class Pong(outputMsg: String)

    case class CreateRequest(body: String)

    implicit val pingFormat = Json.format[Ping]
    implicit val pongFormat = Json.format[Pong]
    implicit val createRequestFormat = Json.format[CreateRequest]
  }

  import todos.handler.DataJsonProtocol._

  class GetAllHandler extends LambdaProxyHandler[AnyContent, List[String]] {
    implicit protected val dynamoDB = Handler.dynamoDB

    override protected def handle(context: Context): Try[List[String]] = {
      logger.info(s"${Handler.containerId} Handling GetAll")
      Try {
        val results = Handler.table.scan(Seq("item" -> cond.isNotNull)).toList
        results.flatMap(res => res.attributes.filter(_.name == "item").map(attr => attr.value.s.get))
      }
    }
  }

  class CreateHandler extends LambdaProxyHandler[CreateRequest, String] {
    implicit protected val dynamoDB = Handler.dynamoDB

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
  val dynamoDB: DynamoDB = DynamoDB.at(Region.EU_WEST_1)

  val table: Table = dynamoDB.table("todos").get

  val containerId = Random.alphanumeric.take(5).mkString
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  logger.info(s"Started container $containerId")

  Runtime.getRuntime.addShutdownHook(
    MonitorableThreadFactory("monitoring-thread-factory", daemonic = false, Some(Thread.currentThread().getContextClassLoader))
    .newThread(new Runnable {
      override def run(): Unit = {
        logger.info(s"Closing down container $containerId")
      }
    }))
}
