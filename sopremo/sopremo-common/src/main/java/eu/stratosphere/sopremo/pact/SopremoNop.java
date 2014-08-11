package eu.stratosphere.sopremo.pact;

import org.apache.flink.api.common.functions.MapFunction;

import eu.stratosphere.sopremo.serialization.SopremoRecord;

public class SopremoNop implements MapFunction<SopremoRecord, SopremoRecord> {
	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.api.common.functions.GenericMap#map(java.lang.Object)
	 */
	@Override
	public SopremoRecord map(SopremoRecord record) throws Exception {
		return record;
	}
}