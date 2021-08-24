//package ucu.distributedalgorithms
//
//import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ManualTime, ScalaTestWithActorTestKit, TestProbe}
//import org.scalatest.wordspec.AnyWordSpecLike
//import ucu.distributedalgorithms.raft.Follower
//import ucu.distributedalgorithms.raft.Raft.{RaftCommand, RaftPersistentState, RaftRequestVoteRequest, RaftRequestVoteResponse, RaftState, RaftVolatileState}
//
//import scala.concurrent.duration.DurationInt
//
//
//// todo check state of the actor, not only reply
//class FollowerSpec extends ScalaTestWithActorTestKit(ManualTime.config) with AnyWordSpecLike {
//
//  val manualTime: ManualTime = ManualTime()
//
//  "Follower Behaviour" must {
//
//    /** Timeout * */
//    "Should log info about follower timeout" in {
//      val cluster = List(Node("localhost", 1))
//      val raftState = RaftState(
//        RaftPersistentState(1, 0, List.empty),
//        RaftVolatileState(0, 0),
//        0,
//      )
//
//      spawn(Follower(cluster, raftState))
//
//      LoggingTestKit.info("Election Timeout for AppendEntries switching to candidate").expect {
//        manualTime.timePasses(4.seconds)
//      }
//    }
//
//    /** Leader election **/
//    "Should not give vote for candidate if term is lower and node not voted" in {
//      val cluster = List(Node("localhost", 1))
//      val raftState = RaftState(
//        RaftPersistentState(5, 0, List.empty),
//        RaftVolatileState(0, 0),
//        0
//      )
//
//      val probe: TestProbe[RaftCommand] = createTestProbe[RaftCommand]()
//
//      val followerActor = spawn(Follower(cluster, raftState))
//
//      followerActor ! RaftRequestVoteRequest(1, 1, 0, 0, probe.ref)
//
//      probe.expectMessage(RaftRequestVoteResponse(5, voteGranted = false))
//    }
//
//    "Should not give vote for candidate if term is lower and node voted for same node" in {
//      val cluster = List(Node("localhost", 1))
//      val raftState = RaftState(
//        RaftPersistentState(5, 1, List.empty),
//        RaftVolatileState(0, 0),
//        0
//      )
//
//      val probe: TestProbe[RaftCommand] = createTestProbe[RaftCommand]()
//
//      val followerActor = spawn(Follower(cluster, raftState))
//
//      followerActor ! RaftRequestVoteRequest(1, 1, 0, 0, probe.ref)
//
//      probe.expectMessage(RaftRequestVoteResponse(5, voteGranted = false))
//    }
//
//    "Should not give vote for candidate if term is lower and node vote for other node" in {
//      val cluster = List(Node("localhost", 1))
//      val raftState = RaftState(
//        RaftPersistentState(5, 4, List.empty),
//        RaftVolatileState(0, 0),
//        0
//      )
//
//      val probe: TestProbe[RaftCommand] = createTestProbe[RaftCommand]()
//
//      val followerActor = spawn(Follower(cluster, raftState))
//
//      followerActor ! RaftRequestVoteRequest(1, 1, 0, 0, probe.ref)
//
//      probe.expectMessage(RaftRequestVoteResponse(5, voteGranted = false))
//    }
//
//    "Should give vote for candidate if term is same and node not voted" in {
//      val cluster = List(Node("localhost", 1))
//      val raftState = RaftState(
//        RaftPersistentState(1, 0, List.empty),
//        RaftVolatileState(0, 0),
//        0
//      )
//
//      val probe: TestProbe[RaftCommand] = createTestProbe[RaftCommand]()
//
//      val followerActor = spawn(Follower(cluster, raftState))
//
//      followerActor ! RaftRequestVoteRequest(1, 1, 0, 0, probe.ref)
//
//      probe.expectMessage(RaftRequestVoteResponse(1, voteGranted = true))
//    }
//
//    "Should give vote for candidate if term is same and node voted for same node" in {
//      val cluster = List(Node("localhost", 1))
//      val raftState = RaftState(
//        RaftPersistentState(1, 1, List.empty),
//        RaftVolatileState(0, 0),
//        0
//      )
//
//      val probe: TestProbe[RaftCommand] = createTestProbe[RaftCommand]()
//
//      val followerActor = spawn(Follower(cluster, raftState))
//
//      followerActor ! RaftRequestVoteRequest(1, 1, 0, 0, probe.ref)
//
//      probe.expectMessage(RaftRequestVoteResponse(1, voteGranted = true))
//    }
//
//    "Should not give vote for candidate if term is same and node voted for other node" in {
//      val cluster = List(Node("localhost", 1))
//      val raftState = RaftState(
//        RaftPersistentState(1, 1, List.empty),
//        RaftVolatileState(0, 0),
//        0
//      )
//
//      val probe: TestProbe[RaftCommand] = createTestProbe[RaftCommand]()
//
//      val followerActor = spawn(Follower(cluster, raftState))
//
//      followerActor ! RaftRequestVoteRequest(1, 3, 0, 0, probe.ref)
//
//      probe.expectMessage(RaftRequestVoteResponse(1, voteGranted = false))
//    }
//
//    "Should give vote for candidate if term is higher and node not voted" in {
//      val cluster = List(Node("localhost", 1))
//      val raftState = RaftState(
//        RaftPersistentState(1, 0, List.empty),
//        RaftVolatileState(0, 0),
//        0
//      )
//
//      val probe: TestProbe[RaftCommand] = createTestProbe[RaftCommand]()
//
//      val followerActor = spawn(Follower(cluster, raftState))
//
//      followerActor ! RaftRequestVoteRequest(5, 1, 0, 0, probe.ref)
//
//      probe.expectMessage(RaftRequestVoteResponse(5, voteGranted = true))
//    }
//
//    "Should give vote for candidate if term is higher and node voted same node before" in {
//      val cluster = List(Node("localhost", 1))
//      val raftState = RaftState(
//        RaftPersistentState(1, 1, List.empty),
//        RaftVolatileState(0, 0),
//        0
//      )
//
//      val probe: TestProbe[RaftCommand] = createTestProbe[RaftCommand]()
//
//      val followerActor = spawn(Follower(cluster, raftState))
//
//      followerActor ! RaftRequestVoteRequest(5, 1, 0, 0, probe.ref)
//
//      probe.expectMessage(RaftRequestVoteResponse(5, voteGranted = true))
//    }
//
//    "Should not give vote for candidate if term is higher and node voted other node before" in {
//      val cluster = List(Node("localhost", 1))
//      val raftState = RaftState(
//        RaftPersistentState(1, 2, List.empty),
//        RaftVolatileState(0, 0),
//        0
//      )
//
//      val probe: TestProbe[RaftCommand] = createTestProbe[RaftCommand]()
//
//      val followerActor = spawn(Follower(cluster, raftState))
//
//      followerActor ! RaftRequestVoteRequest(5, 1, 0, 0, probe.ref)
//
//      probe.expectMessage(RaftRequestVoteResponse(5, voteGranted = false))
//    }
//  }
//}
