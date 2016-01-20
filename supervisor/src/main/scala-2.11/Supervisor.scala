import java.util.Date
import akka.cluster.Member
import dal.elasticsearch.ElasticMetricsDAL
import ping.RichPing
import util.{ConfigGenId, Messages}
import Messages.{PersistenceError, Register}
import actors.GCExtentions
import akka.actor.{ActorLogging, ActorRef}
import akka.cluster.ClusterEvent.MemberUp
import builders.GRoundControlNode
import db.mongo.MongoMetricsDAL
import spray.json.{JsNumber, JsString, JsObject}

import scala.concurrent.Await

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

class TelemetryAdapter extends GCExtentions with ElasticMetricsDAL with ActorLogging {

  implicit val executionContext = context.dispatcher

  override def process(manager: ActorRef): Receive = {

    case MemberUp(member) =>
      println("Subscribe " + member.address)
      subscribeTelemetry(member)

    case telemetry:Array[JsObject] => sender().path.address.host foreach {persistTelemetry(_, telemetry)}

    case rp:RichPing => saveLatency(rp.toJs)

    case PersistenceError(err) =>
      err match {
        case th:Throwable => log.error(th, "Fail to persist")
        case other => log.error("Fail to persist {0}", other.toString)
      }
  }

}
