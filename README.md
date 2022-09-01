# League

This implementation uses `scala` and `sbt`. It was tested on macOS and Linux
using JDK 16.

## Requirements

* JDK (e.g. [OpenJDK](https://openjdk.org/install/))
* [sbt](https://www.scala-sbt.org/1.x/docs/Setup.html)

## Quick Start

If you are using a macOS or Linux and have `bash` installed on your
system, you can use the included `league` script. This will require basic
Unix tools, like `grep`, `sed`, `stat`, `find`, etc. The script tries
to account for differences between macOS and Linux; YMMV.

The script also attempts to detect source changes and only compile
when necessary. Multiple invocations of the script without changes to
the source should not trigger a rebuild (unless you delete the jar, of course).

```
% ./league --help
Source changes detected, recompiling...
[info] welcome to sbt 1.7.1
...
[info] done compiling
[success] ...

---------------

Usage: ./league [input-file]
       ./league -

When using '-' for the input-file, league will read from standard input.
```

If you prefer, or if you have trouble using the script, you can build and run
the jar by hand -

```
% sbt assembly
[info] welcome to sbt 1.7.1
...
[info] done compiling
[success] ...

% java -jar target/scala-2.13/league-assembly-0.1.0.jar --help
Usage: ./league [input-file]
       ./league -

When using '-' for the input-file, league will read from standard input.
```

As you can see from the usage message above, both the jar and the script
allow you to supply a file or to read from standard input.

## Testing

To run the test suite -

```
% sbt test
```

See the test suite documentation in [LeagueTests](src/test/scala/LeagueTests.scala)
for more info.

## The Rules

In this league, a draw (tie) is worth 1 point and a win is worth 3 points. A loss is worth 0 points.
If two or more teams have the same number of points, they should have the same rank and be
printed in alphabetical order (as in the tie for 3rd place in the example below).

## Example

Here's an example passing the input on stdin -

```
% cat <<HERE | ./league -
Lions 3, Snakes 3
Tarantulas 1, FC Awesome 0
Lions 1, FC Awesome 1
Tarantulas 3, Snakes 1
Lions 4, Grouches 0
HERE
```

And its computed output -

```
1. Tarantulas, 6 pts
2. Lions, 5 pts
3. FC Awesome, 1 pt
3. Snakes, 1 pt
5. Grouches, 0 pts
```
