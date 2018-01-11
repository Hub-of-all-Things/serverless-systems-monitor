package org.hatdex.serverless.aws

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.Context
import org.hatdex.serverless.aws.proxy.{BadRequestError, InternalServerError}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._

import scala.reflect.ClassTag
import scala.util.{Failure, Try}

abstract class LambdaHandler[I, O]()(implicit inputReads: Reads[I], outputWrites: Writes[O]) {
  // Either of the following two methods should be overridden
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  protected def handle[T : ClassTag](i: I)(implicit c: Context): Try[O] = {
    logger.error("Function handler not implemented")
    Failure(InternalServerError("Not Implemented"))
  }

  // This function will ultimately be used as the external handler
  final def handle(input: InputStream, output: OutputStream, context: Context): Unit = {
    implicit val c: Context = context
    logger.debug(s"Handling function ${context.getFunctionName} with request ID ${context.getAwsRequestId}")
    val body = scala.io.Source.fromInputStream(input).mkString
    logger.debug(s"Request body $body")
    val read = Try(Json.parse(body).validate[I])
    logger.debug(s"Request parsed")
    if (read.isFailure) {
      logger.debug(s"Request read failure")
      logger.debug(s"Parsing input $body failed: ${read.toString}")
    } else {
      logger.debug(s"Request read success")
    }
    logger.debug(s"Handle request")
    val handled: Try[O] = read.flatMap {
      _.fold(
        error => {
          logger.debug(s"Error when parsing request body")
          val errorMessage = error.map { case (p, e) =>
            p.toJsonString -> e.map(_.message)
          }
          logger.debug(s"Object not valid: ${Json.toJson(errorMessage).toString}")
          Failure(BadRequestError("Object not valid", new RuntimeException(Json.toJson(errorMessage).toString())))
        },
        input => {
          logger.debug(s"Handle request with body")
          handle(input)
        })
    }

    logger.debug(s"Handler result: $handled")

    handled map { result =>
      output.write(Json.toJson(result)
        .toString
        .getBytes)
    }

    output.close()
  }
}
