package io.vertx.asyncsql.test.postgresql

import org.vertx.scala.core.json._
import io.vertx.asyncsql.test.{ BaseSqlTests, SqlTestVerticle }

class PostgreSqlTest extends SqlTestVerticle with BaseSqlTests {

  override def getConfig() =
    baseConf.putString("username", "vertx").putString("password", "test").putString("database", "testdb")

  override protected def blobDataType: String = "BYTEA"

}