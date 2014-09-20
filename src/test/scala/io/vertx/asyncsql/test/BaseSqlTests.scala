package io.vertx.asyncsql.test

import scala.concurrent.{Future, Promise}
import org.vertx.scala.core.json.{JsonObject, Json, JsonArray}
import org.vertx.testtools.VertxAssert._
import org.junit.Test
import scala.util.{Success, Failure, Try}
import org.vertx.scala.core.eventbus.Message
import org.vertx.scala.core.FunctionConverters._

trait BaseSqlTests {
  this: SqlTestVerticle =>

  private val timeout: Int = 15000

  protected def isMysql: Boolean = false

  protected def failedTest: PartialFunction[Throwable, Unit] = {
    case ex: Throwable =>
      logger.warn("failed in test", ex)
      fail("test failed. see warning above")
  }

  private def sendWithTimeout(json: JsonObject): Future[(Message[JsonObject], JsonObject)] = {
    val p = Promise[(Message[JsonObject], JsonObject)]()
    vertx.eventBus.sendWithTimeout(address, json, timeout, {
      case Success(reply) => p.success(reply, reply.body())
      case Failure(ex) => p.failure(ex)
    }: Try[Message[JsonObject]] => Unit)
    p.future
  }

  private def replyWithTimeout(msg: Message[JsonObject], json: JsonObject): Future[(Message[JsonObject], JsonObject)] = {
    val p = Promise[(Message[JsonObject], JsonObject)]()
    msg.replyWithTimeout(json, timeout, {
      case Success(reply) => p.success(reply, reply.body())
      case Failure(ex) => p.failure(ex)
    }: Try[Message[JsonObject]] => Unit)
    p.future
  }

  private def checkOkay(json: JsonObject)(msg: (Message[JsonObject], JsonObject)): (Message[JsonObject], JsonObject) = {
    assertEquals(s"should get 'ok' back when sending ${json.encode()}, but got ${msg._2.encode()}",
      "ok", msg._2.getString("status"))
    (msg._1, msg._2)
  }

  private def checkError(json: JsonObject)(msg: (Message[JsonObject], JsonObject)): (Message[JsonObject], JsonObject) = {
    assertEquals(s"should get an 'error' back when sending ${json.encode()}, but got ${msg._2.encode()}",
      "error", msg._2.getString("status"))
    (msg._1, msg._2)
  }

  protected def sendOk(json: JsonObject): Future[(Message[JsonObject], JsonObject)] =
    sendWithTimeout(json) map checkOkay(json)

  protected def sendFail(json: JsonObject): Future[(Message[JsonObject], JsonObject)] =
    sendWithTimeout(json) map checkError(json)

  private def replyOk(msg: Message[JsonObject], json: JsonObject): Future[(Message[JsonObject], JsonObject)] =
    replyWithTimeout(msg, json) map checkOkay(json)

  private def replyFail(msg: Message[JsonObject], json: JsonObject): Future[(Message[JsonObject], JsonObject)] =
    replyWithTimeout(msg, json) map checkError(json)

  private def setupTableTest(): Future[_] = for {
    (msg, reply) <- sendOk(raw(createTableStatement("some_test")))
  } yield {
    assertEquals(0, reply.getInteger("rows"))
  }

  private def setupTypeTest(): Future[_] = for {
    _ <- setupTableTest()
    (msg, reply) <- sendOk(insert("some_test",
      Json.fromArrayString( """["name","email","is_male","age","money","wedding_date"]"""),
      Json.fromArrayString(
        """[["Mr. Test","test@example.com",true,15,167.31,"2024-04-01"],
          | ["Ms Test2","test2@example.com",false,43,167.31,"1997-12-24"]]""".stripMargin)))
  } yield ()

  private def checkSameFields(arr1: JsonArray, arr2: JsonArray) = {
    import scala.collection.JavaConversions._
    arr1.foreach(elem => assertTrue(arr2.contains(elem)))
  }

