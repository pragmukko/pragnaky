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
import java.security.SecureRandom
import java.util.concurrent.{Executors, ExecutorService}

import akka.actor.{ActorLogging, Props, Actor, ActorSystem}
import akka.actor.Actor.Receive
import ping.RichPing
import spray.json._
import util.Messages.PersistenceError
import utils.ConfigProvider

import scala.annotation.tailrec
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Random
import dal.elasticsearch._

/**
 * Created by yishchuk on 03.12.2015.
 */
object TelemetryGenerator extends App {


  println("Start working")
  var system = ActorSystem("test-generator")
  system.actorOf(Props[TelemetryWriter]) ! "Start"

}

class TelemetryWriter extends Actor with ConfigProvider with ActorLogging with ElasticMetricsDAL {

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
        persistTelemetry(h1, tele :: Nil)
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

  override def receive: Actor.Receive = {
    case "Start" => Future {
      run()
    }

    case PersistenceError(err) =>
      err match {
        case th:Throwable => th.printStackTrace()
        case other => printf("Fail to persist {0}", other)
      }
  }
}
