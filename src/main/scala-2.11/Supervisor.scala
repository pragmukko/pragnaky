import actors.{NewMemberWithId, GCExtentions}
import actors.Messages.{RegisterListener, Start}
import akka.actor.ActorRef
import builders.GRoundControlNode
import spray.json.JsValue

/**
 * Created by max on 11/24/15.
 */
object Supervisor extends App {

  GRoundControlNode
    .build()
    .withREST(false)
    .addExtention[Neo4JAdapter]
    .start()

}

class Neo4JAdapter extends GCExtentions {


  override def process(manager: ActorRef): Receive = {

    case NewMemberWithId(id) =>
      println("Found new server " + id)
      manager ! RegisterListener(self, id)

    case telemetry:Array[JsValue] => telemetry foreach println

  }
}
