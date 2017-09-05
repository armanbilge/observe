// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem
package arb

import gem.math._
import org.scalacheck._
import org.scalacheck.Arbitrary._

trait ArbRadialVelocity {

  implicit val arbRadialVelocity: Arbitrary[RadialVelocity] =
    Arbitrary(arbitrary[Short].map(n => RadialVelocity(n.toInt)))

}
object ArbRadialVelocity extends ArbRadialVelocity
