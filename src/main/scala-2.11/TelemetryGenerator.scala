import java.security.SecureRandom

import db.mongo.MongoMetricsDAL
import spray.json._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

/**
 * Created by yishchuk on 03.12.2015.
 */
object TelemetryGenerator extends App with MongoMetricsDAL{
  implicit val executionContext = ExecutionContext.Implicits.global
  
  new TelemetryWriter(this).start()
  
}

case class TelemetryWriter(dal: MongoMetricsDAL) extends Thread {
  //val hosts = List("10.0.1.10", "10.0.1.13", "10.0.1.15", "10.0.1.25")
  val hosts = (1 to 40) map( "10.0.1." + _ )
  val slow = List("10.0.1.5", "10.0.1.17")
  val hlen = hosts.length
  val rnd = SecureRandom.getInstanceStrong
  rnd.setSeed(System.currentTimeMillis())

  @tailrec
  override final def run() = {
    val tele = telemetryObject(hosts(rnd.nextInt(hlen)), rnd.nextDouble() * 100, rnd.nextDouble(), "eth0", rnd.nextInt(100000), rnd.nextInt(100000))
    val (from, to) = getHosts()
    val lat = new RichPing(System.currentTimeMillis(), from, to, 0, 0, /*rnd.nextInt(5000)*/getLatency(from, to)).toJs
    dal.saveTelemetry(tele)
    dal.saveLatency(lat)

   // println(s"saved telemetry: $tele")
   // println(s"saved latency: $lat")
    Thread.sleep(100)
    run()
  }

  def getLatency(h:String*) = {
    if (h.exists(slow.contains))
      rnd.nextInt(5000)
    else
      rnd.nextInt(1000)
  }

  def getHosts(): (String, String) = {
    val from = hosts(rnd.nextInt(hlen))
    var to = hosts(rnd.nextInt(hlen))
    while ( to == from ) {
      to = hosts(rnd.nextInt(hlen))
    }
    (from, to)
  }

  def telemetryObject(host: String, memory: Double, cpu: Double, netIntName: String, rxBytes: Long, txBytes: Long) = {
    JsObject(
      Map(
        "addr" -> JsString(host),
        "timestamp" -> JsNumber(System.currentTimeMillis()),
        "cpu" -> JsNumber(cpu),
        "memory" -> JsNumber(memory),
        "netStat" -> JsArray(JsObject(
          Map(
            "name" -> JsString(netIntName),
            "rx" -> JsNumber(rxBytes),
            "tx" -> JsNumber(txBytes)
          )
        )
        )
      ))
  }
}