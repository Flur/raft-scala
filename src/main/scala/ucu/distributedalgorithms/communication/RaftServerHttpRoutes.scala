package ucu.distributedalgorithms.communication

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import spray.json._
import ucu.distributedalgorithms.raft.Raft

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

sealed case class Message(text: String)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val messageFormat: RootJsonFormat[Message] = jsonFormat1(Message)
}

class RaftServerHttpRoutes(raft: ActorRef[Raft.RaftCommand])(implicit system: ActorSystem[_]) extends JsonSupport {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}

  // asking someone requires a timeout and a scheduler, if the timeout hits without response
  // the ask is failed with a TimeoutException
  implicit val timeout: Timeout = 10.seconds

  lazy val raftServerHttpRoutes: Route =
    path("message") {
      concat(
        post {
          entity(as[Message]) { message =>
            system.log.info(s"Http server received message: $message")

            val operationPerformed: Future[Raft.ServerResponse] =
              raft.ask(Raft.AppendEntry(message.text, _))

            onSuccess(operationPerformed) {
              case Raft.OK(data) =>
                complete("Message appended to the logs of the majority nodes in the cluster")
              case Raft.KO(reason) =>
                complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
        get {
          val operationPerformed: Future[Raft.ServerResponse] =
            raft.ask(Raft.GetLog(_))

          onSuccess(operationPerformed) {
            case Raft.OK(data) =>
              complete(data.getOrElse(List.empty).mkString(", "))
            case Raft.KO(reason) =>
              complete(StatusCodes.InternalServerError -> reason)
          }
        }
      )
    }
}