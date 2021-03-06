/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.codingcat.xgboost4j

import java.io.File

import com.typesafe.config.ConfigFactory

import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.feature.{OneHotEncoder, StringIndexer, VectorAssembler}
import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}

object AirlineDataTransformer {

  private def buildPreprocessingPipeline(): Pipeline = {
    // string indexers
    val monthIndexer = new StringIndexer().setInputCol("Month").setOutputCol("monthIdx")
    val daysOfMonthIndexer = new StringIndexer().setInputCol("DayOfMonth").
      setOutputCol("dayOfMonthIdx")
    val daysOfWeekIndexer = new StringIndexer().setInputCol("DayOfWeek").
      setOutputCol("daysOfWeekIdx")
    val uniqueCarrierIndexer = new StringIndexer().setInputCol("UniqueCarrier").setOutputCol(
      "uniqueCarrierIndex")
    val originIndexer = new StringIndexer().setInputCol("Origin").setOutputCol(
      "originIndexer")
    val destIndexer = new StringIndexer().setInputCol("Dest").setOutputCol(
      "destIndexer")
    // one-hot encoders
    val monthEncoder = new OneHotEncoder().setInputCol("monthIdx").
      setOutputCol("encodedMonth")
    val daysOfMonthEncoder = new OneHotEncoder().setInputCol("dayOfMonthIdx").
      setOutputCol("encodedDaysOfMonth")
    val daysOfWeekEncoder = new OneHotEncoder().setInputCol("daysOfWeekIdx").
      setOutputCol("encodedDaysOfWeek")
    val uniqueCarrierEncoder = new OneHotEncoder().setInputCol("uniqueCarrierIndex").
      setOutputCol("encodedCarrier")
    val originEncoder = new OneHotEncoder().setInputCol("originIndexer").
      setOutputCol("encodedOrigin")
    val destEncoder = new StringIndexer().setInputCol("destIndexer").setOutputCol(
      "encodedDest")


    val vectorAssembler = new VectorAssembler().setInputCols(
      Array("encodedMonth", "encodedDaysOfMonth", "encodedDaysOfWeek", "DepTime",
        "encodedCarrier", "encodedOrigin", "encodedDest", "Distance")
    ).setOutputCol("features")
    val pipeline = new Pipeline().setStages(
      Array(monthIndexer, daysOfMonthIndexer, daysOfWeekIndexer,
        uniqueCarrierIndexer, originIndexer, destIndexer, monthEncoder, daysOfMonthEncoder,
        daysOfWeekEncoder, uniqueCarrierEncoder, originEncoder, destEncoder, vectorAssembler))
    pipeline
  }

  private def runPreprocessingPipeline(pipeline: Pipeline, dataset: DataFrame): DataFrame = {
    pipeline.fit(dataset).transform(dataset).selectExpr(
      "features", "case when dep_delayed_15min = true then 1.0 else 0.0 end as label")
  }

  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.parseFile(new File(args(0)))
    val rawDataPath = config.getString("me.codingcat.xgboost4j.airline.rawDataPath")
    val outputDataPath = config.getString("me.codingcat.xgboost4j.airline.dataPath")
    val spark = SparkSession.builder().getOrCreate()
    val rawDataSet = spark.read.parquet(rawDataPath)

    val pipeline = buildPreprocessingPipeline()
    val transformedDataSet = runPreprocessingPipeline(pipeline, rawDataSet)
    transformedDataSet.repartition(20).write.mode(SaveMode.Overwrite).
      parquet(outputDataPath)
  }
}
