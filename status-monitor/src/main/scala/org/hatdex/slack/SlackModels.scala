package org.hatdex.slack

import play.api.libs.json.{Format, Json}

object SlackModels {

  case class AttachmentField(
    title: Option[String],
    value: Option[String],
    short: Boolean)

  case class MessageAttachment(
    color: String,
    fields: Seq[AttachmentField],
    fallback: String)

  case class Message(
    text: String,
    attachments: Seq[MessageAttachment])

  implicit val attachmentFieldJsonFormat: Format[AttachmentField] = Json.format[AttachmentField]
  implicit val messageAttachmentJsonFormat: Format[MessageAttachment] = Json.format[MessageAttachment]
  implicit val messageJsonFormat: Format[Message] = Json.format[Message]

}
