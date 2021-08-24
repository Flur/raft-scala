package ucu.distributedalgorithms.raft

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.grpc.GrpcClientSettings
import ucu.distributedalgorithms._
import ucu.distributedalgorithms.raft.AppendEntries.AppendEntriesCommand
import ucu.distributedalgorithms.raft.Raft.RaftState
import ucu.distributedalgorithms.util.{getLastLogIndex, getLastLogTerm}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}


object RequestVote {

  sealed trait RequestVoteCommand

  final case class RequestVoteSuccess(response: RequestVoteResponse) extends RequestVoteCommand

  final case class RequestVoteFailure() extends RequestVoteCommand

  def apply(node: Node, candidate: ActorRef[RequestVoteResponse], state: RaftState): Behavior[RequestVoteCommand] =
    Behaviors.setup { context => new RequestVote(node, state, context, candidate).requestVote() }
}

class RequestVote private(
                           node: Node,
                           state: RaftState,
                           context: ActorContext[RequestVote.RequestVoteCommand],
                           candidate: ActorRef[RequestVoteResponse]
                         ) {

  import RequestVote._

  implicit val system: ActorSystem[Nothing] = context.system
  implicit val ec: ExecutionContextExecutor = system.executionContext

  val clientSettings = GrpcClientSettings.connectToServiceAt(node.host, node.port).withTls(false)
  val client: RaftCommunicationServiceClient = RaftCommunicationServiceClient(clientSettings)

  makeVoteRequest()

  private def requestVote(): Behavior[RequestVoteCommand] = Behaviors.receiveMessage[RequestVoteCommand] {
    case RequestVoteSuccess(r) =>
      candidate ! r

      Behaviors.stopped

    case RequestVoteFailure() =>
      makeVoteRequest()

      Behaviors.same
  }.receiveSignal {
    case (context: ActorContext[RequestVoteCommand], postStop: PostStop) =>
      // todo could be one client for whole app, it's concurrent
      client.close()

      Behaviors.same
  }

  private def makeVoteRequest(): Unit = {
    val reply: Future[RequestVoteResponse] = client.requestVote(
      RequestVoteRequest(state.currentTerm, state.id, getLastLogIndex(state), getLastLogTerm(state))
    )

    context.pipeToSelf(reply) {
      case Success(msg: RequestVoteResponse) =>
        RequestVoteSuccess(msg)

      case Failure(_) => RequestVoteFailure()
    }
  }
}
