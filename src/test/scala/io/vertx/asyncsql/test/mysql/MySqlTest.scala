package io.vertx.asyncsql.test.mysql

import org.vertx.scala.core.json.Json
import io.vertx.asyncsql.test.{ BaseSqlTests, SqlTestVerticle }

class MySqlTest extends SqlTestVerticle with BaseSqlTests {

  val address = "campudus.asyncdb"
  val config = Json.obj("address" -> address, "connection" -> "MySQL", "maxPoolSize" -> 3)

  override def doBefore() = expectOk(raw("DROP TABLE IF EXISTS `some_test`"))
  override def getConfig = config

  override def createTableStatement(tableName: String) = """
CREATE TABLE """ + tableName + """ (
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255),
  email VARCHAR(255) UNIQUE,
  is_male BOOLEAN,
  age INT,
  money FLOAT,
  wedding_date DATE,
  PRIMARY KEY (id)
);
"""

}