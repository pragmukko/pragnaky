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
package dal.elasticsearch

import java.net.InetAddress
import java.util.Date

import akka.actor.{ActorLogging, Actor}
import dal.MetricsDAL
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
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

  me: Actor with ConfigProvider with ActorLogging =>

  val indexTimeout = TimeValue.timeValueMillis(500)
  val esClient = {
    ElasticSearchProvider.client() match {
      case Success(client) => client
      case Failure(th) =>
        th.printStackTrace()
        log.error("Coundn't connect to remote ES, starting local ES instance")
        ElasticSearchInstance.client
    }
  }
  println(s"esClient: $esClient")

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
  settings.put("number_of_shards", 2)
  settings.put("number_of_replicas", 2)
  settings.put("path.home", "Data")
  lazy val node = NodeBuilder.nodeBuilder()
    .settings(settings)
    .node()

  sys addShutdownHook { node.close() }

  def client : Client = node.client()

}

object ElasticSearchProvider extends ConfigProvider {

  def client() : Try[Client] = {
    import scala.collection.JavaConversions._
    import com.ecwid.consul.v1._
    Try {
      val consul = new ConsulClient(config.getString("consul.host"), config.getInt("consul.port"))
      val nodes = consul.getCatalogService(config.getString("consul.service-name"), QueryParams.DEFAULT).getValue
      println(s"consul service nodes: $nodes")
      val addresses = nodes.toList map { n => new InetSocketTransportAddress(InetAddress.getByName(n.getAddress), n.getServicePort) }
      val client: TransportClient = TransportClient.builder().build().addTransportAddresses(addresses: _*)
      if (client.connectedNodes().length == 0) throw new Exception("No Elasticsearch connected nodes")
      client
    }
  }
}
