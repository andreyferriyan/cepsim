package ca.uwo.eng.sel.cepsim.metric

import ca.uwo.eng.sel.cepsim.metric.History.Entry
import ca.uwo.eng.sel.cepsim.query.{EventProducer, EventConsumer, Query}

/** Calculates the latency metric */
object LatencyMetric extends Metric {

  
  /**
    * Calculates the average latency among all consumers from the query.
    * @param query Query from which the latency is being calculated.
    * @param history history Execution history of this query.
    */
  def calculate(query: Query, history: History[Entry]): Double = {
    val sum = query.consumers.foldLeft(0.0)((acc, consumer) => {
      acc + calculate(query, history, consumer)
    })
    sum / query.consumers.size
  }
  
    /**
    * Calculates the average latency of a single consumer from a query.
    * @param query Query to which the consumer belongs.
    * @param history history Execution history of the query.
    * @param consumer Latency from which the latency is being calculated.
    */
  def calculate(query: Query, history: History[Entry], consumer: EventConsumer): Double = {
    
    val entries = history.processedEntriesFrom(consumer).filter(_.quantity > 0)
    //history.
    val sum = entries.foldLeft(0.0)((acc, entry) =>
      acc + calculate(query, history, consumer, entry.time)
    )
    
    sum / entries.size
  }
  
  /**
    * Calculate the latency of the events consumed by a specific consumer.
    *
    * @param query Query to which the consumer belongs.
    * @param history History that contains the query execution.
    * @param consumer Consumer of which the latency is calculated.
    * @param time Simulation time when the latency is calculated. 
    * @return Latency (in ms).
    */
  def calculate(query: Query, history: History[Entry], consumer: EventConsumer, time: Double): Double = {

    /**
      * Estimate the time in the simulation timeline when the producer started producing the
      * informed number of events.
      * @param producer Producer that is being tracked.
      * @param events Total number of events produced.
      * @param consumerTime The consumer entry time.
      * @return Minimum time (before the cloudlet execution) when the events started to be produced.
      */
    def minimumTime(producer: EventProducer, events: Double, consumerTime: Double): Double = {

      if (events == 0)
        return Double.NegativeInfinity

      // from most recent entries to the older ones, starting from the informed cloudlet
      val previousEntries = history.processedEntriesFrom(producer).reverse.dropWhile(_.time > consumerTime)

      // TODO this is not correct. The right approach is to take the older ones, but they need to be removed from history after being processed

      // discard the oldest entries that are not needed to generate the output
      var totalEvents = events
      val neededEntries = previousEntries.takeWhile((entry) => {
        if (totalEvents <= 0) false
        else { totalEvents -= entry.quantity; true }
      })

      val oldest = neededEntries.last
      var minimumTime = oldest.time

      // the last entry wasn't entirely needed
      if (totalEvents < 0) {
        val entry = history.successor(oldest)
        entry match {
          case Some(successor) => {
            val neededQuantity = oldest.quantity + totalEvents // totalEvents is already negative
            // interpolate
            val neededTime = ((successor.time - oldest.time) * neededQuantity) / oldest.quantity
            minimumTime = successor.time - neededTime
          }
          case None => minimumTime = oldest.time
        }
      }

      minimumTime
    }

    val entry = history.processedEntriesFrom(consumer, time)
    
    entry match {
      case Some(consumerEntry) => {
        val eventsPerProduceResult = this.eventsPerProducer(query, consumer, consumerEntry.quantity)
        val minimumPerProducer = eventsPerProduceResult.map((entry) =>
          (entry._1, minimumTime(entry._1, entry._2, consumerEntry.time))
        )

        consumerEntry.time - minimumPerProducer.values.min
      }
      case None => throw new IllegalArgumentException()
    }
  }
}
