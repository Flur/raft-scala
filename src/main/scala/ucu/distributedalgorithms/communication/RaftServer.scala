package ucu.distributedalgorithms.communication

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives
import ucu.distributedalgorithms.Node
import ucu.distributedalgorithms.raft.Raft

import scala.concurrent.Future
import scala.util.{Failure, Success}

object RaftServer {
  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message

  private final case class Started(binding: ServerBinding) extends Message

  case object Stop extends Message


  def apply(host: String, port: Int, clusterNodes: List[Node], id: Int): Behavior[RaftServer.Message] = Behaviors.setup { context =>
    implicit val system: ActorSystem[Nothing] = context.system

    val raft = context.spawn(Raft(clusterNodes, id), "raft")
    val httpRoutes = new RaftServerHttpRoutes(raft)
    val grpcRoutes = new RaftServerGrpcRoutes(raft)
    val routes = Directives.concat(
      httpRoutes.raftServerHttpRoutes,
      grpcRoutes.routes
    )
    val serverBinding: Future[Http.ServerBinding] =
      Http().newServerAt(host, port).bind(routes)

    context.pipeToSelf(serverBinding) {
      case Success(binding) => Started(binding)
      case Failure(ex) => StartFailed(ex)
    }

    def running(binding: ServerBinding): Behavior[Message] =
      Behaviors.receiveMessagePartial[Message] {
        case Stop =>
          context.log.info(
            "Stopping server http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort)
          Behaviors.stopped
      }.receiveSignal {
        case (_, PostStop) =>
          binding.unbind()
          Behaviors.same
      }

    def starting(wasStopped: Boolean): Behaviors.Receive[Message] =
      Behaviors.receiveMessage[Message] {
        case StartFailed(cause) =>
          throw new RuntimeException("Server failed to start", cause)
        case Started(binding) =>
          context.log.info(
            "Server online at http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort)
          if (wasStopped) context.self ! Stop
          running(binding)
        case Stop =>
          // we got a stop message but haven't completed starting yet,
          // we cannot stop until starting has completed
          starting(wasStopped = true)
      }

    starting(wasStopped = false)
  }
}
