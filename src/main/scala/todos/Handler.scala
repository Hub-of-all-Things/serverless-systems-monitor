package todos

import java.util.UUID

import awscala.Region
import awscala.dynamodbv2.{DynamoDB, Table, cond}
import com.amazonaws.services.lambda.runtime.Context
import org.hatdex.serverless.aws.LambdaProxyHandler
import org.hatdex.serverless.aws.AnyContent
import org.hatdex.serverless.aws.proxy.{ProxyRequest, ProxyResponse}
import play.api.libs.json._
import todos.DataJsonProtocol._

import scala.reflect.ClassTag
import scala.util.{Success, Try}


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
  override protected def handle[T: ClassTag](r: ProxyRequest[AnyContent])(implicit context: Context): Try[ProxyResponse[List[String]]] = {
    logger.debug("Handling GetAll")
    val result = Try {
      val results = Handler.table.scan(Seq("item" -> cond.isNotNull)).toList
      results.flatMap(res => res.attributes.filter(_.name == "item").map(attr => attr.value.s.get))
    }
    Success(ProxyResponse(result))
  }
}

class CreateHandler extends LambdaProxyHandler[CreateRequest, String] {
  implicit protected val dynamoDB = Handler.dynamoDB
  override protected def handle(input: CreateRequest, r: ProxyRequest[CreateRequest])(implicit context: Context): Try[String] = {
    Try {
      val id = UUID.randomUUID().toString
      Handler.table.put(id, "item" -> input.body)
      id
    }
  }
}

class PingPongHandler extends LambdaProxyHandler[Ping, Pong] {
  override protected def handle(ping: Ping, r: ProxyRequest[Ping])(implicit context: Context): Try[Pong] = {
    logger.debug("Handling Ping")
    Try(Pong(ping.inputMsg.reverse))
  }
}


object Handler {
  val dynamoDB: DynamoDB = DynamoDB.at(Region.EU_WEST_1)

  val table: Table = dynamoDB.table("todos").get
}
