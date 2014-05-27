package io.vertx.asyncsql.database

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.concurrent.{Promise, Future}
import org.vertx.scala.core.json.{JsonElement, JsonArray, JsonObject, Json}
import org.vertx.scala.core.logging.Logger
import com.github.mauricio.async.db.{Configuration, Connection, QueryResult, RowData}
import com.github.mauricio.async.db.postgresql.exceptions.GenericDatabaseException
import io.vertx.asyncsql.database.pool.AsyncConnectionPool
import org.vertx.scala.mods.ScalaBusMod
import org.vertx.scala.mods.replies._
import org.vertx.scala.core.Vertx
import org.vertx.scala.platform.Container
import io.vertx.asyncsql.Starter
import org.vertx.scala.mods.ScalaBusMod.Receive

trait ConnectionHandler extends ScalaBusMod {
  val verticle: Starter

  def dbType: String

  val config: Configuration
  val maxPoolSize: Int

  lazy val vertx: Vertx = verticle.vertx
  lazy val container: Container = verticle.container
  lazy val logger: Logger = verticle.logger
  lazy val pool = AsyncConnectionPool(verticle, dbType, maxPoolSize, config)

  def transactionStart: String = "BEGIN;"

  def transactionEnd: String = "COMMIT;"

  def transactionRollback: String = "ROLLBACK;"

  def statementDelimiter: String = ";"

  private def timeout = 500L /* FIXME from config file! */

  import org.vertx.scala.core.eventbus._

  private def receiver(withConnectionFn: (Connection => Future[SyncReply]) => Future[SyncReply]): Receive = (msg: Message[JsonObject]) => {
    def sendAsyncWithPool(fn: Connection => Future[QueryResult]) = AsyncReply(sendWithPool(withConnectionFn)(fn))

    {
      case "select" => sendAsyncWithPool(rawCommand(selectCommand(msg.body())))
      case "insert" => sendAsyncWithPool(rawCommand(insertCommand(msg.body())))
      case "prepared" => sendAsyncWithPool(prepared(msg.body()))
      case "raw" => sendAsyncWithPool(rawCommand(msg.body().getString("command")))
    }
  }

  private def regularReceive: Receive = { msg: Message[JsonObject] =>
    receiver(pool.withConnection)(msg).orElse {
      case "start" => startTransaction(msg)
      case "transaction" => transaction(pool.withConnection)(msg.body())
    }
  }

  override def receive: Receive = regularReceive


  //------------------
  //New transaction stuff
  private def mapRepliesToTransactionReceive(c: Connection): BusModReply => BusModReply = {
    case AsyncReply(receiveEndFuture) => AsyncReply(receiveEndFuture.map(mapRepliesToTransactionReceive(c)))
    case Ok(v, None) => Ok(v, Some(ReceiverWithTimeout(inTransactionReceive(c), timeout, () => failTransaction(c))))
    case x => x
  }

  private def inTransactionReceive(c: Connection): Receive = { msg: Message[JsonObject] =>
    def withConnection[T](fn: Connection => Future[T]): Future[T] = fn(c)

    receiver(withConnection)(msg).andThen({
      case x: BusModReply => mapRepliesToTransactionReceive(c)(x)
      case x => x
    }).orElse {
      case "end" => endTransaction(c)
    }
  }

  protected def startTransaction(msg: Message[JsonObject]) = AsyncReply {
    pool.take().flatMap { c =>
      c.sendQuery(transactionStart) map { _ =>
        Ok(Json.obj(), Some(ReceiverWithTimeout(inTransactionReceive(c), timeout, () => failTransaction(c))))
      }
    }
  }

  protected def failTransaction(c: Connection) = {
    logger.info("NO REPLY BACK -> FAIL TRANSACTION!")
    c.sendQuery(transactionRollback).andThen({
      case _ => pool.giveBack(c)
    })
  }

  protected def endTransaction(c: Connection) = {
    logger.info("ending transaction!")
    AsyncReply {
      (for {
        qr <- c.sendQuery(transactionEnd)
        _ <- pool.giveBack(c)
      } yield {
        Ok()
      }) recover {
        case ex => Error("Could not give back connection to pool", "CONNECTION_POOL_EXCEPTION", Json.obj("exception" -> ex))
      }
    }
  }

  //------------------

  def close() = pool.close()

  protected def escapeField(str: String): String = "\"" + str.replace("\"", "\"\"") + "\""

