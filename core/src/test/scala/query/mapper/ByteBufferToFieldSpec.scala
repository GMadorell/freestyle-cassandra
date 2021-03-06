/*
 * Copyright 2017 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package freestyle.cassandra
package query.mapper

import java.nio.ByteBuffer

import cats.MonadError
import com.datastax.driver.core.{ProtocolVersion, TypeCodec}
import freestyle.cassandra.query._
import org.scalacheck.Prop._
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, WordSpec}
import org.scalacheck.ScalacheckShapeless._

import scala.util.{Failure, Success, Try}

class ByteBufferToFieldSpec extends WordSpec with Matchers with Checkers {

  case class User(name: String, age: Int)

  implicit val stringTypeCodec: TypeCodec[String] = TypeCodec.varchar()
  implicit val protocolVersion: ProtocolVersion   = ProtocolVersion.V3

  implicit val printer: Printer = identityPrinter

  import GenericFromReader._
  val fromReader: FromReader[User] = implicitly[FromReader[User]]

  "fromReader" should {

    import cats.instances.try_._

    "return the right value when the reader return a success response" in {

      check {
        forAll { user: User =>
          val reader = new ByteBufferReader() {
            override def read[M[_]](name: String)(
                implicit ME: MonadError[M, Throwable]): M[ByteBuffer] =
              name match {
                case "name" => ME.pure(stringTypeCodec.serialize(user.name, protocolVersion))
                case "age"  => ME.pure(TypeCodec.cint().serialize(user.age, protocolVersion))
              }
          }

          fromReader[Try](reader) == Success(user)
        }
      }

    }

    "return the failure when the reader fails returning the ByteBuffer for the 'name' field" in {

      check {
        forAll { user: User =>
          val exception = new RuntimeException("Test Exception")
          val reader = new ByteBufferReader() {
            override def read[M[_]](name: String)(
                implicit ME: MonadError[M, Throwable]): M[ByteBuffer] =
              name match {
                case "name" => ME.raiseError(exception)
                case "age"  => ME.pure(TypeCodec.cint().serialize(user.age, protocolVersion))
              }
          }

          fromReader[Try](reader) == Failure(exception)
        }
      }

    }

    "return the failure when the reader fails returning the ByteBuffer for the 'age' field" in {

      check {
        forAll { user: User =>
          val exception = new RuntimeException("Test Exception")
          val reader = new ByteBufferReader() {
            override def read[M[_]](name: String)(
                implicit ME: MonadError[M, Throwable]): M[ByteBuffer] =
              name match {
                case "name" => ME.pure(stringTypeCodec.serialize(user.name, protocolVersion))
                case "age"  => ME.raiseError(exception)
              }
          }

          fromReader[Try](reader) == Failure(exception)
        }
      }

    }

  }

}
