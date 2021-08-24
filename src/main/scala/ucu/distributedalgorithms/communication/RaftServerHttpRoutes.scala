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
  implicit val timeout: Timeout = 3.seconds

  lazy val raftServerHttpRoutes: Route =
    path("message") {
      concat(
        post {
          entity(as[Message]) { message =>
            system.log.info(s"Http server received message: $message")

            val operationPerformed: Future[Raft.ServerResponse] =
              raft.ask(Raft.AppendEntry(message.text, _))

            onSuccess(operationPerformed) {
              case Raft.OK =>
                system.log.info("add meessage to major nodes")
                complete("Message appended to the logs of the majority nodes in the cluster: %{}")
              case Raft.KO(reason) =>
                system.log.info("not added")
                complete(StatusCodes.InternalServerError -> reason)
            }
          }
        },
        get {
          // todo when we want to get list of messages we check if it leader if not sends leader id
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Todo, do we need get method?</h1>"))
        }
      )
    }
}