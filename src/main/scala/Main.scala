package io.estatico.league

import java.io._
import scala.collection.mutable

object Main {

  /** Usage information reported when either '--help' or invalid arguments are
    * supplied.
    */
  def usage(): String = """
    |Usage: ./league [input-file]
    |       ./league -
    |
    |When using '-' for the input-file, league will read from standard input.
  """.stripMargin.strip()

  /** Main entry point for 'league'.
    *
    * See the [[Main.usage]] method for the arguments this program expects.
    *
    * @param args The command line arguments passed to this program
    */
  def main(args: Array[String]): Unit = {
    args match {
      case _ if args.contains("--help") =>
        println(usage())

      case Array("-") =>
        run(System.in)

      case Array(filePath) =>
        run(new FileInputStream(new File(filePath)))

      case Array() =>
        System.err.println("ERROR: Missing file path argument\n")
        System.err.println(usage())
        System.exit(1)

      case array =>
        System.err.println(s"ERROR: Unexpected argument: ${array.head}\n")
        System.err.println(usage())
        System.exit(1)
    }
  }

  /** Parses the given input stream and writes the results to stdout. */
  private def run(inputStream: InputStream): Unit = {
    Input.parseRows(inputStream) match {
      case Left(e) =>
        System.err.println(s"ERROR: ${e.message}")
        System.exit(1)

      case Right(rows) =>
        Output.write(rows, new PrintWriter(System.out))
    }
  }
}

/** Module containing all of our input logic and types. */
object Input {

  /** A row parsed from the input. Represents a single game played between two
    * teams.
    */
  final case class Row(
      team1: Team,
      team2: Team
  )

  /** A single team and its score for the current game row. */
  final case class Team(
      name: String,
      score: Int
  )

  /** Signals that the input failed to parse. */
  final case class ParseFailure(message: String) extends Exception {
    override def getMessage: String = message
  }

  /** For the given input, attempt to parse a sequence of rows.
    * The input stream is first read into a string to improve diagnostics
    * reported from the parser. Without it, our error messages only contain a
    * single int position as opposed to the line and column numbers.
    */
  def parseRows(inputStream: InputStream): Either[ParseFailure, Seq[Row]] = {
    Parser.run(scala.io.Source.fromInputStream(inputStream).mkString)
  }

  /** Internal parser implementation.
    *
    * Under the hood, this uses fastparse. Errors are converted to [[ParseFailure]].
    * This allows us to use fastparse but not leak its internal interface to users,
    * allowing us to swap out the implementation later if need be. This also allows
    * us to tell fastparse how to format the error messages so the user doesn't have
    * to figure out how to get the necessary details.
    */
  private object Parser {

    import fastparse._
    import NoWhitespace._

    /** Runs the parser for the given input. */
    def run(input: String): Either[ParseFailure, Seq[Row]] = {
      parse(input, file(_)) match {
        case e: Parsed.Failure =>
          Left(ParseFailure(e.trace().longAggregateMsg))
        case Parsed.Success(value, _) =>
          Right(value)
      }
    }

    /** The root parser; parses an entire file (including stdin). */
    private def file[X: P]: P[Seq[Row]] = rows ~ fin

    /** Parses one or more rows. */
    private def rows[X: P]: P[Seq[Row]] = row.rep(1, sep = newlines1)

    /** Parses zero or more newlines. */
    private def newlines[X: P]: P[Unit] =
      CharIn("\r\n").rep().opaque("<newlines>")

    /** Parses one or more newlines. */
    private def newlines1[X: P]: P[Unit] =
      CharIn("\r\n").rep(1).opaque("<newlines1>")

    /** The end of the file. Can contain trailing newlines. */
    private def fin[X: P]: P[Unit] = newlines ~ End

    /** A single row. We need to make a special case for the opposing team
      * to ensure that its name does not match the team that comes before it.
      * After all, a team can't play against itself!
      */
    private def row[X: P]: P[Row] =
      team.flatMap(team1 => comma ~/ opposingTeam(team1).map(Row(team1, _)))

    /** A comma, optionally surrounded by spaces. */
    private def comma[X: P]: P[Unit] = (spaces ~ "," ~ spaces).opaque("<comma>")

    /** A single team, including its score. */
    private def team[X: P]: P[Team] = (teamName ~/ score).map(Team.tupled)

    /** A special case of [[team]] which validates that we aren't repeating
      * the previous team name.
      */
    private def opposingTeam[X: P](team1: Team): P[Team] =
      (&(team1.name ~/ score) ~/ Fail).opaque(
        // This error message may read strangely, but this is because fastparse
        // always prefixes its error messages with 'Expected', which this phrasing
        // attempts to account for.
        s"team name other than '${team1.name}'; duplicate team names for row"
      ) | team

