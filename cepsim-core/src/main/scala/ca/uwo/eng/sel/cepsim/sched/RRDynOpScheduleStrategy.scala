package ca.uwo.eng.sel.cepsim.sched

import ca.uwo.eng.sel.cepsim.placement.Placement
import ca.uwo.eng.sel.cepsim.query.{EventProducer, InputVertex, Vertex}
import ca.uwo.eng.sel.cepsim.sched.alloc.AllocationStrategy

import scala.concurrent.duration._


/** RRDynOpScheduleStrategy companion object. */
object RRDynOpScheduleStrategy {
  def apply(allocStrategy: AllocationStrategy, iterations: Int) = new RRDynOpScheduleStrategy(allocStrategy, iterations)

  def apply(allocStrategy: AllocationStrategy, iterationLength: Double, capacity: Double) = new RRDynOpScheduleStrategy(allocStrategy, iterationLength, capacity)
  def apply(allocStrategy: AllocationStrategy, iterationLength: Duration, capacity: Double) = new RRDynOpScheduleStrategy(allocStrategy, iterationLength, capacity)
}


/**
  * This scheduling strategy is similar to the Round-Robin strategy. Nevertheless, this strategy skips
  * vertices which do not have events on their input queues.
  *
 * @param iterations Number of passes over the vertices.
  */
class RRDynOpScheduleStrategy private (allocStrategy: AllocationStrategy, iterations: Int, iterationLength: Duration, capacity: Double) extends OpScheduleStrategy {

  def this(allocStrategy: AllocationStrategy, iterations: Int) = this(allocStrategy, iterations, null, 0.0)
  def this(allocStrategy: AllocationStrategy, iterationLength: Duration, capacity: Double) = this(allocStrategy, -1, iterationLength, capacity)

  // used for java code
  def this(allocStrategy: AllocationStrategy, iterationLength: Double, capacity: Double) = this(allocStrategy, iterationLength millisecond, capacity)

  /**
   * Allocates instructions to vertices from a placement.
   *
   * @param instructions Number of instructions to be allocated.
   * @param placement Placement object encapsulating the vertices.
   * @return A list of pairs, in which the first element is a vertices and the second the number of
   *         instructions allocated to that vertex.
   */
  override def allocate(instructions: Double, placement: Placement): Iterator[(Vertex, Double)] = {
    val instrPerOperator = allocStrategy.instructionsPerOperator(instructions, placement)

    var iterationsNo = iterations
    if (iterationsNo == -1)
      // number of instructions per millisecond
      iterationsNo = Math.ceil(instructions / (iterationLength.toUnit(MILLISECONDS) * (capacity * 1000))).toInt

    new RRDynamicScheduleIterator(placement, iterationsNo, instrPerOperator)
  }

  /**
   * Iterator returned by the strategy.
   *
   * @param placement Placement object encapsulating the vertices.
   * @param iterations Number of passes over the vertices.
   * @param instrPerOperator Map containing the total number of instructions allocated to each vertex.
   */
  class RRDynamicScheduleIterator(placement: Placement, iterations: Int, instrPerOperator: Map[Vertex, Double])
    extends Iterator[(Vertex, Double)] {


    /** List with all vertices in the iteration order determined by the placement. */
    private val vertices: List[Vertex] = placement.iterator.toList

    /** Current index in the vertices list. */
    private var currentIndex = -1

    /** Count the number of passes. */
    var count = 0


    private def inputEvents(v: Vertex): Double =
      v match {
        case in: InputVertex => in.totalInputEvents
        case _ => v.asInstanceOf[EventProducer].inputQueue
      }

    private def nextIndex(): (Int, Int) = {

      var nextIndex = currentIndex
      var nextCount = count

      nextIndex += 1
      if (nextIndex == vertices.length) {

        // passed over all iterations
        if (nextCount == iterations - 1) {
          nextIndex = -1

          // one more iteration
        } else {
          nextCount += 1
          nextIndex = 0

          // position the index on the first vertex which has something to process
          while ((nextIndex < vertices.length) && (inputEvents(vertices(nextIndex)) == 0))
            nextIndex += 1

          if (nextIndex == vertices.length) nextIndex = -1
        }
      }

      (nextIndex, nextCount)

//      do {
//        nextIndex += 1
//
//        if (nextIndex == vertices.length) {
//
//          if (nextCount == iterations - 1) {
//            (-1, iterations)
//          }
//
//          nextCount += 1
//          nextIndex = 0
//        }
//
//      } while ((inputEvents(vertices(nextIndex)) == 0) && (nextCount > 0) && (nextCount < iterations))
//      if (nextCount == iterations) nextIndex = -1
//
//
//      (nextIndex, nextCount)
    }

    override def hasNext: Boolean = (nextIndex()._1 != -1)


    override def next(): (Vertex, Double) = {


      val t = nextIndex()
      currentIndex = t._1
      count = t._2

      val v = vertices(currentIndex)
      val instructions = Math.floor(instrPerOperator(v) / iterations)

      // if it is the last iteration, then return all remaining instructions
      (v, if (count == iterations - 1) instrPerOperator(v) - (count * instructions) else instructions)
    }

  }

}