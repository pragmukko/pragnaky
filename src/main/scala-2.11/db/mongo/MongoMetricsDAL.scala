package db.mongo

import akka.actor.{Actor, ActorRef}
import reactivemongo.api.MongoDriver
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONDocument
import spray.json.{JsValue, JsObject}
import util.Messages
import util.Messages.PersistenceError

import scala.concurrent.{Future, ExecutionContextExecutor}
import scala.util.{Failure, Success}


/**
 * Created by max on 11/29/15.
 */
trait MongoMetricsDAL extends Mongo2Spray {

  _: Actor =>

  implicit val executionContext:ExecutionContextExecutor

  val (nodes, telemetry, latency) = connect()

  def addNodeIfNotExists(ip:String, router:String) = handleError {
    nodes.update(BSONDocument("addr" -> ip), BSONDocument("addr" -> ip, "router" -> router), upsert = true)
  }

  def saveTelemetry(json:JsObject) = handleError {
    telemetry.insert(json)
  }

  def saveLatency(json:JsObject) = handleError {
    latency.insert(json)
  }

  def connect(): (BSONCollection, BSONCollection, BSONCollection) = {
    val driver = new MongoDriver
    val connection = driver.connection(List("localhost"))
    val db = connection("ClusterStat")
    (db("nodes"), db("telemetry"), db("latency"))
  }

  def handleError(fnc: => Future[WriteResult] ) = {
    fnc onComplete {
      case Success(res) if res.ok => // Evething is OK -> do nothing
      case Success(res) => self ! PersistenceError(res)
      case Failure(th)  => self ! PersistenceError(th)
    }
  }

}
