import java.security.SecureRandom
import java.util.concurrent.{Executors, ExecutorService}

import db.mongo.MongoMetricsDAL
import spray.json._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.util.Random

/**
 * Created by yishchuk on 03.12.2015.
 */
object TelemetryGenerator extends App{


  println("Start working")
  new TelemetryWriter().run()//.start()
  
}

class TelemetryWriter extends MongoMetricsDAL{

  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8))

  //val hosts = List("10.0.1.10", "10.0.1.13", "10.0.1.15", "10.0.1.25")
  val hosts = (1 to 30) map( "10.0.1." + _ )
  val slow = List("10.0.1.5", "10.0.1.7", "10.0.1.4", "10.0.1.3", "10.0.1.6")
  val hlen = hosts.length
  val rnd = Random

  @tailrec
  final def run() : Unit = {
    val tele = telemetryObject(hosts(rnd.nextInt(hlen)), rnd.nextDouble() * 100, rnd.nextDouble(), "eth0", rnd.nextInt(100000), rnd.nextInt(100000))
    val (from, to) = getHosts()
    val lat = new RichPing(System.currentTimeMillis(), from, to, 0, 0, /*rnd.nextInt(5000)*/getLatency(from, to)).toJs

    telemetry.insert(tele)
    latency.insert(lat)

    //println(s"saved telemetry: $tele")
   // println(s"saved latency: $lat")
    Thread.sleep(100)
    run()
  }

  def getLatency(h:String*) = {
    h.count(slow.contains) match {
      case 1 => rnd.nextInt(5000)
      case _ => rnd.nextInt(100)
    }
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