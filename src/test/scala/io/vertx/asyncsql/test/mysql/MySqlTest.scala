package io.vertx.asyncsql.test.mysql

import org.junit.Test
import org.vertx.scala.core.json._
import io.vertx.asyncsql.test.{ BaseSqlTests, SqlTestVerticle }
import org.vertx.testtools.VertxAssert

class MySqlTest extends SqlTestVerticle with BaseSqlTests {

  val address = "campudus.asyncdb"
  val config = Json.obj("address" -> address, "connection" -> "MySQL")

  override def getConfig = config

  override def createTableStatement(tableName: String) = """
CREATE TABLE IF NOT EXISTS """ + tableName + """ (
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

  @Test
  override def simpleConnection(): Unit = super.simpleConnection()
  @Test
  override def multipleFields(): Unit = super.multipleFields()
  @Test
  override def createAndDropTable(): Unit = super.createAndDropTable()
  @Test
  override def insertCorrect(): Unit = super.insertCorrect()
  @Test
  override def insertTypeTest(): Unit = super.insertTypeTest()
  @Test
  override def insertUniqueProblem(): Unit = super.insertUniqueProblem()
  @Test
  override def insertMaliciousDataTest(): Unit = super.insertMaliciousDataTest()
  @Test
  override def selectWithoutFields(): Unit = super.selectWithoutFields()
  @Test
  override def selectEverything(): Unit = super.selectEverything()
  @Test
  override def selectFiltered(): Unit = super.selectFiltered()
  @Test
  override def selectWithCondition(): Unit = super.selectWithCondition()
  @Test
  override def updateWithoutCondition(): Unit = super.updateWithoutCondition()
  @Test
  override def updateWithCondition(): Unit = super.updateWithCondition()
  @Test
  override def preparedSelect(): Unit = super.preparedSelect()
  @Test
  override def transaction(): Unit = super.transaction()

}