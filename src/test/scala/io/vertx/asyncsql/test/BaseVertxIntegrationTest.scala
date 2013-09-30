package io.vertx.asyncsql.test

import scala.concurrent.{ Future, Promise }

import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.json.JsonObject
import org.vertx.scala.core.logging.Logger
import org.vertx.scala.testtools.TestVerticle
import org.vertx.testtools.VertxAssert.{ assertEquals, fail, testComplete }

trait BaseVertxIntegrationTest { this: TestVerticle =>
  val address: String

  protected def asyncTest[X](fn: => Future[X]): Future[Unit] = {
    fn recover {
      case x =>
        logger.error("async fail in test code!", x)
        fail("Something failed asynchronously: " + x.getClass() + x.getMessage())
    } map { _ =>
      testComplete
    }
  }

  protected def ebSend(q: JsonObject): Future[JsonObject] = {
    val p = Promise[JsonObject]
    logger.info("sending " + q.encode() + " to " + address)
    vertx.eventBus.send(address, q, { reply: Message[JsonObject] =>
      logger.info("got a reply: " + reply.body.asInstanceOf[JsonObject].encode())
      p.success(reply.body.asInstanceOf[JsonObject])
    })
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