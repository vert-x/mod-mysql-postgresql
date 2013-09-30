package io.vertx.asyncsql.database

import org.vertx.scala.platform.Verticle

import com.github.mauricio.async.db.Configuration

class MySqlConnectionHandler(val verticle: Verticle, val config: Configuration, val dbType: String = "mysql") extends ConnectionHandler {
  override protected def escapeField(str: String): String = "`" + str.replace("`", "\\`") + "`"
  override protected def escapeString(str: String): String = "'" + str.replace("'", "''") + "'"

  //  override def transactionStart = "START TRANSACTION;"
  //  override def transactionEnd = ";COMMIT;"
  //  override def statementDelimiter = ";"
}