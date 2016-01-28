package web

import utils.{ConfigProvider, ClusterNode}

/**
 * Created by max on 1/19/16.
 */
object ClusterAwareRestService extends App with ClusterNode with ConfigProvider {

  new RestService(mediator, config)(system).start()

}
