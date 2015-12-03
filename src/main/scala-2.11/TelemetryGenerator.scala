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
  val hosts = List("10.0.1.10", "10.0.1.13", "10.0.1.15", "10.0.1.25")
  val hlen = hosts.length
  val rnd = SecureRandom.getInstanceStrong
  rnd.setSeed(System.currentTimeMillis())

  @tailrec
  override final def run() = {
    val tele = telemetryObject(hosts(rnd.nextInt(hlen)), rnd.nextDouble()*100, rnd.nextDouble()*100, "eth0", rnd.nextInt(100000), rnd.nextInt(100000))
    val lat = new RichPing(System.currentTimeMillis(), hosts(rnd.nextInt(hlen)), hosts(rnd.nextInt(hlen)), 0, 0, rnd.nextInt(3000)).toJs
    dal.saveTelemetry(tele)
    dal.saveLatency(lat)

    println(s"saved telemetry: $tele")
    println(s"saved latency: $lat")
    Thread.sleep(1000)
    run()
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