package com.linkedin.drelephant.tuning;

import com.linkedin.drelephant.mapreduce.heuristics.CommonConstantsHeuristic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import models.AppHeuristicResult;
import models.AppResult;
import models.JobDefinition;
import models.JobExecution;
import models.JobSuggestedParamSet;
import models.JobSuggestedParamValue;
import models.TuningAlgorithm;
import models.TuningJobDefinition;
import models.TuningParameter;
import models.TuningParameterConstraint;

import static org.junit.Assert.*;
import static play.test.Helpers.*;
import static common.DBTestUtil.*;


public class IPSOManagerTestRunner implements Runnable {

  private void populateTestData() {
    try {
      initDBIPSO();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void run() {
    populateTestData();
    JobDefinition jobDefinition = JobDefinition.find.byId(100003);
    TuningJobDefinition tuningJobDefinition =
        TuningJobDefinition.find.where().eq("job.id", jobDefinition.id).findUnique();
    TuningAlgorithm tuningAlgorithm = tuningJobDefinition.tuningAlgorithm;
    JobSuggestedParamSet jobSuggestedParamSet =
        JobSuggestedParamSet.find.where().eq("fitness_job_execution_id", 1541).findUnique();
    JobExecution jobExecution = JobExecution.find.byId(1541L);
    AutoTuningOptimizeManager optimizeManager = checkIPSOManager(tuningAlgorithm);
    testIPSOIntializePrerequisite(optimizeManager, tuningAlgorithm, jobSuggestedParamSet);
    testIPSOExtractParameterInformation(jobExecution, optimizeManager);
    testIPSOParameterOptimizer(jobExecution, optimizeManager);
    testIPSOApplyIntelligenceOnParameter(tuningJobDefinition, jobDefinition, optimizeManager);
    testIPSONumberOfConstraintsViolated(optimizeManager);
  }

  private AutoTuningOptimizeManager checkIPSOManager(TuningAlgorithm tuningAlgorithm) {
    AutoTuningOptimizeManager optimizeManager = OptimizationAlgoFactory.getOptimizationAlogrithm(tuningAlgorithm);
    assertTrue("Optimization Algorithm type ", optimizeManager instanceof IPSOManager);
    return optimizeManager;
  }

  private void testIPSOIntializePrerequisite(AutoTuningOptimizeManager optimizeManager, TuningAlgorithm tuningAlgorithm,
      JobSuggestedParamSet jobSuggestedParamSet) {
    optimizeManager.intializePrerequisite(tuningAlgorithm, jobSuggestedParamSet);
    List<TuningParameterConstraint> tuningParameterConstraint =
        TuningParameterConstraint.find.where().eq("job_definition_id", 100003).findList();
    assertTrue(" Parameters Constraint Size ", tuningParameterConstraint.size() == 7);
  }

  private void testIPSOExtractParameterInformation(JobExecution jobExecution,
      AutoTuningOptimizeManager optimizeManager) {
    List<AppResult> results = AppResult.find.select("*")
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS, "*")
        .fetch(AppResult.TABLE.APP_HEURISTIC_RESULTS + "." + AppHeuristicResult.TABLE.APP_HEURISTIC_RESULT_DETAILS, "*")
        .where()
        .eq(AppResult.TABLE.FLOW_EXEC_ID, jobExecution.flowExecution.flowExecId)
        .eq(AppResult.TABLE.JOB_EXEC_ID, jobExecution.jobExecId)
        .findList();
    assertTrue(" Apps for Jobs ", results.size() > 0);
    Map<String, Map<String, Double>> usageData = optimizeManager.extractParameterInformation(results);
    testMapData(usageData);
    testReduceData(usageData);
  }

  private void testMapData(Map<String, Map<String, Double>> usageData) {
    assertTrue(" Usage data ",
        usageData.get("map").get(CommonConstantsHeuristic.UtilizedParameterKeys.MAX_PHYSICAL_MEMORY.getValue()) == 595);
    assertTrue(" Usage data ", usageData.get("map")
        .get(CommonConstantsHeuristic.UtilizedParameterKeys.MAX_TOTAL_COMMITTED_HEAP_USAGE_MEMORY.getValue()) == 427);
    assertTrue(" Usage data ",
        usageData.get("map").get(CommonConstantsHeuristic.UtilizedParameterKeys.MAX_VIRTUAL_MEMORY.getValue()) == 2200);
  }

  private void testReduceData(Map<String, Map<String, Double>> usageData) {
    assertTrue(" Usage data ",
        usageData.get("reduce").get(CommonConstantsHeuristic.UtilizedParameterKeys.MAX_PHYSICAL_MEMORY.getValue())
            == 497);
    assertTrue(" Usage data ", usageData.get("reduce")
        .get(CommonConstantsHeuristic.UtilizedParameterKeys.MAX_TOTAL_COMMITTED_HEAP_USAGE_MEMORY.getValue()) == 300);
    assertTrue(" Usage data ",
        usageData.get("reduce").get(CommonConstantsHeuristic.UtilizedParameterKeys.MAX_VIRTUAL_MEMORY.getValue())
            == 2100);
  }

  private void testIPSOParameterOptimizer(JobExecution jobExecution, AutoTuningOptimizeManager optimizeManager) {
    optimizeManager.parameterOptimizer(jobExecution.job.id);
    List<TuningParameterConstraint> parameterConstraints = TuningParameterConstraint.find.where().
        eq("job_definition_id", 100003).findList();
    for (TuningParameterConstraint parameterConstraint : parameterConstraints) {
      testMapParameterBoundries(parameterConstraint);
      testReduceParameterBoundries(parameterConstraint);
    }
  }

  private void testMapParameterBoundries(TuningParameterConstraint parameterConstraint) {
    if (parameterConstraint.tuningParameter.paramName.equals(
        CommonConstantsHeuristic.ParameterKeys.MAPPER_MEMORY_HADOOP_CONF.getValue())) {
      assertTrue("Mapper Memory Lower Bound ", parameterConstraint.lowerBound == 2048.0);
      assertTrue("Mapper Memory Upper Bound ", parameterConstraint.upperBound == 2048.0);
    }
    if (parameterConstraint.tuningParameter.paramName.equals(
        CommonConstantsHeuristic.ParameterKeys.MAPPER_HEAP_HADOOP_CONF.getValue())) {
      assertTrue("Mapper Heap Memory Lower Bound ", parameterConstraint.lowerBound == 427.0);
      assertTrue("Mapper Heap Memory Upper Bound ", parameterConstraint.upperBound == 512.4);
    }
  }

  private void testReduceParameterBoundries(TuningParameterConstraint parameterConstraint) {
    if (parameterConstraint.tuningParameter.paramName.equals(
        CommonConstantsHeuristic.ParameterKeys.REDUCER_MEMORY_HADOOP_CONF.getValue())) {
      assertTrue("Mapper Memory Lower Bound ", parameterConstraint.lowerBound == 1024.0);
      assertTrue("Mapper Memory Upper Bound ", parameterConstraint.upperBound == 2048.0);
    }
    if (parameterConstraint.tuningParameter.paramName.equals(
        CommonConstantsHeuristic.ParameterKeys.REDUCER_HEAP_HADOOP_CONF.getValue())) {
      assertTrue("Reducer Heap Memory Lower Bound ", parameterConstraint.lowerBound == 300.0);
      assertTrue("Reducer Heap Memory Upper Bound ", parameterConstraint.upperBound == 360.0);
    }
  }

  private void testIPSOApplyIntelligenceOnParameter(TuningJobDefinition tuningJobDefinition,
      JobDefinition jobDefinition, AutoTuningOptimizeManager optimizeManager) {
    List<TuningParameter> tuningParameterList = TuningParameter.find.where()
        .eq(TuningParameter.TABLE.tuningAlgorithm + "." + TuningAlgorithm.TABLE.id,
            tuningJobDefinition.tuningAlgorithm.id)
        .eq(TuningParameter.TABLE.isDerived, 0)
        .findList();
    optimizeManager.applyIntelligenceOnParameter(tuningParameterList, jobDefinition);
    for (TuningParameter tuningParameter : tuningParameterList) {
      testMapMinMaxValue(tuningParameter);
      testReduceMinMaxValue(tuningParameter);
      testNonIPSOParameter(tuningParameter);
    }
  }

  private void testMapMinMaxValue(TuningParameter tuningParameter) {
    if (tuningParameter.paramName.equals(CommonConstantsHeuristic.ParameterKeys.MAPPER_MEMORY_HADOOP_CONF.getValue())) {
      assertTrue("Mapper Memory Lower Bound ", tuningParameter.minValue == 2048.0);
      assertTrue("Mapper Memory Upper Bound ", tuningParameter.maxValue == 2048.0);
    }
    if (tuningParameter.paramName.equals(CommonConstantsHeuristic.ParameterKeys.MAPPER_HEAP_HADOOP_CONF.getValue())) {
      assertTrue("Mapper Heap Memory Lower Bound ", tuningParameter.minValue == 427.0);
      assertTrue("Mapper Heap Memory Upper Bound ", tuningParameter.maxValue == 512.4);
    }
  }

  private void testReduceMinMaxValue(TuningParameter tuningParameter) {
    if (tuningParameter.paramName.equals(
        CommonConstantsHeuristic.ParameterKeys.REDUCER_MEMORY_HADOOP_CONF.getValue())) {
      assertTrue("Mapper Memory Lower Bound ", tuningParameter.minValue == 1024.0);
      assertTrue("Mapper Memory Upper Bound ", tuningParameter.maxValue == 2048.0);
    }
    if (tuningParameter.paramName.equals(CommonConstantsHeuristic.ParameterKeys.REDUCER_HEAP_HADOOP_CONF.getValue())) {
      assertTrue("Reducer Heap Memory Lower Bound ", tuningParameter.minValue == 300.0);
      assertTrue("Reducer Heap Memory Upper Bound ", tuningParameter.maxValue == 360.0);
    }
  }

  private void testNonIPSOParameter(TuningParameter tuningParameter) {
    if (tuningParameter.paramName.equals("mapreduce.task.io.sort.factor")) {
      assertTrue("Task Sort Factor Min Value ", tuningParameter.minValue == 10.0);
      assertTrue("Task Sort Factor Max Value ", tuningParameter.maxValue == 150.0);
    }
  }

  private void testIPSONumberOfConstraintsViolated(AutoTuningOptimizeManager optimizeManager) {
    JobSuggestedParamValue jobSuggestedParamValue = new JobSuggestedParamValue();
    jobSuggestedParamValue.tuningParameter = TuningParameter.find.byId(11);
    jobSuggestedParamValue.paramValue = 2048.0;
    JobSuggestedParamValue jobSuggestedParamValue1 = new JobSuggestedParamValue();
    jobSuggestedParamValue1.tuningParameter = TuningParameter.find.byId(16);
    jobSuggestedParamValue1.paramValue = 2048.0;
    List<JobSuggestedParamValue> jobSuggestedParamValueList = new ArrayList<JobSuggestedParamValue>();
    jobSuggestedParamValueList.add(jobSuggestedParamValue);
    jobSuggestedParamValueList.add(jobSuggestedParamValue1);
    int violations = optimizeManager.numberOfConstraintsViolated(jobSuggestedParamValueList);
    assertTrue("Parameter constraint violeted " + violations, violations > 0);
    jobSuggestedParamValueList.clear();
    jobSuggestedParamValue1.paramValue = 1024.0;
    jobSuggestedParamValueList.add(jobSuggestedParamValue);
    jobSuggestedParamValueList.add(jobSuggestedParamValue1);
    violations = optimizeManager.numberOfConstraintsViolated(jobSuggestedParamValueList);
    assertTrue("Parameter constraint violeted " + violations, violations == 0);
  }
}
