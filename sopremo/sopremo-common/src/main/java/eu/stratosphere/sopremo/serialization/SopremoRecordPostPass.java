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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.flink.api.common.distributions.DataDistribution;
import org.apache.flink.api.common.operators.DualInputOperator;
import org.apache.flink.api.common.operators.Order;
import org.apache.flink.api.common.operators.Ordering;
import org.apache.flink.api.common.operators.SingleInputOperator;
import org.apache.flink.api.common.operators.base.CoGroupOperatorBase;
import org.apache.flink.api.common.operators.base.GenericDataSinkBase;
import org.apache.flink.api.common.operators.base.GroupReduceOperatorBase;
import org.apache.flink.api.common.operators.base.MapOperatorBase;
import org.apache.flink.api.common.operators.util.FieldList;
import org.apache.flink.api.common.typeutils.TypeComparatorFactory;
import org.apache.flink.api.common.typeutils.TypePairComparatorFactory;
import org.apache.flink.api.common.typeutils.TypeSerializerFactory;
import org.apache.flink.compiler.CompilerException;
import org.apache.flink.compiler.CompilerPostPassException;
import org.apache.flink.compiler.dag.MapNode;
import org.apache.flink.compiler.dag.TempMode;
import org.apache.flink.compiler.dataproperties.GlobalProperties;
import org.apache.flink.compiler.dataproperties.LocalProperties;
import org.apache.flink.compiler.dataproperties.RequestedGlobalProperties;
import org.apache.flink.compiler.dataproperties.RequestedLocalProperties;
import org.apache.flink.compiler.operators.MapDescriptor;
import org.apache.flink.compiler.plan.Channel;
import org.apache.flink.compiler.plan.DualInputPlanNode;
import org.apache.flink.compiler.plan.NAryUnionPlanNode;
import org.apache.flink.compiler.plan.OptimizedPlan;
import org.apache.flink.compiler.plan.PlanNode;
import org.apache.flink.compiler.plan.SingleInputPlanNode;
import org.apache.flink.compiler.plan.SinkPlanNode;
import org.apache.flink.compiler.plan.SourcePlanNode;
import org.apache.flink.compiler.plan.WorksetIterationPlanNode;
import org.apache.flink.compiler.postpass.ConflictingFieldTypeInfoException;
import org.apache.flink.compiler.postpass.GenericFlatTypePostPass;
import org.apache.flink.compiler.postpass.MissingFieldTypeInfoException;
import org.apache.flink.runtime.operators.shipping.ShipStrategyType;
import org.apache.flink.runtime.operators.util.LocalStrategy;
import org.apache.flink.util.Visitor;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import eu.stratosphere.pact.common.IdentityMap;
import eu.stratosphere.sopremo.io.SopremoOperatorInfoHelper;
import eu.stratosphere.sopremo.packages.ITypeRegistry;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.util.IdentitySet;
import eu.stratosphere.util.reflect.ReflectUtil;

/**
 */
public class SopremoRecordPostPass extends GenericFlatTypePostPass<Class<? extends IJsonNode>, SopremoRecordSchema> {

	private SopremoRecordLayout layout;

	private ITypeRegistry typeRegistry;

	public final static boolean PRUNE_LAYOUT = true;

	{
		this.setPropagateParentSchemaDown(false);
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * eu.stratosphere.compiler.postpass.GenericRecordPostPass#postPass(eu.stratosphere.compiler.dag.candidate
	 * .OptimizedPlan)
	 */
	@Override
	public void postPass(final OptimizedPlan plan) {
		// SchemaCollectingSopremoRecordPostPass schemaCollectingSopremoRecordPostPass = new
		// SchemaCollectingSopremoRecordPostPass();
		// schemaCollectingSopremoRecordPostPass.postPass(plan);
		final PlanWithSopremoPostPass planWithSopremoPostPass = (PlanWithSopremoPostPass) plan.getOriginalPactPlan();
		this.layout = planWithSopremoPostPass.getLayout();
		this.typeRegistry = planWithSopremoPostPass.getTypeRegistry();

		this.removeDummyNodes(plan);
		super.postPass(plan);
		if (PRUNE_LAYOUT)
			this.addIdentityMapsToOutputsWithMultipleChannels(plan);
	}

