package ca.uwo.eng.sel.cepsim.integration

import ca.uwo.eng.sel.cepsim.QueryCloudlet
import ca.uwo.eng.sel.cepsim.gen.UniformGenerator
import ca.uwo.eng.sel.cepsim.metric.History.Processed
import ca.uwo.eng.sel.cepsim.placement.Placement
import ca.uwo.eng.sel.cepsim.query.{EventConsumer, EventProducer, Operator, Query}
import ca.uwo.eng.sel.cepsim.sched.{DefaultOpScheduleStrategy}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._

/**
 * Created by virso on 2014-08-13.
 */
@RunWith(classOf[JUnitRunner])
class BoundedQueryCloudletTest extends FlatSpec
  with Matchers {

  trait Fixture {
    val gen = UniformGenerator(100000, 10.milliseconds)

    val prod1 = EventProducer("p1", 1000, gen, true)
    val f1 = Operator("f1", 2500, 1000)
    val f2 = Operator("f2", 8000, 1000)
    val cons1 = EventConsumer("c1", 1000, 1000)

    var query1 = Query("q1", Set(prod1, f1, f2, cons1), Set((prod1, f1, 1.0), (f1, f2, 0.5), (f2, cons1, 0.1)))

    //val vm = Vm("vm1", 1000) // 1 billion instructions per second

  }

  "A QueryCloudlet" should "respect the buffer bounds" in new Fixture {
    val placement = Placement(query1, 1)
    val schedStrategy = DefaultOpScheduleStrategy.uniform()
    var cloudlet = QueryCloudlet("c1", placement, schedStrategy) //, 0.0)
    cloudlet.init(0.0)

    // 10 millions instructions ~ 10 milliseconds
    // 1000 events will be generated per simulation tick
    // each operator will use 2.5 millions instructions
    val h = cloudlet.run(10000000, 0.0, 1000)


    prod1.outputQueues(f1) should be(0)
    f1.outputQueues(f2)    should be(0)

    f2.inputQueues(f1)     should be(187.50 +- 0.01) // f2 will process 312.5 events only (from the total of 500)
    f2.outputQueues(cons1) should be(0.0)
    cons1.inputQueues(f2)  should be(0.00 +- 0.01)    // 0.25 will be accumulated into cons1
    cons1.outputQueue      should be(31)

    // check if history is being correctly logged
    h should have size (4)
    h.toList should contain theSameElementsInOrderAs (
      List(Processed("c1", 0.0, prod1, 1000), Processed("c1", 2.5, f1, 1000),
      Processed("c1", 5.0, f2, 312.5), Processed("c1", 7.5, cons1, 31)))

    // --------------------------------------------------------------------------
    // SECOND ITERATION
    // --------------------------------------------------------------------------

    //var cloudlet2 = QueryCloudlet("c2", placement, schedStrategy, 10.0)
    cloudlet.run(10000000, 10.0, 1000)

    prod1.outputQueues(f1) should be(0)

    f1.inputQueues(prod1)  should be(0) // f2 buffer can only store more 687.5 events, so 1375 can be processed
    f1.outputQueues(f2)    should be(0)

    f2.inputQueues(f1)     should be(375.0 +- 0.01)
    f2.outputQueues(cons1) should be(0)
    cons1.inputQueues(f2)  should be(0.00  +- 0.01)  // 0.25 will be accumulated into cons1
    cons1.outputQueue      should be(62)

    // --------------------------------------------------------------------------
    // THIRD ITERATION
    // --------------------------------------------------------------------------
    //var cloudlet3 = QueryCloudlet("c3", placement, schedStrategy, 20.0)
    cloudlet.run(10000000, 20.0, 1000)

    prod1.outputQueues(f1) should be(0)

    f1.inputQueues(prod1)  should be(0) // f2 buffer can only store more 625 events, so 1250 can be processed
    f1.outputQueues(f2)    should be(0)

    f2.inputQueues(f1)     should be(562.50 +- 0.01)
    f2.outputQueues(cons1) should be(0)
    cons1.inputQueues(f2)  should be(0.00  +- 0.01) // 0.75 will be accumulated into cons1
    cons1.outputQueue      should be(93)

    // --------------------------------------------------------------------------
    // FOURTH ITERATION
    // --------------------------------------------------------------------------
    //var cloudlet4 = QueryCloudlet("c4", placement, schedStrategy, 30.0)
    cloudlet.run(10000000, 30.0, 1000)

    prod1.outputQueues(f1) should be(0)

    f1.inputQueues(prod1)  should be(125) // f2 buffer can only store more 437.5 events, so 875 can be processed
    f1.outputQueues(f2)    should be(0)

    f2.inputQueues(f1)     should be(687.50)
    f2.outputQueues(cons1) should be(0)
    cons1.inputQueues(f2)  should be(0.00  +- 0.01)

    // this iteration produces 32 events because of the remainder accumulated from
    // the previous iterations
    cons1.outputQueue      should be(125)

    // --------------------------------------------------------------------------
    // FIFTH ITERATION
    // --------------------------------------------------------------------------
    //var cloudlet5 = QueryCloudlet("c5", placement, schedStrategy, 30.0)
    cloudlet.run(10000000, 40.0, 1000)

    prod1.outputQueues(f1) should be(0) // f1 buffer can only store more 875 events
    prod1.inputQueue       should be(0)
    gen.nonProcessed       should be(125)

    f1.inputQueues(prod1)  should be(375) // f2 buffer can only store more 312.5 events, so 625 can be processed
    f1.outputQueues(f2)    should be(0)

    f2.inputQueues(f1)     should be(687.50)
    f2.outputQueues(cons1) should be(0)
    cons1.inputQueues(f2)  should be(0.00 +- 0.01)
    cons1.outputQueue      should be(156)

  }


}
