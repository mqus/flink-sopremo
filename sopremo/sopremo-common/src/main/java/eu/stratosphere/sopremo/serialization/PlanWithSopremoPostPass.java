/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
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
package eu.stratosphere.sopremo.serialization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.flink.api.common.Plan;
import org.apache.flink.api.common.operators.Operator;
import org.apache.flink.api.common.operators.base.GenericDataSinkBase;

import com.google.common.collect.Iterables;

import eu.stratosphere.sopremo.io.SopremoOperatorInfoHelper;
import eu.stratosphere.sopremo.packages.ITypeRegistry;
import eu.stratosphere.util.IdentitySet;
import eu.stratosphere.util.dag.OneTimeTraverser;

/**
 */
public final class PlanWithSopremoPostPass extends Plan {
	private final SopremoRecordLayout layout;

	private final ITypeRegistry typeRegistry;

	/**
	 * 
	 */
	private static final String CONNECTING_OUTPUT = "__dummy_connecting_sink";

	/**
	 * Initializes PlanWithSopremoPostPass.
	 * 
	 * @param sinks
	 */
	public PlanWithSopremoPostPass(final SopremoRecordLayout layout, final ITypeRegistry typeRegistry,
			final Collection<GenericDataSinkBase<SopremoRecord>> sinks) {
		super(connectUnconnectedDataflows(sinks));
		if (layout == null)
			throw new NullPointerException();
		this.layout = layout;
		this.typeRegistry = typeRegistry;
	}

	private static Collection<GenericDataSinkBase<SopremoRecord>> connectUnconnectedDataflows(Collection<GenericDataSinkBase<SopremoRecord>> sinks) {
		if (sinks.size() > 1) {
			UnconnectedComponentConnector unconnectedComponentConnector = new UnconnectedComponentConnector();
			unconnectedComponentConnector.process(new ArrayList<GenericDataSinkBase<SopremoRecord>>(sinks));
			return unconnectedComponentConnector.connect();
		}
		return sinks;
	}

	private static class UnconnectedComponentConnector {

		final Map<GenericDataSinkBase<SopremoRecord>, Set<GenericDataSinkBase<SopremoRecord>>> connectedSinks =
			new IdentityHashMap<GenericDataSinkBase<SopremoRecord>, Set<GenericDataSinkBase<SopremoRecord>>>();

		final Map<Set<GenericDataSinkBase<SopremoRecord>>, Set<Operator<?>>> reachableOperators = new IdentityHashMap<Set<GenericDataSinkBase<SopremoRecord>>, Set<Operator<?>>>();

		public void process(List<GenericDataSinkBase<SopremoRecord>> sinkList) {
			final GenericDataSinkBase<SopremoRecord> firstSink = sinkList.get(0);
			this.addFirst(firstSink);

			for (int sinkIndex = 1; sinkIndex < sinkList.size(); sinkIndex++) {
				final GenericDataSinkBase<SopremoRecord> nextSink = sinkList.get(sinkIndex);
				this.addNext(nextSink);

			}
		}

		public Collection<GenericDataSinkBase<SopremoRecord>> connect() {
			IdentitySet<Set<GenericDataSinkBase<SopremoRecord>>> clusters = new IdentitySet<Set<GenericDataSinkBase<SopremoRecord>>>(this.connectedSinks.values());
			if (clusters.size() == 1)
				return clusters.iterator().next();

			final GenericDataSinkBase<SopremoRecord> connectingSink = new GenericDataSinkBase<SopremoRecord>(new DevNullOutputFormat(), SopremoOperatorInfoHelper.sink(), CONNECTING_OUTPUT);
			final List<GenericDataSinkBase<SopremoRecord>> allSinks = new ArrayList<GenericDataSinkBase<SopremoRecord>>();
			final List<Operator<SopremoRecord>> clusterRepresentative = new ArrayList<Operator<SopremoRecord>>();
			for (Set<GenericDataSinkBase<SopremoRecord>> cluster : clusters) {
				allSinks.addAll(cluster);
				// add any node of each cluster to the connecting sink
				clusterRepresentative.add(cluster.iterator().next().getInput());
			}
			connectingSink.setInput(Operator.createUnionCascade(clusterRepresentative));
			allSinks.add(connectingSink);
			return allSinks;
		}

		private void addNext(GenericDataSinkBase<SopremoRecord> nextSink) {
			IdentitySet<Operator<?>> reachables = new IdentitySet<Operator<?>>();
			Iterables.addAll(reachables, OneTimeTraverser.INSTANCE.getReachableNodes(Arrays.asList(nextSink),
				eu.stratosphere.pact.common.plan.OperatorNavigator.INSTANCE));

			for (Entry<Set<GenericDataSinkBase<SopremoRecord>>, Set<Operator<?>>> operatorEntry : this.reachableOperators.entrySet())
				if (reachables.removeAll(operatorEntry.getValue())) {
					// some overlap, add sink to cluster with new operators
					Set<GenericDataSinkBase<SopremoRecord>> sinkCluster = operatorEntry.getKey();
					sinkCluster.add(nextSink);
					this.connectedSinks.put(nextSink, sinkCluster);
					operatorEntry.getValue().addAll(reachables);
					return;
				}

			final IdentitySet<GenericDataSinkBase<SopremoRecord>> newCluster = new IdentitySet<GenericDataSinkBase<SopremoRecord>>(Collections.singleton(nextSink));
			this.connectedSinks.put(nextSink, newCluster);
			this.reachableOperators.put(newCluster, reachables);
		}

		private void addFirst(final GenericDataSinkBase<SopremoRecord> firstSink) {
			final IdentitySet<GenericDataSinkBase<SopremoRecord>> firstSet = new IdentitySet<GenericDataSinkBase<SopremoRecord>>(Collections.singleton(firstSink));
			this.connectedSinks.put(firstSink, firstSet);
			IdentitySet<Operator<?>> firstReachables = new IdentitySet<Operator<?>>();
			this.reachableOperators.put(firstSet, firstReachables);
			Iterables.addAll(firstReachables, OneTimeTraverser.INSTANCE.getReachableNodes(Arrays.asList(firstSink),
				eu.stratosphere.pact.common.plan.OperatorNavigator.INSTANCE));
		}
	}

	/**
	 * Returns the layout.
	 * 
	 * @return the layout
	 */
	public SopremoRecordLayout getLayout() {
		return this.layout;
	}

	/**
	 * Returns the typeRegistry.
	 * 
	 * @return the typeRegistry
	 */
	public ITypeRegistry getTypeRegistry() {
		return this.typeRegistry;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.api.plan.Plan#getPostPassClassName()
	 */
	@Override
	public String getPostPassClassName() {
		return SopremoRecordPostPass.class.getName();
	}
}