	//
	// /*
	// * (non-Javadoc)
	// * @see
	// * eu.stratosphere.compiler.postpass.GenericRecordPostPass#traverse(eu.stratosphere.compiler.dag.candidate
	// * .PlanNode, eu.stratosphere.compiler.postpass.AbstractSchema, boolean)
	// */
	// @Override
	// protected void traverse(final PlanNode node, final SopremoRecordSchema parentSchema, final boolean
	// createUtilities) {
	// // FIXME: workaround for Stratosphere #206
	// if (node instanceof SourcePlanNode)
	// ((SourcePlanNode) node).setSerializer(createSerializer(new SopremoRecordSchema()));
	// else if (node instanceof SinkPlanNode)
	// this.setOrdering(((SingleInputPlanNode) node).getInput(),
	// ((GenericDataSinkBase<?>) node.getPactContract()).getLocalOrder());
	// else if (node.getPactContract() instanceof SopremoReduceOperator)
	// this.setOrdering(((SingleInputPlanNode) node).getInput(),
	// ((SopremoReduceOperator) node.getPactContract()).getInnerGroupOrder());
	// else if (node.getPactContract() instanceof SopremoGroupReduceOperator)
	// this.setOrdering(((SingleInputPlanNode) node).getInput(),
	// ((SopremoGroupReduceOperator) node.getPactContract()).getInnerGroupOrder());
	// else if (node.getPactContract() instanceof SopremoCoGroupOperator) {
	// this.setOrdering(((DualInputPlanNode) node).getInput1(),
	// ((SopremoCoGroupOperator) node.getPactContract()).getFirstInnerGroupOrdering());
	// this.setOrdering(((DualInputPlanNode) node).getInput2(),
	// ((SopremoCoGroupOperator) node.getPactContract()).getSecondInnerGroupOrdering());
	// }
	// super.traverse(node, parentSchema, createUtilities);
	// }

	/*
	 * (non-Javadoc)
	 * @see
	 * eu.stratosphere.compiler.postpass.GenericRecordPostPass#createComparator(eu.stratosphere.util
	 * .FieldList, boolean[], eu.stratosphere.compiler.postpass.AbstractSchema)
	 */
	@Override
	protected TypeComparatorFactory<?> createComparator(final FieldList fields, final boolean[] directions,
			final SopremoRecordSchema schema) {
		if (PRUNE_LAYOUT) {
			final int[] usedKeys = schema.getUsedKeys().toIntArray();
			final int[] sortFields = fields.toArray();

			for (int index = 0; index < sortFields.length; index++)
				if (sortFields[index] != SopremoRecordLayout.VALUE_INDEX)
					sortFields[index] = Arrays.binarySearch(usedKeys, sortFields[index]);
			return new SopremoRecordComparatorFactory(this.layout.project(usedKeys), this.typeRegistry, sortFields, directions);
		}
		return new SopremoRecordComparatorFactory(this.layout, this.typeRegistry, fields.toArray(), directions);
	}

	private Set<PlanNode> visited = new IdentitySet<PlanNode>();

