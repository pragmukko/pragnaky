package util

import java.util.Date

import org.hyperic.sigar.{CpuTimer, Sigar}
import spray.json.{JsArray, JsString, JsNumber, JsObject}
import scala.collection.JavaConversions._
import scala.util.Try

/**
 * Created by max on 11/25/15.
 */
trait Telemetry {


  val sigar = new Sigar()

  def nodeTelemetry = Try {
    val mem = sigar.getMem()
    val cpu = sigar.getCpuPerc().getCombined
    val when = new Date().getTime
    val netStat = getNetStat
    JsObject(
      Map(
        "when" -> JsNumber(when),
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

}
