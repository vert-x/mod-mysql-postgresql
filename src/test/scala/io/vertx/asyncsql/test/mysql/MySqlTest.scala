package io.vertx.asyncsql.test.mysql

import io.vertx.asyncsql.test.{BaseSqlTests, SqlTestVerticle}
import org.junit.Test
import org.vertx.scala.core.json._
import org.vertx.testtools.VertxAssert._

class MySqlTest extends SqlTestVerticle with BaseSqlTests {

  override def isMysql = true

  override def doBefore() = expectOk(raw("DROP TABLE IF EXISTS `some_test`"))

  override def getConfig() = baseConf.putString("connection", "MySQL")

  override def createDateTable(dateDataType: String) = s"""
      |  CREATE TABLE date_test (
      |    id INT NOT NULL AUTO_INCREMENT,
      |    test_date $dateDataType,
      |    PRIMARY KEY(id)
      |  );
    """.stripMargin

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
);"""

  @Test
  def datetimeTest(): Unit =
    (for {
      (m, r) <- sendOk(raw("DROP TABLE IF EXISTS date_test"))
      (msg, r2) <- sendOk(raw(createDateTable("datetime")))
      (msg, insertReply) <- sendOk(raw("INSERT INTO date_test (test_date) VALUES ('2015-04-04');"))
      (msg, reply) <- sendOk(raw("SELECT test_date FROM date_test"))
    } yield {
      val receivedFields = reply.getArray("fields")
      assertEquals(Json.arr("test_date"), receivedFields)
      logger.info("date is: " + reply.getArray("results").get[JsonArray](0).get[String](0));
      assertEquals("2015-04-04T00:00:00.000", reply.getArray("results").get[JsonArray](0).get[String](0))
      testComplete()
    }) recover failedTest

}