package ucu.distributedalgorithms.raft

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.grpc.GrpcClientSettings
import ucu.distributedalgorithms._
import ucu.distributedalgorithms.raft.AppendEntries.AppendEntriesCommand
import ucu.distributedalgorithms.raft.Raft.RaftState

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}


object AppendEntries {

  sealed trait AppendEntriesCommand

  final case class AppendEntriesSuccess(response: AppendEntriesResponse) extends AppendEntriesCommand

  final case class AppendEntriesFailure() extends AppendEntriesCommand

  def apply(node: Node, leader: ActorRef[AppendEntriesResponse], state: RaftState): Behavior[AppendEntriesCommand] =
    Behaviors.setup { context => new AppendEntries(node, state, context, leader).appendEntries() }
}

class AppendEntries private(
                           node: Node,
                           state: RaftState,
                           context: ActorContext[AppendEntriesCommand],
                           leader: ActorRef[AppendEntriesResponse]
                         ) {

  import AppendEntries._

  makeVoteRequest()

  private def appendEntries(): Behavior[AppendEntriesCommand] = Behaviors.receiveMessage {
    case AppendEntriesSuccess(r) =>
      leader ! r

      Behaviors.stopped

    case AppendEntriesFailure() =>
      makeVoteRequest()

      Behaviors.same
  }

  private def makeVoteRequest(): Unit = {
    implicit val system: ActorSystem[Nothing] = context.system
    implicit val ec: ExecutionContextExecutor = system.executionContext

    val clientSettings = GrpcClientSettings.connectToServiceAt(node.host, node.port).withTls(false)

    val client: RaftCommunicationService = RaftCommunicationServiceClient(clientSettings)

    context.log.info("Sent append entries to node {} and term", node.host, state.currentTerm)

    val reply: Future[AppendEntriesResponse] = client.appendEntries(
      AppendEntriesRequest(state.currentTerm, state.id, 0, 0, Seq.empty[LogEntry])
    )

    context.pipeToSelf(reply) {
      case Success(msg: AppendEntriesResponse) =>
        AppendEntriesSuccess(msg)

      case Failure(_) => AppendEntriesFailure()
    }
  }
}
