package io.estatico.league

import io.circe.{Codec, Encoder}
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import junit.framework.TestCase

import java.io.{File, InputStream, PrintWriter, StringWriter}
import java.nio.file.Paths
import scala.io.Source

/** Test suite for league.
  *
  * Fixtures are stored in the test resources directory. These fixtures are used
  * to test with various inputs and outputs.
  *
  * There are three different types of fixture files -
  *
  * <ul>
  * <li>`*-input.txt` - The raw, unparsed input file</li>
  * <li>`*-input.json` - The parsed input file, serialized to JSON</li>
  * <li>`*-output.txt` - The computed output</li>
  * </ul>
  *
  * The prefix of these files denotes groups of files which go together for
  * a test case; e.g. `ok-001-*` corresponds to [[testOkInput001]],
  * `error-002-*` corresponds to [[testErrorInput002]], etc.
  *
  * If a given `*-input.txt` file does not exist, the test will fail. However, if
  * the associated `*-input.json` or `*-output.txt` file does not exist, running
  * the test suite will generate them. If any fixtures are generated, the test suite
  * fails. It is the responsibility of the test writer to ensure that any changes
  * or additions to test fixtures are correct. To regenerate an existing fixture,
  * simply delete it and run the test suite again. Because the `testOkInput*` tests
  * can generate two separate files, you will need to run the test suite twice to generate
  * each file. While this may be cumbersome, it's kind of the point. We want to be sure
  * that any fixture files we add we do so intentionally, so take care when using `git add`!
  *
  * Why do we test both the parsed inputs `*-input.json` and computed outputs `*-output.txt`?
  * Why not just worry about the outputs? The idea here is about testing at various levels.
  * It's important to catch bugs early. A bug could manifest and output tests could pass only
  * coincidentally, but an underlying issue could exist where we are parsing the input.
  *
  * N.B. We are doing some trickery when generating fixtures. See the doc on
  * [[generateResourceAndThrow]] for more info.
  */
class LeagueTests extends TestCase {

  // Tests for inputs which should parse successfully.
  def testOkInput001(): Unit = goTestOkInput()
  def testOkInput002(): Unit = goTestOkInput()
  def testOkInput003(): Unit = goTestOkInput()
  def testOkInput004(): Unit = goTestOkInput()
  def testOkInput005(): Unit = goTestOkInput()

  // Tests for inputs which should produce parse errors.
  def testErrorInput001(): Unit = goTestErrorInput()
  def testErrorInput002(): Unit = goTestErrorInput()
  def testErrorInput003(): Unit = goTestErrorInput()
  def testErrorInput004(): Unit = goTestErrorInput()
  def testErrorInput005(): Unit = goTestErrorInput()
  def testErrorInput006(): Unit = goTestErrorInput()

  /** Tests for inputs which should parse successfully. */
  private def goTestOkInput(): Unit = {
    val id = getResourceId("testOkInput")
    val inputTxtFileName = s"ok-$id-input.txt"
    val inputJsonFileName = s"ok-$id-input.json"
    val outputTxtFileName = s"ok-$id-output.txt"
    val inputTxtStream = requireResourceStream(inputTxtFileName)
    val inputRows = rightOrThrow(Input.parseRows(inputTxtStream))

    val inputJsonStream = getResourceStreamOrGenerateAndThrow(
      inputJsonFileName,
      _.write(Encoder[Seq[Input.Row]].apply(inputRows).toString)
    )

    val expectedInputRows = rightOrThrow(
      decode[Seq[Input.Row]](Source.fromInputStream(inputJsonStream).mkString)
    )
    TestCase.assertEquals(expectedInputRows, inputRows)

    val outputTxtInputStream = getResourceStreamOrGenerateAndThrow(
      outputTxtFileName,
      Output.write(inputRows, _)
    )

    TestCase.assertEquals(
      Source.fromInputStream(outputTxtInputStream).mkString,
      inspectPrintWriter(Output.write(inputRows, _))
    )
  }

