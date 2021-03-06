package mesosphere.marathon
package core.task.tracker.impl

import akka.Done
import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.core.instance.update.{ InstanceChange, InstanceChangeHandler }
import mesosphere.marathon.core.task.tracker.InstanceTrackerUpdateStepProcessor
import mesosphere.marathon.metrics.{ Metrics, ServiceMetric, Timer }

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Takes care of processing [[InstanceChange]]s and will be called after an instance state
  * change has been persisted in the repository
  */
private[tracker] class InstanceTrackerUpdateStepProcessorImpl(
    steps: Seq[InstanceChangeHandler]) extends InstanceTrackerUpdateStepProcessor with StrictLogging {

  private[this] val stepTimers: Map[String, Timer] = steps.map { step =>
    step.name -> Metrics.timer(ServiceMetric, getClass, s"step-${step.name}")
  }(collection.breakOut)

  logger.info(
    "Started TaskTrackerUpdateStepsProcessorImpl with steps:\n{}",
    steps.map(step => s"* ${step.name}").mkString("\n"))

  override def process(change: InstanceChange)(implicit ec: ExecutionContext): Future[Done] = {
    steps.foldLeft(Future.successful(Done)) { (resultSoFar, nextStep) =>
      resultSoFar.flatMap { _ =>
        stepTimers(nextStep.name) {
          logger.debug(s"Executing ${nextStep.name} for [${change.instance.instanceId}]")
          nextStep.process(change).map { _ =>
            logger.debug(s"Done with executing ${nextStep.name} for [${change.instance.instanceId}]")
            Done
          }
        }
      }
    }
  }

}
