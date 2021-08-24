//package ucu.distributedalgorithms
//
//import akka.actor.testkit.typed.scaladsl.{ManualTime, ScalaTestWithActorTestKit, TestProbe}
//import akka.actor.typed.scaladsl.Behaviors
//import akka.actor.typed.{ActorRef, Behavior}
//import org.scalatest.wordspec.AnyWordSpecLike
//import ucu.distributedalgorithms.raft.{Candidate, Raft}
//import ucu.distributedalgorithms.raft.Raft._
//
//object query {
//  def apply(cluster: List[Node], state: RaftState, candidate: ActorRef[Command]): Behavior[Command] = Behaviors.receiveMessage { msg =>
////    case RequestVoteQueryCompleted(_) =>
//      Behaviors.empty[Command]
//  }
//}
//
//class CandidateSpec extends ScalaTestWithActorTestKit(ManualTime.config) with AnyWordSpecLike {
//
//  val manualTime: ManualTime = ManualTime()
//
//  "Candidate actor" must {
//    "Should became leader after successful election" in {
//
//    }
//
//    "Should increment vote" in {
//      val cluster = List(Node("localhost", 1), Node("localhost", 2))
//      val raftState = RaftState(
//        RaftPersistentState(1, 0, List.empty),
//        RaftVolatileState(0, 0),
//        0
//      )
//
//      val probe: TestProbe[Command] = createTestProbe[Command]()
//
//      val followerActor = spawn(Candidate(1, cluster, raftState, query.apply))
//
//      followerActor ! RequestVoteResultFromQuery(1, 1, true)
//      followerActor ! GetRaftActorData(probe.ref)
//
//      probe.expectMessage(RaftActorData(2, raftState, CandidateStateType()))
//    }
//
//    "Should not take into account responses from older vote requests" in {
//      val cluster = List(Node("localhost", 1), Node("localhost", 2))
//      val raftState = RaftState(
//        RaftPersistentState(1, 0, List.empty),
//        RaftVolatileState(0, 0),
//        0
//      )
//
//      val probe: TestProbe[Command] = createTestProbe[Command]()
//
//      val followerActor = spawn(Candidate(1, cluster, raftState, query.apply))
//
//      followerActor ! RequestVoteResultFromQuery(1, 1, true)
//
//      probe.expectMessage(RequestVoteResult(2, false))
//    }
//
//
//    /** Votes for other actor */
//    "Should give a vote" in {
//
//    }
//
//    /** Elections */
//    "Should win an election" in {
//
//    }
//
//    "Another server establishes itself as leader" in {
//
//    }
//
//    "Timeout" in {
//      // todo
//    }
//
//    "If the term in the RPC is smaller than the candidate’s current term, then the candidate rejects the RPC and continues in candidate state." in {
//
//    }
//
//    "If the leader’s term (included in its RPC) is at least\nas large as the candidate’s current term, then the candidate\nrecognizes the leader as legitimate and returns to follower\nstate" in {
//
//    }
//  }
//}