	/*
	 * (non-Javadoc)
	 * @see org.apache.flink.compiler.postpass.GenericFlatTypePostPass#traverse(org.apache.flink.compiler.plan.PlanNode,
	 * org.apache.flink.compiler.postpass.AbstractSchema, boolean)
	 */
	@Override
	protected void traverse(PlanNode node, SopremoRecordSchema parentSchema, boolean createUtilities) {
		if (!this.visited.add(node))
			return;
		if (node instanceof NAryUnionPlanNode) {
			// only propagate the info down
			// for (Channel channel : node.getInputs()) {
			// FieldList partitioningFields = node.getGlobalProperties().getPartitioningFields();
			// if (partitioningFields != null)
			// for (int i = 0; i < partitioningFields.size(); i++)
			// parentSchema.add(partitioningFields.get(i));
			// }

			FieldList groupedFields = node.getLocalProperties().getGroupedFields();
			if (groupedFields != null)
				for (int i = 0; i < groupedFields.size(); i++)
					parentSchema.add(groupedFields.get(i));

			FieldList partitioning = node.getGlobalProperties().getPartitioningFields();
			if (partitioning != null)
				for (int i = 0; i < partitioning.size(); i++)
					parentSchema.add(partitioning.get(i));
		}
		super.traverse(node, parentSchema, createUtilities);
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.compiler.postpass.GenericRecordPostPass#createEmptySchema()
	 */
	@Override
	protected SopremoRecordSchema createEmptySchema() {
		return new SopremoRecordSchema();
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * eu.stratosphere.compiler.postpass.GenericRecordPostPass#createPairComparator(eu.stratosphere.pact.common
	 * .util.FieldList, eu.stratosphere.util.FieldList, boolean[],
	 * eu.stratosphere.compiler.postpass.AbstractSchema, eu.stratosphere.compiler.postpass.AbstractSchema)
	 */
	@Override
	protected TypePairComparatorFactory<?, ?> createPairComparator(final FieldList fields1, final FieldList fields2,
			final boolean[] sortDirections,
			final SopremoRecordSchema schema1, final SopremoRecordSchema schema2) throws MissingFieldTypeInfoException {
		return new SopremoRecordPairComparatorFactory();
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * eu.stratosphere.compiler.postpass.GenericRecordPostPass#createSerializer(eu.stratosphere.compiler.postpass
	 * .AbstractSchema)
	 */
	@Override
	protected TypeSerializerFactory<?> createSerializer(final SopremoRecordSchema schema) {
		if (PRUNE_LAYOUT)
			return new SopremoRecordSerializerFactory(this.layout.project(schema.getUsedKeys().toIntArray()), this.typeRegistry);
		return new SopremoRecordSerializerFactory(this.layout, this.typeRegistry);
	}

	@Override
	protected void getDualInputNodeSchema(final DualInputPlanNode node, final SopremoRecordSchema input1Schema,
			final SopremoRecordSchema input2Schema)
	{
		// add the nodes local information. this automatically consistency checks
		final DualInputOperator<?, ?, ?, ?> contract = node.getTwoInputNode().getPactContract();

		// add the information to the schema
		FieldList groupedFields = node.getLocalProperties().getGroupedFields();
		if (groupedFields != null)
			for (int i = 0; i < groupedFields.size(); i++) {
				input1Schema.add(groupedFields.get(i));
				input2Schema.add(groupedFields.get(i));
			}

		FieldList partitioning = node.getGlobalProperties().getPartitioningFields();
		if (partitioning != null)
			for (int i = 0; i < partitioning.size(); i++) {
				input1Schema.add(partitioning.get(i));
				input2Schema.add(partitioning.get(i));
			}

		final int[] localPositions1 = contract.getKeyColumns(0);
		final int[] localPositions2 = contract.getKeyColumns(1);

		if (localPositions1.length != localPositions2.length)
			throw new CompilerException(
				"Error: The keys for the first and second input have a different number of fields.");

		for (int i = 0; i < localPositions1.length; i++)
			input1Schema.add(localPositions1[i]);
		for (int i = 0; i < localPositions2.length; i++)
			input2Schema.add(localPositions2[i]);

		// this is a temporary fix, we should solve this more generic
		if (contract instanceof CoGroupOperatorBase) {
			Ordering groupOrder1 = ((CoGroupOperatorBase<?, ?, ?, ?>) contract).getGroupOrderForInputOne();
			Ordering groupOrder2 = ((CoGroupOperatorBase<?, ?, ?, ?>) contract).getGroupOrderForInputTwo();

			if (groupOrder1 != null) {
				addOrderingToSchema(groupOrder1, input1Schema);
			}
			if (groupOrder2 != null) {
				addOrderingToSchema(groupOrder2, input2Schema);
			}
		}
	}

	@Override
	protected void getSingleInputNodeSchema(final SingleInputPlanNode node, final SopremoRecordSchema schema)
			throws CompilerPostPassException, ConflictingFieldTypeInfoException
	{

		// add the information to the schema
		FieldList groupedFields = node.getLocalProperties().getGroupedFields();
		if (groupedFields != null)
			for (int i = 0; i < groupedFields.size(); i++)
				schema.add(groupedFields.get(i));

		FieldList partitioning = node.getGlobalProperties().getPartitioningFields();
		if (partitioning != null)
			for (int i = 0; i < partitioning.size(); i++)
				schema.add(partitioning.get(i));

		SingleInputOperator<?, ?, ?> contract = node.getSingleInputNode().getPactContract();
		int[] keyColumns = contract.getKeyColumns(0);
		for (int index = 0; index < keyColumns.length; index++)
			schema.add(keyColumns[index]);

		// this is a temporary fix, we should solve this more generic
		if (contract instanceof GroupReduceOperatorBase) {
			Ordering groupOrder = ((GroupReduceOperatorBase<?, ?, ?>) contract).getGroupOrder();
			if (groupOrder != null) {
				addOrderingToSchema(groupOrder, schema);
			}
		}
	}

	@Override
	protected void getSinkSchema(final SinkPlanNode sinkPlanNode, final SopremoRecordSchema schema)
			throws CompilerPostPassException {
		final GenericDataSinkBase<?> sink = sinkPlanNode.getSinkNode().getPactContract();
		final Ordering partitioning = sink.getPartitionOrdering();
		final Ordering sorting = sink.getLocalOrder();

		if (partitioning != null)
			this.addOrderingToSchema(partitioning, schema);
		if (sorting != null)
			this.addOrderingToSchema(sorting, schema);
	}

	/**
	 * @param node
	 * @param outgoingChannels
	 * @param channelsToBeChanged
	 */
	private PlanNode addDummyNode(PlanNode node, List<Channel> outgoingChannels, List<Channel> channelsToBeChanged,
			SopremoRecordLayout layout) {
		ITypeRegistry registry = ((SopremoRecordSerializerFactory) outgoingChannels.get(0).getSerializer()).getTypeRegistry();
		Channel inMemoryChannel = new Channel(node);
		inMemoryChannel.setShipStrategy(ShipStrategyType.FORWARD);
		inMemoryChannel.setLocalStrategy(LocalStrategy.NONE);

		MapDescriptor mapDescriptor = new MapDescriptor();
		MapNode mapNode =
			new MapNode(new MapOperatorBase<SopremoRecord, SopremoRecord, IdentityMap>(new IdentityMap(),
				SopremoOperatorInfoHelper.unary(), "dummy " + node));
		mapNode.setDegreeOfParallelism(node.getDegreeOfParallelism());
		SingleInputPlanNode dummyNode = mapDescriptor.instantiate(inMemoryChannel, mapNode);
		inMemoryChannel.setTarget(dummyNode);
		inMemoryChannel.setSerializer(new SopremoRecordSerializerFactory(layout, registry));

		for (Channel originalChannel : channelsToBeChanged) {
			Channel channelWithNewSource = new ForwardingChannel(dummyNode, originalChannel);
			PlanNode target = originalChannel.getTarget();
			if (target instanceof SingleInputPlanNode)
				ReflectUtil.setField(target, "input", channelWithNewSource);
			else if (target instanceof DualInputPlanNode) {
				if (target instanceof WorksetIterationPlanNode) {
					throw new UnsupportedOperationException();
				}
				if (((DualInputPlanNode) target).getInput1() == originalChannel)
					ReflectUtil.setField(target, "input1", channelWithNewSource);
				else if (((DualInputPlanNode) target).getInput2() == originalChannel)
					ReflectUtil.setField(target, "input2", channelWithNewSource);
				else
					throw new IllegalStateException();
			} else
				throw new UnsupportedOperationException();

			outgoingChannels.set(outgoingChannels.indexOf(originalChannel), inMemoryChannel);
		}
		return dummyNode;
	}

	private void addIdentityMapsToOutputsWithMultipleChannels(OptimizedPlan plan) {
		plan.accept(new Visitor<PlanNode>() {
			private Set<PlanNode> visited = new IdentitySet<PlanNode>();

			@Override
			public boolean preVisit(PlanNode visitable) {
				if (this.visited.add(visitable)) {
					if (visitable instanceof WorksetIterationPlanNode)
						((WorksetIterationPlanNode) visitable).acceptForStepFunction(this);
					checkNode(visitable);
					return true;
				}
				return false;
			}

			@Override
			public void postVisit(PlanNode visitable) {
			}
		});
	}

	/**
	 * @param node
	 * @return
	 */
	private void checkNode(PlanNode node) {
		if (node instanceof WorksetIterationPlanNode)
			node = addDummyNode(node, node.getOutgoingChannels(), node.getOutgoingChannels(),
				((SopremoRecordSerializerFactory) ((WorksetIterationPlanNode) node).getSolutionSetSerializer()).getLayout());

		List<Channel> outgoingChannels = node.getOutgoingChannels();
		if (outgoingChannels.size() > 1) {
			ListMultimap<SopremoRecordLayout, Channel> layouts = ArrayListMultimap.create();
			for (Channel channel : outgoingChannels)
				layouts.put(((SopremoRecordSerializerFactory) channel.getSerializer()).getLayout(), channel);

			// we need indeed different layouts; create a dummy map node for each layout
			if (layouts.keySet().size() > 1) {
				// layout to dummy node is empty, so we can ignore all nodes that also require empty layout
				layouts.removeAll(SopremoRecordLayout.EMPTY);
				for (SopremoRecordLayout layout : layouts.keySet()) {
					List<Channel> channelsToBeChanged = layouts.get(layout);
					addDummyNode(node, outgoingChannels, channelsToBeChanged, SopremoRecordLayout.EMPTY);
				}
			}
			fixInputs(node);
		}
		
	}

	private void fixInputs(PlanNode node) {
		if(node instanceof NAryUnionPlanNode) {
			Iterable<Channel> inputs = node.getInputs();
			for (Channel channel : inputs) {
				channel.setSerializer(new SopremoRecordSerializerFactory(SopremoRecordLayout.EMPTY, this.typeRegistry));
				if(channel.getSource() instanceof SourcePlanNode)
					((SourcePlanNode)channel.getSource()).setSerializer(new SopremoRecordSerializerFactory(SopremoRecordLayout.EMPTY, this.typeRegistry));
			}
		}
	}

	private void addOrderingToSchema(final Ordering o, final SopremoRecordSchema schema) {
		for (int i = 0; i < o.getNumberOfFields(); i++)
			schema.add(o.getFieldNumber(i));
	}

	private void removeDummyNodes(final OptimizedPlan plan) {
		Collection<SinkPlanNode> dataSinks = plan.getDataSinks();
		for (SinkPlanNode sinkPlanNode : new ArrayList<SinkPlanNode>(dataSinks))
			if (sinkPlanNode.getPlanNode().getPactContract().getUserCodeWrapper().getUserCodeClass() == DevNullOutputFormat.class) {
				dataSinks.remove(sinkPlanNode);
				// should be union node; code is written to be easily performed on other node types, but that could
				// result in unforseeable side-effects
				NAryUnionPlanNode source = (NAryUnionPlanNode) sinkPlanNode.getInput().getSource();
				Iterable<Channel> inputs = source.getListOfInputs();
				for (Channel channel : inputs) {
					// here we depend on a modifiable return
					channel.getSource().getOutgoingChannels().remove(channel);
				}
			}
	}

	private void setOrdering(final Channel input, final Ordering localOrder) {
		if (localOrder != null) {
			Ordering mergedOrder;
			if (input.getLocalProperties().getOrdering() != null) {
				mergedOrder = input.getLocalProperties().getOrdering().clone();

				final int[] fieldPositions = localOrder.getFieldPositions();
				final Order[] fieldOrders = localOrder.getFieldOrders();
				final IntList coveredFields = new IntArrayList(mergedOrder.getFieldPositions());

				for (int index = 0; index < fieldOrders.length; index++)
					if (!coveredFields.contains(fieldPositions[index]))
						mergedOrder.appendOrdering(fieldPositions[index], null, fieldOrders[index]);
			} else
				mergedOrder = localOrder;
			input.setLocalStrategy(input.getLocalStrategy(), new FieldList(mergedOrder.getFieldPositions()),
				mergedOrder.getFieldSortDirections());
		}
	}

	/**
	 * @author arvid
	 */
	public static class ForwardingChannel extends Channel {
		private Channel originalChannel;

		public ForwardingChannel(SingleInputPlanNode inputNode, Channel originalChannel) {
			super(inputNode);
			this.originalChannel = originalChannel;
		}

		/**
		 * @see eu.stratosphere.compiler.plan.Channel#adjustGlobalPropertiesForFullParallelismChange()
		 */
		@Override
		public void adjustGlobalPropertiesForFullParallelismChange() {
			this.originalChannel.adjustGlobalPropertiesForFullParallelismChange();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getDataDistribution()
		 */
		@Override
		public DataDistribution getDataDistribution() {
			return this.originalChannel.getDataDistribution();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getGlobalProperties()
		 */
		@Override
		public GlobalProperties getGlobalProperties() {
			return this.originalChannel.getGlobalProperties();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getLocalProperties()
		 */
		@Override
		public LocalProperties getLocalProperties() {
			return this.originalChannel.getLocalProperties();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getLocalStrategy()
		 */
		@Override
		public LocalStrategy getLocalStrategy() {
			return this.originalChannel.getLocalStrategy();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getLocalStrategyComparator()
		 */
		@Override
		public TypeComparatorFactory<?> getLocalStrategyComparator() {
			return this.originalChannel.getLocalStrategyComparator();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getLocalStrategyKeys()
		 */
		@Override
		public FieldList getLocalStrategyKeys() {
			return this.originalChannel.getLocalStrategyKeys();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getLocalStrategySortOrder()
		 */
		@Override
		public boolean[] getLocalStrategySortOrder() {
			return this.originalChannel.getLocalStrategySortOrder();
		}

		/**
		 * @return
		 * @see org.apache.flink.compiler.plan.Channel#getRelativeTempMemory()
		 */
		@Override
		public double getRelativeTempMemory() {
			return this.originalChannel.getRelativeTempMemory();
		}

		/**
		 * @param relativeTempMemory
		 * @see org.apache.flink.compiler.plan.Channel#setRelativeTempMemory(double)
		 */
		@Override
		public void setRelativeTempMemory(double relativeTempMemory) {
			this.originalChannel.setRelativeTempMemory(relativeTempMemory);
		}

		/**
		 * @return
		 * @see org.apache.flink.compiler.plan.Channel#getRelativeMemoryGlobalStrategy()
		 */
		@Override
		public double getRelativeMemoryGlobalStrategy() {
			return this.originalChannel.getRelativeMemoryGlobalStrategy();
		}

		/**
		 * @param relativeMemoryGlobalStrategy
		 * @see org.apache.flink.compiler.plan.Channel#setRelativeMemoryGlobalStrategy(double)
		 */
		@Override
		public void setRelativeMemoryGlobalStrategy(double relativeMemoryGlobalStrategy) {
			this.originalChannel.setRelativeMemoryGlobalStrategy(relativeMemoryGlobalStrategy);
		}

		/**
		 * @return
		 * @see org.apache.flink.compiler.plan.Channel#getRelativeMemoryLocalStrategy()
		 */
		@Override
		public double getRelativeMemoryLocalStrategy() {
			return this.originalChannel.getRelativeMemoryLocalStrategy();
		}

		/**
		 * @param relativeMemoryLocalStrategy
		 * @see org.apache.flink.compiler.plan.Channel#setRelativeMemoryLocalStrategy(double)
		 */
		@Override
		public void setRelativeMemoryLocalStrategy(double relativeMemoryLocalStrategy) {
			this.originalChannel.setRelativeMemoryLocalStrategy(relativeMemoryLocalStrategy);
		}

		/**
		 * @return
		 * @see org.apache.flink.compiler.plan.Channel#isOnDynamicPath()
		 */
		@Override
		public boolean isOnDynamicPath() {
			return this.originalChannel.isOnDynamicPath();
		}

		/**
		 * @return
		 * @see org.apache.flink.compiler.plan.Channel#getCostWeight()
		 */
		@Override
		public int getCostWeight() {
			return this.originalChannel.getCostWeight();
		}

		/**
		 * @return
		 * @see org.apache.flink.compiler.plan.Channel#getEstimatedOutputSize()
		 */
		@Override
		public long getEstimatedOutputSize() {
			return this.originalChannel.getEstimatedOutputSize();
		}

		/**
		 * @return
		 * @see org.apache.flink.compiler.plan.Channel#getEstimatedNumRecords()
		 */
		@Override
		public long getEstimatedNumRecords() {
			return this.originalChannel.getEstimatedNumRecords();
		}

		/**
		 * @return
		 * @see org.apache.flink.compiler.plan.Channel#getEstimatedAvgWidthPerOutputRecord()
		 */
		@Override
		public float getEstimatedAvgWidthPerOutputRecord() {
			return this.originalChannel.getEstimatedAvgWidthPerOutputRecord();
		}

		/**
		 * @return
		 * @see org.apache.flink.compiler.plan.Channel#getRequiredGlobalProps()
		 */
		@Override
		public RequestedGlobalProperties getRequiredGlobalProps() {
			return this.originalChannel.getRequiredGlobalProps();
		}

		/**
		 * @param requiredGlobalProps
		 * @see org.apache.flink.compiler.plan.Channel#setRequiredGlobalProps(org.apache.flink.compiler.dataproperties.RequestedGlobalProperties)
		 */
		@Override
		public void setRequiredGlobalProps(RequestedGlobalProperties requiredGlobalProps) {
			this.originalChannel.setRequiredGlobalProps(requiredGlobalProps);
		}

		/**
		 * @return
		 * @see org.apache.flink.compiler.plan.Channel#getRequiredLocalProps()
		 */
		@Override
		public RequestedLocalProperties getRequiredLocalProps() {
			return this.originalChannel.getRequiredLocalProps();
		}

		/**
		 * @param requiredLocalProps
		 * @see org.apache.flink.compiler.plan.Channel#setRequiredLocalProps(org.apache.flink.compiler.dataproperties.RequestedLocalProperties)
		 */
		@Override
		public void setRequiredLocalProps(RequestedLocalProperties requiredLocalProps) {
			this.originalChannel.setRequiredLocalProps(requiredLocalProps);
		}

		/**
		 * @param newUnionNode
		 * @see org.apache.flink.compiler.plan.Channel#swapUnionNodes(org.apache.flink.compiler.plan.PlanNode)
		 */
		@Override
		public void swapUnionNodes(PlanNode newUnionNode) {
			this.originalChannel.swapUnionNodes(newUnionNode);
		}

		/**
		 * @return
		 * @see org.apache.flink.compiler.plan.Channel#getMaxDepth()
		 */
		@Override
		public int getMaxDepth() {
			return this.originalChannel.getMaxDepth();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getReplicationFactor()
		 */
		@Override
		public int getReplicationFactor() {
			return this.originalChannel.getReplicationFactor();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getSerializer()
		 */
		@Override
		public TypeSerializerFactory<?> getSerializer() {
			return this.originalChannel.getSerializer();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getShipStrategy()
		 */
		@Override
		public ShipStrategyType getShipStrategy() {
			return this.originalChannel.getShipStrategy();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getShipStrategyComparator()
		 */
		@Override
		public TypeComparatorFactory<?> getShipStrategyComparator() {
			return this.originalChannel.getShipStrategyComparator();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getShipStrategyKeys()
		 */
		@Override
		public FieldList getShipStrategyKeys() {
			return this.originalChannel.getShipStrategyKeys();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getShipStrategySortOrder()
		 */
		@Override
		public boolean[] getShipStrategySortOrder() {
			return this.originalChannel.getShipStrategySortOrder();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getTarget()
		 */
		@Override
		public PlanNode getTarget() {
			return this.originalChannel.getTarget();
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#getTempMode()
		 */
		@Override
		public TempMode getTempMode() {
			return this.originalChannel.getTempMode();
		}

		/**
		 * @param dataDistribution
		 * @see eu.stratosphere.compiler.plan.Channel#setDataDistribution(eu.stratosphere.api.common.distributions.DataDistribution)
		 */
		@Override
		public void setDataDistribution(DataDistribution dataDistribution) {
			this.originalChannel.setDataDistribution(dataDistribution);
		}

		/**
		 * @param strategy
		 * @see eu.stratosphere.compiler.plan.Channel#setLocalStrategy(eu.stratosphere.pact.runtime.task.util.LocalStrategy)
		 */
		@Override
		public void setLocalStrategy(LocalStrategy strategy) {
			this.originalChannel.setLocalStrategy(strategy);
		}

		/**
		 * @param strategy
		 * @param keys
		 * @see eu.stratosphere.compiler.plan.Channel#setLocalStrategy(eu.stratosphere.pact.runtime.task.util.LocalStrategy,
		 *      eu.stratosphere.api.common.operators.util.FieldList)
		 */
		@Override
		public void setLocalStrategy(LocalStrategy strategy, FieldList keys) {
			this.originalChannel.setLocalStrategy(strategy, keys);
		}

		/**
		 * @param strategy
		 * @param keys
		 * @param sortDirection
		 * @see eu.stratosphere.compiler.plan.Channel#setLocalStrategy(eu.stratosphere.pact.runtime.task.util.LocalStrategy,
		 *      eu.stratosphere.api.common.operators.util.FieldList, boolean[])
		 */
		@Override
		public void setLocalStrategy(LocalStrategy strategy, FieldList keys, boolean[] sortDirection) {
			this.originalChannel.setLocalStrategy(strategy, keys, sortDirection);
		}

		/**
		 * @param localStrategyComparator
		 * @see eu.stratosphere.compiler.plan.Channel#setLocalStrategyComparator(eu.stratosphere.api.common.typeutils.TypeComparatorFactory)
		 */
		@Override
		public void setLocalStrategyComparator(TypeComparatorFactory<?> localStrategyComparator) {
			this.originalChannel.setLocalStrategyComparator(localStrategyComparator);
		}

		/**
		 * @param factor
		 * @see eu.stratosphere.compiler.plan.Channel#setReplicationFactor(int)
		 */
		@Override
		public void setReplicationFactor(int factor) {
			this.originalChannel.setReplicationFactor(factor);
		}

		/**
		 * @param serializer
		 * @see eu.stratosphere.compiler.plan.Channel#setSerializer(eu.stratosphere.api.common.typeutils.TypeSerializerFactory)
		 */
		@Override
		public void setSerializer(TypeSerializerFactory<?> serializer) {
			this.originalChannel.setSerializer(serializer);
		}

		/**
		 * @param strategy
		 * @see eu.stratosphere.compiler.plan.Channel#setShipStrategy(eu.stratosphere.pact.runtime.shipping.ShipStrategyType)
		 */
		@Override
		public void setShipStrategy(ShipStrategyType strategy) {
			this.originalChannel.setShipStrategy(strategy);
		}

		/**
		 * @param strategy
		 * @param keys
		 * @see eu.stratosphere.compiler.plan.Channel#setShipStrategy(eu.stratosphere.pact.runtime.shipping.ShipStrategyType,
		 *      eu.stratosphere.api.common.operators.util.FieldList)
		 */
		@Override
		public void setShipStrategy(ShipStrategyType strategy, FieldList keys) {
			this.originalChannel.setShipStrategy(strategy, keys);
		}

		/**
		 * @param strategy
		 * @param keys
		 * @param sortDirection
		 * @see eu.stratosphere.compiler.plan.Channel#setShipStrategy(eu.stratosphere.pact.runtime.shipping.ShipStrategyType,
		 *      eu.stratosphere.api.common.operators.util.FieldList, boolean[])
		 */
		@Override
		public void setShipStrategy(ShipStrategyType strategy, FieldList keys, boolean[] sortDirection) {
			this.originalChannel.setShipStrategy(strategy, keys, sortDirection);
		}

		/**
		 * @param shipStrategyComparator
		 * @see eu.stratosphere.compiler.plan.Channel#setShipStrategyComparator(eu.stratosphere.api.common.typeutils.TypeComparatorFactory)
		 */
		@Override
		public void setShipStrategyComparator(TypeComparatorFactory<?> shipStrategyComparator) {
			this.originalChannel.setShipStrategyComparator(shipStrategyComparator);
		}

		/**
		 * @param target
		 * @see eu.stratosphere.compiler.plan.Channel#setTarget(eu.stratosphere.compiler.plan.PlanNode)
		 */
		@Override
		public void setTarget(PlanNode target) {
			this.originalChannel.setTarget(target);
		}

		/**
		 * @param tempMode
		 * @see eu.stratosphere.compiler.plan.Channel#setTempMode(eu.stratosphere.compiler.dag.TempMode)
		 */
		@Override
		public void setTempMode(TempMode tempMode) {
			this.originalChannel.setTempMode(tempMode);
		}

		/**
		 * @return
		 * @see eu.stratosphere.compiler.plan.Channel#toString()
		 */
		@Override
		public String toString() {
			return "Channel (" + this.getSource() + (this.getTarget() == null ? ')' : ") -> (" + this.getTarget() + ')') +
				'[' + this.getShipStrategy() + "] [" + this.getLocalStrategy() + "] " +
				(this.getTempMode() == null || this.getTempMode() == TempMode.NONE ? "{NO-TEMP}" : this.getTempMode());
		}

	}
}