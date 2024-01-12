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

object LoadBalancer extends IOApp.Simple with Http4sDsl[IO]:

  val logger = Slf4jLogger.getLogger[IO]

  val serverURIs =
    List(uri"http://localhost:8081", uri"http://localhost:8082")

  def routes(client: Client[IO], uris: std.Queue[IO, Uri]): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case req @ GET -> Root =>
      for {
        nextUri <- uris.take
        _ <- uris.offer(nextUri)
        _ <- logger.info(s"Forwarding request to server $nextUri")
        res <- client.expect[String](req.withUri(nextUri)).flatMap(Ok(_))
      } yield res
    }

  def run: IO[Unit] =
    EmberClientBuilder.default[IO].build.use { client =>
      for {
        uris <- std.Queue.unbounded[IO, Uri]
        _ <- serverURIs.traverse(uris.offer)
        _ <- EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"80")
          .withHttpApp(
            Logger.httpApp(true, true)(routes(client, uris).orNotFound)
          )
          .build
          .useForever
      } yield ()
    }
