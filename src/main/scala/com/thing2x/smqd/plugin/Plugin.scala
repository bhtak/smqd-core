// Copyright 2018 UANGEL
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.thing2x.smqd.plugin

import com.thing2x.smqd.{LifeCycle, Smqd}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

// 2018. 7. 4. - Created by Kwon, Yeong Eon

trait Plugin extends LifeCycle {
  def name: String
  def status: InstanceStatus

  def execStart(): Unit
  def execStop(): Unit

  def failure: Option[Throwable]
}

abstract class AbstractPlugin(val name: String, val smqdInstance: Smqd, val config: Config) extends Plugin with StrictLogging {

  private[plugin] var definition: Option[InstanceDefinition[_]] = None

  private var v_status = InstanceStatus.STOPPED
  private def status_=(newStatus: InstanceStatus): Unit = {
    v_status = newStatus
    val path = definition match {
      case Some(instDef) =>
        instDef.path
      case _ =>
        "$SYS/plugins/events/"+name
    }
    val topic = "$SYS/plugins/events/"+path
    val key = path.replaceAll("[/]", ".")
    smqdInstance.publish(topic, s"""{"$key": {"status":"${v_status.toString}"}}""")
  }

  def status: InstanceStatus = v_status

  private var _cause: Option[Throwable] = None

  def preStarting(): Unit = {
    status = InstanceStatus.STARTING
  }

  def postStarted(): Unit = {
    _cause = None
    status = InstanceStatus.RUNNING
  }

  def preStopping(): Unit = {
    status = InstanceStatus.STOPPING
  }

  def postStopped(): Unit = {
    _cause = None
    status = InstanceStatus.STOPPED
  }

  def execStart(): Unit = {
    try {
      preStarting()
      start()
      postStarted()
    }
    catch {
      case th: Throwable =>
        failure(th)
    }
  }

  def execStop(): Unit = {
    try {
      preStopping()
      stop()
      postStopped()
    }
    catch {
      case th: Throwable =>
        failure(th)
    }
  }

  def failure(ex: Throwable): Unit = {
    logger.warn(s"Failure in plugin instance '$name'", ex)
    _cause = Some(ex)
    status = InstanceStatus.FAIL
    if (shouldExitOnFailure) scala.sys.exit(1)
  }

  final def failure: Option[Throwable] = _cause

  val shouldExitOnFailure: Boolean = false
}