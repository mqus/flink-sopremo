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

package eu.stratosphere.pact.common;

import eu.stratosphere.api.common.functions.AbstractFunction;
import eu.stratosphere.api.common.functions.GenericMap;
import eu.stratosphere.configuration.Configuration;
import eu.stratosphere.sopremo.serialization.SopremoRecord;

/**
 * Trivial PACT stub which emits the pairs without modifications.
 */
public class IdentityMap extends AbstractFunction implements GenericMap<SopremoRecord, SopremoRecord> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1791753394604966098L;

	private transient SopremoRecord writeRecord;

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.api.common.functions.AbstractFunction#open(eu.stratosphere.configuration.Configuration)
	 */
	@Override
	public void open(Configuration parameters) throws Exception {
		super.open(parameters);
		this.writeRecord = new SopremoRecord();
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.api.common.functions.GenericMap#map(java.lang.Object)
	 */
	@Override
	public SopremoRecord map(SopremoRecord record) throws Exception {
		this.writeRecord.setNode(record.getNode());
		return this.writeRecord;
	}
}
