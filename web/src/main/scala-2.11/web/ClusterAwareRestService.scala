package web

import java.net.InetAddress

import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import utils.{ConfigProvider, ClusterNode}
import org.elasticsearch.client.Client

/**
 * Created by max on 1/19/16.
 */
object ClusterAwareRestService extends App with ClusterNode with ConfigProvider {

  new RestService(clientProvider, config)(system).start()

  def clientProvider(callback: Client => String  ) : String = {
    val addresses = listMembers
      .filter(_.hasRole("server"))
      .map( m => new InetSocketTransportAddress(InetAddress.getByName(m.address.host.getOrElse("localhost")), 9300) )

    val client = addresses.foldLeft(TransportClient.builder().build()) {
      (acc, addr) => acc.addTransportAddress(addr)
    }

    try {
      callback(client)
    } catch {
      case th:Throwable =>
        th.printStackTrace()
        throw th
    } finally {
      client.close()
    }
  }

}
