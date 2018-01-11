package org.hatdex.serverless.aws

import play.api.libs.json._

import scala.util.{Success, Try}

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

  trait JsonProtocol {

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


    private def generateProxyRequestReads[T](implicit reads: Reads[T]): Reads[ProxyRequest[T]] = {
      Json.reads[ProxyRequest[T]]
    }

    implicit def RequestJsonReads[T](implicit reads: Reads[T]): Reads[ProxyRequest[T]] = {
      val stringReads: Reads[T] = Reads.StringReads
        .map(s => Try(Json.parse(s)))
        .collect(JsonValidationError("")) {
          case Success(js) => js
        }
        .andThen(reads)

      generateProxyRequestReads(stringReads)
    }


    implicit def ResponseJsonWrites[T](implicit writes: Writes[T]): Writes[ProxyResponse[T]] = (response: ProxyResponse[T]) => {
      JsObject(Map(
        "statusCode" -> JsNumber(response.statusCode),
        "headers" -> Json.toJson(response.headers),
        "body" -> JsString(Json.toJson(response.body)(responseBodyJsonWrites(writes)).toString)))
    }
  }

  object JsonProtocol extends JsonProtocol


}
