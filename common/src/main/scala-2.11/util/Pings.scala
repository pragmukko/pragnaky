import java.net.InetAddress

import spray.json.{JsString, JsNumber, JsObject}

case class PingTick(hosts:List[InetAddress])

case class PingHost(host: InetAddress)

case class RichPing(time: Long, source: String, dest: String, pingTo: Int, pingFrom: Int, pingTotal: Int) {
  def toJs = JsObject (
    Map(
      "time" -> JsNumber(time),
      "source" -> JsString(source),
      "dest" -> JsString(dest),
      "pingTo" -> JsNumber(pingTo),
      "pingFrom" -> JsNumber(pingFrom),
      "pingTotal" -> JsNumber(pingTotal))
  )
}

