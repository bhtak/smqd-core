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

package com.thing2x.smqd

import java.io.{ByteArrayOutputStream, OutputStreamWriter, PrintWriter}

import spray.json.{DefaultJsonProtocol, JsArray, JsBoolean, JsObject, JsString, JsValue, RootJsonFormat}

// 2018. 7. 7. - Created by Kwon, Yeong Eon

package object plugin extends DefaultJsonProtocol {

  implicit object PluginInstanceOrdering extends Ordering[InstanceDefinition[Plugin]] {
    override def compare(x: InstanceDefinition[Plugin], y: InstanceDefinition[Plugin]): Int = {
      x.name.compare(y.name)
    }
  }

  implicit object PluginInstanceFormat extends RootJsonFormat[InstanceDefinition[Plugin]] {
    override def read(json: JsValue): InstanceDefinition[Plugin] = ???

    override def write(obj: InstanceDefinition[Plugin]): JsValue = {
      val entities = Map(
        "name" -> JsString(obj.name),
        "status" -> JsString(obj.status),
        "auto-start" -> JsBoolean(obj.autoStart),
        "plugin" -> JsString(obj.pluginDef.name),
        "package" -> JsString(obj.pluginDef.packageName))

      obj.instance.status match {
        case InstanceStatus.FAIL if obj.instance.failure.isDefined =>
          val cause = obj.instance.failure.get
          val buffer = new ByteArrayOutputStream(4096)
          val pw = new PrintWriter(new OutputStreamWriter(buffer))
          cause.printStackTrace(pw)
          pw.close()
          val stack = new String(buffer.toString)

          JsObject( entities + ("failure" -> JsObject (
            "message" -> JsString(cause.getMessage),
            "stack" -> JsString(stack)
          )))
        case _ =>
          JsObject( entities )
      }

    }
  }

  implicit object PluginDefinitionFormat extends RootJsonFormat[PluginDefinition] {
    override def read(json: JsValue): PluginDefinition = ???
    override def write(obj: PluginDefinition): JsValue = {
      val ptype =
        if (classOf[Service].isAssignableFrom(obj.clazz)) "Service"
        else if (classOf[BridgeDriver].isAssignableFrom(obj.clazz)) "BridgeDriver"
        else "UnknownPlugin"

      val multi = if (obj.multiInstantiable) {
        "MULTIPLE"
      } else {
        "SINGLE"
      }

      val instances = obj.instances.map(inst => PluginInstanceFormat.write(inst)).toVector

      JsObject (
        "name" -> JsString(obj.name),
        "class" ->  JsString(obj.clazz.getCanonicalName),
        "class-archive" -> JsString(obj.clazz.getProtectionDomain.getCodeSource.getLocation.toString),
        "package" -> JsString(obj.packageName),
        "version" -> JsString(obj.version),
        "type" -> JsString(ptype),
        "instantiability" -> JsString(multi),
        "instances" -> JsArray(instances)
      )
    }
  }

  implicit object PluginRepositoryDefinitionFormat extends RootJsonFormat[RepositoryDefinition] {
    override def read(json: JsValue): RepositoryDefinition = ???
    override def write(obj: RepositoryDefinition): JsValue = {
      if (obj.location.isDefined) {
        JsObject (
          "name" -> JsString(obj.name),
          "provider" -> JsString(obj.provider),
          "installable" -> JsBoolean(obj.installable),
          "installed" -> JsBoolean(obj.installed),
          "description" -> JsString(obj.description),
          "location" -> JsString(obj.location.get.toString)
        )
      }
      else {
        JsObject (
          "name" -> JsString(obj.name),
          "provider" -> JsString(obj.provider),
          "installable" -> JsBoolean(obj.installable),
          "installed" -> JsBoolean(obj.installed),
          "description" -> JsString(obj.description),
          "group" -> JsString(obj.module.get.group),
          "artifact" -> JsString(obj.module.get.artifact),
          "version" -> JsString(obj.module.get.version),
          "reolvers" -> JsArray(obj.module.get.resolvers.toVector.map(JsString(_)))
        )
      }
    }
  }

  implicit object PluginPackageDefinitionFormat extends RootJsonFormat[PackageDefinition] {
    override def read(json: JsValue): PackageDefinition = ???
    override def write(obj: PackageDefinition): JsValue = {
      JsObject (
        "name"-> JsString(obj.name),
        "vendor" -> JsString(obj.vendor),
        "description" -> JsString(obj.description),
        "origin" -> JsString(obj.repository.location.toString),
        "installable" -> JsBoolean(obj.repository.installable),
        "installed" -> JsBoolean(obj.repository.installed),
        "plugins" -> JsArray(obj.plugins.map(p => PluginDefinitionFormat.write(p)).toVector)
      )
    }
  }


  sealed trait ExecResult
  case class ExecSuccess(message: String) extends ExecResult
  case class ExecFailure(message: String, cause: Option[Throwable]) extends ExecResult
  case class ExecInvalidStatus(message: String) extends ExecResult
  case class ExecUnknownCommand(cmd: String) extends ExecResult
}
