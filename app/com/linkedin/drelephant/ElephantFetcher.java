package com.linkedin.drelephant;

import com.linkedin.drelephant.hadoop.HadoopJobData;
import org.apache.hadoop.security.authentication.client.AuthenticationException;

import java.io.IOException;
import java.util.List;


public interface ElephantFetcher {

  public List<HadoopJobData> fetchJobList() throws IOException, AuthenticationException;

  public void fetchJobData(HadoopJobData job_data) throws IOException, AuthenticationException;

  public void finishJob(HadoopJobData job_data, boolean success);

}