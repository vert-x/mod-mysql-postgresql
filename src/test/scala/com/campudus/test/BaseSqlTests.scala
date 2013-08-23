package com.campudus.test

import scala.concurrent.Future
import org.vertx.scala.core.json.JsonArray
import org.vertx.testtools.VertxAssert._
import org.vertx.scala.core.json.Json

trait BaseSqlTests { this: SqlTestVerticle =>
  lazy val logger = getContainer().logger()

  def withTable[X](tableName: String)(fn: => Future[X]) = {
    for {
      _ <- createTable(tableName)
      sth <- fn
      _ <- dropTable(tableName)
    } yield sth
  }

  def asyncTableTest[X](tableName: String)(fn: => Future[X]) = asyncTest(withTable(tableName)(fn))

  private def typeTestInsert[X](fn: => Future[X]) = asyncTableTest("some_test") {
    expectOk(insert("some_test",
      new JsonArray("""["name","email","is_male","age","money","wedding_date"]"""),
      new JsonArray("""[["Mr. Test","test@example.com",true,15,167.31,"2024-04-01"],
            ["Ms Test2","test2@example.com",false,43,167.31,"1997-12-24"]]"""))) flatMap { _ =>
      fn
    }
  }

  def simpleConnection(): Unit = asyncTest {
    expectOk(raw("SELECT 0")) map { reply =>
      assertEquals(1, reply.getNumber("rows"))
      val res = reply.getArray("results")
      assertEquals(1, res.size())
      assertEquals(0, res.get[JsonArray](0).get[Int](0))
    }
  }

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

  def createAndDropTable(): Unit = asyncTest {
    createTable("some_test") flatMap (_ => dropTable("some_test")) map { reply =>
      assertEquals(0, reply.getNumber("rows"))
    }
  }

  def insertCorrect(): Unit = asyncTableTest("some_test") {
    expectOk(insert("some_test", new JsonArray("""["name","email"]"""), new JsonArray("""[["Test","test@example.com"],["Test2","test2@example.com"]]""")))
  }

  def insertTypeTest(): Unit = typeTestInsert {
    Future.successful()
  }

  def insertMaliciousDataTest(): Unit = asyncTableTest("some_test") {
    // If this SQL injection works, the drop table of asyncTableTest would throw an exception
    expectOk(insert("some_test",
      new JsonArray("""["name","email","is_male","age","money","wedding_date"]"""),
      new JsonArray("""[["Mr. Test","test@example.com",true,15,167.31,"2024-04-01"],
            ["Ms Test2','some@example.com',false,15,167.31,'2024-04-01');DROP TABLE some_test;--","test2@example.com",false,43,167.31,"1997-12-24"]]""")))
  }

  def insertUniqueProblem(): Unit = asyncTableTest("some_test") {
    expectError(insert("some_test", new JsonArray("""["name","email"]"""), new JsonArray("""[["Test","test@example.com"],["Test","test@example.com"]]"""))) map { reply =>
      logger.info("expected error: " + reply.encode())
    }
  }

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
    // FIXME check date conversion
    // assertEquals("2024-04-01", mrTest.get[JsonObject](5))
  }

  private def checkMrsTest(mrsTest: JsonArray) = {
    assertEquals("Mrs. Test", mrsTest.get[String](0))
    assertEquals("test2@example.com", mrsTest.get[String](1))
    assertEquals(false, mrsTest.get[Boolean](2))
    assertEquals(43, mrsTest.get[Integer](3))
    assertEquals(167.31, mrsTest.get[Integer](4))
    // FIXME check date conversion
    // assertEquals("1997-12-24", mrsTest.get[JsonObject](5))
  }

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

  // TODO make possible to query multiple tables in a single select
  //  def selectMultipleTables(): Unit = asyncTest(withTable("some_test1")(withTable("some_test2") {
  //    val mrTest = Json.arr(List("Mr. Test", "test@example.com", 15))
  //    val mrsTest = Json.arr(List("Mrs. Test", "test2@example.com", 14))
  //    val mrExample = Json.arr(List("Mr. Example", "test@example.com", 13))
  //    for {
  //      _ <- ebSend(insert("some_test1", Json.arr(List("name", "email", "age")), mrTest))
  //      _ <- ebSend(insert("some_test1", Json.arr(List("name", "email", "age")), mrsTest))
  //      _ <- ebSend(insert("some_test2", Json.arr(List("name", "email", "age")), mrExample))
  //      reply <- ebSend(select(Json(List("some_test1", "some_test2"), Json.arr(List("some_test2.name", "some_test2.email", "some_test2.age"))))
  //    } yield {
  //      assertEquals("ok", reply.getString("status"))
  //      assertEquals(2, reply.getString("status"))
  //      val results = reply.getArray("results")
  //      assertTrue("should contain Mr. Example", results.get[JsonArray](0).contains(mrExample))
  //    }
  //  }))

  def selectWithCondition(): Unit = fail("not implemented")
  def updateWithoutCondition(): Unit = fail("not implemented")
  def updateWithCondition(): Unit = fail("not implemented")
  def preparedSelect(): Unit = typeTestInsert {
    expectOk(prepared("SELECT email FROM some_test WHERE name=? AND age=?", Json.arr(List("Mr. Test", 15)))) map { reply =>
      val receivedFields = reply.getArray("fields")
      assertEquals(Json.arr(List("email")), receivedFields)
      assertEquals(1, reply.getInteger("rows"))
      assertEquals("test@example.com", reply.getArray("results").get[JsonArray](0).get[String](0))
    }
  }
  def transaction(): Unit = fail("not implemented")
}