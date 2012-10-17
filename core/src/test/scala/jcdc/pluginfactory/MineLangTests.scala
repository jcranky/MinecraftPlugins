package jcdc.pluginfactory

import org.scalacheck.Properties
import org.scalacheck.Prop.secure
import MineLang._
import MineLangExamples._

case class Point(x:Int, y:Int){
  def this(x:java.lang.Integer, y:java.lang.Integer, z:Unit) = this(x, y)
  override def toString = s"($x,$y)"
  def invoke1(i:java.lang.Integer) = "6"
  def invoke2(i:Int) = "6"
  def invoke3(i:Int, i2:java.lang.Integer) = "6"
  def invoke4(i:Int, i2:java.lang.Integer*) = "6"
  def invokeHof(f: Int => Int) = f(7)
}

object MineLangTests extends Properties("MinecraftParserTests") {

  val constructorCall1    = "((new jcdc.pluginfactory.Point 5 6))"
  val constructorCall2    = "((new jcdc.pluginfactory.Point 5 6 unit))"
  val instanceCall0 = "((.toString (new jcdc.pluginfactory.Point 5 6)))"
  val instanceCall1 = """((.invoke1 (new jcdc.pluginfactory.Point 5 6) 0))"""
  val instanceCall2 = """((.invoke2 (new jcdc.pluginfactory.Point 5 6) 0))"""
  val instanceCall3 = """((.invoke3 (new jcdc.pluginfactory.Point 5 6) 0 0))"""
  val staticCall1   = """((java.lang.String/valueOf 5))"""
  val staticField1  = """(java.lang.Math/PI)"""

  val lamTest = "(((lam (x) x) 7))"
  val invokeWithHof = """((.invokeHof (new jcdc.pluginfactory.Point 5 6) (lam (x) x)))"""


  // simple java interop tests
  evalTest("constructorCall1", constructorCall1, ObjectValue(Point(5,6)))
  evalTest("constructorCall2", constructorCall2, ObjectValue(Point(5,6)))
  evalTest("instanceCall0",    instanceCall0,    ObjectValue("(5,6)"))
  evalTest("instanceCall1",    instanceCall1,    ObjectValue("6"))
  evalTest("instanceCall2",    instanceCall2,    ObjectValue("6"))
  evalTest("instanceCall3",    instanceCall3,    ObjectValue("6"))
  evalTest("staticCall1",      staticCall1,      ObjectValue("5"))
  evalTest("staticField1",     staticField1,     ObjectValue(Math.PI))
  evalTest("lamTest",       lamTest, ObjectValue(7))
//  evalTest("invokeWithHof", invokeWithHof, ObjectValue(7))

  // more full tests
  val fact = "((defrec fact (n) (if (eq n 0) 1 (* n (fact (- n 1))))) (fact 5))"
  evalTest("houseTest",     house, UnitValue)
  evalTest("fact",          fact,  ObjectValue(120))
  evalTest("expansionTest", expansionTest,
    ObjectValue(Cube(TestServer.world(12,3,12), TestServer.world(-2,3,-2))))

  def evalTest(name:String, code:String, expected:Value) =
    property(name) = secure { run(code, expected) }

  def run(code:String, expected:AnyRef): Boolean = try {
    val actual = MineLang.run(code, TestServer.player)
    println(s"Result: $actual")
    actual == expected
  } catch {
    case e: Throwable =>
      e.printStackTrace
      throw e
  }
}

//  val testScriptFull =
//    """
//     ((begin
//      (goto origin)
//      (corners (loc (+ X 20) (+ Y 50) (+ Z 20)) (loc (- X 20) Y (- Z 20)))
//      (floor stone)
//      (walls brick)
//      7
//     ))
//    """.stripMargin.trim
//
//  val valTest = "((val x 7) x)"
//
//  val defTest = """
//    (
//      (def d (a1 a2) (begin (corners (loc a1 a1 a1) (loc a2 a2 a2)) (set stone) (+ a1 a2)))
//      (val x 7)
//      (let (g 9) (d g 7))
//    )
//  """
//  property("origin")  = secure { parseExpr("origin") }
//  property("XYZ")     = secure { parseExpr("XYZ") }
//  property("(5 6 7)") = secure { parseExpr("(loc 5 6 7)") }
//  property("(5 Y 7)") = secure { parseExpr("(loc 5 Y 7)") }
//  property("((+ 5 10) Y 7)")        = secure { parseExpr("(loc (+ 5 10) Y 7)") }
//  property("((+ X 20) Y (+ Z 20))") = secure { parseExpr("(loc (+ X 20) Y (+ Z 20))") }
//  property("((- X 20) Y (- Z 20))") = secure { parseExpr("(loc (- X 20) Y (- Z 20))") }
//  property("((- 5 10) (+ Y 20) Z)") = secure { parseExpr("(loc (- 5 10) (+ Y 20) Z)") }
//  property("origin")  = secure { parseExpr("(set stone)") }
//  property("((goto origin))") = secure { run("((goto origin))") }
//  property("(corners XYZ origin)") = secure { run("((corners XYZ origin))") }
//  property("(corners XYZ (5 6 7))") = secure { run("((corners XYZ (loc 5 6 7)))") }
//  property("(corners (loc (+ X 20) Y (+ Z 20)) (loc (+ X 20) Y (+ Z 20)))") =
//    secure { run("((corners (loc (+ X 20) Y (+ Z 20)) (loc (+ X 20) Y (+ Z 20))))") }
//  property("(set stone)")   = secure { run("((set stone))") }
//  property("(walls brick)") = secure { run("((walls brick))") }
//  property("testScriptFull") = secure { parseExpr(testScriptFull) }
//  property("testScriptFull") = secure { run(testScriptFull) }
//  property("valTest") = secure { parse(valTest) }
//  property("valTest") = secure { run  (valTest) }
//  property("defTest") = secure { parse(defTest) }
//  property("defTest") = secure { run  (defTest) }
//  def parseExpr(code:String): Boolean =
//    attemptT(p, truthfully(println(s"Parse Tree: ${WorldEditLang.parseExpr(Reader.read(code))}")))
//
//  def parse(code:String): Boolean =
//    attemptT(p, truthfully(println(s"Parse Tree: ${WorldEditLang.parse(code)}")))
