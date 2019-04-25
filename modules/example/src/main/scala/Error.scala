// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.codec.all._

object Error extends IOApp {

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world",
    )

  val query =
    sql"""
      SELECT name, 42::int4
      FROM   country
      WHERE  population > $varchar
      AND    population < $int4
    """.query(varchar ~ int4)

  def prog[F[_]: Bracket[?[_], Throwable]](s: Session[F]): F[ExitCode] =
    s.prepare(query).use(_.unique("42" ~ 1000000)).as(ExitCode.Success).recover {
      case SqlState.UndefinedTable(ex) => ExitCode.Error
    }

  def run(args: List[String]): IO[ExitCode] =
    session.use(prog(_))

}