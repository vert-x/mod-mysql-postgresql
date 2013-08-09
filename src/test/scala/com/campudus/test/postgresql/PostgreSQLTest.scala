package com.campudus.test.postgresql

import org.junit.Test
import scala.concurrent.Promise
import com.campudus.test.TestVerticle
import scala.concurrent.Future
import org.vertx.testtools.VertxAssert._
import org.vertx.java.core.json.JsonObject
import org.vertx.scala.platform.Verticle
import org.vertx.scala.platform.Container
import org.vertx.scala.core.Vertx
import org.vertx.java.core.eventbus.Message
import com.campudus.vertx.VertxScalaHelpers

class PostgreSQLTest extends TestVerticle with VertxScalaHelpers {

  val address = "campudus.asyncdb"
  val config = new JsonObject().putString("address", address)
  lazy val logger = container.logger()

  override def getConfig = config

  override def before() = {
    Future.successful()
  }

  private def query(q: String) = new JsonObject().putString("action", "query").putString("query", q)

  private def ebSend(q: JsonObject): Future[JsonObject] = {
    val p = Promise[JsonObject]
    logger.info("sending " + q.encode() + " to " + address)
    vertx.eventBus.send(address, q, { reply: Message[JsonObject] =>
      logger.info("got a reply: " + reply.body().encode())
      p.success(reply.body())
    })
    p.future
  }

  private def expectOk(q: JsonObject): Future[JsonObject] = ebSend(q) map { reply =>
    assertEquals("ok", reply.getString("status"))
    reply
  }

  private def expectError(q: JsonObject, errorId: Option[String] = None, errorMessage: Option[String] = None): Future[JsonObject] = ebSend(q) map { reply =>
    assertEquals("error", reply.getString("status"))
    errorId.map(assertEquals(_, reply.getString("id")))
    errorMessage.map(assertEquals(_, reply.getString("message")))
    reply
  }

  @Test
  def simpleConnection() {
    logger.info("started simple connection test-info")

    expectOk(query("SELECT 0")) map { reply =>
      assertEquals(0, reply.getInteger("result"))
      testComplete()
    }
  }

}