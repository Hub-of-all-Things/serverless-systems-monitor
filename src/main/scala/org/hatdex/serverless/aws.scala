package org.hatdex.serverless

import play.api.libs.json._

import scala.collection.Seq

package object aws {
  type AnyContent = String

  /**
   * Deserializer for String types.
   */
  implicit object AnyContentReads extends Reads[String] {
    def reads(json: JsValue) = json match {
      case JsString(s) => JsSuccess(s)
      case _ => JsError(Seq(JsPath -> Seq(JsonValidationError("error.expected.jsstring"))))
    }
  }
}
