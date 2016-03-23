/*
* Copyright 2015-2016 Pragmukko Project [http://github.org/pragmukko]
* Licensed under the Apache License, Version 2.0 (the "License"); you may not
* use this file except in compliance with the License. You may obtain a copy of
* the License at
*
*    [http://www.apache.org/licenses/LICENSE-2.0]
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations under
* the License.
*/
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
