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
package util

import java.util.Date

import org.hyperic.sigar.{CpuTimer, Sigar}
import kamon.sigar.SigarProvisioner
import spray.json.{JsArray, JsString, JsNumber, JsObject}
import scala.collection.JavaConversions._
import scala.util.Try

/**
 * Created by max on 11/25/15.
 */
trait Telemetry {


  lazy val sigar = {
    SigarProvisioner.provision()
    println(s"sigar is provisioned: ${SigarProvisioner.isNativeLoaded}")
    new Sigar()
  }

  def nodeTelemetry = Try {
    val mem = sigar.getMem()
    val cpu = sigar.getCpuPerc().getCombined
    val when = new Date().getTime
    val netStat = getNetStat
    JsObject(
      Map(
        "cpu" -> JsNumber(cpu),
        "memory" -> JsNumber(mem.getUsedPercent),
        "netStat" -> JsArray(netStat :_*)
      )
    )
  }

  private def getNetStat = {
    sigar.getNetInterfaceList.map(i => (i, sigar.getNetInterfaceStat(i))).map {
      case (name, stat) =>
        JsObject(
          Map(
            "name" -> JsString(name),
            "rx" -> JsNumber(stat.getRxBytes),
            "tx" -> JsNumber(stat.getTxBytes)
          )
        )
    } toList
  }

  def netGateway = sigar.getNetInfo.getDefaultGateway()

}
