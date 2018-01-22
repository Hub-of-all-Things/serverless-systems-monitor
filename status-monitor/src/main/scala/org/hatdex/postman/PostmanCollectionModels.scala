package org.hatdex.postman

import java.time.ZonedDateTime

import play.api.libs.functional.syntax._
import play.api.libs.json._

object PostmanCollectionModels {

  case class CollectionInfo(
    id: String,
    name: String,
    schema: String)

  case class CollectionItem(
    id: String,
    name: String,
    item: Option[Seq[CollectionItem]],
    request: Option[JsValue],
    response: Option[Seq[JsValue]])

  case class PostmanCollection(
    item: Seq[CollectionItem],
    info: CollectionInfo)

  case class Stat (
    total: Int,
    pending: Int,
    failed: Int)

  case class RunStats (
    iterations: Stat,
    items: Stat,
    scripts: Stat,
    prerequests: Stat,
    requests: Stat,
    tests: Stat,
    assertions: Stat,
    testScripts: Stat,
    prerequestScripts: Stat)

  case class RunTimings(
    responseAverage: Double,
    started: ZonedDateTime,
    completed: ZonedDateTime)

  case class RunTransfers(
    responseTotal: Long)

  case class PostmanCollectionRun(
    stats: RunStats,
    timings: RunTimings,
    transfers: RunTransfers,
    failures: Seq[JsValue],
    error: Option[JsValue])

  case class PostmanCollectionRunResult(
    collection: PostmanCollection,
    run: PostmanCollectionRun)

  implicit val collectionInfoJsonFormat: Format[CollectionInfo] = Json.format[CollectionInfo]
  implicit val collectionItemJsonFormat: Format[CollectionItem] = (
    (__ \ "id").format[String] and
      (__ \ "name").format[String] and
      (__ \ "item").lazyFormatNullable[Seq[CollectionItem]](implicitly[Format[Seq[CollectionItem]]]) and
      (__ \ "request").formatNullable[JsValue] and
      (__ \ "response").formatNullable[Seq[JsValue]])(CollectionItem.apply, unlift(CollectionItem.unapply))

  implicit val postmanCollectionJsonFormat: Format[PostmanCollection] = Json.format[PostmanCollection]
  implicit val statJsonFormat: Format[Stat] = Json.format[Stat]
  implicit val runStatsJsonFormat: Format[RunStats] = Json.format[RunStats]
  implicit val runTimingsJsonFormat: Format[RunTimings] = Json.format[RunTimings]
  implicit val runTransferJsonFormat: Format[RunTransfers] = Json.format[RunTransfers]
  implicit val postmanCollectionRunFormat: Format[PostmanCollectionRun] = Json.format[PostmanCollectionRun]
  implicit val postmanCollectionRunResult: Format[PostmanCollectionRunResult] = Json.format[PostmanCollectionRunResult]

}
