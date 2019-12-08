// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.Void
import cats.effect.IO
import cats.implicits._
import skunk.codec.all._
import skunk.exception._
import skunk.implicits._

case object ExtendedQueryErrorTest extends SkunkTest {

  sessionTest("syntax error") { s =>
    for {
      e <- s.prepare(sql"foo".query(int4)).use(ps => ps.stream(Void, 64).compile.drain).assertFailsWith[PostgresErrorException]
      _ <- assert("message",  e.message  === "Syntax error at or near \"foo\".")
      _ <- assert("hint",     e.hint     === None)
      _ <- assert("position", e.position === Some(1))
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("invalid input syntax") { s =>
    for {
      e <- s.prepare(sql"select 1 < 'foo'".query(int4)).use(ps => ps.stream(Void, 64).compile.drain).assertFailsWith[PostgresErrorException]
      _ <- assert("message",  e.message  === "Invalid input syntax for integer: \"foo\".")
      _ <- assert("hint",     e.hint     === None)
      _ <- assert("position", e.position === Some(12))
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("unknown column, no hint") { s =>
    for {
      e <- s.prepare(sql"select abc".query(int4)).use(ps => ps.stream(Void, 64).compile.drain).assertFailsWith[PostgresErrorException]
      _ <- assert("message",  e.message  === "Column \"abc\" does not exist.")
      _ <- assert("hint",     e.hint     === None)
      _ <- assert("position", e.position === Some(8))
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("unknown column, hint") { s =>
    for {
      e <- s.prepare(sql"select popsulation from country".query(int4)).use(ps => ps.stream(Void, 64).compile.drain).assertFailsWith[PostgresErrorException]
      _ <- assert("message",  e.message  === "Column \"popsulation\" does not exist.")
      _ <- assert("hint",     e.hint     === Some("Perhaps you meant to reference the column \"country.population\"."))
      _ <- assert("position", e.position === Some(8))
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("column alignment, unmapped column") { s =>
    for {
      e <- s.prepare(sql"select name, population from country".query(varchar)).use(ps => ps.stream(Void, 64).compile.drain).assertFailsWith[ColumnAlignmentException]
      _ <- assert("message",  e.message  === "Asserted and actual column types differ.")
      // TODO: check that the reported alignment is varchar ~ int4 vs varchar
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("column alignment, type mismatch, one row") { s =>
    for {
      e <- s.prepare(sql"select 1".query(varchar ~ varchar)).use(ps => ps.stream(Void, 64).compile.drain).assertFailsWith[ColumnAlignmentException]
      _ <- assert("message",  e.message  === "Asserted and actual column types differ.")
      // TODO: check that the reported alignment is varchar ~ int4 vs varchar ~ varchar
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("column alignment, type mismatch, many row2") { s =>
    for {
      e <- s.prepare(sql"select name, population from country".query(varchar ~ varchar)).use(ps => ps.stream(Void, 64).compile.drain).assertFailsWith[ColumnAlignmentException]
      _ <- assert("message",  e.message  === "Asserted and actual column types differ.")
      // TODO: check that the reported alignment is varchar ~ int4 vs varchar ~ varchar
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("decode error, one row") { s =>
    for {
      e <- s.prepare(sql"select null::varchar".query(varchar)).use(ps => ps.stream(Void, 64).compile.drain).assertFailsWith[DecodeException[IO, _, _]]
      _ <- assert("message",  e.message  === "Decoding error.")
      _ <- assertEqual("detail", e.detail, Some("This query's decoder was unable to decode a row of data."))
      // TODO: check the specific error
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("decode error, many rows") { s =>
    for {
      e <- s.prepare(sql"select null::varchar from country".query(varchar)).use(ps => ps.stream(Void, 64).compile.drain).assertFailsWith[DecodeException[IO, _, _]]
      _ <- assert("message",  e.message  === "Decoding error.")
      _ <- assertEqual("detail", e.detail, Some("This query's decoder was unable to decode a row of data."))
      // TODO: check the specific error
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("not a query") { s =>
    for {
      e <- s.prepare(sql"set seed = 0.123".query(int4)).use(ps => ps.stream(Void, 64).compile.drain).assertFailsWith[NoDataException]
      _ <- assert("message",  e.message  === "Statement does not return data.")
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("not a query, with warning") { s =>
    for {
      e <- s.prepare(sql"commit".query(int4)).use(ps => ps.stream(Void, 64).compile.drain).assertFailsWith[NoDataException]
      _ <- assertEqual("message", e.message, "Statement does not return data.")
      _ <- s.assertHealthy
    } yield "ok"
  }

}
