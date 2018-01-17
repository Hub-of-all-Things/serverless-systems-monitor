package org.hatdex.travis

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.lambda.runtime.Context
import org.hatdex.serverless.aws.{AnyContent, LambdaProxyHandlerAsync}
import play.api.libs.json.JsValue
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.Future

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