  private def checkMrTest(mrTest: JsonArray) = {
    assertEquals("Mr. Test", mrTest.get[String](0))
    assertEquals("test@example.com", mrTest.get[String](1))
    assertTrue(mrTest.get[Any](2) match {
      case b: Boolean => b
      case i: Number => i.intValue() == 1
      case x => false
    })
    assertEquals(15, mrTest.get[Number](3).intValue())
    assertEquals(167.31, mrTest.get[Number](4).doubleValue(), 0.0001)
    // FIXME check date conversion
    // assertEquals("2024-04-01", mrTest.get[JsonObject](5))
  }

  @Test
  def simpleConnection(): Unit = (for {
    (msg, reply) <- sendOk(raw("SELECT 0"))
  } yield {
    val res = reply.getArray("results")
    assertEquals(1, res.size())
    assertEquals(0, res.get[JsonArray](0).get[Number](0).intValue())
    testComplete()
  }) recover failedTest

  @Test
  def poolSize(): Unit = asyncTest {
    val n = 10
    val futures = for {
      i <- 1 to n
    } yield {
      expectOk(raw("SELECT " + i)) map {
        reply =>
          val res = reply.getArray("results")
          assertEquals(1, res.size())
          val result = res.get[JsonArray](0).get[Number](0).intValue()
          assertEquals(i, result)
          result
      }
    }

    val fs = Future.sequence(futures) map (_.sum)
    fs map (assertEquals((n * (n + 1)) / 2, _))
  }

  @Test
  def multipleFields(): Unit = (for {
    (msg, reply) <- sendOk(raw("SELECT 1 a, 0 b"))
  } yield {
    val res = reply.getArray("results")
    assertEquals(1, res.size())
    val firstElem = res.get[JsonArray](0)
    assertEquals(1, firstElem.get[Number](0).intValue())
    assertEquals(0, firstElem.get[Number](1).intValue())
    testComplete()
  }) recover failedTest

  @Test
  def multipleFieldsOrder(): Unit =
    (for {
      _ <- setupTypeTest()
      (msg, reply) <- sendOk(raw("SELECT is_male, age, email, money, name FROM some_test WHERE is_male = true"))
    } yield {
      import collection.JavaConverters._
      val receivedFields = reply.getArray("fields")
      val results = reply.getArray("results").get[JsonArray](0)

      assertEquals(1, reply.getInteger("rows"))

      val columnNamesList = receivedFields.asScala.toList

      assertEquals("Mr. Test", results.get(columnNamesList.indexOf("name")))
      assertEquals("test@example.com", results.get(columnNamesList.indexOf("email")))
      assertEquals(15, results.get[Int](columnNamesList.indexOf("age")))
      assertTrue(results.get[Any](columnNamesList.indexOf("is_male")) match {
        case b: Boolean => b
        case i: Number => i.intValue() == 1
        case x => false
      })
      assertEquals(167.31, results.get[Number](columnNamesList.indexOf("money")).doubleValue(), 0.01)
      testComplete()
    }) recover failedTest

  @Test
  def createAndDropTable(): Unit = (for {
    (msg, dropIfExistsReply) <- sendOk(raw("DROP TABLE IF EXISTS some_test;"))
    (msg, createReply) <- sendOk(raw("CREATE TABLE some_test (id SERIAL, name VARCHAR(255));"))
    (msg, insertReply) <- sendOk(raw("INSERT INTO some_test (name) VALUES ('tester');"))
    (msg, selectReply) <- sendOk(raw("SELECT name FROM some_test"))
    (msg, dropReply) <- {
      assertEquals("tester", try {
        selectReply.getArray("results").get[JsonArray](0).get[String](0)
      } catch {
        case ex: Throwable => fail(s"Should be able to get a result before drop, but got ${selectReply.encode()}")
      })
      sendOk(raw("DROP TABLE some_test;"))
    }
    (msg, selectReply) <- sendFail(raw("SELECT name FROM some_test"))
  } yield {
    val error = selectReply.getString("message")
    assertTrue(s"Not the right error message $error",
      error.contains("some_test") && (error.contains("doesn't exist") || error.contains("does not exist")))
    testComplete()
  }) recover failedTest

