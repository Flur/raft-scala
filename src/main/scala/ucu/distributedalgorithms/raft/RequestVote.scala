package ucu.distributedalgorithms.raft

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.grpc.GrpcClientSettings
import ucu.distributedalgorithms._
import ucu.distributedalgorithms.raft.Raft.{FollowerTimeout, RaftCommand, RaftState}
import ucu.distributedalgorithms.util.{getLastLogIndex, getLastLogTerm}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Random, Success}


object RequestVote {

  sealed trait RequestVoteCommand

  final case class RequestVoteSuccess(response: RequestVoteResponse) extends RequestVoteCommand
  final case object RequestVoteTimerKey extends RequestVoteCommand

  final case class RequestVoteFailure() extends RequestVoteCommand

  final case object RequestVoteTimeout extends RequestVoteCommand

  def apply(node: Node, candidate: ActorRef[RequestVoteResponse], state: RaftState): Behavior[RequestVoteCommand] =
    Behaviors.withTimers[RequestVoteCommand] { timers =>
      Behaviors.setup { context => new RequestVote(node, state, context, candidate, timers).requestVote() }
    }
}

class RequestVote private(
                           node: Node,
                           state: RaftState,
                           context: ActorContext[RequestVote.RequestVoteCommand],
                           candidate: ActorRef[RequestVoteResponse],
                           timers: TimerScheduler[RequestVote.RequestVoteCommand],
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
      val duration = Random.between(2000, 3000).milliseconds

      timers.startSingleTimer(RequestVoteTimerKey, RequestVoteTimeout, duration)

      Behaviors.same

    case RequestVoteTimeout =>
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
