/***********************************************************************************************************************
 *
 * Copyright (C) 2010-2013 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/
package eu.stratosphere.sopremo.server;

import java.net.InetSocketAddress;
import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.Plan;
import org.apache.flink.api.common.typeutils.TypeSerializerFactory;
import org.apache.flink.compiler.PactCompiler;
import org.apache.flink.compiler.plan.OptimizedPlan;
import org.apache.flink.compiler.plantranslate.NepheleJobGraphGenerator;
import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.Path;
import org.apache.flink.runtime.client.JobClient;
import org.apache.flink.runtime.execution.librarycache.LibraryCacheManager;
import org.apache.flink.runtime.jobgraph.AbstractJobVertex;
import org.apache.flink.runtime.jobgraph.JobEdge;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.operators.util.TaskConfig;
import org.apache.flink.util.StringUtils;

import eu.stratosphere.sopremo.execution.ExecutionResponse.ExecutionState;
import eu.stratosphere.sopremo.io.Sink;
import eu.stratosphere.sopremo.operator.Operator;
import eu.stratosphere.sopremo.operator.SopremoPlan;
import eu.stratosphere.sopremo.pact.SopremoUtil;

/**
 */
public class SopremoExecutionThread implements Runnable {
	private final SopremoJobInfo jobInfo;

	private final InetSocketAddress jobManagerAddress;

	private int numPacts, numVertices;

	/**
	 * The logging object used for debugging.
	 */
	private static final Log LOG = LogFactory.getLog(SopremoExecutionThread.class);

	public SopremoExecutionThread(final SopremoJobInfo environment, final InetSocketAddress jobManagerAddress) {
		this.jobInfo = environment;
		this.jobManagerAddress = jobManagerAddress;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		this.processPlan();
	}

	JobGraph getJobGraph(final Plan pactPlan, Configuration jobConfig) {
		final PactCompiler compiler = new PactCompiler();

		final int dop = jobConfig.getInteger(ConfigConstants.DEFAULT_PARALLELIZATION_DEGREE_KEY, -1);
		if (dop != -1)
			compiler.setDefaultDegreeOfParallelism(dop);
		// final int intra = jobConfig.getInteger(ConfigConstants.PARALLELIZATION_MAX_INTRA_NODE_DEGREE_KEY, -1);
		// if (intra != -1)
		// compiler.setMaxIntraNodeParallelism(intra);
		// final int machines = jobConfig.getInteger(SopremoJobInfo.MAX_MACHINES, -1);
		// if (machines != -1)
		// compiler.setMaxMachines(machines);

		final OptimizedPlan optPlan = compiler.compile(pactPlan);
		this.numPacts = optPlan.getAllNodes().size();
		final NepheleJobGraphGenerator gen = new NepheleJobGraphGenerator();
		JobGraph jobGraph = gen.compileJobGraph(optPlan);
		if (SopremoUtil.DEBUG)
			checkForConsistency(jobGraph);
		this.numVertices = jobGraph.getAllReachableJobVertices().length;
		LOG.info(String.format("Plan of job %s contains %d pacts and %d vertices", this.jobInfo.getJobId(), this.numPacts, this.numVertices));
		return jobGraph;
	}

	/**
	 * @param jobGraph
	 */
	private void checkForConsistency(JobGraph jobGraph) {
		for (AbstractJobVertex vertex : jobGraph.getAllJobVertices()) {
			if (vertex.getInvokableClass() == org.apache.flink.runtime.iterative.task.IterationTailPactTask.class
				|| vertex.getInvokableClass() == org.apache.flink.runtime.iterative.task.IterationHeadPactTask.class)
				continue; // ignore for now

			for (int outputIndex = 0; outputIndex < vertex.getNumberOfForwardConnections(); outputIndex++) {
				JobEdge connection = vertex.getForwardConnection(outputIndex);
				TaskConfig sourceConfig = new TaskConfig(vertex.getConfiguration());
				TaskConfig targetConfig = new TaskConfig(connection.getConnectedVertex().getConfiguration());

				int chainedTasks = sourceConfig.getNumberOfChainedStubs();
				if (chainedTasks > 0) {
					sourceConfig = sourceConfig.getChainedStubConfig(chainedTasks - 1);
				}
				// is there a need to verify connections between chained tasks? can we even do that?
				// for (int taskIndex = 0; taskIndex < chainedTasks; taskIndex++) {
				// TaskConfig chainConfig = sourceConfig.getChainedStubConfig(chainedTasks);
				// checkForConsistency(chainConfig, chainConfig, 0);
				// sourceConfig = chainConfig;
				// }

				checkForConsistency(sourceConfig, targetConfig, getInputIndex(targetConfig, connection));
			}
		}
	}

	private void checkForConsistency(TaskConfig sourceConfig, TaskConfig targetConfig, int inputIndex) {
		TypeSerializerFactory<?> sourceSerializer = sourceConfig.getOutputSerializer(ClassLoader.getSystemClassLoader());
		TypeSerializerFactory<?> targetSerializer =
			targetConfig.getInputSerializer(inputIndex, ClassLoader.getSystemClassLoader());

		if (!sourceSerializer.equals(targetSerializer))
			throw new IllegalStateException(String.format(
				"Source %s not correctly connected to target %s:\nSource serializer: %s\nTarget serializer: %s",
				sourceConfig.getTaskName(),
				targetConfig.getTaskName(),
				sourceSerializer,
				targetSerializer));
	}

