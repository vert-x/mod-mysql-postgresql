package io.vertx.asyncsql.test

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.concurrent.Future
import org.vertx.scala.core.json.{ Json, JsonArray }
import org.vertx.testtools.VertxAssert._
import scala.concurrent.Promise
import org.vertx.scala.testtools.TestVerticle
import org.vertx.scala.core.AsyncResult
import org.vertx.scala.core.eventbus.Message
import org.vertx.java.core.json.JsonObject
import org.junit.Test

class FileHandlesTests extends TestVerticle {

  val config = Json.obj(
    "address" -> "backend",
    "connection" -> "PostgreSQL",
    "host" -> "localhost",
    "port" -> 5432,
    "username" -> "vertx",
    "password" -> "test",
    "database" -> "testdb")

  val expected = 10000
  var count = 0
  var startedAt: Long = 0

  override def asyncBefore(): Future[Unit] = {
    startedAt = System.currentTimeMillis()

    val p = Promise[Unit]
    container.deployModule(System.getProperty("vertx.modulename"), config, 1, { deploymentID: AsyncResult[String] =>
      if (deploymentID.failed()) {
        logger.info(deploymentID.cause())
        p.failure(deploymentID.cause())
      }
      assertTrue("deploymentID should not be null", deploymentID.succeeded())

      p.success()
    })
    p.future
  }

  def sendEvent(): Unit = {
    vertx.eventBus.send("query.me", "select '' || count(*) from pg_tables;", { event: Message[String] =>
      count += 1
      if (count == expected) {
        logger.info(s"Received ${count} messages in ${System.currentTimeMillis() - startedAt}")
        testComplete()
      }
    })
  }

  @Test
  def filehandleTest(): Unit = {
    vertx.eventBus.registerHandler("query.me", { msg: Message[String] =>
      val json = Json.obj(
        "action" -> "raw",
        "command" -> msg.body())

      vertx.eventBus.send("backend", json, { dbMsg: Message[JsonObject] =>
        val result = dbMsg.body()
        msg.reply(result.getArray("results").get[JsonArray](0).get(0).toString());
      })
    })

    logger.info(s"Sending ${expected} times.")
    for (i <- 0 until expected) sendEvent()
  }
}