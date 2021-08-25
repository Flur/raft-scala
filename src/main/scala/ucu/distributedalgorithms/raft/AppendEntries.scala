package ucu.distributedalgorithms.raft

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.grpc.GrpcClientSettings
import ucu.distributedalgorithms._
import ucu.distributedalgorithms.raft.AppendEntries.{AppendEntriesCommand, AppendEntriesSuccess}
import ucu.distributedalgorithms.raft.Raft.RaftState

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}


object AppendEntries {

  sealed trait AppendEntriesCommand

  final case class AppendEntriesSuccess(response: AppendEntriesResponse, followerIndexInCluster: Int) extends AppendEntriesCommand

  final case class AppendEntriesFailure() extends AppendEntriesCommand

  final case object MakeVoteRequest extends AppendEntriesCommand

  final case object AppendEntriesRetryKey extends AppendEntriesCommand

  def apply(node: Node, leader: ActorRef[AppendEntriesSuccess], state: RaftState, nextIndex: Int, followerIndexInCluster: Int): Behavior[AppendEntriesCommand] =
    Behaviors.withTimers { timers =>
      Behaviors.setup { context => new AppendEntries(nextIndex, followerIndexInCluster, node, state, context, leader, timers).appendEntries() }
    }
}

class AppendEntries private(
                             nextIndex: Int,
                             followerIndexInCluster: Int,
                             node: Node,
                             state: RaftState,
                             context: ActorContext[AppendEntriesCommand],
                             leader: ActorRef[AppendEntriesSuccess],
                             timers: TimerScheduler[AppendEntriesCommand]
                           ) {

  import AppendEntries._

  implicit val system: ActorSystem[Nothing] = context.system
  implicit val ec: ExecutionContextExecutor = system.executionContext

  val clientSettings = GrpcClientSettings.connectToServiceAt(node.host, node.port).withTls(false)
  val client: RaftCommunicationServiceClient = RaftCommunicationServiceClient(clientSettings)

  appendEntriesRequest()

  private def appendEntries(): Behavior[AppendEntriesCommand] = Behaviors.receiveMessage[AppendEntriesCommand] {
    case r@AppendEntriesSuccess(_, _) =>
      leader ! r

      timers.cancel(AppendEntriesRetryKey)

      Behaviors.stopped

    case AppendEntriesFailure() =>
      timers.startSingleTimer(AppendEntriesRetryKey, MakeVoteRequest, 1500.milliseconds)

      context.log.info("Send retry append entries to node {}-{} and term {}", node.host, node.port, state.currentTerm)

      Behaviors.same

    case MakeVoteRequest =>
      timers.cancel(AppendEntriesRetryKey)

      appendEntriesRequest()

      Behaviors.same

  }.receiveSignal {
    case (context: ActorContext[AppendEntriesCommand], postStop: PostStop) =>
      // todo could be one client for whole app, it's concurrent
      client.close()

      timers.cancel(AppendEntriesRetryKey)

      Behaviors.same
  }

  private def appendEntriesRequest(): Unit = {
    context.log.info("Sent append entries to node {}-{} and term {}", node.host, node.port, state.currentTerm)

    val logEntries = state.log.slice(nextIndex, state.log.length - 1)
    var prevLogTerm = 0
    var prevLogIndex = nextIndex - 1

    if (prevLogIndex > 0) {
      prevLogTerm = state.log(nextIndex).term
    } else {
      prevLogIndex = 0
    }

    val reply: Future[AppendEntriesResponse] = client.appendEntries(
      AppendEntriesRequest(state.currentTerm, state.id, prevLogIndex, prevLogTerm, logEntries)
    )

    context.pipeToSelf(reply) {
      case Success(msg: AppendEntriesResponse) =>
        AppendEntriesSuccess(msg, followerIndexInCluster)

      case Failure(_) => AppendEntriesFailure()
    }
  }
}
