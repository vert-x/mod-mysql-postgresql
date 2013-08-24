package io.vertx.asyncsql.test.mysql

import org.junit.Test
import org.vertx.scala.core.json._
import io.vertx.asyncsql.test.{ BaseSqlTests, SqlTestVerticle }
import org.vertx.testtools.VertxAssert

class MySqlTest extends SqlTestVerticle with BaseSqlTests {

  val address = "campudus.asyncdb"
  val config = Json.obj("address" -> address, "connection" -> "MySQL")

  override def getConfig = config

  // FIXME test stuff
  @Test
  def something(): Unit = VertxAssert.testComplete()

  //  @Test
  //  override def selectFiltered(): Unit = super.selectFiltered()
  //  @Test
  //  override def selectEverything(): Unit = super.selectEverything()
  //  @Test
  //  override def insertUniqueProblem(): Unit = super.insertUniqueProblem()
  //  @Test
  //  override def insertMaliciousDataTest(): Unit = super.insertMaliciousDataTest()
  //  @Test
  //  override def insertTypeTest(): Unit = super.insertTypeTest()
  //  @Test
  //  override def insertCorrect(): Unit = super.insertCorrect()
  //  @Test
  //  override def createAndDropTable(): Unit = super.createAndDropTable()
  //  @Test
  //  override def multipleFields(): Unit = super.multipleFields()
  //  @Test
  //  override def simpleConnection(): Unit = super.simpleConnection()

}