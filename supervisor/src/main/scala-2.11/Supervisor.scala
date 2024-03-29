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
import java.net.InetAddress
import java.util.Date

import actors.Messages.Start
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Put
import dal.elasticsearch.ElasticMetricsDAL
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.search.aggregations.metrics.avg.Avg
import org.elasticsearch.search.sort.SortOrder
import ping.RichPing
import util.{ConfigGenId, Messages}
import util.Messages._
import actors.GCExtentions
import akka.actor.{ActorLogging, ActorRef}
import akka.cluster.ClusterEvent._
import builders.GRoundControlNode
import spray.json.{JsNumber, JsString, JsObject}
import utils.ConfigProvider

import scala.util.parsing.json.{JSONFormat, JSONObject, JSONArray}

import scala.collection.JavaConversions._

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

class TelemetryAdapter extends GCExtentions with ElasticMetricsDAL with ActorLogging with ConfigProvider {

  implicit val executionContext = context.dispatcher

  val mediator = DistributedPubSub(system).mediator
  mediator ! Put(self)

  override def process(manager: ActorRef): Receive = {

    case Start => subscribeExistingMemebers()

    case MemberUp(member) =>
      println("Subscribe " + member.address)
      subscribeTelemetry(member)

    case telemetry:Array[JsObject] =>
      println("telemetry: " + telemetry)
      sender().path.address.host foreach {persistTelemetry(_, telemetry)}

    case rp:RichPing => saveLatency(rp.toJs)

    case PersistenceError(err) =>
      err match {
        case th:Throwable => log.error(th, "Fail to persist")
        case other => log.error("Fail to persist {0}", other.toString)
      }
      System.currentTimeMillis()

    case Edges =>
      val latencyAggr = AggregationBuilders.avg("avg_ping").field("pingTotal")
      val srcAddrAggr = AggregationBuilders.terms("source").field("source").size(0).subAggregation(latencyAggr)
      val destAddrAggr = AggregationBuilders.terms("dest").field("dest").size(0).subAggregation(srcAddrAggr)
      val since = System.currentTimeMillis() - ( 5 * 60 * 1000 )
      val resp = esClient
        .prepareSearch("stat")
        .setTypes("latency")
        .setQuery(s"""{"range": {"time" : {"gte": $since}}}""")
        .addAggregation(destAddrAggr)
        .execute()
        .actionGet()

      val terms = resp.getAggregations.get("dest").asInstanceOf[Terms]
      val respArray = terms.getBuckets.flatMap {
        destBucket =>
          val dst = destBucket.getKey

          destBucket.getAggregations.get("source").asInstanceOf[Terms].getBuckets.map {
            sourceBucket =>
              Map(
                "dest" -> dst,
                "source" -> sourceBucket.getKey,
                "last" -> sourceBucket.getAggregations.get("avg_ping").asInstanceOf[Avg].getValue
              )
          }
      }

      sender() ! JSONArray(respArray.toList).toString{ case m:Map[String, _] => JSONObject(m).toString()}

    case Nodes =>
      val aggr = AggregationBuilders.terms("nodes").field("addr").size(0)
      val since = System.currentTimeMillis() - ( 5 * 60 * 1000 )
      val resp = esClient
        .prepareSearch("stat")
        .setTypes("telemetry")
        .addAggregation(aggr)
        .setQuery(s"""{"range": {"timestamp" : {"gte": $since}}}""")
        .execute()
        .actionGet()

      val terms = resp.getAggregations.get("nodes").asInstanceOf[Terms]
      val nodes = terms.getBuckets.map(_.getKey.toString).toList.map {
        addr =>
          val inetAddr = InetAddress.getByName(addr)
          Map(
            "host" -> inetAddr.getHostName(),
            "addr" -> inetAddr.getHostAddress()
          )
      }
      sender() ! JSONArray(nodes).toString(jsonObjFormatter)

    case RawQuery(dataType, query, sort, limit, fields) =>
      val request = esClient.prepareSearch("stat")
        .setTypes(dataType)
        .setSearchType(SearchType.QUERY_THEN_FETCH)
        .setFrom(0)
        .setSize(limit.getOrElse(10000))
        .setQuery(query)

      fields
        .toList
        .flatMap(_.split(':').toList)
        .foreach(request.addField(_))

      sort
        .map(_.split(':').toList)
        .collect{ case a :: b :: Nil => a -> b  }
        .foreach(x => request.addSort(x._1, SortOrder.valueOf(x._2) ))

      val res = request.execute().actionGet()
      //val strRes = JSONArray(res.getHits.flatMap(_.getFields).map(kvp => (kvp._1, kvp._2.getValues)).toList).toString(jsonObjFormatter)
      sender() ! new String(res.toString)


    case unknown => println("UNKNOWN: " + unknown)

  }

  def subscribeExistingMemebers() : Unit = {
    val m = cluster.state.members.filter(_.hasRole("embedded"))
    println("!!" + m)
    m.foreach(subscribeTelemetry)
  }

  def jsonObjFormatter(v:Any) : String = v match {
    case m:java.util.Map[String, Any] =>
      JSONObject(m.toMap).toString(jsonObjFormatter)

    case m:Map[String, _] => JSONObject(m).toString(jsonObjFormatter)

    case a:java.util.List[Any] =>
      JSONArray(a.toList).toString(jsonObjFormatter)

    case jso:JSONObject => jso.toString(jsonObjFormatter)

    case null =>
      "0"

    case other =>
      JSONFormat.defaultFormatter(other)

  }

}
