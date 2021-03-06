package Praktikum4
import Praktikum4.Memory.Declarations.Descriptor
import scala.collection.immutable.TreeMap
import scala.annotation.tailrec
import scala.collection.immutable.TreeMap

@tailrec
object Tree {

  case object Nil extends Tree[Nothing] with Expression with Statement with Declarations with FormalParameters with ConstIdent with Type with Field with FieldList with ProcedureDeclaration with Ident with FPSection {
    override def toString = "."
    override def print(n: Int): String = ""
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = Memory.Declarations.NilDescriptor
    override def isDefined = false
  }

  case object Integer
  case class Integer(int: Symbol) extends Tree[Integer] with Expression with IndexExpression {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("Integer")
      OberonInstructions.IntegerVal(int.value.get.toString.toInt)
      Memory.Declarations.IntegerType
    }

    override def num = int.value.get.toString.toInt
    override def print(n: Int) = ->("Integer(" + int + ")", n)
  }

  //Selector         = {�.� ident | �[� Expression �]�}.
  trait Ident extends Tree[Ident] with Expression with Type with Declarations with ConstIdent {
    val identIdent: Symbol = Symbol("", -1, -1)
    val optionalIdent: Expression = Nil
    override def num = Memory.SymbolTables(identIdent.value.get.toString).get.toInt
  }

  case object Ident
  case class IdentNode(override val identIdent: Symbol, override val optionalIdent: Expression = Nil) extends Tree[Ident] with Ident {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap): Memory.Declarations.Descriptor = {
      trace("IdentNode(" + identIdent + ")")
      val eOp = {
        val t = symbolTable.get(identIdent.value.get.toString)
        if (t.isDefined) {
          t
        } else
          Memory.SymbolTables(identIdent.value.get.toString)
      }
      if (eOp.isEmpty)
        Memory.Declarations.NilDescriptor
      else {
        val e = eOp.get
        e match {
          case x: Memory.Declarations.Type => x
          case x: Memory.Declarations.Variable => {

            val addr = x.address
            val t = x._type
            OberonInstructions.IntegerVal(addr)
            if (e.level > 0) {
              if (e.level == Memory.Level.value) {
                OberonInstructions.GetFP
                OberonInstructions.AdditionInstruction
              } else {
                OberonInstructions.IntegerVal(e.level)
                OberonInstructions.GetSL
                OberonInstructions.AdditionInstruction
              }
            }
            if (x.isVariable) {
              OberonInstructions.ContInstruction(1)
            }
            x
          }
          case x: Memory.Declarations.IntConst => {
            OberonInstructions.IntegerVal(x.intval)
            x
          }
        }
      }
    }

    override def print(n: Int) = ->("IdentNode(" + identIdent + ")", n) + optionalIdent.print(n + 1)
  }

  case object Content
  case class Content(address: Expression) extends Ident {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("Content")
      def compileIdent(i: Ident): Memory.Declarations.Descriptor = {
        val t = address.compile(symbolTable)
        val e = {
          val e = Memory.SymbolTables.applyOnly(i.identIdent.value.get.toString)
          if (e.isEmpty) {
            val e = symbolTable.get(i.identIdent.value.get.toString)
            if (e.isEmpty){
              Memory.SymbolTables(i.identIdent.value.get.toString)
            }
            else
              e
          } 
          else
            e
        }
        if (e.isDefined) {
          e.get match {
            case e: ConstIdent => t
            case c: Memory.Declarations.IntConst => {
              c
            }
            case _ => {
              t match {
                case v: Memory.Declarations.Variable => {
                  OberonInstructions.ContInstruction(v._type.size)
                }
                case a: Memory.Declarations.ArrayType => {
                  address match {
                    case i: Ident => OberonInstructions.ContInstruction(a.size)
                    case x => OberonInstructions.ContInstruction(1)
                  }
                }
                case a: Memory.Declarations.RecordType => {
                  OberonInstructions.IntegerVal(0)
                  OberonInstructions.ContInstruction(t.size)
                }
                case x => {
                  OberonInstructions.ContInstruction(t.size)
                }
              }
              t
            }
          }
        } else {
          error("Content: Variable in Symboltable", (i.identIdent.value.get.toString, e))
          Memory.Declarations.NilDescriptor
        }
      }
      address match {
        case r: RecordReference => {
          val t = r.compile()
          OberonInstructions.ContInstruction(1)
          t
        }
        case arrref: ArrayReference => {
          arrref.ident match {
            case i: Ident => compileIdent(i)
            case x => {
              error("Content: Ident", x)
              Memory.Declarations.NilDescriptor
            }
          }
        }
        case i: Ident => {
          compileIdent(i)
        }
        case _ => {
          val t = Memory.Declarations.IntegerType
          OberonInstructions.ContInstruction(t.size)
          t
        }
      }
    }

    override def print(n: Int) = ->("Content", n) + address.print(n + 1)
  }

  // string 		 = ...
  case object Str
  case class Str(string: Symbol) extends Expression {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("Str")
      OberonInstructions.StringVal(string.value.get.toString)
      Memory.Declarations.StringType
    }

    override def print(n: Int) = ->("Str(" + string + ")", n)
  }

  //Read             = READ [Prompt].
  case object Read
  case class Read(prompt: Expression) extends Expression {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("Read")
      Memory.Declarations.IntegerType
    }
    override def print(n: Int) = ->("Read", n) + prompt.print(n + 1)
  }

  //Prompt           = string.
  case object Prompt
  case class Prompt(stringNode: Str) extends Expression {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("Prompt")
      Memory.Declarations.IntegerType
    }
  }

  //Factor           = ident Selector | integer | string |
  //                    Read |
  //                   �(� Expression �)�.
  //Term             = Factor {(�*� | �/�) Factor}.
  //SimpleExpression = [�-�] Term
  //                   {(�+� | �-�) Term}.
  //Expression       = SimpleExpression
  //                   [(�=� | �#� | �<� |
  //                     �<=� | �>� | �>=�)
  //                    SimpleExpression].
  trait Expression extends Statement {
    val left: Expression = Nil
    val right: Expression = Nil
    def value: String = "Expression"

    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("Expression -> " + value)
      val l = left.compile(symbolTable)
      val r = right.compile(symbolTable)
      compileOperator
      r
    }

    override def print(n: Int) = ->(value, n) + left.print(n + 1) + right.print(n + 1)

    def compileOperator = ()
    def *(expr: Expression): Expression = new *(this, expr)
    def /(expr: Expression): Expression = new /(this, expr)
    def +(expr: Expression): Expression = new +(this, expr)
    def -(expr: Expression): Expression = new -(this, expr)
    def :=(expr: Expression): Expression = new :=(this, expr)
    def <(expr: Expression): Expression = new <(this, expr)
    def <=(expr: Expression): Expression = new <=(this, expr)
    def >(expr: Expression): Expression = new >(this, expr)
    def >=(expr: Expression): Expression = new >=(this, expr)
  }

  case class *(override val left: Expression, override val right: Expression) extends Expression {
    override def compileOperator = OberonInstructions.MultiplicationInstruction
    override def value = "*"
  }
  case class /(override val left: Expression, override val right: Expression) extends Expression {
    override def compileOperator = OberonInstructions.DivisionInstruction
    override def value = "/"
  }
  case class +(override val left: Expression, override val right: Expression) extends Expression {
    override def compileOperator = OberonInstructions.AdditionInstruction
    override def value = "+"
  }
  case class -(override val left: Expression, override val right: Expression) extends Expression {
    override def compileOperator = OberonInstructions.SubtractionInstruction
    override def value = "-"
  }
  case class :=(override val left: Expression, override val right: Expression) extends Expression {
    override def compileOperator = OberonInstructions.EqualsInstruction
    override def value = "="
  }
  case class <(override val left: Expression, override val right: Expression) extends Expression {
    override def compileOperator = OberonInstructions.LessThanInstruction
    override def value = "<"
  }
  case class <=(override val left: Expression, override val right: Expression) extends Expression {
    override def compileOperator = OberonInstructions.LessEqualThanInstruction
    override def value = "<="
  }
  case class >(override val left: Expression, override val right: Expression) extends Expression {
    override def compileOperator = OberonInstructions.GreaterThanInstruction
    override def value = ">"
  }
  case class >=(override val left: Expression, override val right: Expression) extends Expression {
    override def compileOperator = OberonInstructions.GreaterEqualThanInstruction
    override def value = ">="
  }

  case class Neg(override val left: Expression) extends Expression {
    override def compileOperator = {
      trace("Negative")
      OberonInstructions.IntegerVal(-1)
      OberonInstructions.MultiplicationInstruction
    }
    override def value = "Negative"
  }

  //ActualParameters  = Expression {�,� Expression}.
  case object ActualParameters
  case class ActualParameters(expressionActualParameters: Expression, actualParameters: Tree[ActualParameters]) extends Tree[ActualParameters] {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("ActualParameters")
      expressionActualParameters.compile(symbolTable)
      if (symbolTable.isEmpty) {
        error("ActualParameters:symbolTable member", (symbolTable, expressionActualParameters))
      }
      val (name, d) = symbolTable.first
      d match {
        case v: Memory.Declarations.Variable => {
          if (v.isVariable) {
            val varpar = ReferenceParameter(expressionActualParameters, IdentNode(Symbol("ident", 0, 0, Some(name))))
            val param = varpar.compile(symbolTable);
          } else {
            expressionActualParameters.compile(symbolTable); // Memory.SymbolTables().
          }
          OberonInstructions.GetSP
          OberonInstructions.AssignmentInstruction(v.size)
          OberonInstructions.GetSP
          OberonInstructions.IntegerVal(v.size)
          OberonInstructions.AdditionInstruction
          OberonInstructions.SetSP
        }
        case x => {
          error("ActualParameters: Variable", x)
        }
      }
      actualParameters.compile(symbolTable - name)
      Memory.Declarations.NilDescriptor
    }
    override def print(n: Int) = ->("ActualParameters", n) + expressionActualParameters.print(n + 1) + actualParameters.print(n + 1)
  }

  //IndexExpression  = integer | ConstIdent.
  trait IndexExpression extends Tree[IndexExpression] {
    def num = Int.MinValue
  }

  //ConstIdent       = ident.
  trait ConstIdent extends IndexExpression {
    override def num = Int.MinValue
  }

  //ArrayType = �ARRAY� �[� IndexExpression �]� �OF� Type.
  case object ArrayType
  case class ArrayType(elemArrayType: IndexExpression, _typeArrayType: Type) extends Type {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("ArrayType")
      val num = elemArrayType.num
      val desc = _typeArrayType.compile(symbolTable)
      val size = desc.size
      desc match {
        case t: Memory.Declarations.Type => Memory.Declarations.ArrayType(num, t)
        case x => {
          error("ArrayType: Type Descriptor", x)
          Memory.Declarations.NilDescriptor
        }
      }
    }
    override def print(n: Int) = ->("ArrayType", n) + elemArrayType.print(n + 1) + _typeArrayType.print(n + 1)
  }

  case object ArrayReference
  case class ArrayReference(ident: Expression, next: Expression) extends Tree[ArrayReference] with Expression {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("ArrayReference")
      val basetype = ident.compile(symbolTable)
      //      trace("base: " + basetype)
      if (next.isDefined) {
        val (t, s) = basetype match {
          case v: Memory.Declarations.Variable => {
            v._type match {
              case a: Memory.Declarations.ArrayType => {
                (a, a.basetype.size)
              }
              case x => {
                OberonInstructions.MultiplicationInstruction
                OberonInstructions.AdditionInstruction
                (x, basetype.size)
              }
            }
          }
          case i: Memory.Declarations.SimpleType => {
            OberonInstructions.MultiplicationInstruction
            OberonInstructions.AdditionInstruction
            (i, i.size)
          }
          case x => {
            error("ArrayReference: VariableDescriptor", x)
            (Memory.Declarations.NilDescriptor, Int.MinValue)
          }
        }
        OberonInstructions.IntegerVal(s)
        next.compile {
          basetype match {
            case v: Memory.Declarations.Variable => {
              v._type match {
                case a: Memory.Declarations.ArrayType => {
                  a.basetype match {
                    case r: Memory.Declarations.RecordType => {
                      r.symbolTable.symbolTable
                    }
                    case _ => symbolTable
                  }
                }
                case _ => symbolTable
              }
            }
            case _ => symbolTable
          }
        }
        t
      } else {
        OberonInstructions.MultiplicationInstruction
        OberonInstructions.AdditionInstruction
        Memory.Declarations.NilDescriptor
      }

    }

    override def print(n: Int) = ->("ArrayReference", n) + ident.print(n + 1) + next.print(n + 1)
  }

  // FieldListList
  trait FieldList extends Tree[FieldList] {
    val fields: Field = Nil
    val nextFieldList: FieldList = Nil
    override def print(n: Int) = ->("FieldList", n) + fields.print(n + 1) + nextFieldList.print(n + 1)
  }

  case object FieldListNode
  case class FieldListNode(override val fields: Field, override val nextFieldList: FieldList) extends FieldList {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("FieldListNode")
      Memory.Declarations.SymbolTable() + fields.compile(symbolTable) + nextFieldList.compile(symbolTable)
    }
  }

  //FieldList = [IdentList �:� Type].
  trait Field extends Tree[Field] {
    val idlField: Ident = Nil
    val _typeField: Type = Nil
    override def print(n: Int) = ->("Field", n) + idlField.print(n + 1) + _typeField.print(n + 1)
  }

  case object FieldNode
  case class FieldNode(override val idlField: Ident, override val _typeField: Type) extends Field {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("FieldNode")
      val t = _typeField.compile(symbolTable)
      def compileIdent(expr: Expression): Memory.Declarations.SymbolTableTrait = {
        if (expr.isDefined) {
          expr match {
            case id: Tree.Ident => {
              t match {
                case tt: Memory.Declarations.Type => {
                  Memory.Declarations.SymbolTable() + (id.identIdent.value.get.toString, t) + compileIdent(id.optionalIdent)
                }
                case x => {
                  error("FieldNode: Type", x)
                  Memory.Declarations.NilDescriptor
                }
              }
            }
            case x => {
              error("FieldNode: Ident", x)
              Memory.Declarations.NilDescriptor
            }
          }
        } else
          Memory.Declarations.NilDescriptor
      }
      Memory.Declarations.SymbolTable(symbolTable) + compileIdent(idlField)
    }

    override def print(n: Int) = ->("FieldNode", n) + idlField.print(n + 1) + _typeField.print(n + 1)
  }

  //RecordType = �RECORD� FieldList {�;� FieldList} �END�.
  case object RecordType
  case class RecordType(fieldsRecordType: FieldList) extends Type {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("RecordType")
      fieldsRecordType.compile(symbolTable) match {
        case t: Memory.Declarations.SymbolTableTrait => Memory.Declarations.RecordType(t)
        case x => {
          error("SymbolTable", x)
          Memory.Declarations.NilDescriptor
        }
      }
    }

    override def print(n: Int) = ->("RecordType", n) + fieldsRecordType.print(n + 1)
  }

  case object RecordReference
  case class RecordReference(record: Ident, field: Expression) extends Tree[RecordReference] with Expression {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("RecordRefNode")
      val a = record.compile(symbolTable) match {
        case s: Memory.Declarations.SimpleType => {
          field.compile(symbolTable)
        }
        case a: Memory.Declarations.ArrayType => {
          val f = field.compile(symbolTable)
          OberonInstructions.IntegerVal(f.size)
          f
        }
        case recordTable: Memory.Declarations.RecordType => {
          OberonInstructions.IntegerVal(recordTable.startAddress)
          val name = record.identIdent.value.get.toString
          val memTable = {
            val t = symbolTable.get(name)
            if (t.isDefined)
              t.get
            else {
              val t = Memory.SymbolTables(name)
              if (t.isDefined)
                t.get
              else {
                error(name + " in symboltable ", t)
                Memory.Declarations.NilDescriptor
              }
            }
          }
          memTable match {
            case r: Memory.Declarations.RecordType => {
              val f = field.compile(r.symbolTable.symbolTable)
              f
            }
            case x => {
              error(record.identIdent.value.get.toString + " in symboltable ", memTable)
              Memory.Declarations.NilDescriptor
            }
          }
        }
        case x => {
          error("RecordReference: Record Declaration", x)
          Memory.Declarations.NilDescriptor
        }
      }
      OberonInstructions.AdditionInstruction
      a
    }

    override def print(n: Int) = ->("RecordReference", n) + record.print(n + 1) + field.print(n + 1)
  }

  //Type = ident | ArrayType | RecordType.
  trait Type extends Tree[Type]

  //FPSection = [�VAR�] IdentList �:� Type.
  trait FPSection extends FormalParameters {
    def isVariable = true
    override val identFPSection: Expression = Nil
    override val _typeFPSection: Type = Nil
    override val optionalFPSection: FormalParameters = Nil
    def identSize(expr:Expression,n:Int = 0):Int = {
      expr match {
        case i:Ident => {
          if (i.optionalIdent.isDefined)
          	identSize(i.optionalIdent,n+1)
          else
            n+1
        }
        case _ => n+1
      }
    }
    override def size(n:Int = 0):Int = {
      if (optionalFPSection.isDefined)
        optionalFPSection.size(n+1)+identSize(identFPSection)
      else
        n+identSize(identFPSection)
    }
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap): Memory.Declarations.Descriptor = {
      trace(name)
      def compileIdent(expr: Expression, t: Memory.Declarations.SymbolTableTrait): Memory.Declarations.SymbolTableTrait = {
        val n = Memory.paramSize
        expr match {
          case i: Ident => {
            val entry = _typeFPSection match {
              case i: Ident => {
                val d = Memory.SymbolTables(i.identIdent.value.get.toString)
                if (d.isDefined) {
                  d.get match {
                    case a: Memory.Declarations.ArrayType => {
                      Memory.paramSize -= 1
                      Memory.Declarations.Variable(-n, a, isVariable)                     
                    }
                    case r: Memory.Declarations.RecordType => {
                      Memory.paramSize -= 1
                      Memory.Declarations.Variable(-n, r, isVariable)                  
                    }
                    case s: Memory.Declarations.SimpleType => {
                      Memory.paramSize -= 1
                      Memory.Declarations.Variable(-n, s, isVariable)                   
                    }
                    case r: Memory.Declarations.ReferenceVariable => {
                      r._type
                    }
                    case x => {
                      error("FPSection: Type Declaration", x)
                      Memory.Declarations.NilDescriptor
                    }
                  }
                } else {
                  error("FPSection: Ident in Symboltables", i)
                  Memory.Declarations.NilDescriptor
                }
              }
              case a: ArrayType => {
                val d = a.compile(symbolTable)
                d match {
                  case t: Memory.Declarations.ArrayType => {
                    Memory.Declarations.Variable(Memory.SymbolTables.currentAddress, t, isVariable)
                  }
                  case x => {
                    error("FPSection: ArrayType", x)
                    Memory.Declarations.NilDescriptor
                  }
                }
              }
              case r: RecordType => {
                val d = r.compile(symbolTable)
                d match {
                  case t: Memory.Declarations.RecordType => {
                    Memory.Declarations.Variable(Memory.SymbolTables.currentAddress, t, isVariable)
                  }
                  case x => {
                    error("FPSection: RecordType", x)
                    Memory.Declarations.NilDescriptor
                  }
                }
              }
              case x => {
                error("ReferenceParameter: Ident or ArrayType oder Recordtype", x)
                Memory.Declarations.NilDescriptor
              }
            }
            if (!isVariable) {
              Memory.SymbolTables + (i.identIdent.value.getOrElse("foo").toString, entry)
            }
            val tn = t + (i.identIdent.value.getOrElse("foo").toString, entry)
            if (i.optionalIdent.isDefined)
              compileIdent(i.optionalIdent, tn)
            else
              tn
          }
          case x => {
            error("FPSection:compileIdent:Ident ", expr)
            t
          }
        }
      }
      val t = compileIdent(identFPSection, Memory.Declarations.SymbolTable(new TreeMap))
      optionalFPSection.compile(symbolTable) match {
        case s: Memory.Declarations.SymbolTableTrait => {
          if (s.isDefined)
            Memory.Declarations.SymbolTable(s.symbolTable ++ t.symbolTable)
          else
            t
        }
        case x => t
      }
    }
    val name = "FPSection"
  }

  case object ValueParameter
  case class ValueParameter(override val identFPSection: Expression, override val _typeFPSection: Type, override val optionalFPSection: FormalParameters = Nil) extends FPSection {
    override def isVariable = false
    override def print(n: Int) = ->("ValueParameter", n) + identFPSection.print(n + 1) + _typeFPSection.print(n + 1) + optionalFPSection.print(n + 1)
    override val name = "ValueParameter"
  }

  case object ReferenceParameter
  case class ReferenceParameter(override val identFPSection: Expression, override val _typeFPSection: Type, override val optionalFPSection: FormalParameters = Nil) extends FPSection {
    override def isVariable = true
    override def print(n: Int) = ->("ReferenceParameter", n) + identFPSection.print(n + 1) + _typeFPSection.print(n + 1) + optionalFPSection.print(n + 1)
    override val name = "ReferenceParameter"
  }

  //FormalParameters = FPSection {�;� FPSection}.
  trait FormalParameters extends Tree[FormalParameters] {
    val optionalFPSection: FormalParameters = Nil
    val identFPSection: Expression = Nil
    val _typeFPSection: Type = Nil
    def size(n:Int = 0):Int = {
      if (optionalFPSection.isDefined)
        optionalFPSection.size(n+1)
      else
        n+1
    }
    override def print(n: Int) = ->("FormalParameters", n) + optionalFPSection.print(n + 1) + identFPSection.print(n + 1) + _typeFPSection.print(n + 1)
  }

  //ProcedureHeading = �PROCEDURE� ident �(� [FormalParameters] �)�.
  case object ProcedureHeading
  case class ProcedureHeading(identProcedureHeading: Ident, formalParameters: FormalParameters = Nil) extends Tree[ProcedureHeading] {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("ProecureHeading")
      val name = identProcedureHeading.identIdent.value.get.toString
      val startaddress = OberonInstructions.newLabel      
      Memory.paramSize = formalParameters.size()+3
      trace("Memory.paramSize: " + Memory.paramSize)
      trace("formalParameters: " + formalParameters)
      val params = formalParameters.compile(symbolTable)
      params match {
        case p: Memory.Declarations.SymbolTableTrait => {
          val lengthparblock = p.size
          val framesize = Memory.SymbolTables.currentAddress
          val procedure = Memory.Declarations.Procedcure(name, startaddress, lengthparblock,
            framesize, p)
          Memory.Level.dec
          Memory.SymbolTables + ("#" + name, procedure)
          Memory.Level.inc
          procedure
        }
        case x => {
          error("ProcedureHeading: SymbolTable", x)
          Memory.Declarations.NilDescriptor
        }
      }
    }
    override def print(n: Int) = ->("ProcedureHeading", n) + identProcedureHeading.print(n + 1) + formalParameters.print(n + 1)
  }

  //ProcedureBody    = Declarations �BEGIN� StatementSequence �END� 
  case object ProcedureBody
  case class ProcedureBody(declarationsProcedureBody: Declarations, statementProcedureBody: Statement) extends Tree[ProcedureBody] {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("ProcedureBody")
      val (name, table) = symbolTable.first
      table match {
        case procedure: Memory.Declarations.Procedcure => {

          // aus Symboltabelle holen
          OberonInstructions.LabelInstruction(procedure.startaddress)

          // Init
          OberonInstructions.PushRegisterInstruction("RK")
          OberonInstructions.PushRegisterInstruction("FP")
          OberonInstructions.IntegerVal(Memory.Level.value)
          OberonInstructions.PushRegisterInstruction("SL")

          // FP := SP;
          OberonInstructions.GetSP
          OberonInstructions.SetFP

          // SL(level) := FP
          OberonInstructions.GetFP
          OberonInstructions.IntegerVal(Memory.Level.value)
          OberonInstructions.SetSL

          // SP := SP + framesize;
          OberonInstructions.GetSP
          procedure.framesize = Memory.SymbolTables().size
          OberonInstructions.IntegerVal(procedure.framesize)
          OberonInstructions.AdditionInstruction
          OberonInstructions.SetSP

          // content         
          declarationsProcedureBody.compile(symbolTable)
          //          OberonInstructions.IntegerVal(-4)
          statementProcedureBody.compile(procedure.params.symbolTable ++ symbolTable)

          // finish		

          // SP := FP
          OberonInstructions.GetFP
          OberonInstructions.SetSP

          OberonInstructions.IntegerVal(Memory.Level.value)
          OberonInstructions.PopRegisterInstruction("SL")
          OberonInstructions.PopRegisterInstruction("FP")
          OberonInstructions.PopRegisterInstruction("RK")

          // SP := SP-lengthparblock;
          OberonInstructions.GetSP
          OberonInstructions.IntegerVal(procedure.lengthparblock)
          OberonInstructions.SubtractionInstruction
          OberonInstructions.SetSP

          // Speicher freigeben
          OberonInstructions.ReduceStack(procedure.framesize + 3 + procedure.lengthparblock)

          OberonInstructions.ReturnInstruction
          procedure
        }
        case x => {
          error("Procedure Boddy: Procedure Declaration", x)
          Memory.Declarations.NilDescriptor
        }
      }
    }
    override def print(n: Int) = ->("ProcedureBody", n) + declarationsProcedureBody.print(n + 1) + statementProcedureBody.print(n + 1)
  }

  //ProcedureDeclaration = ProcedureHeading �;� ProcedureBody ident.
  trait ProcedureDeclaration extends Tree[ProcedureDeclaration] with Declarations {
    val procedureHeading: Tree[ProcedureHeading] = Nil
    val procedureBody: Tree[ProcedureBody] = Nil
    val ident: Ident = Nil
    override val next: ProcedureDeclaration = Nil
    override def print(n: Int) = ->("ProcedureDeclaration", n) + procedureHeading.print(n + 1) + procedureBody.print(n + 1) + ident.print(n + 1) + next.print(n + 1)
  }

  case object ProcedureDeclarationNode
  case class ProcedureDeclarationNode(override val procedureHeading: Tree[ProcedureHeading], override val procedureBody: Tree[ProcedureBody], override val ident: Ident, override val next: ProcedureDeclaration = Nil) extends ProcedureDeclaration with Declarations {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("ProcedureDeclarationNode")
      Memory.Level.inc
      Memory.SymbolTables + ("integer", Memory.Declarations.IntegerType)
      Memory.SymbolTables + ("boolean", Memory.Declarations.BooleanType)
      Memory.SymbolTables + ("string", Memory.Declarations.StringType)
      val name = ""
      val proc = procedureHeading.compile(symbolTable)
      procedureBody.compile(TreeMap(Tuple(name, proc)))
      Memory.Level.dec
      next.compile(symbolTable)
      if (!next.isDefined)
        OberonInstructions.LabelInstruction(Memory.mainProgramStart)
      Memory.Declarations.NilDescriptor
    }
  }

  //Declarations     = [�CONST� ident �=� Expression �;�
  //                            {ident �=� Expression �;�}]
  //                   [�TYPE� ident �=� Type �;�
  //                           {ident �=� Type �;�}]
  //                   [�VAR� IdentList �:� Type �;�
  //                          {IdentList �:� Type �;�}]
  //                   {ProcedureDeclaration �;�}.
  trait Declarations extends Tree[Declarations] {
    val next: Declarations = Nil
  }

  case object ConstDeclarations
  case class ConstDeclarations(ident: Ident, expression: Expression, override val next: Declarations = Nil) extends Declarations {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("ConstDeclarations")
      val d = expression.compile(symbolTable);
      expression match {
        case i: Integer => {
          Memory.SymbolTables + (ident.identIdent.value.get.toString, Memory.Declarations.IntConst(i.int.value.get.toString.toInt))
          next.compile(symbolTable + Tuple(ident.identIdent.value.get.toString, Memory.Declarations.IntConst(i.int.value.get.toString.toInt)));
        }
        case c: Content => {
          trace("content")
          trace(c.identIdent)
        }
        case x => {
          error("ConstDeclarations with Integervalue", x)
        }
      }
      d
    }
    override def print(n: Int) = ->("ConstDeclarations", n) + ident.print(n + 1) + expression.print(n + 1) + next.print(n + 1)
  }

  case object TypeDeclarations
  case class TypeDeclarations(ident: Ident, _type: Type, override val next: Declarations = Nil) extends Declarations {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("TypeDeclarations")
      val d = _type.compile(symbolTable);
      Memory.SymbolTables + (ident.identIdent.value.get.toString, d)
      next.compile(symbolTable + Tuple(ident.identIdent.value.get.toString, d))
      d
    }
    override def print(n: Int) = ->("TypeDeclarations", n) + ident.print(n + 1) + _type.print(n + 1) + next.print(n + 1)
  }

  case object VarDeclarations
  case class VarDeclarations(ident: Ident, _type: Type, override val next: Declarations = Nil) extends Declarations {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("VarDeclarations")
      val anyType = _type.compile(symbolTable)
      def compileIdent(ident: Expression): Memory.Declarations.Descriptor = {
        if (ident.isDefined) {
          ident match {
            case i: Ident => {
              anyType match {
                case t: Memory.Declarations.SimpleType => {
                  val entry = Memory.Declarations.ValueVariable(Memory.SymbolTables.currentAddress, t)
                  Memory.SymbolTables + (i.identIdent.value.get.toString, entry)
                  t
                }
                case t: Memory.Declarations.ArrayType => {
                  val entry = Memory.Declarations.ValueVariable(Memory.SymbolTables.currentAddress, t)
                  Memory.SymbolTables + (i.identIdent.value.get.toString, entry)
                  t
                }
                case t: Memory.Declarations.RecordType => {
                  Memory.SymbolTables + (i.identIdent.value.get.toString, concreteRecordType(t, Memory.SymbolTables.currentAddress))
                  t
                }
                case x => {
                  error("VarDeclarations: SimpleType or Record", x)
                  Memory.Declarations.NilDescriptor
                }
              }
              compileIdent(i.optionalIdent)
            }
            case x => {
              error("VarDeclarations: Ident", x)
              Memory.Declarations.NilDescriptor
            }
          }
        } else
          Memory.Declarations.NilDescriptor
      }
      val d = compileIdent(ident)
      next.compile()
      d
    }

    def concreteRecordType(r: Memory.Declarations.RecordType, currentAddress: Int): Memory.Declarations.RecordType = {
      val startAddr = currentAddress
      var i = 0
      var newTable = Memory.Declarations.SymbolTable()
      for ((name, desc) <- r.symbolTable.symbolTable) {
        newTable = desc match {
          case r: Memory.Declarations.RecordType => newTable + (name, concreteRecordType(r, i))
          case t: Memory.Declarations.Type => {
            newTable + (name, Memory.Declarations.ValueVariable(i, t))
          }
          case t: Memory.Declarations.IntConst => newTable
          case x => {
            error("concreteRecordType:Type", x)
            Memory.Declarations.SymbolTable()
          }
        }
        i += desc.size
      }
      Memory.Declarations.RecordType(newTable, startAddr)
    }

    override def print(n: Int) = ->("VarDeclarations", n) + ident.print(n + 1) + _type.print(n + 1) + next.print(n + 1)
  }

  case object Print
  case class Print(expression: Expression) extends Tree[Print] with Expression {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("Print")
      expression.compile(symbolTable)
      OberonInstructions.PrintInstruction
      Memory.Declarations.NilDescriptor
    }
    override def print(n: Int) = ->("Print", n) + expression.print(n + 1)
  }

  //   IfStatement | �PRINT� Expression |
  //   WhileStatement | RepeatStatement].
  //StatementSequence = Statement {�;� Statement}.
  case object StatementSequence
  case class StatementSequence(st: Statement, sts: Tree[StatementSequence]) extends Statement with Tree[StatementSequence] {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("StatementSequence")
      st.compile(symbolTable)
      sts.compile(symbolTable)
    }

    override def print(n: Int) = ->("StatementSequence", n) + st.print(n + 1) + sts.print(n + 1)
  }

  trait Statement extends Tree[Statement]

  //Module           = �MODULE� ident �;� Declarations
  //                   �BEGIN� StatementSequence
  //                   �END� ident �.�.
  case object Module
  case class Module(idStart: Ident, declarations: Declarations, statement: Statement, idEnd: Ident) extends Tree[Module] {

    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("Module");
      OberonInstructions.StringVal(idStart.identIdent.value.get.toString)
      val mainProgramStart = OberonInstructions.newLabel
      Memory.mainProgramStart = mainProgramStart
      OberonInstructions.JumpInstruction(mainProgramStart)

      Memory.SymbolTables + ("integer", Memory.Declarations.IntegerType)
      Memory.SymbolTables + ("boolean", Memory.Declarations.BooleanType)
      Memory.SymbolTables + ("string", Memory.Declarations.StringType)
      declarations.compile(Memory.SymbolTables().symbolTable)
      OberonInstructions.LabelInstruction(mainProgramStart)
      Memory.setMainProgramLength(Memory.SymbolTables.currentAddress)
      OberonInstructions.IntegerVal(Memory.mainProgramLength)
      OberonInstructions.SetSP
      statement.compile(Memory.SymbolTables().symbolTable)
      OberonInstructions.StopInstruction
      Memory.Declarations.NilDescriptor
    }

    override def print(n: Int) = ->("Module", n) + idStart.print(n + 1) + declarations.print(n + 1) + statement.print(n + 1) + idEnd.print(n + 1)
  }

  //Assignment        = ident Selector �:=� Expression
  case object Assignment
  case class Assignment(ident: Expression, expression: Expression) extends Statement {

    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("Assignment")
      val t = expression.compile(symbolTable)
      ident.compile()
      OberonInstructions.AssignmentInstruction {
        t match {
          case a: Memory.Declarations.ArrayType => 1
          case r: Memory.Declarations.RecordType => {
            OberonInstructions.IntegerVal(t.size)
            t.size
          }
          case _ => t.size
        }
      }
      Memory.Declarations.NilDescriptor
    }

    override def print(n: Int) = ->("Assignment", n) + ident.print(n + 1) + expression.print(n + 1)
  }

  //ProcedureCall = ident �(� [ActualParameters] �)�.
  case object ProcedureCall
  case class ProcedureCall(ident: Ident, params: Tree[ActualParameters] = Nil) extends Statement {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("ProcedureCall")
      val name = ident.identIdent.value.get.toString
      val procedure = Memory.SymbolTables("#" + name)
      Memory.Level.inc
      if (procedure.isDefined) {
        procedure.get match {
          case p: Memory.Declarations.Procedcure => {
            if (p.params.size == 0) {
              OberonInstructions.InitStack(p.framesize + 3)
            } else {
              OberonInstructions.InitStack(p.lengthparblock + p.framesize + 3);
              params.compile(p.params.symbolTable)
            }
            OberonInstructions.CallInstruction(p.startaddress);
          }
          case x => {
            error("ProcedureCall: Procedure Declaration", (name, x))
          }
        }
      } else
        error("ProcedureCall: Procedure Declaration", (name, procedure))
      Memory.Level.dec
      Memory.Declarations.NilDescriptor
    }
    override def print(n: Int) = ->("ProcedureCall", n) + ident.print(n + 1) + params.print(n + 1)
  }

  //IfStatement = �IF� Expression �THEN� StatementSequence
  //	{�ELSIF� Expression �THEN� StatementSequence}
  //  	[�ELSE� StatementSequence] �END�.
  case object IfStatement
  case class IfStatement(condition: Expression, statementSequence: Statement, alternatve: Statement = Nil) extends Statement with Tree[IfStatement] {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("IfStatement")
      val l1 = OberonInstructions.newLabel
      val l2 = OberonInstructions.newLabel
      condition.compile()
      OberonInstructions.BranchFalseInstruction(l1)
      statementSequence.compile()
      OberonInstructions.JumpInstruction(l2)
      OberonInstructions.LabelInstruction(l1)
      alternatve.compile()
      OberonInstructions.LabelInstruction(l2)
      Memory.Declarations.NilDescriptor
    }
    override def print(n: Int) = ->("IfStatement", n) + condition.print(n + 1) + statementSequence.print(n + 1) + alternatve.print(n + 1)
  }

  //WhileStatement = �WHILE� Expression �DO� StatementSequence �END�.
  case object WhileStatement
  case class WhileStatement(condition: Expression, statement: Statement) extends Statement {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("WhileStatement")
      val l1 = OberonInstructions.newLabel
      val l2 = OberonInstructions.newLabel
      OberonInstructions.LabelInstruction(l1)
      condition.compile(symbolTable);
      OberonInstructions.BranchFalseInstruction(l2)
      statement.compile(symbolTable);
      OberonInstructions.JumpInstruction(l1)
      OberonInstructions.LabelInstruction(l2)
      Memory.Declarations.NilDescriptor
    }
    override def print(n: Int) = ->("WhileStatement", n) + condition.print(n + 1) + statement.print(n + 1)
  }

  //RepeatStatement = �REPEAT� StatementSequence �UNTIL� Expression.
  case object RepeatStatement
  case class RepeatStatement(statement: Statement, condition: Expression) extends Statement {
    override def compile(symbolTable: Map[String, Descriptor] = new TreeMap) = {
      trace("RepeatStatement")
      val l1 = OberonInstructions.newLabel
      val l2 = OberonInstructions.newLabel
      OberonInstructions.LabelInstruction(l1)
      statement.compile(symbolTable);
      condition.compile(symbolTable);
      OberonInstructions.BranchTrueInstruction(l2)
      OberonInstructions.JumpInstruction(l1)
      OberonInstructions.LabelInstruction(l2)
      Memory.Declarations.NilDescriptor
    }
    override def print(n: Int) = ->("RepeatStatement", n) + condition.print(n + 1) + statement.print(n + 1)
  }

  trait Tree[+T] {
    def isDefined = true
    def print(n: Int): String
    override def toString = "AbstractSyntaxTree:\n" + print(0)
    def compile(symbolTable2: Map[String, Descriptor] = new TreeMap): Memory.Declarations.Descriptor = Memory.Declarations.NilDescriptor
  }

  def ->(value: String, n: Int) = "	" * n + value + "\n"
  def trace(s: Any) = if (OberonDebug.compile) println("compile: " + s)
  def error(s: Any, found: Any) = {
    Thread.sleep(40)
    Console.err.println("compile error: " + s + " is missing, found: " + found)
    System.exit(-1)
  }
}