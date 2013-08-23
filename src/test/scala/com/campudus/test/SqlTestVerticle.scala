package com.campudus.test

import com.campudus.vertx.VertxExecutionContext
import scala.concurrent.Future
import org.vertx.java.core.json.JsonObject
import org.vertx.java.core.Handler
import org.vertx.java.core.AsyncResult
import org.vertx.testtools.VertxAssert._
import org.vertx.scala.platform.Verticle
import org.junit.runner.RunWith
import org.vertx.testtools.JavaClassRunner
import java.lang.reflect.InvocationTargetException
import org.vertx.java.core.logging.impl.LoggerFactory
import org.vertx.scala.core.json.JSON
import scala.concurrent.Promise
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.Vertx
import org.vertx.java.core.json.JsonArray
import com.campudus.vertx.VertxScalaHelpers

abstract class SqlTestVerticle extends org.vertx.testtools.TestVerticle with VertxExecutionContext with VertxScalaHelpers {

  lazy val log = container.logger()

  override def start() = {
    initialize()

    log.info("starting module " + System.getProperty("vertx.modulename"))

    container.deployModule(System.getProperty("vertx.modulename"), getConfig(), new Handler[AsyncResult[String]]() {
      override def handle(deploymentID: AsyncResult[String]) = {
        if (deploymentID.failed()) {
          log.info(deploymentID.cause())
        }
        assertTrue("deploymentID should not be null", deploymentID.succeeded())

        before() map { _ =>
          log.info("starting tests")
          startTests()
        }
      }
    })
  }

  def before(): Future[_] = Future.successful()

  def getConfig(): JsonObject = new JsonObject()

  val address: String

  protected def asyncTest[X](fn: => Future[X]) = {
    fn recover {
      case x =>
        log.error("async fail in test code!", x)
        fail("Something failed asynchronously: " + x.getClass() + x.getMessage())
    } map { _ =>
      testComplete()
    }
  }

  protected def ebSend(q: JsonObject): Future[JsonObject] = {
    val p = Promise[JsonObject]
    log.info("sending " + q.encode() + " to " + address)
    Vertx(vertx).eventBus.send(address, q) { reply: Message[JsonObject] =>
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

  protected def raw(q: String) = new JsonObject().putString("action", "raw").putString("command", q)

  protected def insert(table: String, fields: JsonArray, values: JsonArray) =
    new JsonObject().putString("action", "insert").putString("table", table).putArray("fields", fields).putArray("values", values)

  protected def select(table: String, fields: JsonArray) = new JsonObject().putString("action", "select").putString("table", table).putArray("fields", fields)

  protected def createTable(tableName: String) = expectOk(raw("""
CREATE TABLE """ + tableName + """ (
  id SERIAL,
  name VARCHAR(255),
  email VARCHAR(255) UNIQUE,
  is_male BOOLEAN,
  age INT,
  money FLOAT,
  wedding_date DATE
);
""")) map { reply =>
    assertEquals(0, reply.getNumber("rows"))
    reply
  }

  protected def dropTable(tableName: String) = expectOk(raw("DROP TABLE " + tableName + ";")) map { reply =>
    reply
  }

}