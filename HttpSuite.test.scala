//> using toolkit typelevel:latest
//> using dep com.disneystreaming::weaver-cats:0.8.3
//> using dep com.dimafeng::testcontainers-scala:0.41.0
//> using testFramework "weaver.framework.CatsEffect"

import cats.effect.*
import org.http4s.client.*
import weaver.*
import org.http4s.ember.client.EmberClientBuilder
import com.dimafeng.testcontainers.*
import utils.*
import java.io.File
import org.testcontainers.containers.Network
import scala.concurrent.duration._
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration
import org.testcontainers.containers.wait.strategy.WaitStrategy
import cats.syntax.all._

object HttpSuite extends IOSuite {

  val serverPorts = List(8081, 8082, 8083)

  override type Res = Client[IO]
  override def sharedResource: Resource[IO, Res] =
    for {
      client <- EmberClientBuilder.default[IO].build
      _ <- serverResource(serverPorts)
      _ <- loadBalancerResource
    } yield (client)

  test("return success status").usingRes { case client =>
    for {
      statusCode <- client.get("http://localhost") { response =>
        IO.pure(response.status.code)
      }
    } yield expect(statusCode == 200)
  }

  test("query subsequent servers").usingRes { case client => 
    for {
      responses <- client.get("http://localhost") {
        _.bodyText.compile.lastOrError  
      }.replicateA(serverPorts.size)
      expected = serverPorts.map(port => s"Hello from backend server $port")
    } yield expect(responses.toSet == expected.toSet)
  }
}

object utils {
  private val loadBalancer = FixedHostPortGenericContainer(
    "load-balancer:latest",
    exposedHostPort = 80,
    exposedContainerPort = 80,
    command = Seq("8081", "8082", "8083"),
    env = Map(("GATEWAY_ADDRESS" -> "172.17.0.1")),
    waitStrategy = Wait.forLogMessage(s".*Ember-Server service bound to address: 0.0.0.0:80.*", 1)
  )

  private def server(port: Int) =
    FixedHostPortGenericContainer(
      "server:latest",
      exposedHostPort = port,
      exposedContainerPort = port,
      command = Seq(port.toString),
      waitStrategy = Wait.forLogMessage(s".*Ember-Server service bound to address: 0.0.0.0:$port.*", 1)
    )

  private def runContainer(container: Container) =
    Resource.make(IO(container.start()).as(container))(c => IO(c.stop()))

  val loadBalancerResource = runContainer(loadBalancer)

  def serverResource(ports: List[Int]) = ports.traverse(port => runContainer(server(port)))
}
