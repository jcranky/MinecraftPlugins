package jcdc.pluginfactory

object YMLGenerator {
  def main(args: Array[String]): Unit = new MineLangPlugin().writeYML("Josh Cough", "0.2.1")
}