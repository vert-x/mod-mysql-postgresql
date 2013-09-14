package io.vertx.asyncsql.test

import org.vertx.java.core.json.JsonObject
import scala.concurrent.Future
import scala.concurrent.Promise
import org.vertx.scala.core.Vertx
import org.vertx.testtools.TestVerticle
import org.vertx.testtools.VertxAssert._
import io.vertx.helpers.VertxExecutionContext
import org.vertx.scala.core.eventbus.Message
import java.util.concurrent.atomic.AtomicInteger
import org.vertx.scala.core.logging.Logger

trait BaseVertxIntegrationTest extends VertxExecutionContext { this: TestVerticle =>
  var log: Logger = null

  val address: String

  protected def asyncTest[X](fn: => Future[X]): Future[Unit] = {
    fn recover {
      case x =>
        log.error("async fail in test code!", x)
        fail("Something failed asynchronously: " + x.getClass() + x.getMessage())
    } map { _ =>
      testComplete
    }
  }

  protected def ebSend(q: JsonObject): Future[JsonObject] = {
    val p = Promise[JsonObject]
    log.info("sending " + q.encode() + " to " + address)
    Vertx(getVertx()).eventBus.send(address, q) { reply: Message[JsonObject] =>
      log.info("got a reply: " + reply.body.encode())
      p.success(reply.body)
    }
    p.future
  }

  protected def expectOk(q: JsonObject): Future[JsonObject] = ebSend(q) map { reply =>
    assertEquals("ok", reply.getString("status"))
    reply
  }

  protected def expectError(q: JsonObject, errorId: Option[String] = None, errorMessage: Option[String] = None): Future[JsonObject] = ebSend(q) map { reply =>
    assertEquals("error", reply.getString("status"))
    errorId.map(assertEquals(_, reply.getString("id")))
    errorMessage.map(assertEquals(_, reply.getString("message")))
    reply
  }

}