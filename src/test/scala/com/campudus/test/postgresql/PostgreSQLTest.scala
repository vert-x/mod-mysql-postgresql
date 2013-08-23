package com.campudus.test.postgresql

import org.junit.Test
import scala.concurrent.Promise
import scala.concurrent.Future
import org.vertx.testtools.VertxAssert._
import org.vertx.java.core.json.JsonObject
import org.vertx.scala.platform.Verticle
import org.vertx.scala.platform.Container
import org.vertx.scala.core.Vertx
import org.vertx.java.core.eventbus.Message
import com.campudus.vertx.VertxScalaHelpers
import org.vertx.java.core.json.JsonArray
import com.campudus.test.SqlTestVerticle
import org.vertx.testtools.VertxAssert._
import scala.util.Failure
import scala.util.Success
import com.github.mauricio.async.db.column.DateEncoderDecoder

class PostgreSQLTest extends SqlTestVerticle with VertxScalaHelpers {

  val address = "campudus.asyncdb"
  val config = new JsonObject().putString("address", address)
  lazy val logger = container.logger()

  override def getConfig = config

  def withTable[X](tableName: String)(fn: => Future[X]) = {
    for {
      _ <- createTable(tableName)
      sth <- fn
      _ <- dropTable(tableName)
    } yield sth
  }

  def asyncTableTest[X](tableName: String)(fn: => Future[X]) = asyncTest(withTable(tableName)(fn))

  @Test
  def simpleConnection(): Unit = asyncTest {
    expectOk(raw("SELECT 0")) map { reply =>
      assertEquals(1, reply.getNumber("rows"))
      val res = reply.getArray("results")
      assertEquals(1, res.size())
      assertEquals(0, res.get[JsonArray](0).get[Int](0))
    }
  }

  @Test
  def multipleFields(): Unit = asyncTest {
    expectOk(raw("SELECT 1, 0")) map { reply =>
      assertEquals(1, reply.getNumber("rows"))
      val res = reply.getArray("results")
      assertEquals(1, res.size())
      val firstElem = res.get[JsonArray](0)
      assertEquals(1, firstElem.get[Integer](0))
      assertEquals(0, firstElem.get[Integer](1))
    }
  }

  @Test
  def createAndDropTable(): Unit = asyncTest {
    createTable("some_test") flatMap (_ => dropTable("some_test")) map { reply =>
      assertEquals(0, reply.getNumber("rows"))
    }
  }

  @Test
  def insertCorrect(): Unit = asyncTableTest("some_test") {
    expectOk(insert("some_test", new JsonArray("""["name","email"]"""), new JsonArray("""[["Test","test@example.com"],["Test2","test2@example.com"]]""")))
  }

  private def typeTestInsert[X](fn: => Future[X]) = asyncTableTest("some_test") {
    expectOk(insert("some_test",
      new JsonArray("""["name","email","is_male","age","money","wedding_date"]"""),
      new JsonArray("""[["Mr. Test","test@example.com",true,15,167.31,"2024-04-01"],
            ["Ms Test2","test2@example.com",false,43,167.31,"1997-12-24"]]"""))) flatMap { _ =>
      fn
    }
  }

  @Test
  def insertTypeTest(): Unit = typeTestInsert {
    Future.successful()
  }

  @Test
  def insertMaliciousDataTest(): Unit = asyncTableTest("some_test") {
    // If this SQL injection works, the drop table of asyncTableTest would throw an exception
    expectOk(insert("some_test",
      new JsonArray("""["name","email","is_male","age","money","wedding_date"]"""),
      new JsonArray("""[["Mr. Test","test@example.com",true,15,167.31,"2024-04-01"],
            ["Ms Test2','some@example.com',false,15,167.31,'2024-04-01');DROP TABLE some_test;--","test2@example.com",false,43,167.31,"1997-12-24"]]""")))
  }

  @Test
  def insertUniqueProblem(): Unit = asyncTableTest("some_test") {
    expectError(insert("some_test", new JsonArray("""["name","email"]"""), new JsonArray("""[["Test","test@example.com"],["Test","test@example.com"]]"""))) map { reply =>
      logger.info("expected error: " + reply.encode())
    }
  }

  @Test
  def selectEverything(): Unit = typeTestInsert {
    val fieldsArray = new JsonArray("""["name","email","is_male","age","money","wedding_date"]""")
    expectOk(select("some_test", fieldsArray)) map { reply =>
      logger.info("reply: " + reply.encode())
      val receivedFields = reply.getArray("fields")
      assertEquals(fieldsArray, receivedFields)
      val results = reply.getArray("results")
      val mrTest = results.get[JsonArray](0)
      checkMrTest(mrTest)
    }
  }

  private def checkTestPerson(mrOrMrs: JsonArray) = {
    mrOrMrs.get[String](0) match {
      case "Mr. Test" => checkMrTest(mrOrMrs)
      case "Mrs. Test" => checkMrsTest(mrOrMrs)
    }
  }

  private def checkMrTest(mrTest: JsonArray) = {
    assertEquals("Mr. Test", mrTest.get[String](0))
    assertEquals("test@example.com", mrTest.get[String](1))
    assertEquals(true, mrTest.get[Boolean](2))
    assertEquals(15, mrTest.get[Integer](3))
    assertEquals(167.31, mrTest.get[Integer](4))
    assertEquals(DateEncoderDecoder.decode("2024-04-01").toString(), mrTest.get[String](5))
  }

  private def checkMrsTest(mrsTest: JsonArray) = {
    assertEquals("Mrs. Test", mrsTest.get[String](0))
    assertEquals("test2@example.com", mrsTest.get[String](1))
    assertEquals(false, mrsTest.get[Boolean](2))
    assertEquals(43, mrsTest.get[Integer](3))
    assertEquals(167.31, mrsTest.get[Integer](4))
    assertEquals(DateEncoderDecoder.decode("1997-12-24").toString(), mrsTest.get[String](5))
  }

  @Test
  def selectFiltered(): Unit = typeTestInsert {
    val fieldsArray = new JsonArray("""["name","email"]""")
    expectOk(select("some_test", fieldsArray)) map { reply =>
      val receivedFields = reply.getArray("fields")
      assertEquals(fieldsArray, receivedFields)
      assertEquals(2, reply.getInteger("rows"))
      val results = reply.getArray("results")
      val mrOrMrs = results.get[JsonArray](0)
      mrOrMrs.get[String](0) match {
        case "Mr. Test" =>
          assertEquals("Mr. Test", mrOrMrs.get[String](0))
          assertEquals("test@example.com", mrOrMrs.get[String](1))
        case "Mrs. Test" =>
          assertEquals("Mrs. Test", mrOrMrs.get[String](0))
          assertEquals("test2@example.com", mrOrMrs.get[String](1))
      }
    }
  }

}