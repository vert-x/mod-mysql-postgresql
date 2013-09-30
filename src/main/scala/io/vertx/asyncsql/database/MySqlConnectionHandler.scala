package io.vertx.asyncsql.database

import io.vertx.helpers.Verticle
import com.github.mauricio.async.db.Configuration
import org.vertx.scala.core.logging.Logger

class MySqlConnectionHandler(val verticle: Verticle, val config: Configuration, val dbType: String = "mysql") extends ConnectionHandler {
  val logger = new Logger(verticle.container.logger)
  override protected def escapeField(str: String): String = "`" + str.replace("`", "\\`") + "`"
  override protected def escapeString(str: String): String = "'" + str.replace("'", "''") + "'"

//  override def transactionStart = "START TRANSACTION;"
//  override def transactionEnd = ";COMMIT;"
//  override def statementDelimiter = ";"
}