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
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger
import cats.effect.*
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.Logger
import org.http4s.client.Client
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration.*

object Server1 extends IOApp with Http4sDsl[IO]:

  val logger = Slf4jLogger.getLogger[IO]

  def routes(port: String): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case req @ GET -> Root =>
      IO.sleep(1.second) *> Ok(s"Hello from backend server $port") <* logger
        .info(
          "replied with hello message"
        )
    }

  def run(args: List[String]): IO[ExitCode] = {
    val port = args.headOption.getOrElse("8081")
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromString(port).get)
      .withHttpApp(Logger.httpApp(true, true)(routes(port).orNotFound))
      .build
      .useForever
      .as(ExitCode.Success)

  }
