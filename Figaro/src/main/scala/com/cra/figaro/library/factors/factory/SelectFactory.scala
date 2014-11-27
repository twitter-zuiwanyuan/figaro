/**
 *
 */
package com.cra.figaro.library.factors.factory

import com.cra.figaro.algorithm.lazyfactored._
import com.cra.figaro.language._
import com.cra.figaro.library.factors._
import com.cra.figaro.library.compound._
import com.cra.figaro.util._
import com.cra.figaro.library.factors.Factory

/**
 * @author gtakata
 *
 */
object SelectFactory {
  def makeFactors[T](dist: AtomicDist[T]): List[Factor[Double]] = {
    val (intermed, clauseFactors) = intermedAndClauseFactors(dist)
    val intermedFactor = makeSimpleDistribution(intermed, dist.probs)
    intermedFactor :: clauseFactors
  }

  def makeFactors[T](dist: CompoundDist[T]): List[Factor[Double]] = {
    val (intermed, clauseFactors) = intermedAndClauseFactors(dist)
    val intermedFactor = makeComplexDistribution(intermed, dist.probs)
    intermedFactor :: clauseFactors
  }
  def makeFactors[T](select: AtomicSelect[T]): List[Factor[Double]] = {
    val selectVar = Variable(select)
    if (selectVar.range.exists(!_.isRegular)) {
      assert(selectVar.range.size == 1) // Select's range must either be a list of regular values or {*}
      StarFactory.makeStarFactor(select)
    } else {
      val probs = getProbs(select)
      List(makeSimpleDistribution(selectVar, probs))
    }
  }

  def makeFactors[T](select: CompoundSelect[T]): List[Factor[Double]] = {
    val selectVar = Variable(select)
    if (selectVar.range.exists(!_.isRegular)) {
      assert(selectVar.range.size == 1) // Select's range must either be a list of regular values or {*}
      StarFactory.makeStarFactor(select)
    } else {
      val probs = getProbs(select)
      List(makeComplexDistribution(selectVar, probs))
    }
  }

  def makeFactors[T](select: ParameterizedSelect[T]): List[Factor[Double]] = {
    val selectVar = Variable(select)
    if (selectVar.range.exists(!_.isRegular)) {
      assert(selectVar.range.size == 1) // Select's range must either be a list of regular values or {*}
      StarFactory.makeStarFactor(select)
    } else {
      val probs = parameterizedGetProbs(select)
      List(makeSimpleDistribution(selectVar, probs))
    }
  }

  def makeFactors[T](select: IntSelector): List[Factor[Double]] = {
    val elementVar = Variable(select)
    val counterVar = Variable(select.counter)
    val comb = new BasicFactor[Double](List(counterVar), List(elementVar))
    comb.fillByRule((l: List[Any]) => {
      val counterValue :: elementValue :: _ = l.asInstanceOf[List[Extended[Int]]]
      if (counterValue.isRegular && elementValue.isRegular) {
        if (elementValue.value < counterValue.value) 1.0 / counterValue.value; else 0.0
      } else 1.0

    })
    List(comb)
  }

  private def getProbs[U, T](select: Select[U, T]): List[U] = getProbs(select, select.clauses)

  def getProbs[U, T](elem: Element[T], clauses: List[(U, T)]): List[U] = {
    val selectVar = Variable(elem)
    def getProb(xvalue: Extended[T]): U = {
      clauses.find(_._2 == xvalue.value).get._1 // * cannot be a value of a Select
    }
    val probs =
      for { xvalue <- selectVar.range } yield getProb(xvalue)
    probs
  }

  private def parameterizedGetProbs[T](select: ParameterizedSelect[T]): List[Double] = {
    val outcomes = select.outcomes
    val map = select.parameter.MAPValue
    for {
      xvalue <- Variable(select).range
      index = outcomes.indexOf(xvalue.value)
    } yield map(index)
  }

  private def intermedAndClauseFactors[U, T](dist: Dist[U, T]): (Variable[Int], List[Factor[Double]]) = {
    val intermed = new Variable(ValueSet.withoutStar((0 until dist.clauses.size).toSet))
    val clauseFactors = dist.outcomes.zipWithIndex map (pair =>
      Factory.makeConditionalSelector(dist, intermed, pair._2, Variable(pair._1)))
    (intermed, clauseFactors)
  }

  def makeSimpleDistribution[T](target: Variable[T], probs: List[Double]): Factor[Double] = {
    val factor = new BasicFactor[Double](List(), List(target))
    for { (prob, index) <- probs.zipWithIndex } {
      factor.set(List(index), prob)
    }
    factor
  }

  private def makeComplexDistribution[T](target: Variable[T], probElems: List[Element[Double]]): Factor[Double] = {
    val probVars: List[Variable[Double]] = probElems map (Variable(_))
    val nVars = probVars.size
    val factor = new BasicFactor[Double](probVars, List(target))
    val probVals: List[List[Extended[Double]]] = probVars map (_.range)
    for { indices <- factor.allIndices } {
      // unnormalized is a list, one for each probability element, of the value of that element under these indices
      val unnormalized =
        //     expects outcome to be first, but isn't   
        for { (probIndex, position) <- indices.toList.take(nVars).zipWithIndex } yield {
          val xprob = probVals(position)(probIndex) // The probability of the particular value of the probability element in this position
          if (xprob.isRegular) xprob.value; else 0.0
        }
      val normalized = normalize(unnormalized).toArray
      // The first variable specifies the position of the remaining variables, so indices(0) is the correct probability
      factor.set(indices, normalized(indices.last))
    }
    factor
  }
}