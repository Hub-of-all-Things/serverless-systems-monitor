package org.hatdex.serverless.aws

import com.amazonaws.services.lambda.runtime.Context
import org.hatdex.serverless.aws.proxy._
import play.api.libs.json.{Reads, Writes}
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag
import scala.util.{Failure, Try}

abstract class LambdaProxyHandler[In, Out]()(implicit inputReads: Reads[In], outputWrites: Writes[Out])
  extends LambdaHandler[ProxyRequest[In], ProxyResponse[Out]]()(
    JsonProtocol.RequestJsonReads[In](inputReads),
    JsonProtocol.ResponseJsonWrites[Out](outputWrites)) {

  override val logger: Logger = LoggerFactory.getLogger(this.getClass)

  protected def handle()(implicit c: Context): Try[Out] = {
    logger.error("Request with no data")
    Failure(BadRequestError("Empty Request Body"))
  }

  protected def handle(i: In)(implicit c: Context): Try[Out] = {
    logger.error("Not Handling proxy request data")
    Failure(InternalServerError("Not Handling proxy request data"))
  }

  protected def handle(i: In, r: ProxyRequest[In])(implicit c: Context): Try[Out] = {
    handle(i)
  }

  override protected def handle[T : ClassTag](r: ProxyRequest[In])(implicit c: Context): Try[ProxyResponse[Out]] = {
    logger.info("Handle proxied request")
    val result = r.body.map(handle(_, r)(c))
      .getOrElse(handle()(c))
    Try(ProxyResponse(result))
  }
}
