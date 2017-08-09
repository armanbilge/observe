package edu.gemini.seqexec.server

import edu.gemini.seqexec.model.dhs.ImageFileId

/**
  * Defines the interface for dhs client, with methods, e.g. to request image creation
  */
trait DhsClient {
  /**
    * Requests the DHS to create an image returning the obs id if applicable
    */
  def createImage(p: DhsClient.ImageParameters): SeqAction[ImageFileId]

  /**
    * Set the keywords for an image
    */
  def setKeywords(id: ImageFileId, keywords: DhsClient.KeywordBag, finalFlag: Boolean = false): SeqAction[Unit]

}

object DhsClient {

  type Contributor = String

  sealed case class Lifetime(str: String)
  object Permanent extends Lifetime("PERMANENT")
  object Temporary extends Lifetime("TEMPORARY")
  object Transient extends Lifetime("TRANSIENT")

  final case class ImageParameters(lifetime: Lifetime, contributors: List[Contributor])

  // TODO: Implement the unsigned types, if needed.
  sealed case class KeywordType protected (str: String)
  object TypeInt8 extends KeywordType("INT8")
  object TypeInt16 extends KeywordType("INT16")
  object TypeInt32 extends KeywordType("INT32")
  object TypeFloat extends KeywordType("FLOAT")
  object TypeDouble extends KeywordType("DOUBLE")
  object TypeBoolean extends KeywordType("BOOLEAN")
  object TypeString extends KeywordType("STRING")


  // The developer uses these classes to define all the typed keywords
  sealed class Keyword[T] protected (val n: String, val t: KeywordType, val v: T)
  final case class Int8Keyword(name: String, value: Byte) extends Keyword[Byte](name, TypeInt8, value)
  final case class Int16Keyword(name: String, value: Short) extends Keyword[Short](name, TypeInt16, value)
  final case class Int32Keyword(name: String, value: Int) extends Keyword[Int](name, TypeInt32, value)
  final case class FloatKeyword(name: String, value: Float) extends Keyword[Float](name, TypeFloat, value)
  final case class DoubleKeyword(name: String, value: Double) extends Keyword[Double](name, TypeDouble, value)
  final case class BooleanKeyword(name: String, value: Boolean) extends Keyword[Boolean](name, TypeBoolean, value)
  final case class StringKeyword(name: String, value: String) extends Keyword[String](name, TypeString, value)

  // At the end, I want to just pass a list of keywords to be sent to the DHS. I cannot do this with Keyword[T],
  // because I cannot mix different types in a List. But at the end I only care about the value as a String, so I
  // use an internal representation, and offer a class to the developer (KeywordBag) to create the list from typed
  // keywords.

  final case class InternalKeyword(name: String, keywordType: KeywordType, value: String)

  protected implicit def internalKeywordConvert[T](k: Keyword[T]): InternalKeyword = InternalKeyword(k.n, k.t, k.v.toString)

  final case class KeywordBag(keywords: List[InternalKeyword]) {
    def add[T](k: Keyword[T]): KeywordBag = KeywordBag(keywords :+ internalKeywordConvert(k))
    def append(other: KeywordBag): KeywordBag =  KeywordBag(keywords ::: other.keywords)
  }

  //TODO: Add more apply methods if necessary
  object KeywordBag {
    def empty: KeywordBag = KeywordBag(List())
    def apply[A](k1: Keyword[A]): KeywordBag = KeywordBag(List(internalKeywordConvert(k1)))
    def apply[A, B](k1: Keyword[A], k2: Keyword[B]): KeywordBag = KeywordBag(List(internalKeywordConvert(k1), internalKeywordConvert(k2)))
    def apply[A, B, C](k1: Keyword[A], k2: Keyword[B], k3: Keyword[C]): KeywordBag =
      KeywordBag(List(internalKeywordConvert(k1), internalKeywordConvert(k2), internalKeywordConvert(k3)))
    def apply[A, B, C, D](k1: Keyword[A], k2: Keyword[B], k3: Keyword[C], k4: Keyword[D]): KeywordBag =
      KeywordBag(List(internalKeywordConvert(k1), internalKeywordConvert(k2), internalKeywordConvert(k3),
        internalKeywordConvert(k4)))
    def apply[A, B, C, D, E](k1: Keyword[A], k2: Keyword[B], k3: Keyword[C], k4: Keyword[D], k5: Keyword[E]): KeywordBag =
      KeywordBag(List(internalKeywordConvert(k1), internalKeywordConvert(k2), internalKeywordConvert(k3),
        internalKeywordConvert(k4), internalKeywordConvert(k5)))
    def apply[A, B, C, D, E, F](k1: Keyword[A], k2: Keyword[B], k3: Keyword[C], k4: Keyword[D], k5: Keyword[E], k6: Keyword[F]): KeywordBag =
      KeywordBag(List(internalKeywordConvert(k1), internalKeywordConvert(k2), internalKeywordConvert(k3),
        internalKeywordConvert(k4), internalKeywordConvert(k5), internalKeywordConvert(k6)))
  }

}
