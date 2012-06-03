package Praktikum4
import scala.collection.immutable.HashMap
import cip.base.CodeGen

object Memory {

  import Declarations._
  // level -> symbolTabelle
  object SymbolTables {
    private val symbolTables = Array(new HashMap[String, Descriptor])
    def +(s: String, d: Descriptor) = {
      traceN("new entry: " + s + " -> " + d)
      symbolTables(Level.value) = symbolTables(Level.value) + Tuple(s, d)
      trace("currentAddress: " + currentAddress + " + " + d.size + " = " + (currentAddress+d.size) + "")
      incCurrAddr(d.size)
    }
    def apply(s: String) = symbolTables(Level.value).get(s)
    def apply() = symbolTables(Level.value)
    override def toString = {
      var s = "\nSymbolTable:\n";
      for (item <- symbolTables; i <- 0 until symbolTables.length) {
        s += "  Level " + i + "\n"
        item.foreach {
          case (x, y) => s += "    " + x + "->" + y
        }
      }
      s
    }
    private def incCurrAddr(i: Int) = {
      if (i > 0)
        currAddr += i
    }
    private var currAddr = 0
    def currentAddress = currAddr
    
    def traceN(x:Any) = if (OberonDebug.symbolTable) print("Memory.SymbolTable: " + x)
    def trace(x:Any) = if (OberonDebug.symbolTable) println("Memory.SymbolTable: " + x)
  }
  def mainProgramLength = cip.TreeGenerator.lengthDataSegmentMainProgram
  def setMainProgramLength(i: Int) = cip.TreeGenerator.lengthDataSegmentMainProgram = i

  object Level {
    def inc = CodeGen.level += 1
    def dec = CodeGen.level -= 1
    def reset = CodeGen.level = 0
    def value = CodeGen.level
    override def toString = "Level(" + value + ")"
  }

  object Declarations {
    case object IntConst
    case class IntConst(intval: Int) extends Descriptor {
      def print(n: Int) = ->("IntConst(" + intval + ")", n)
    }
    trait VariableDescriptor extends Descriptor {
      val address: Int
      val _type: Type
      val isParameter: Boolean
      override val size = _type.size
      override def toInt = address
    }
    case object Variable
    case class Variable(address: Int, _type: Type) extends Descriptor with VariableDescriptor {
      def print(n: Int) = ->("Variable(address=" + address + ")", n) + _type.print(n + 1)
      val isParameter = false
    }

    case object ParameterVariable
    case class ParameterVariable(address: Int, _type: Type) extends Descriptor with VariableDescriptor {
      def print(n: Int) = ->("ParameterVariable(address=" + address + ")", n) + _type.print(n + 1)
      val isParameter = true
    }

    case object Procedcure
    case class Procedcure(name: String, startaddress: Int, lengthparblock: Int,
      framesize: Int, params: ParameterVariable) extends Descriptor {
      def print(n: Int) = ->("Procedcure(name=" + name + "startaddress=" + startaddress + "lengthparblock=" + lengthparblock + "framesize=" + framesize + ")", n) + params.print(n + 1)
      override def toInt = startaddress
    }

    trait Type extends Descriptor
    case object ArrayType
    case class ArrayType(numberOfElems: Int, basetype: Type) extends Type {
      def print(n: Int) = ->("ArrayType(numberOfElems=" + numberOfElems + ")", n) + basetype.print(n + 1)
      override def toInt = numberOfElems
      override val size: Int = numberOfElems*basetype.size
    }

    case object RecordType
    case class RecordType(symbolTable: Map[String, Descriptor]) extends Type {
      def print(n: Int) = ->("RecordType", n) + ->(symbolTable.toString, n + 1)
      override def toInt = symbolTable.size
    }

    trait SimpleType extends Type {
      val name: String
      override val size = 1
    }
    case object IntegerType extends SimpleType {
      val name = "IntegerType"
      def print(n: Int) = ->(name, n)
    }
    case object StringType extends SimpleType {
      val name = "StringType"
      def print(n: Int) = ->(name, n)
    }
    case object BooleanType extends SimpleType {
      val name = "BooleanType"
      def print(n: Int) = ->(name, n)
    }

    object NilDescriptor extends Descriptor {
      def print(n: Int) = "NilDescriptor"
      override val size = -1
    }

    trait Descriptor {
      val size = 0;
      val level = Level.value

      def print(n: Int): String
      def toInt: Int = -1
      override def toString = print(0)
    }

    def ->(value: String, n: Int) = "	" * n + value + "\n"
    def trace(s: Any) = println("Memory:Declarations " + s)

  }
  def trace(s: Any) = if (OberonDebug.memory) println("Memory: " + s)
}