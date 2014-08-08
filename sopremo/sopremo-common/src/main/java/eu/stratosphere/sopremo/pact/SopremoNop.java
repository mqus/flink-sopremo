package eu.stratosphere.sopremo.pact;

import eu.stratosphere.api.common.functions.AbstractFunction;
import eu.stratosphere.api.common.functions.GenericMap;
import eu.stratosphere.sopremo.serialization.SopremoRecord;
import eu.stratosphere.util.Collector;

public class SopremoNop extends AbstractFunction implements GenericMap<SopremoRecord, SopremoRecord> {
	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.api.common.functions.GenericMap#map(java.lang.Object)
	 */
	@Override
	public SopremoRecord map(SopremoRecord record) throws Exception {
		return record;
	}
}