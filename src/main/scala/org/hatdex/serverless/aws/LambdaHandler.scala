package org.hatdex.serverless.aws

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.Context
import org.hatdex.serverless.aws.proxy.{BadRequestError, ProxyRequest, ProxyResponse}
import play.api.libs.json._

import scala.util.{Failure, Success, Try}

package object proxy {

  case class RequestInput(body: String)

  case class ProxyRequest[T](
    path: String,
    httpMethod: String,
    headers: Option[Map[String, String]] = None,
    queryStringParameters: Option[Map[String, String]] = None,
    stageVariables: Option[Map[String, String]] = None,
    body: Option[T] = None
  )

  case class ProxyResponse[T](
    statusCode: Int,
    headers: Option[Map[String, String]] = None,
    body: Either[Option[T], ErrorResponse] = Left(None)
  )

  object ProxyResponse {
    protected val defaultHeaders = Some(Map("Access-Control-Allow-Origin" -> "*"))

    def apply[T](result: Try[T]): ProxyResponse[T] = {
      result.map { b => ProxyResponse(200, defaultHeaders, Left(Some(b))) }
        .recover {
          case e: ErrorResponse => ProxyResponse[T](e.getStatus, defaultHeaders, Right(e))
          case e: RuntimeException => ProxyResponse[T](500, defaultHeaders, Right(InternalServerError("Unexpected error", e)))
          case e => ProxyResponse[T](500, defaultHeaders, Right(InternalServerError("Fatal error", e)))
        }
        .get
    }

    def apply(): ProxyResponse[String] = {
      ProxyResponse(200, defaultHeaders, Left(None))
    }
  }

  sealed abstract class ErrorResponse(val status: Int, message: String, cause: Throwable = None.orNull) extends RuntimeException(message, cause) {
    def getStatus: Int = status
  }

  case class BadRequestError(message: String, cause: Throwable = None.orNull) extends ErrorResponse(400, message, cause)

  case class InternalServerError(message: String, cause: Throwable = None.orNull) extends ErrorResponse(500, message, cause)


  protected val errorResponseWrites: Writes[ErrorResponse] = (error: ErrorResponse) => {
    val stackTrace = Option(error.getCause)
      .map(c => c.getStackTrace.toSeq.map(s => s.toString))

    JsObject(Seq(
      "status" -> JsNumber(error.getStatus),
      "error" -> JsString(error.getClass.getSimpleName),
      "message" -> JsString(error.getMessage),
      "cause" -> Json.toJson(stackTrace)))
  }

  protected def responseBodyJsonWrites[T](implicit writes: Writes[T]): Writes[Either[Option[T], ErrorResponse]] =
    (body: Either[Option[T], ErrorResponse]) => {
      body.fold(
        content => Json.toJson(content),
        error => Json.toJson(error)(errorResponseWrites)
      )
    }


  def RequestJsonReads[T](reads: Reads[T]): Reads[ProxyRequest[T]] = {
    implicit val stringReads: Reads[T] = Reads.StringReads
      .map(s => Try(Json.parse(s)))
      .collect(JsonValidationError("")) {
        case Success(js) => js
      }
      .andThen(reads)

    Json.reads[ProxyRequest[T]]
  }



  def ResponseJsonWrites[T](implicit writes: Writes[T]): Writes[ProxyResponse[T]] = (response: ProxyResponse[T]) => {
    JsObject(Map(
      "statusCode" -> JsNumber(response.statusCode),
      "headers" -> Json.toJson(response.headers),
      "body" -> JsString(Json.toJson(response.body)(responseBodyJsonWrites(writes)).toString)))
  }


}


abstract class LambdaHandler[I, O]()(implicit inputReads: Reads[I], outputWrites: Writes[O]) {
  // Either of the following two methods should be overridden

  protected def handle(i: I, c: Context): Try[O]

  // This function will ultimately be used as the external handler
  final def handle(input: InputStream, output: OutputStream, context: Context): Unit = {
    val logger = context.getLogger
    logger.log(s"Handling function ${context.getFunctionName} with request ID ${context.getAwsRequestId}")
    val body = scala.io.Source.fromInputStream(input).mkString
    logger.log(s"Request body $body")
    val read = Try(Json.parse(body).validate[I])
    logger.log(s"Request parsed")
    if (read.isFailure) {
      logger.log(s"Request read failure")
      logger.log(s"Parsing input ${body} failed: ${read.toString}")
    } else {
      logger.log(s"Request read success")
    }
    logger.log(s"Handle request")
    val handled: Try[O] = read.flatMap {
      _.fold(
        error => {
          logger.log(s"Error when parsing request body")
          val errorMessage = error.map { case (p, e) =>
            p.toJsonString -> e.map(_.message)
          }
          logger.log(s"Object not valid: ${Json.toJson(errorMessage).toString}")
          Failure(BadRequestError("Object not valid", new RuntimeException(Json.toJson(errorMessage).toString())))
        },
        input => {
          logger.log(s"Handle request with body")
          handle(input, context)
        })
    }

    logger.log(s"Handler result: ${handled}")

    handled map { result =>
      output.write(Json.toJson(result)
        .toString
        .getBytes)
    }

    output.close()
  }
}

abstract class LambdaProxyHandler[In, Out] (implicit inputReads: Reads[In], outputWrites: Writes[Out])
  extends LambdaHandler[ProxyRequest[In], ProxyResponse[Out]]()(proxy.RequestJsonReads[In](inputReads), proxy.ResponseJsonWrites[Out](outputWrites)) {

  implicit val requestReads = proxy.RequestJsonReads[In](inputReads)
  implicit val responseWrites = proxy.ResponseJsonWrites[Out](outputWrites)

  protected def handleProxied(c: Context): Try[Out] = {
    Failure(BadRequestError("Empty Request Body"))
  }
  protected def handleProxied(i: In, c: Context): Try[Out] = {
    handleProxied(c)
  }

  protected def handle(i: ProxyRequest[In], c: Context): Try[ProxyResponse[Out]] = {
    val result = i.body.map(handleProxied(_, c)).getOrElse(handleProxied(c))
    Try(ProxyResponse(result))
  }
}