  @Test
  def insertCorrectWithMissingValues(): Unit = (for {
    _ <- setupTableTest()
    _ <- sendOk(insert("some_test",
      Json.fromArrayString( """["name","email"]"""),
      Json.fromArrayString( """[["Test","test@example.com"],
                              | ["Test2","test2@example.com"]]""".stripMargin)))
  } yield testComplete()) recover failedTest

  @Test
  def insertNullValues(): Unit = (for {
    _ <- setupTableTest()
    _ <- sendOk(insert("some_test",
      Json.fromArrayString( """["name","email"]"""),
      Json.fromArrayString( """[[null,"test@example.com"],
                              | [null,"test2@example.com"]]""".stripMargin)))
  } yield testComplete()) recover failedTest

  @Test
  def insertTypeTest(): Unit = (for {
    _ <- setupTypeTest()
  } yield testComplete()) recover failedTest

  @Test
  def insertMaliciousDataTest(): Unit = (for {
    _ <- setupTableTest()
    (msg, insertReply) <- sendOk(insert("some_test",
      Json.fromArrayString( """["name","email","is_male","age","money","wedding_date"]"""),
      Json.fromArrayString(
        """[["Mr. Test","test@example.com",true,15,167.31,"2024-04-01"],
          | ["Ms Test2','some@example.com',false,15,167.31,'2024-04-01');DROP TABLE some_test;--","test2@example.com",false,43,167.31,"1997-12-24"]]""".stripMargin)))
    (msg, selectReply) <- sendOk(raw("SELECT * FROM some_test"))
  } yield {
    assertEquals(2, selectReply.getArray("results").size())
    testComplete()
  }) recover failedTest

  @Test
  def insertUniqueProblem(): Unit = (for {
    _ <- setupTableTest()
    (msg, reply) <- sendFail(insert("some_test",
      Json.fromArrayString( """["name","email"]"""),
      Json.fromArrayString(
        """[["Test","test@example.com"],
          | ["Test","test@example.com"]]""".stripMargin)))
  } yield testComplete()) recover failedTest

  @Test
  def selectWithoutFields(): Unit = (for {
    _ <- setupTypeTest()
    (msg, reply) <- sendOk(select("some_test"))
  } yield {
    val receivedFields = reply.getArray("fields")
    logger.info("received: " + receivedFields.encode())

    def assertFieldName(field: String) = {
      assertTrue("fields should contain '" + field + "'", receivedFields.contains(field))
    }
    assertFieldName("id")
    assertFieldName("name")
    assertFieldName("email")
    assertFieldName("is_male")
    assertFieldName("age")
    assertFieldName("money")
    assertFieldName("wedding_date")
    val moneyField = receivedFields.toArray.indexOf("money")

    val mrTest = reply.getArray("results").get[JsonArray](0)
    assertTrue(mrTest.contains("Mr. Test"))
    assertTrue(mrTest.contains("test@example.com"))
    assertTrue(mrTest.contains(true) || mrTest.contains(1))
    assertTrue(mrTest.contains(15))
    assertEquals(167.31, mrTest.get[Number](moneyField).doubleValue(), 0.0001)
    testComplete()
  }) recover failedTest

  @Test
  def selectEverything(): Unit = {
    val fieldsArray = Json.arr("name", "email", "is_male", "age", "money", "wedding_date")
    (for {
      _ <- setupTypeTest()
      (msg, reply) <- sendOk(select("some_test", fieldsArray))
    } yield {
      val receivedFields = reply.getArray("fields")
      checkSameFields(fieldsArray, receivedFields)
      val results = reply.getArray("results")
      val mrTest = results.get[JsonArray](0)
      checkMrTest(mrTest)
      testComplete()
    }) recover failedTest
  }

  @Test
  def selectFiltered(): Unit = {
    val fieldsArray = Json.arr("name", "email")

    (for {
      _ <- setupTypeTest()
      (msg, reply) <- sendOk(select("some_test", fieldsArray))
    } yield {
      val receivedFields = reply.getArray("fields")
      assertEquals(s"arrays ${fieldsArray.encode()} and ${receivedFields.encode()} should match",
        fieldsArray, receivedFields)
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
      testComplete()
    }) recover failedTest
  }

