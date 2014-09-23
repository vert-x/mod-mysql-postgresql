package io.vertx.asyncsql.test

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

import org.vertx.scala.core.AsyncResult
import org.vertx.scala.core.json._
import org.vertx.scala.testtools.TestVerticle
import org.vertx.testtools.VertxAssert.{assertEquals, assertTrue}

abstract class SqlTestVerticle extends TestVerticle with BaseVertxIntegrationTest {

  val address = "campudus.asyncdb"

  protected val baseConf = Json.obj("address" -> address, "maxPoolSize" -> 3, "transactionTimeout" -> 5000L)

  override final def before() {}

  override def asyncBefore(): Future[Unit] = {
    val p = Promise[Unit]
    container.deployModule(System.getProperty("vertx.modulename"), getConfig(), 1, { deploymentID: AsyncResult[String] =>
      if (deploymentID.failed()) {
        logger.info(s"Deployment failed, cause: ${deploymentID.cause()}")
        p.failure(deploymentID.cause())
      }
      assertTrue("deploymentID should not be null", deploymentID.succeeded())

      before()
      doBefore() onComplete {
        case Success(_) =>
          logger.info("starting tests")
          p.success()
        case Failure(ex) => p.failure(ex)
      }
    })
    p.future
  }

  def doBefore(): Future[_] = {
    Future.successful()
  }

  def getConfig(): JsonObject = Json.emptyObj()

  protected def raw(q: String) = Json.obj("action" -> "raw", "command" -> q)

  protected def insert(table: String, fields: JsonArray, values: JsonArray) =
    Json.obj("action" -> "insert", "table" -> table, "fields" -> fields, "values" -> values)

  protected def select(table: String, fields: JsonArray): JsonObject = select(table, Some(fields))

  protected def select(table: String, fields: JsonArray, conditions: JsonObject): JsonObject = select(table, Some(fields), Some(conditions))

  protected def select(table: String, fields: Option[JsonArray] = None, conditions: Option[JsonObject] = None): JsonObject = {
    val js = Json.obj("action" -> "select", "table" -> table)
    fields.map(js.putArray("fields", _))
    conditions.map(js.putObject("conditions", _))
    js
  }

  protected def prepared(statement: String, values: JsonArray) = Json.obj("action" -> "prepared", "statement" -> statement, "values" -> values)

  protected def transaction(statements: JsonObject*) = Json.obj("action" -> "transaction", "statements" -> Json.arr(statements: _*))

  protected def newTransaction = Json.obj("action" -> "start")

  protected def createTable(tableName: String) = expectOk(raw(createTableStatement(tableName))) map { reply =>
    assertEquals(0, reply.getInteger("rows"))
    reply
  }

  protected def dropTable(tableName: String) = expectOk(raw("DROP TABLE " + tableName + ";")) map { reply =>
    reply
  }

  protected def createDateTable(dateDataType: String) = s"""
      |  CREATE TABLE date_test (
      |    id SERIAL,
      |    test_date $dateDataType
      |  );
    """.stripMargin

  protected def createTableStatement(tableName: String) = """
          DROP TABLE IF EXISTS """ + tableName + """;
          CREATE TABLE """ + tableName + """ (
            id SERIAL,
            name VARCHAR(255),
            email VARCHAR(255) UNIQUE,
            is_male BOOLEAN,
            age INT,
            money DOUBLE PRECISION,
            wedding_date DATE
          );"""

  protected def createTableTestTwo: String = s"""CREATE TABLE test_two (
         |  id SERIAL,
         |  name VARCHAR(255),
         |  one_id BIGINT NOT NULL,
         |  PRIMARY KEY (id)
         |);""".stripMargin
}