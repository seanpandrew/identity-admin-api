package util.scientist

import org.scalatest.{FlatSpec, Matchers}

class ScientistTest extends FlatSpec with Matchers {

  object Box {
    var lastResult: Result = null
    val publish: Result => Unit = result => Box.lastResult = result
  }

  case class Test[A](returns: A, expectedResult: Result, experiment: SyncExperiment[A])

  // TODO - add test to ensure sync runs on current thread. As a quick test can
  // use `println(Thread.currentThread().getName()`.

  "Science" should "run and compare experiments" in {
    val tests = List(
      Test(
        List(1, 2, 3),
        MisMatch(List(1, 2, 3), List(2, 3, 4)),
        SyncExperiment(
          name = "a",
          control = () => List(1, 2, 3),
          candidate = () => List(2, 3, 4),
          publish = Box.publish
        )
      )
    )

    tests.foreach { test =>
      val returned = Science.run(test.experiment)
      returned shouldBe test.returns
      Box.lastResult shouldBe test.expectedResult
    }
  }

  it should "ignore tests if requested" in {
    def ignoreEvenFirst(list: List[Int]): Boolean = {
      list.headOption.exists(_ % 2 == 0)
    }

    val tests = List(
      Test(
        List(1, 2, 3),
        MisMatch(List(1, 2, 3), List(2, 3, 4)),
        SyncExperiment[List[Int]](
          name = "a",
          control = () => List(1, 2, 3),
          candidate = () => List(2, 3, 4),
          publish = Box.publish,
          shouldIgnore = ignoreEvenFirst
        )
      ),
      Test(
        List(2, 3),
        Ignore(List(2, 3), List(2, 3, 4)),
        SyncExperiment[List[Int]](
          name = "a",
          control = () => List(2, 3),
          candidate = () => List(2, 3, 4),
          publish = Box.publish,
          shouldIgnore = ignoreEvenFirst
        )
      )
    )

    tests.foreach { test =>
      val returned = Science.run(test.experiment)
      returned shouldBe test.returns
      Box.lastResult shouldBe test.expectedResult
    }
  }
}
