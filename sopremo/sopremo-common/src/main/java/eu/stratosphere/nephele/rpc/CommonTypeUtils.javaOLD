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

package eu.stratosphere.nephele.rpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.flink.core.fs.Path;
import org.apache.flink.runtime.client.AbstractJobResult;
import org.apache.flink.runtime.client.JobCancelResult;
import org.apache.flink.runtime.client.JobProgressResult;
import org.apache.flink.runtime.client.JobSubmissionResult;
import org.apache.flink.runtime.event.job.JobEvent;
import org.apache.flink.runtime.event.job.VertexEvent;
import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.execution.librarycache.LibraryCacheProfileRequest;
import org.apache.flink.runtime.execution.librarycache.LibraryCacheProfileResponse;
import org.apache.flink.runtime.execution.librarycache.LibraryCacheUpdate;
import org.apache.flink.runtime.io.network.channels.ChannelID;
import org.apache.flink.runtime.io.network.channels.ChannelType;
import org.apache.flink.runtime.io.network.gates.GateID;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobID;
import org.apache.flink.runtime.jobgraph.JobStatus;
import org.apache.flink.runtime.jobgraph.JobVertexID;

/**
 * This utility class provides a list of types frequently used by the RPC protocols included in this package.
 */
public class CommonTypeUtils {

	/**
	 * Private constructor to prevent instantiation.
	 */
	private CommonTypeUtils() {
	}

	/**
	 * Returns a list of types frequently used by the RPC protocols of this package and its parent packages.
	 * 
	 * @return a list of types frequently used by the RPC protocols of this package
	 */
	public static List<Class<?>> getRPCTypesToRegister() {

		final ArrayList<Class<?>> types = new ArrayList<Class<?>>();

		types.add(AbstractJobResult.class);
		types.add(AbstractJobResult.ReturnCode.class);
		types.add(ChannelID.class);
		types.add(ChannelType.class);
		types.add(ExecutionState.class);
		types.add(GateID.class);
		types.add(HashMap.class);
		types.add(JobCancelResult.class);
		types.add(JobEvent.class);
		types.add(JobGraph.class);
		types.add(JobID.class);
		types.add(JobProgressResult.class);
		types.add(JobStatus.class);
		types.add(JobSubmissionResult.class);
		types.add(JobVertexID.class);
		types.add(LibraryCacheProfileRequest.class);
		types.add(LibraryCacheProfileResponse.class);
		types.add(LibraryCacheUpdate.class);
		types.add(Path.class);
		types.add(Set.class);
		types.add(VertexEvent.class);

		return types;
	}
}