	/**
	 * @param connection
	 * @return
	 */
	private int getInputIndex(TaskConfig targetConfig, JobEdge connection) {
		int gateIndex = connection.getIndexOfInputGate(), groupSize;
		for (int groupIndex = 0, totalIndex = 0; (groupSize = targetConfig.getGroupSize(groupIndex)) != -1; groupIndex++, totalIndex +=
			groupSize) {
			if (gateIndex < totalIndex + groupSize)
				return groupIndex;
		}
		throw new IllegalStateException("Cannot find input index");
	}

	private JobExecutionResult executePlan(final SopremoPlan plan, Configuration jobConfig) {
		final Plan pactPlan = plan.asPactPlan();

		JobGraph jobGraph;
		try {
			jobGraph = this.getJobGraph(pactPlan, jobConfig);
		} catch (final Exception e) {
			LOG.error("Could not generate job graph " + this.jobInfo.getJobId(), e);
			this.jobInfo.setStatusAndDetail(ExecutionState.ERROR, "Could not generate job graph: "
				+ StringUtils.stringifyException(e));
			return null;
		}

		try {
			for (final String requiredPackage : this.jobInfo.getInitialRequest().getQuery().getRequiredPackages()) {
				final Path libPath = LibraryCacheManager.contains(requiredPackage);
				if (libPath == null) {
					LOG.error("Could not find associated packages " + requiredPackage + " of job " +
						this.jobInfo.getJobId());
					this.jobInfo.setStatusAndDetail(ExecutionState.ERROR, "Could not find associated packages: " +
						requiredPackage);
					return null;
				}
				jobGraph.addJar(libPath);
			}
		} catch (final Exception e) {
			LOG.error("Could not find retrieve package information from library manager " + this.jobInfo.getJobId(), e);
			this.jobInfo.setStatusAndDetail(ExecutionState.ERROR,
				"Could not find retrieve package information from library manager: "
					+ StringUtils.stringifyException(e));
			return null;
		}

		JobClient client;
		try {
			// client = new JobClient(jobGraph, this.jobInfo.getConfiguration(), this.jobManagerAddress);
			// TODO: workaround for core#349
			final Configuration configuration = new Configuration();
			configuration.addAll(this.jobInfo.getConfiguration());
			configuration.setString(ConfigConstants.JOB_MANAGER_IPC_ADDRESS_KEY, this.jobManagerAddress.getHostName());
			configuration.setInteger(ConfigConstants.JOB_MANAGER_IPC_PORT_KEY, this.jobManagerAddress.getPort());
			client = new JobClient(jobGraph, configuration);
		} catch (final Exception e) {
			LOG.error("Could not open job manager " + this.jobInfo.getJobId(), e);
			this.jobInfo.setStatusAndDetail(ExecutionState.ERROR, "Could not open job manager: "
				+ StringUtils.stringifyException(e));
			return null;
		}

		try {
			this.jobInfo.setJobClient(client);
			this.jobInfo.setStatusAndDetail(ExecutionState.RUNNING, "");
			return client.submitJobAndWait();
		} catch (final Exception e) {
			LOG.error("The job was not successfully executed " + this.jobInfo.getJobId(), e);
			this.jobInfo.setStatusAndDetail(ExecutionState.ERROR,
				"The job was not successfully executed: "
					+ StringUtils.stringifyException(e));
			return null;
		}
	}

	/**
	 * @param plan
	 */
	private void gatherStatistics(final SopremoPlan plan, final JobExecutionResult result) {
		final StringBuilder statistics = new StringBuilder();
		statistics.append("Executed in ").append(result.getNetRuntime()).append(" ms");
		statistics.append(this.numPacts).append(" Pacts");
		statistics.append(this.numVertices).append(" Nephele vertices");
		for (final Operator<?> op : plan.getContainedOperators())
			if (op instanceof Sink)
				try {
					final String path = ((Sink) op).getOutputPath();
					final long length = FileSystem.get(new URI(path)).getFileStatus(new Path(path)).getLen();
					statistics.append("\n").append("Sink ").append(path).append(": ").append(length).append(" B");
				} catch (final Exception e) {
					LOG.warn("While gathering statistics", e);
				}
		this.jobInfo.setStatusAndDetail(ExecutionState.FINISHED, statistics.toString());
	}

	private void processPlan() {
		try {
			LOG.info("Starting job " + this.jobInfo.getJobId());
			final SopremoPlan plan = this.jobInfo.getInitialRequest().getQuery();
			final JobExecutionResult runtime =
				this.executePlan(plan, this.jobInfo.getInitialRequest().getConfiguration());
			if (runtime != null) {
				switch (this.jobInfo.getInitialRequest().getMode()) {
				case RUN:
					this.jobInfo.setStatusAndDetail(ExecutionState.FINISHED, "");
					break;
				case RUN_WITH_STATISTICS:
					this.gatherStatistics(plan, runtime);
					break;
				}
				LOG.info(String.format("Finished job %s in %s ms", this.jobInfo.getJobId(), runtime.getNetRuntime()));
			}
		} catch (final Throwable ex) {
			LOG.error("Cannot process plan " + this.jobInfo.getJobId(), ex);
			this.jobInfo.setStatusAndDetail(ExecutionState.ERROR,
				"Cannot process plan: " + StringUtils.stringifyException(ex));
		}
	}

}
