package eventbox

import akka.actor.{Actor, ActorRef}
import akka.util.Timeout
import eventbox.events._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try

abstract class EventActor(eventManager: ActorRef)(implicit ec:ExecutionContext) extends Actor {

  implicit val timeout: Timeout = Timeout(30 seconds)

  def on: PartialFunction[Any, Try[Any]]

  // ---

  override def preStart() : Unit = {
    eventManager ! ActorJoin
  }

  override def receive:Receive = {
    case msg:Event => {

      if (on.isDefinedAt(msg)){

        try {
          on(msg).map {

            case f: Future[_] => {
              f.map { data =>
                eventManager ! EventEnd(msg, EventSuccess(data))
              }.recover {
                case t:Throwable => recover(msg, t)
              }
            }
            case data  => {
              eventManager ! EventEnd(msg, EventSuccess(data))
            }

          }.recover {
            case t:Throwable => recover(msg, t)
          }
        } catch {
          case t:Throwable => recover(msg, t)
        }

      } else {
        eventManager ! EventEnd(msg, EventSuccess(Unit))
      }

    }
  }

  private def recover(msg:Event, t:Throwable) = {
    eventManager ! EventEnd(msg, EventError(t))
  }
}
