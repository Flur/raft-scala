package ucu.distributedalgorithms.communication

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Route, RouteResult}
import ucu.distributedalgorithms.RaftCommunicationServiceHandler
import ucu.distributedalgorithms.raft.Raft

import scala.concurrent.{ExecutionContext, Future}

class RaftServerGrpcRoutes(raft: ActorRef[Raft.RaftCommand])(implicit system: ActorSystem[_]) {

  implicit val executionContext: ExecutionContext = system.executionContext

  val handler: HttpRequest => Future[HttpResponse] =
    RaftCommunicationServiceHandler(new RaftServiceImpl(raft))

  val routes: Route = { ctx => handler(ctx.request).map(RouteResult.Complete) }
}