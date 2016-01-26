package web

import java.net.InetAddress

import akka.actor.Actor
import akka.actor.Actor.Receive
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberEvent, InitialStateAsSnapshot}
import akka.cluster.pubsub.{DistributedPubSubSettings, DistributedPubSubMediator}
import akka.dispatch.Dispatchers
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import utils.{ConfigProvider, ClusterNode}
import org.elasticsearch.client.Client

/**
 * Created by max on 1/19/16.
 */
object ClusterAwareRestService extends App with ClusterNode with ConfigProvider {

  new RestService(mediator, config)(system).start()

}
