package org.hatdex.serverless.aws

import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.MessageAttributeValue
import play.api.libs.json.{JsValue, Json}

class SNSClient(region: String, topicArn: String) {
  def publishSns(message: String): String = {
    val snsClient = AmazonSNSClientBuilder.standard()
      .withRegion(region)
      .build()


    import com.amazonaws.services.sns.model.PublishRequest
    val publishRequest = new PublishRequest(topicArn, message)

    val publishResult = snsClient.publish(publishRequest)

    publishResult.getMessageId
  }

  private def attribute(attributeName: String, attributeValue: JsValue): (String, MessageAttributeValue) = {
    val messageAttributeValue = new MessageAttributeValue()
      .withDataType("String")
      .withStringValue(attributeValue.toString)
    (attributeName, messageAttributeValue)
  }

  private def attribute(attributeName: String, attributeValue: String): (String, MessageAttributeValue) = {
    attribute(attributeName, Json.toJson(attributeValue))
  }
}
