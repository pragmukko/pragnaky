package dal.elasticsearch

import java.util.Date

import akka.actor.{ActorRef, Actor}
import dal.MetricsDAL
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.node.NodeBuilder
import spray.json.{JsNumber, JsString, JsObject}
import util.Messages.PersistenceError
import utils.ConfigProvider

import scala.util.{Failure, Success, Try}

/**
 * Created by max on 1/18/16.
 */
trait ElasticMetricsDAL extends MetricsDAL {

  me: Actor =>

  val esClient = ElasticSearchInstance.client
  val indexTimeout = TimeValue.timeValueMillis(500)

  override def persistTelemetry(host: String, telemetry: Seq[JsObject]): Unit = handleBulkError {
    val withAddr = telemetry.map( t => JsObject(
      t.fields +
        ("addr" -> JsString(host)) +
        ("timestamp" -> JsNumber(new Date().getTime()))
    ))
    val bulk = withAddr.foldLeft(esClient.prepareBulk()) {
      (acc, doc) =>
        acc.add(esClient.prepareIndex("stat", "telemetry").setSource(doc.toString()))
        acc
    }
    bulk.get(indexTimeout)
  }

  override def saveLatency(json: JsObject): Unit = handleError {
    esClient.prepareIndex("stat", "latency").setSource(json.toString()).get(indexTimeout)
  }

  def handleError(fnc: => IndexResponse ) = {
    Try(fnc) match {
      case Success(resp) if resp.isCreated => // success, do nothing
      case Failure(th) => me.self ! PersistenceError(th)
      case _ => me.self ! PersistenceError("Can't index message")
    }

  }

  def handleBulkError(fnc: => BulkResponse ) = {
    Try(fnc) match {
      case Success(resp) if resp.hasFailures =>
        me.self ! PersistenceError("Can't index message. " + resp.buildFailureMessage())
      case Failure(th) => me.self ! PersistenceError(th)
      case _ =>
    }
  }

}

object ElasticSearchInstance {

  val settings = Settings.settingsBuilder()
  settings.put("network.host", "0.0.0.0")
  val node = NodeBuilder.nodeBuilder().local(false).settings(settings).node()

  sys addShutdownHook { node.close() }

  def client : Client = node.client()

}