  @Test
  def preparedSelect(): Unit = (for {
    _ <- setupTypeTest()
    (msg, reply) <- sendOk(prepared("SELECT email FROM some_test WHERE name=? AND age=?", Json.arr("Mr. Test", 15)))
  } yield {
    val receivedFields = reply.getArray("fields")
    assertEquals(Json.arr("email"), receivedFields)
    assertEquals(1, reply.getInteger("rows"))
    assertEquals("test@example.com", reply.getArray("results").get[JsonArray](0).get[String](0))
    testComplete()
  }) recover failedTest

  @Test
  def simpleTransaction(): Unit = (for {
    _ <- setupTypeTest()
    (msg, transactionReply) <- sendOk(
      transaction(
        insert("some_test", Json.arr("name", "email", "is_male", "age", "money"),
          Json.arr(Json.arr("Mr. Test jr.", "test3@example.com", true, 5, 2))),
        raw("UPDATE some_test SET age=6 WHERE name = 'Mr. Test jr.'")))
    (msg, reply) <- sendOk(raw("SELECT SUM(age) FROM some_test WHERE is_male = true"))
  } yield {
    val results = reply.getArray("results")
    assertEquals(1, results.size())
    assertEquals(21, results.get[JsonArray](0).get[Number](0).intValue())
    testComplete()
  }) recover failedTest

  @Test
  def transactionWithPreparedStatement(): Unit = (for {
    _ <- setupTypeTest()
    (msg, transactionReply) <- sendOk(
      transaction(
        insert("some_test", Json.arr("name", "email", "is_male", "age", "money"),
          Json.arr(Json.arr("Mr. Test jr.", "test3@example.com", true, 5, 2))),
        prepared("UPDATE some_test SET age=? WHERE name=?", Json.arr(6, "Mr. Test jr."))))
    (msg, reply) <- sendOk(raw("SELECT SUM(age) FROM some_test WHERE is_male = true"))
  } yield {
    val results = reply.getArray("results")
    assertEquals(1, results.size())
    assertEquals(21, results.get[JsonArray](0).get[Number](0).intValue())
    testComplete()
  }) recover failedTest

  @Test
  def startAndEndTransaction(): Unit = (for {
    (msg, beginReply) <- sendOk(Json.obj("action" -> "begin"))
    (msg, selectReply) <- replyOk(msg, raw("SELECT 15"))
    (msg, commitReply) <- {
      val arr = selectReply.getArray("results")
      assertEquals("ok", selectReply.getString("status"))
      assertEquals(1, arr.size())
      assertEquals(15, arr.get[JsonArray](0).get[Number](0).longValue())

      replyOk(msg, Json.obj("action" -> "commit"))
    }
  } yield testComplete()) recover failedTest


  @Test
  def updateInTransaction(): Unit = (for {
    _ <- setupTypeTest()
    (msg, beginReply) <- sendOk(Json.obj("action" -> "begin"))
    (msg, updateReply) <- replyOk(msg, raw("UPDATE some_test set email = 'updated@test.com' WHERE name = 'Mr. Test'"))
    (msg, commitReply) <- replyOk(msg, Json.obj("action" -> "commit"))
    (msg, checkReply) <- sendOk(raw("SELECT email FROM some_test WHERE name = 'Mr. Test'"))
  } yield {
    val results = checkReply.getArray("results")
    val mrTest = results.get[JsonArray](0)
    assertEquals("updated@test.com", mrTest.get[String](0))
    logger.info("all tests completed")
    testComplete()
  }) recover failedTest

