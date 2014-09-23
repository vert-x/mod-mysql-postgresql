package io.vertx.asyncsql.test.mysql

import io.vertx.asyncsql.test.{BaseSqlTests, SqlTestVerticle}
import org.junit.Test
import org.vertx.scala.core.json._
import org.vertx.testtools.VertxAssert._

class MySqlTest extends SqlTestVerticle with BaseSqlTests {

  override def doBefore() = expectOk(raw("DROP TABLE IF EXISTS `some_test`"))

  override def getConfig() = baseConf.putString("connection", "MySQL")

  override def createDateTable(dateDataType: String) = s"""
      |  CREATE TABLE date_test (
      |    id INT NOT NULL AUTO_INCREMENT,
      |    test_date $dateDataType,
      |    PRIMARY KEY(id)
      |  );
    """.stripMargin

  override def createTableStatement(tableName: String) = s"""
          CREATE TABLE $tableName (
            id INT NOT NULL AUTO_INCREMENT,
            name VARCHAR(255),
            email VARCHAR(255) UNIQUE,
            is_male BOOLEAN,
            age INT,
            money FLOAT,
            wedding_date DATE,
            PRIMARY KEY (id)
          );""".stripMargin

  override def createTableTestTwo: String = """CREATE TABLE test_two (
         |  id SERIAL,
         |  name VARCHAR(255),
         |  one_id BIGINT UNSIGNED NOT NULL,
         |  PRIMARY KEY (id)
         |);""".stripMargin

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

  @Test
  def zeroDateTest(): Unit = (for {
    _ <- setupTableTest()
    (msg, insertReply) <- sendOk(raw("INSERT INTO some_test (name, wedding_date) VALUES ('tester', '0000-00-00');"))
    (msg, reply) <- sendOk(prepared("SELECT wedding_date FROM some_test WHERE name=?", Json.arr("tester")))
  } yield {
    val receivedFields = reply.getArray("fields")
    logger.info(reply.getArray("results").get[JsonArray](0).get[String](0))
    assertEquals(Json.arr("wedding_date"), receivedFields)
    assertEquals(null, reply.getArray("results").get[JsonArray](0).get[String](0))
    testComplete()
  }) recover failedTest

}