  protected def escapeString(str: String): String = "'" + str.replace("'", "''") + "'"

  protected def escapeValue(v: Any): String = v match {
    case null => "NULL"
    case x: Int => x.toString
    case x: Boolean => x.toString
    case x => escapeString(x.toString)
  }

  protected def selectCommand(json: JsonObject): String = {
    val table = escapeField(json.getString("table"))
    Option(json.getArray("fields")) match {
      case Some(fields) => fields.asScala.toStream.map(elem => escapeField(elem.toString)).mkString("SELECT ", ",", " FROM " + table)
      case None => "SELECT * FROM " + table
    }
  }

  protected def insertCommand(json: JsonObject): String = {
    val table = json.getString("table")
    val fields = json.getArray("fields").asScala
    val lines = json.getArray("values").asScala
    val listOfLines = for {
      line <- lines
    } yield {
      line.asInstanceOf[JsonArray].asScala.toStream.map(v => escapeValue(v)).mkString("(", ",", ")")
    }
    new StringBuilder("INSERT INTO ")
      .append(escapeField(table))
      .append(" ")
      .append(fields.map(f => escapeField(f.toString)).mkString("(", ",", ")"))
      .append(" VALUES ")
      .append(listOfLines.mkString(",")).toString
  }

  sealed trait CommandType {
    val query: Connection => Future[QueryResult]
  }

  case class Raw(stmt: String) extends CommandType {
    val query = rawCommand(stmt)
  }

  case class Prepared(json: JsonObject) extends CommandType {
    val query = prepared(json)
  }

  protected def transaction(withConnection: (Connection => Future[SyncReply]) => Future[SyncReply])(json: JsonObject): AsyncReply = AsyncReply(withConnection({
    c: Connection =>
      logger.info("TRANSACTION-JSON: " + json.encodePrettily())

      Option(json.getArray("statements")) match {
        case Some(statements) => c.inTransaction {
          conn: Connection =>
            val futures = statements.asScala.map {
              case js: JsonObject =>
                js.getString("action") match {
                  case "select" => Raw(selectCommand(js))
                  case "insert" => Raw(insertCommand(js))
                  case "prepared" => Prepared(js)
                  case "raw" => Raw(js.getString("command"))
                }
              case _ => throw new IllegalArgumentException("'statements' needs JsonObjects!")
            }
            val f = futures.foldLeft(Future[Any]()) {
              case (fut, cmd) => fut flatMap (_ => cmd.query(conn))
            }
            f map (_ => Ok(Json.obj()))
        }
        case None => throw new IllegalArgumentException("No 'statements' field in request!")
      }
  }))


  protected def sendWithPool(withConnection: (Connection => Future[SyncReply]) => Future[SyncReply])(fn: Connection => Future[QueryResult]): Future[SyncReply] = withConnection({
    c: Connection =>
      fn(c) map buildResults recover {
        case x: GenericDatabaseException =>
          Error(x.errorMessage.message)
        case x =>
          Error(x.getMessage())
      }
  })

  protected def prepared(json: JsonObject): Connection => Future[QueryResult] = {
    c: Connection =>
      c.sendPreparedStatement(json.getString("statement"), json.getArray("values").toArray())
  }

  protected def rawCommand(command: String): Connection => Future[QueryResult] = {
    c: Connection => c.sendQuery(command)
  }

  private def buildResults(qr: QueryResult): SyncReply = {
    val result = new JsonObject()
    result.putString("message", qr.statusMessage)
    result.putNumber("rows", qr.rowsAffected)

    qr.rows match {
      case Some(resultSet) =>
        val fields = (new JsonArray() /: resultSet.columnNames) {
          (arr, name) =>
            arr.addString(name)
        }

        val rows = (new JsonArray() /: resultSet) {
          (arr, rowData) =>
            arr.add(rowDataToJsonArray(rowData))
        }

        result.putArray("fields", fields)
        result.putArray("results", rows)
      case None =>
    }

    Ok(result)
  }

  private def dataToJson(data: Any): Any = data match {
    case null => null
    case x: Boolean => x
    case x: Number => x
    case x: String => x
    case x: Array[Byte] => x
    case x: JsonElement => x
    case x => x.toString()
  }

  private def rowDataToJsonArray(rowData: RowData): JsonArray = Json.arr(rowData.map(dataToJson).toList: _*)
}