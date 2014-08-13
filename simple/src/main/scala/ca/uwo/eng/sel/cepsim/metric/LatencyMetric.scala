package ca.uwo.eng.sel.cepsim.metric

import ca.uwo.eng.sel.cepsim.query.{EventProducer, EventConsumer, Query}

/** Calculates the latency metric */
object LatencyMetric extends Metric {

  /**
    * Calculate the latency of the events consumed by a specific consumer on a cloudlet.
    *
    * @param query Query to which the consumer belongs.
    * @param history History of the query execution.
    * @param cloudlet Cloudlet of which the latency is calculated.
    * @param consumer Consumer of which the latency is calculated.
    * @return Latency (in ms).
    */
  def calculate(query: Query, history: History, cloudlet: String, consumer: EventConsumer): Double = {

    /**
      * Estimate the time in the simulation timeline when the producer started producing the
      * informed number of events.
      * @param producer Producer that is being tracked.
      * @param events Total number of events produced.
      * @return Minimum time (before the cloudlet execution) when the events started to be produced.
      */
    def minimumTime(producer: EventProducer, events: Double): Double = {

      if (events == 0)
        return Double.NegativeInfinity

      // from most recent entries to the older ones, starting from the informed cloudlet
      val previousEntries = history.from(producer).reverse.dropWhile(_.cloudlet != cloudlet)

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

    val entry = history.from(cloudlet, consumer)
    entry match {
      case Some(consumerEntry) => {
        val eventsPerProduceResult = this.eventsPerProducer(query, consumer, consumerEntry.quantity)
        val minimumPerProducer = eventsPerProduceResult.map((entry) => (entry._1, minimumTime(entry._1, entry._2)))

        consumerEntry.time - minimumPerProducer.values.min
      }
      case None => throw new IllegalArgumentException()
    }
  }
}