  /** Tests for inputs which should produce parse errors. */
  private def goTestErrorInput(): Unit = {
    val id = getResourceId("testErrorInput")
    val inputTxtFileName = s"error-$id-input.txt"
    val outputTxtFileName = s"error-$id-output.txt"
    val inputTxtStream = requireResourceStream(inputTxtFileName)
    val Input.ParseFailure(message) = expectLeft(
      Input.parseRows(inputTxtStream)
    )
    val outputTxtStream = getResourceStreamOrGenerateAndThrow(
      outputTxtFileName,
      _.write(message)
    )
    TestCase.assertEquals(
      Source.fromInputStream(outputTxtStream).mkString,
      message
    )
  }

  /** Use this for loading resources required by the test which cannot be generated;
    * e.g. `*-input.txt` files.
    */
  private def requireResourceStream(name: String): InputStream = {
    getClass.getClassLoader.getResourceAsStream(name) match {
      case null => throw new RuntimeException(s"Resource does not exist: $name")
      case is   => is
    }
  }

  /** Use this for loading resources which can be generated. The `generate`
    * function supplied will be used to write the new fixture if it does not exist.
    * When generation occurs, this function throws an exception to ensure we don't
    * allow the test suite to pass.
    */
  private def getResourceStreamOrGenerateAndThrow(
      name: String,
      generate: PrintWriter => Unit
  ): InputStream = {
    getClass.getClassLoader.getResourceAsStream(name) match {
      case null => generateResourceAndThrow(name, generate)
      case is   => is
    }
  }

  /** Helper which allows us to write with a [[PrintWriter]] and then
    * inspect what it wrote in the returned String.
    */
  private def inspectPrintWriter(block: PrintWriter => Unit): String = {
    val sw = new StringWriter
    block(new PrintWriter(sw))
    sw.toString
  }

  /** Helper which throws an [[AssertionError]] if the supplied [[Either]]
    * is a [[Right]].
    */
  private def expectLeft[E, A](x: Either[E, A]): E = {
    x match {
      case Left(e) => e
      case _       => throw new AssertionError(s"Expected Left, got: $x")
    }
  }

  /** Helper which returns the right value if the [[Either]] is [[Right]];
    *  otherwise, throws the [[Left]] value. Requires that the type variable
    *  on the left is [[Throwable]].
    */
  private def rightOrThrow[E <: Throwable, A](x: Either[E, A]): A = {
    x match {
      case Left(e)  => throw e
      case Right(a) => a
    }
  }

  /** Parse the resource id from the test method name; e.g. [[testErrorInput002]]
    * has a resource id of `002`. The return value is [[String]] as opposed to
    * [[Int]] to ensure we get the padded zeroes.
    *
    * N.B. Care must be taken that this function is only called in the right places.
    * In order to inspect the method name, we are peeking at the thread's stack
    * trace, so if things get moved around this won't work right (although it
    * should throw a RuntimeException if its assumptions are broken, which should
    * prevent any undefined behavior from happening).
    */
  private def getResourceId(prefix: String): String = {
    val methodName = Thread.currentThread.getStackTrace()(3).getMethodName
    val id = methodName.stripPrefix(prefix)
    if (methodName == id) {
      throw new RuntimeException(
        s"Expected method prefix '$prefix' but got '$methodName'"
      )
    }
    id
  }

  /** When reading the fixtures, the test suite will use the normal resource
    * input stream obtained from the class loader. However, these are actually
    * read at runtime not from the source tree but from the compilation target
    * directory, usually `./target/scala-2.13/test-classes/`. This means that when
    * generating sources we can't just write to that same path. Instead, we do some
    * trickery in this method to obtain the actual source location and write it
    * there.
    */
  private def generateResourceAndThrow(
      filePath: String,
      block: PrintWriter => Unit
  ): Nothing = {
    val file = new File(
      Paths
        .get(
          System.getProperty("user.dir"),
          "src",
          "test",
          "resources",
          filePath
        )
        .toUri
    )
    val pw = new PrintWriter(file)
    block(pw)
    pw.flush()
    throw new RuntimeException(
      s"Resource file did not exist, generated it: ${file.getCanonicalPath}"
    )
  }

  // Derived JSON codecs so we can serialize the parsed input rows to fixtures.
  implicit val inputTeamJsonCodec: Codec[Input.Team] = deriveCodec
  implicit val inputRowJsonCodec: Codec[Input.Row] = deriveCodec
}
