package org.hatdex.serverless.aws

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.Context
import org.hatdex.serverless.aws.proxy._
import play.api.libs.json.{Reads, Writes}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


abstract class LambdaProxyHandler[I, O]()(implicit val iReads: Reads[I], val oWrites: Writes[O])
  extends LambdaStreamHandler[ProxyRequest[I], ProxyResponse[O]] {

  implicit protected val inputReads: Reads[ProxyRequest[I]] = JsonProtocol.RequestJsonReads[I](iReads)
  implicit protected val outputWrites: Writes[ProxyResponse[O]] = JsonProtocol.ResponseJsonWrites[O](oWrites)

  final def handle(input: InputStream, output: OutputStream, context: Context): Unit =
    handle(handleAsync _)(input, output, context)

  protected val executionContext: ExecutionContext = ExecutionContext.global
  final protected def handleAsync(i: ProxyRequest[I], c: Context): Future[ProxyResponse[O]] = {
    val result = i.body.map(handle(_, c))
      .getOrElse(handle(c))

    Future.successful(ProxyResponse(result))
  }

  protected def handle(i: I, c: Context): Try[O] = handle(c)

  protected def handle(c: Context): Try[O] = Failure(BadRequestError("Empty Request Body"))
}

abstract class LambdaProxyHandlerAsync[I, O]()(implicit val iReads: Reads[I], val oWrites: Writes[O])
  extends LambdaStreamHandler[ProxyRequest[I], ProxyResponse[O]] {

  protected implicit val inputReads: Reads[ProxyRequest[I]] = JsonProtocol.RequestJsonReads[I](iReads)
  protected implicit val outputWrites: Writes[ProxyResponse[O]] = JsonProtocol.ResponseJsonWrites[O](oWrites)

  final def handle(input: InputStream, output: OutputStream, context: Context): Unit =
    handle(handleAsync _)(input, output, context)

  protected implicit val executionContext: ExecutionContext = ExecutionContext.global
  final protected def handleAsync(i: ProxyRequest[I], c: Context): Future[ProxyResponse[O]] = {
    val result = i.body.map(handle(_, c))
      .getOrElse(handle(c))

    result.map(r => ProxyResponse(Success(r)))
      .recover {
        case e: ErrorResponse => ProxyResponse(Failure(e))
        case e => ProxyResponse(Failure(InternalServerError("Unexpected error", e)))
      }
  }

  protected def handle(i: I, c: Context): Future[O] = handle(c)

  protected def handle(c: Context): Future[O] = Future.failed(BadRequestError("Empty Request Body"))
}
