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
package eu.stratosphere.sopremo.execution;

import java.io.IOException;

import org.apache.flink.core.io.IOReadableWritable;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

/**
 * A response from a {@link SopremoExecutionProtocol} that reflects the state of a job.
 */
public class ExecutionResponse implements IOReadableWritable {

	private ExecutionState state;

	private String details;

	private SopremoID jobId;

	/**
	 * Needed for deserialization.
	 */
	public ExecutionResponse() {
	}

	/**
	 * Initializes ExecutionResponse with the given job id, state, and response.
	 * 
	 * @param jobId
	 *        the id of the jbo
	 * @param state
	 *        the current state
	 * @param response
	 *        a detailed response (optional)
	 */
	public ExecutionResponse(final SopremoID jobId, final ExecutionState state, final String response) {
		this.jobId = jobId;
		this.state = state;
		this.details = response;
	}

	/**
	 * Returns the response.
	 * 
	 * @return the response
	 */
	public String getDetails() {
		return this.details;
	}

	/**
	 * Returns the jobId.
	 * 
	 * @return the jobId
	 */
	public SopremoID getJobId() {
		return this.jobId;
	}

	/**
	 * Returns the state.
	 * 
	 * @return the state
	 */
	public ExecutionState getState() {
		return this.state;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.core.io.IOReadableWritable#read(java.io.DataInput)
	 */
	@Override
	public void read(final DataInputView in) throws IOException {
		this.jobId = new SopremoID();
		this.jobId.read(in);
		this.state = ExecutionState.values()[in.readInt()];
		this.details = in.readUTF();
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.core.io.IOReadableWritable#write(java.io.DataOutput)
	 */
	@Override
	public void write(final DataOutputView out) throws IOException {
		this.jobId.write(out);
		out.writeInt(this.state.ordinal());
		out.writeUTF(this.details);
	}

	public static enum ExecutionState {
		SETUP, ENQUEUED, RUNNING, FINISHED, ERROR;
	}

}
