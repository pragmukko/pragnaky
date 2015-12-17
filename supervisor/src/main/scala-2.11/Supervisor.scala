import java.util.Date

import util.{ConfigGenId, Messages}
import Messages.{PersistenceError, Register}
import actors.GCExtentions
import akka.actor.{ActorLogging, ActorRef}
import akka.cluster.ClusterEvent.MemberUp
import builders.GRoundControlNode
import db.mongo.MongoMetricsDAL
import spray.json.{JsNumber, JsString, JsObject}

/**
 * Created by max on 11/24/15.
 */
object GRoundControlNodeGenId extends GRoundControlNode with ConfigGenId

object Supervisor extends App {

  GRoundControlNodeGenId
    .build()
    .withREST(false)
    .addExtention[TelemetryAdapter]
    .start()
}

class TelemetryAdapter extends GCExtentions with MongoMetricsDAL with ActorLogging {

  implicit val executionContext = context.dispatcher

  override def process(manager: ActorRef): Receive = {

    case MemberUp(member) =>
      println("Subscribe " + member.address)
      subscribeTelemetry(member)

    case Register(gateway) =>
      sender().path.address.host.foreach( addNodeIfNotExists(_, gateway) )

    case telemetry:Array[JsObject] => persistTelemetry(sender(), telemetry)

    case rp:RichPing => saveLatency(rp.toJs)

    case PersistenceError(err) =>
      err match {
        case th:Throwable => log.error(th, "Fail to persist")
        case other => log.error("Fail to persist {0}", other.toString)
      }
  }

  def persistTelemetry(addr:ActorRef, telemetry:Array[JsObject]) = {
    addr.path.address.host.toList flatMap {
      host =>
        telemetry.map( t => JsObject(
          t.fields +
            ("addr" -> JsString(host)) +
            ("timestamp" -> JsNumber(new Date().getTime()))
        ))
    } foreach saveTelemetry
  }

}