  @Test
  def violateForeignKey(): Unit = (for {
    (msg, beginResult) <- sendOk(Json.obj("action" -> "begin"))
    (msg, _) <- replyOk(msg, raw("DROP TABLE IF EXISTS test_two;"))
    (msg, _) <- replyOk(msg, raw("DROP TABLE IF EXISTS test_one;"))
    (msg, _) <- replyOk(msg, raw( """CREATE TABLE test_one (
                                    |  id SERIAL,
                                    |  name VARCHAR(255),
                                    |  PRIMARY KEY (id)
                                    |);""".stripMargin))
    (msg, _) <- replyOk(msg, raw(
      s"""CREATE TABLE test_two (
         |  id SERIAL,
         |  name VARCHAR(255),
         |  one_id BIGINT ${if (isMysql) "UNSIGNED" else ""} NOT NULL,
         |  PRIMARY KEY (id)
         |);""".stripMargin))
    (msg, _) <- replyOk(msg, raw(
      """ALTER TABLE test_two ADD CONSTRAINT test_two_one_id_fk
        |FOREIGN KEY (one_id)
        |REFERENCES test_one (id);""".stripMargin))
    (msg, _) <- replyOk(msg, raw("INSERT INTO test_one (name) VALUES ('first'),('second');"))
    (msg, setupResult) <- replyOk(msg, raw("INSERT INTO test_two (name, one_id) VALUES ('twoone', 1);"))
    (msg, insertViolatedResult) <- replyFail(msg, raw("INSERT INTO test_two (name, one_id) VALUES ('twothree', 3);"))
    (msg, rollbackResult) <- replyOk(msg, raw("ROLLBACK;"))
  } yield testComplete()) recover failedTest

  @Test
  def wrongQueryInTransaction(): Unit = (for {
    _ <- setupTypeTest()
    (msg, beginReply) <- sendOk(Json.obj("action" -> "begin"))
    (msg, updateReply) <- replyWithTimeout(msg, raw("this is a bad raw query for sql"))
  } yield {
    assertEquals("error", updateReply.getString("status"))
    testComplete()
  }) recover failedTest

  @Test
  def rollBackTransaction(): Unit = {
    val fieldsArray = Json.arr("name", "email", "is_male", "age", "money", "wedding_date")
    (for {
      _ <- setupTypeTest()
      (msg, beginReply) <- sendOk(Json.obj("action" -> "begin"))
      (msg, reply) <- replyOk(msg, raw("UPDATE some_test set email = 'shouldRollback@test.com' WHERE name = 'Mr. Test'"))
      (msg, checkUpdateReply) <- replyOk(msg, raw("SELECT email FROM some_test WHERE name = 'Mr. Test'"))
      (msg, endReply) <- {
        val results = checkUpdateReply.getArray("results")
        val mrTest = results.get[JsonArray](0)
        assertEquals("shouldRollback@test.com", mrTest.get[String](0))

        logger.info("Update done, now do rollback")
        replyOk(msg, Json.obj("action" -> "rollback"))
      }
      (msg, checkReply) <- sendOk(select("some_test", fieldsArray))
    } yield {
      val results = checkReply.getArray("results")
      val mrTest = results.get[JsonArray](0)
      checkMrTest(mrTest)
      logger.info("rolled back nicely")
      testComplete()
    }) recover failedTest
  }

  @Test
  def dateTest(): Unit = (for {
    _ <- setupTableTest()
    (msg, insertReply) <- sendOk(raw("INSERT INTO some_test (name, wedding_date) VALUES ('tester', '2015-04-04');"))
    (msg, reply) <- sendOk(prepared("SELECT wedding_date FROM some_test WHERE name=?", Json.arr("tester")))
  } yield {
    val receivedFields = reply.getArray("fields")
    assertEquals(Json.arr("wedding_date"), receivedFields)
    assertEquals("2015-04-04", reply.getArray("results").get[JsonArray](0).get[String](0))
    testComplete()
  }) recover failedTest

  @Test
  def timestampTest(): Unit = (for {
    (m, r) <- sendOk(raw("DROP TABLE IF EXISTS date_test"))
    (msg, r2) <- sendOk(raw(createDateTable("timestamp")))
    (msg, insertReply) <- sendOk(raw("INSERT INTO date_test (test_date) VALUES ('2015-04-04T10:04:00.000');"))
    (msg, reply) <- sendOk(raw("SELECT test_date FROM date_test"))
  } yield {
    val receivedFields = reply.getArray("fields")
    assertEquals(Json.arr("test_date"), receivedFields)
    logger.info("date is: " + reply.getArray("results").get[JsonArray](0).get[String](0))
    assertEquals("2015-04-04T10:04:00.000", reply.getArray("results").get[JsonArray](0).get[String](0))
    testComplete()
  }) recover failedTest

}

