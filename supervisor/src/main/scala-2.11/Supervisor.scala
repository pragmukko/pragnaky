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
import akka.cluster.ClusterEvent.MemberUp
import builders.GRoundControlNode
import spray.json.{JsNumber, JsString, JsObject}

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

class TelemetryAdapter extends GCExtentions with ElasticMetricsDAL with ActorLogging {

  implicit val executionContext = context.dispatcher

  val mediator = DistributedPubSub(system).mediator
  mediator ! Put(self)

  override def process(manager: ActorRef): Receive = {

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

    case Edges =>
      val latencyAggr = AggregationBuilders.avg("avg_ping").field("pingTotal")
      val srcAddrAggr = AggregationBuilders.terms("source").field("source").size(0).subAggregation(latencyAggr)
      val destAddrAggr = AggregationBuilders.terms("dest").field("dest").size(0).subAggregation(srcAddrAggr)
      val resp = esClient.prepareSearch("stat").setTypes("latency").addAggregation(destAddrAggr).execute().actionGet()

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
      val resp = esClient.prepareSearch("stat").setTypes("telemetry").addAggregation(aggr).execute().actionGet()
      val terms = resp.getAggregations.get("nodes").asInstanceOf[Terms]
      sender() ! JSONArray(terms.getBuckets.map(_.getKey).toList).toString()

    case RawQuery(dataType, query, sort, limit) =>
      val request = esClient.prepareSearch("stat")
        .setTypes(dataType)
        .setSearchType(SearchType.QUERY_THEN_FETCH)
        .setFrom(0)
        .setSize(limit.getOrElse(10000))
        .setQuery(query)

      sort
        .map(_.split(':').toList)
        .collect{ case a :: b :: Nil => a -> b  }
        .foreach(x => request.addSort(x._1, SortOrder.valueOf(x._2) ))

      val res = request.execute().actionGet()
      val strRes = JSONArray(res.getHits.getHits.map(_.sourceAsMap().toMap[String, Any]).map(JSONObject(_)).toList).toString(jsonObjFormatter)
      sender() ! strRes

  }

  def jsonObjFormatter(v:Any) : String = v match {
    case m:java.util.Map[String, Any] =>
      JSONObject(m.toMap).toString(jsonObjFormatter)

    case a:java.util.List[Any] =>
      JSONArray(a.toList).toString(jsonObjFormatter)

    case jso:JSONObject => jso.toString(jsonObjFormatter)

    case null =>
      "0"
    case other =>
      JSONFormat.defaultFormatter(other)

  }

}
