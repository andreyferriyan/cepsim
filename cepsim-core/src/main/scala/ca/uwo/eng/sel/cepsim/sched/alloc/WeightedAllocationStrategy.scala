package ca.uwo.eng.sel.cepsim.sched.alloc

import ca.uwo.eng.sel.cepsim.placement.Placement
import ca.uwo.eng.sel.cepsim.query.Vertex

import java.util.{Map => JavaMap}

import scala.collection.JavaConversions

object WeightedAllocationStrategy {
  def apply() = new WeightedAllocationStrategy(Map.empty.withDefaultValue(1.0))
  def apply(weights: Map[Vertex, Double]) = new WeightedAllocationStrategy(weights)
  def apply(weights: JavaMap[Vertex, Double]) = new WeightedAllocationStrategy(
    JavaConversions.mapAsScalaMap(weights).toMap)
}


/**
 * Created by virso on 15-02-04.
 */
class WeightedAllocationStrategy(weights: Map[Vertex, Double]) extends AllocationStrategy {
  /**
   * Calculate the number os instructions to be allocated for each operator.
   *
   * @param instructions Number of instructions to be allocated.
   * @param placement Placement object encapsulating the vertices.
   * @return A map of vertices to the number of instructions allocated to that vertex.
   */
  override def instructionsPerOperator(instructions: Double, placement: Placement): Map[Vertex, Double] = {

    // allocate the same amount of instructions for each query
    val perQuery = instructions / placement.queries.size

    // calculate the amount of instructions per operator
    var instrPerOperator = Map.empty[Vertex, Double] withDefaultValue (0.0)
    placement.queries.foreach { (q) =>
      val total = placement.vertices(q).foldLeft(0.0) {
        (sum, v) => sum + weights(v) * v.ipe
      }
      placement.vertices(q).foreach { (v) =>
        val vertexInstr = (v.ipe * weights(v) / total) * perQuery
        instrPerOperator = instrPerOperator updated(v, instrPerOperator(v) + vertexInstr)
      }
    }
    instrPerOperator
  }
}