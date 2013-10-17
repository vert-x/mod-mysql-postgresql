package io.vertx.asyncsql.test.postgresql

import org.junit.Test
import org.vertx.scala.core.json._
import org.vertx.testtools.VertxAssert._
import io.vertx.asyncsql.test.{ BaseSqlTests, SqlTestVerticle }
import scala.concurrent.Future

class BadlyConfiguredPostgreSqlTest extends SqlTestVerticle {

  val address = "campudus.asyncdb"
  val config = Json.obj("address" -> address, "database" -> "nonexistent")

  override def getConfig = config

  @Test
  def testNonExistentDatabase(): Unit = asyncTest {
    expectError(raw("SELECT 0")) map { reply =>
      assertTrue(reply.getString("message").contains("nonexistent"))
    }
  }
}