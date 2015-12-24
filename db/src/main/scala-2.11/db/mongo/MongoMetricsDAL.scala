package db.mongo

import java.text.SimpleDateFormat
import java.util.{TimeZone, Date}

import akka.actor.{Actor, ActorRef}
import reactivemongo.api.{DefaultDB, MongoDriver}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONDocument
import spray.json._
import util.Messages.PersistenceError

import scala.concurrent.{Future, ExecutionContextExecutor}
import scala.util.{Failure, Success}


/**
 * Created by max on 11/29/15.
 */
trait MongoMetricsDAL extends Mongo2Spray {

  me: Any =>

  val actorOpt: Option[ActorRef] = me match {
    case a: Actor => Some(a.self)
    case _ => None
  }

  implicit val executionContext:ExecutionContextExecutor

  lazy val (_db, nodes, telemetry, latency) = connect()
  lazy val knownDbs = Map("nodes" -> nodes, "telemetry" -> telemetry, "latency" -> latency)

  def addNodeIfNotExists(ip:String, router:String) = handleError {
    nodes.update(BSONDocument("addr" -> ip), BSONDocument("addr" -> ip, "router" -> router), upsert = true)
  }

  def saveTelemetry(json:JsObject) = handleError {
    val jsDoc = JsObject( json.fields + ("creationTime" -> JsObject("$date" -> JsNumber(System.currentTimeMillis()))))
    telemetry.insert(jsDoc)
  }

  def saveLatency(json:JsObject) = handleError {
    val jsDoc = JsObject( json.fields + ("creationTime" -> JsObject("$date" -> JsNumber(System.currentTimeMillis()))))
    latency.insert(jsDoc)
  }

  def nowISODate() = {
    val tz = TimeZone.getTimeZone("UTC")
    val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm.SSZ")
    df.setTimeZone(tz)
    df.format(new Date())
  }

  def connect(): (DefaultDB, BSONCollection, BSONCollection, BSONCollection) = {
    val driver = new MongoDriver
    val connection = driver.connection(List("localhost"))
    val db = connection("ClusterStat")
    (db, db("nodes"), db("telemetry"), db("latency"))
  }

  def handleError(fnc: => Future[WriteResult] ) = {
    fnc onComplete {
      case Success(res) if res.ok => // Evething is OK -> do nothing
      case Success(res) => actorOpt foreach {_ ! PersistenceError(res)}; println(s"can't insert, result: $res")
      case Failure(th)  => actorOpt foreach {_ ! PersistenceError(th)}; println(s"can't insert, cause: $th")
    }
  }

  def persistTelemetry(host: String, telemetry:Seq[JsObject]) = {
    telemetry.map( t => JsObject(
      t.fields +
        ("addr" -> JsString(host)) +
        ("timestamp" -> JsNumber(new Date().getTime()))
    )) foreach saveTelemetry
  }

}