    /** Zero or more spaces. */
    private def spaces[X: P]: P[Unit] = " ".rep().opaque("<spaces>")

    /** One or more spaces. */
    private def spaces1[X: P]: P[Unit] = " ".rep(1).opaque("<spaces1>")

    /** A single team name. Must start with an alpha character.
      * May contain numbers, hyphens, or underscores.
      */
    private def teamName[X: P]: P[String] = (
      CharIn("A-Za-z") ~ CharIn("A-Za-z0-9\\-_").rep()
    ).rep(1, sep = " ").!.opaque("<team-name>")

    /** A team's score for a single game. */
    private def score[X: P]: P[Int] = (
      spaces1 ~ CharIn("0-9").rep(1).!.map(_.toInt)
    ).opaque("<score>")
  }
}

/** Module containing all of our output logic and types. */
object Output {

  /** Scans the [[Input.Row]]s, computes the [[Output.Row]]s, and writes the
    * result to the supplied [[PrintWriter]].
    */
  def write(rows: Seq[Input.Row], pw: PrintWriter): Unit = {
    writeOutputRows(fromInputRows(rows), pw)
  }

  /** Consumes the pre-computed [[Output.Row]]s and writes them to the
    * supplied [[PrintWriter]].
    */
  def writeOutputRows(rows: Seq[Output.Row], pw: PrintWriter): Unit = {
    rows.foreach { row =>
      pw.println(row.render())
      pw.flush()
    }
  }

  /** Scans the given [[Input.Row]]s and computes the resulting [[Output.Row]]s.
    */
  def fromInputRows(rows: Seq[Input.Row]): Seq[Row] = {
    // Internal state to keep track of total points awarded.
    val state = new mutable.HashMap[String, Int]
    rows.foreach { row =>
      // In the event of a tie, each team is awarded one point.
      if (row.team1.score == row.team2.score) {
        Seq(row.team1, row.team2).foreach { team =>
          state.updateWith(team.name)(points => Some(points.getOrElse(0) + 1))
        }
      } else {
        val (winner, loser) = {
          if (row.team1.score > row.team2.score) {
            (row.team1, row.team2)
          } else {
            (row.team2, row.team1)
          }
        }
        // The winner is awarded 3 points.
        state.updateWith(winner.name)(points => Some(points.getOrElse(0) + 3))
        // The loser is awarded no points.
        // N.B. It's important to do this update, even though we aren't adding
        // any points, because we have to ensure that the key is in the state
        // map. Otherwise, we'll end up omitting teams which never win.
        state.updateWith(loser.name)(points => Some(points.getOrElse(0)))
      }
    }
    // This is a little clever but also a fairly clean/straightforward
    // way to do this operation, IMHO. We need to sort the teams first
    // by the number of points awarded, descending, and then alphabetically.
    // However, for teams which have the same number of points they are to
    // be given the same rank, just shown in alphabetical order. Also,
    // teams which are ranked after such teams must also account for the
    // "missing" ranks. See the README for an example of such a situation.
    state.iterator.toIndexedSeq
      // First, we sort the state by points descending and then by name.
      .sortBy { case (name, points) => (-points, name.toLowerCase) }
      // We'll use the index as a temporary rank. We'll need to fix this up
      // for any teams which have the same number of points.
      .zipWithIndex
      // Now we account for teams with the same points, grouping them together.
      .groupBy { case ((_, points), _) => points }
      // We don't need Map, we just need the groups.
      .valuesIterator
      // We'll need to convert it to something we can sort.
      .toSeq
      // Unfortunately, we have to sort again by the rank. Because
      // groupBy returns a Map, it messes up our original ordering.
      // Note that head is safe here since groupBy guarantees that
      // we'll be getting back non-empty groups.
      .sortBy { seq => seq.head._2 }
      // Here we flatten our groups into the final Seq, using the
      // first rank as the rank for teams in the group. This works
      // because while we drop the other ranks in the group on the floor,
      // the next groups will have their own first rank which accounts
      // for those that we skipped.
      .flatMap { seq =>
        val rank = seq.head._2 + 1
        seq.iterator.map { case ((name, points), _) =>
          Row(rank, team = name, points)
        }
      }
  }

  /** A single output row. Represents the computed points and rank of a
    * single team for a some set of games played.
    */
  final case class Row(
      rank: Int,
      team: String,
      points: Int
  ) {

    /** Renders the row for output. */
    def render(): String = {
      val unit = if (points == 1) "pt" else "pts"
      s"$rank. $team, $points $unit"
    }
  }
}
