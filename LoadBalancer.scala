//> using toolkit typelevel:latest
//> using dep org.http4s::http4s-ember-server:0.23.25
//> using dep org.http4s::http4s-ember-client:0.23.25
//> using dep org.http4s::http4s-dsl:0.23.25
//> using dep ch.qos.logback:logback-classic:1.4.14
//> using resourceDir ./resources

import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import cats.effect.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.Logger
import org.http4s.client.Client
import org.http4s.*
import org.http4s.implicits.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration.*

object LoadBalancer extends IOApp with Http4sDsl[IO]:

  val logger = Slf4jLogger.getLogger[IO]

  def routes(
      client: Client[IO],
      urisRef: Ref[IO, std.Queue[IO, Uri]]
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case req @ GET -> Root =>
      for {
        urisQueue <- urisRef.get
        nextUri <- urisQueue.take
        _ <- urisQueue.offer(nextUri)
        _ <- logger.info(s"Forwarding request to server $nextUri")
        res <- client.expect[String](req.withUri(nextUri)).flatMap(Ok(_))
      } yield res
    }

  private def healthCheck(
      client: Client[IO],
      uris: List[Uri],
      urisRef: Ref[IO, std.Queue[IO, Uri]]
  ): IO[Unit] = {
    case class UriStatus(uri: Uri, healthy: Boolean)
    for {
      statuses <- uris
        .parTraverse { uri =>
          client
            .status(Request(uri = uri))
            .map(status => UriStatus(uri, status.isSuccess))
            .handleError(_ => UriStatus(uri, healthy = false))
            .flatTap(status =>
              if (status.healthy) logger.info(s"${status.uri} healthcheck ok")                
              else logger.warn(s"${status.uri} doesn't respond") 
            )
        }
      healthyUris = statuses.filter(_.healthy).map(_.uri)
      healthyQueue <- std.Queue.bounded[IO, Uri](uris.size)
      _ <- healthyUris.traverse(healthyQueue.offer)
      _ <- urisRef.set(healthyQueue)
    } yield ()
  }

  def run(args: List[String]): IO[ExitCode] = {
    val serverUris =
      args.map(port => s"http://localhost:$port").map(Uri.unsafeFromString)

    EmberClientBuilder.default[IO].build.use { client =>
      for {
        urisRef <- std.Queue.bounded[IO, Uri](serverUris.size).flatMap(Ref.of)
        _ <- urisRef.get.flatTap(queue => serverUris.traverse(queue.offer))
        _ <- (IO.sleep(10.seconds) *> healthCheck(
          client,
          serverUris,
          urisRef
        )).foreverM.start
        _ <- EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"80")
          .withHttpApp(
            Logger.httpApp(true, true)(routes(client, urisRef).orNotFound)
          )
          .build
          .useForever
      } yield ExitCode.Success
    }
  }
