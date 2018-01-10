package todos

import java.util.UUID

import awscala.Region
import awscala.dynamodbv2.{DynamoDB, Table, cond}
import com.amazonaws.services.lambda.runtime.Context
import org.hatdex.serverless.aws.LambdaProxyHandler
import org.hatdex.serverless.aws.AnyContent
import play.api.libs.json.Json
import todos.DataJsonProtocol._

import scala.util.Try


object DataJsonProtocol {
  case class Ping(inputMsg: String)

  case class Pong(outputMsg: String)

  case class CreateRequest(body: String)

  implicit val pingFormat = Json.format[Ping]
  implicit val pongFormat = Json.format[Pong]
  implicit val createRequestFormat = Json.format[CreateRequest]
}

class GetAllHandler extends LambdaProxyHandler[AnyContent, List[String]] {
  implicit protected val dynamoDB = Handler.dynamoDB
  override protected def handleProxied(context: Context): Try[List[String]] = {
    val logger = context.getLogger
    logger.log("Handling Ping")
    Try {
      val results = Handler.table.scan(Seq("item" -> cond.isNotNull)).toList
      results.flatMap(res => res.attributes.filter(_.name == "item").map(attr => attr.value.s.get))
    }
  }
}

class CreateHandler extends LambdaProxyHandler[CreateRequest, String] {
  implicit protected val dynamoDB = Handler.dynamoDB
  override protected def handleProxied(input: CreateRequest, context: Context): Try[String] = {
    Try {
      val id = UUID.randomUUID().toString
      Handler.table.put(id, "item" -> input.body)
      id
    }
  }
}

class PingPongHandler extends LambdaProxyHandler[Ping, Pong] {
  override protected def handleProxied(ping: Ping, context: Context): Try[Pong] = {
    val logger = context.getLogger
    logger.log("Handling Ping")
    Try(Pong(ping.inputMsg.reverse))
  }
}


object Handler {
  val dynamoDB: DynamoDB = DynamoDB.at(Region.EU_WEST_1)

  val table: Table = dynamoDB.table("todos").get
}
