package ucu.distributedalgorithms.raft

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.grpc.GrpcClientSettings
import ucu.distributedalgorithms._
import ucu.distributedalgorithms.raft.AppendEntries.AppendEntriesCommand
import ucu.distributedalgorithms.raft.Raft.RaftState

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}


object AppendEntries {

  sealed trait AppendEntriesCommand

  final case class AppendEntriesSuccess(response: AppendEntriesResponse) extends AppendEntriesCommand

  final case class AppendEntriesFailure() extends AppendEntriesCommand

  final case object MakeVoteRequest extends AppendEntriesCommand
  final case object AppendEntriesRetryKey extends AppendEntriesCommand

  def apply(node: Node, leader: ActorRef[AppendEntriesResponse], state: RaftState): Behavior[AppendEntriesCommand] =
    Behaviors.withTimers { timers =>
      Behaviors.setup { context => new AppendEntries(node, state, context, leader, timers).appendEntries() }
    }
}

class AppendEntries private(
                             node: Node,
                             state: RaftState,
                             context: ActorContext[AppendEntriesCommand],
                             leader: ActorRef[AppendEntriesResponse],
                             timers: TimerScheduler[AppendEntriesCommand]
                           ) {

  import AppendEntries._

  implicit val system: ActorSystem[Nothing] = context.system
  implicit val ec: ExecutionContextExecutor = system.executionContext

  val clientSettings = GrpcClientSettings.connectToServiceAt(node.host, node.port).withTls(false)
  val client: RaftCommunicationServiceClient = RaftCommunicationServiceClient(clientSettings)

  makeVoteRequest()

  private def appendEntries(): Behavior[AppendEntriesCommand] = Behaviors.receiveMessage[AppendEntriesCommand] {
    case AppendEntriesSuccess(r) =>
      leader ! r

      timers.cancel(AppendEntriesRetryKey)

      Behaviors.stopped

    case AppendEntriesFailure() =>
      timers.startSingleTimer(AppendEntriesRetryKey, MakeVoteRequest, 90.milliseconds)

      context.log.info("Send retry append entries to node {}-{} and term {}", node.host, node.port, state.currentTerm)

      Behaviors.same

    case MakeVoteRequest =>
      timers.cancel(AppendEntriesRetryKey)

      makeVoteRequest()

      Behaviors.same

  }.receiveSignal {
    case (context: ActorContext[AppendEntriesCommand], postStop: PostStop) =>
      // todo could be one client for whole app, it's concurrent
      client.close()

      timers.cancel(AppendEntriesRetryKey)

      Behaviors.same
  }

  private def makeVoteRequest(): Unit = {
    context.log.info("Sent append entries to node {}-{} and term {}", node.host, node.port, state.currentTerm)

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
