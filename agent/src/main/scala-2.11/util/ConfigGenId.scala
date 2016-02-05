package util

import utils.{NetUtils, ConfigProvider}

/**
 * Created by yishchuk on 11.12.2015.
 */
trait ConfigGenId { cp: ConfigProvider =>

  override lazy val additionalConfig = {
    val memberId = s"member-${NetUtils.localHost.getHostAddress}"
    System.setProperty("member-id", memberId)
    List(
      "member-id" -> memberId
    )
  }

}