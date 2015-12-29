package ping
import java.net.InetAddress

import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString}

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

object RichPingProtocol extends DefaultJsonProtocol {
  implicit val richPingFormat = jsonFormat6(RichPing)
}

object PingStart