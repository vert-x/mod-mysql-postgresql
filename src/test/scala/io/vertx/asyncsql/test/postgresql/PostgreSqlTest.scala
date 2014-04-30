package io.vertx.asyncsql.test.postgresql

import org.vertx.scala.core.json._
import io.vertx.asyncsql.test.{ BaseSqlTests, SqlTestVerticle }

class PostgreSqlTest extends SqlTestVerticle with BaseSqlTests {

  val address = "campudus.asyncdb"
  val config = Json.obj("address" -> address, "maxPoolSize" -> 3)

  override def getConfig = config

}