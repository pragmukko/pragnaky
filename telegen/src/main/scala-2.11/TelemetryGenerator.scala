import java.security.SecureRandom
import java.util.concurrent.{Executors, ExecutorService}

import db.mongo.MongoMetricsDAL
import ping.RichPing
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
  val hosts = (1 to 4).flatMap( n => (1 to 10).map( t => n -> t ) ).map( kvp => s"10.0.${kvp._1}.${kvp._2}")
  val slow = (1 to 20) map( "10.0.1." + _ )
  val hlen = hosts.length
  val rnd = Random

  @tailrec
  final def run() : Unit = {
    hosts foreach {
      h1 =>
        val tele = telemetryObject(h1, rnd.nextDouble() * 100, rnd.nextDouble(), "eth0", rnd.nextInt(100000), rnd.nextInt(100000))
        //val (from, to) = getHosts()
        saveTelemetry(tele)
        hosts foreach {
          h2 =>
            if (h1 != h2) {
              val lat = new RichPing(System.currentTimeMillis(), h1, h2, 0, 0, /*rnd.nextInt(5000)*/ getLatency(h1, h2)).toJs
              saveLatency(lat)
            }
        }
    }

    //telemetry.insert(tele)
    //latency.insert(lat)

    //saveTelemetry(tele)
    //saveLatency(lat)


    //println(s"saved telemetry: $tele")
   // println(s"saved latency: $lat")
    Thread.sleep(5000)
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