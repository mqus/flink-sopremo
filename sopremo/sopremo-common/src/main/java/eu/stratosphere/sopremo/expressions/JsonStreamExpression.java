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
package eu.stratosphere.sopremo.expressions;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import javolution.text.TypeFormat;

import org.apache.flink.api.common.io.InputFormat;
import org.apache.flink.api.common.operators.GenericDataSourceBase;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.FileInputSplit;
import org.apache.flink.core.fs.FileStatus;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.Path;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.serializers.FieldSerializer;

import eu.stratosphere.sopremo.io.SopremoFormat;
import eu.stratosphere.sopremo.io.SopremoFormat.SopremoFileInputFormat;
import eu.stratosphere.sopremo.io.SopremoOperatorInfoHelper;
import eu.stratosphere.sopremo.io.Source;
import eu.stratosphere.sopremo.operator.JsonStream;
import eu.stratosphere.sopremo.operator.Operator;
import eu.stratosphere.sopremo.operator.Operator.Output;
import eu.stratosphere.sopremo.serialization.SopremoRecord;
import eu.stratosphere.sopremo.type.ArrayNode;
import eu.stratosphere.sopremo.type.IArrayNode;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.util.reflect.ReflectUtil;

/**
 * Tag expression for nested operators.
 */
@DefaultSerializer(JsonStreamExpression.JsonStreamExpressionSerializer.class)
public class JsonStreamExpression extends EvaluationExpression {
	private transient WeakReference<JsonStreamExpression> equalStream;

	private final int inputIndex;

	private final JsonStream stream;

	private transient IJsonNode values;

	/**
	 * Initializes a JsonStreamExpression with the given index.
	 * 
	 * @param inputIndex
	 *        the index
	 */
	public JsonStreamExpression(final int inputIndex) {
		this(null, inputIndex);
	}

	/**
	 * Initializes a JsonStreamExpression with the given {@link JsonStream}.
	 * 
	 * @param stream
	 *        the stream that should be used
	 */
	public JsonStreamExpression(final JsonStream stream) {
		this(stream, -1);
	}

	/**
	 * Initializes a JsonStreamExpression with the given {@link JsonStream} and index.
	 * 
	 * @param stream
	 *        the stream that should be used
	 * @param inputIndex
	 *        the index
	 */
	public JsonStreamExpression(final JsonStream stream, final int inputIndex) {
		this.stream = stream;
		this.inputIndex = inputIndex;
	}

	/**
	 * Initializes JsonStreamExpression.
	 */
	JsonStreamExpression() {
		this.stream = null;
		this.inputIndex = 0;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.expressions.UnevaluableExpression#toString(java.lang.StringBuilder)
	 */
	@Override
	public void appendAsString(final Appendable appendable) throws IOException {
		if (this.stream != null)
			appendable.append(this.stream.getSource().getOperator().getName()).append("@");
		if (this.inputIndex != -1)
			TypeFormat.format(this.inputIndex, appendable);
		else if (this.stream != null)
			TypeFormat.format(this.stream.getSource().getIndex(), appendable);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		final JsonStreamExpression other = (JsonStreamExpression) obj;
		final Output thisSource = this.stream.getSource();
		final Output otherSource = other.stream.getSource();
		if (thisSource.getIndex() != otherSource.getIndex())
			return false;
		// at this point we assume that both stream expression could be equal
		if (this.equalStream != null && this.equalStream.get() == other)
			return true;

		this.equalStream = new WeakReference<JsonStreamExpression>(other);
		// here the actual recursion is very likely to occur
		return thisSource.getOperator().equals(otherSource.getOperator());
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.expressions.EvaluationExpression#evaluate(eu.stratosphere.sopremo.type.IJsonNode)
	 */
	@Override
	public IJsonNode evaluate(final IJsonNode node) {
		if (this.values == null) {
			if (!(this.stream instanceof Source))
				throw new IllegalStateException("Sopremo currently only supports Sources to be used as side channels");
			Source source = (Source) this.stream;
			if (source.isAdhoc())
				this.values = source.getAdhocValues();
			else {
				final SopremoFormat format = source.getFormat();
				final Configuration configuration = new Configuration();
				final GenericDataSourceBase<SopremoRecord, InputFormat<SopremoRecord, ?>> dataSource =
					new GenericDataSourceBase<SopremoRecord, InputFormat<SopremoRecord, ?>>(format.getInputFormat(),
						SopremoOperatorInfoHelper.source(), "Side");
				format.configureForInput(configuration, dataSource, source.getInputPath());
				try {
					this.values = this.readValues(format, configuration, this.getInputs(source));
				} catch (final Throwable e) {
					throw new IllegalStateException("Cannot read values from side channel", e);
				}
			}
		}
		return this.values;
	}

	/**
	 * Returns the inputIndex.
	 * 
	 * @return the inputIndex
	 */
	public int getInputIndex() {
		return this.inputIndex;
	}

	/**
	 * Returns the input index of the stream. If the inputIndex is set, it is directly return. Else the stream will be
	 * looked up in the provided list of inputs.
	 * 
	 * @param inputs
	 *        the inputs in which to look up the index
	 * @return the index of the stream wrapped in this expression
	 */
	public int getInputIndex(final List<JsonStream> inputs) {
		if (this.inputIndex != -1)
			return this.inputIndex;
		return inputs.indexOf(this.stream);
	}

	/**
	 * Returns the JsonStream
	 * 
	 * @return the stream
	 */
	public JsonStream getStream() {
		return this.stream;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + this.stream.getSource().hashCode();
		return result;
	}

	/**
	 * Creates an {@link InputSelection} based on this expressions stream an index.
	 * 
	 * @param operator
	 * @return the created InputSelection
	 */
	public EvaluationExpression toInputSelection(final Operator<?> operator) {
		InputSelection inputSelection;
		if (this.inputIndex != -1)
			inputSelection = new InputSelection(this.inputIndex);
		else if (operator.getSource() == this.stream.getSource())
			inputSelection = new InputSelection(0);
		else {
			final int index = operator.getInputs().indexOf(this.stream.getSource());
			if (index == -1)
				return this;
			inputSelection = new InputSelection(index);
		}
		return inputSelection;
	}

	private List<Path> getInputs(Source source) throws IOException {
		final List<Path> inputs = new ArrayList<Path>();

		Path nephelePath = new Path(source.getInputPath());
		FileSystem fs = nephelePath.getFileSystem();
		FileStatus fileStatus = fs.getFileStatus(nephelePath);
		if (!fileStatus.isDir())
			inputs.add(fileStatus.getPath());
		else
			for (FileStatus status : fs.listStatus(nephelePath))
				inputs.add(status.getPath());
		return inputs;
	}

	private IArrayNode<IJsonNode> readValues(final SopremoFormat format, final Configuration configuration, final List<Path> inputs)
			throws IOException {
		final IArrayNode<IJsonNode> array = new ArrayNode<IJsonNode>();
		final SopremoFileInputFormat input = (SopremoFileInputFormat) ReflectUtil.newInstance(format.getInputFormat());
		for (final Path path : inputs) {
			input.setFilePath(path);
			input.configure(configuration);
			for (final FileInputSplit fileInputSplit : input.createInputSplits(1)) {
				input.open(fileInputSplit);
				while (!input.reachedEnd()) {
					final IJsonNode value = input.nextValue();
					if (value != null)
						array.add(value.clone());
				}
			}
		}
		return array;
	}

	/**
	 * @author arvid
	 */
	public static class JsonStreamExpressionSerializer extends Serializer<JsonStreamExpression> {
		private final FieldSerializer<JsonStreamExpression> delegate;

		/**
		 * Initializes JsonStreamExpression.JsonStreamExpressionSerializer.
		 */
		public JsonStreamExpressionSerializer(Kryo kryo) {
			this.delegate = new FieldSerializer<JsonStreamExpression>(kryo, JsonStreamExpression.class);
		}

		/*
		 * (non-Javadoc)
		 * @see com.esotericsoftware.kryo.Serializer#copy(com.esotericsoftware.kryo.Kryo, java.lang.Object)
		 */
		@Override
		public JsonStreamExpression copy(Kryo kryo, JsonStreamExpression original) {
			return new JsonStreamExpression(original.stream, original.inputIndex);
		}

		/*
		 * (non-Javadoc)
		 * @see com.esotericsoftware.kryo.Serializer#read(com.esotericsoftware.kryo.Kryo,
		 * com.esotericsoftware.kryo.io.Input, java.lang.Class)
		 */
		@Override
		public JsonStreamExpression read(Kryo kryo, Input input, Class<JsonStreamExpression> type) {
			return this.delegate.read(kryo, input, type);
		}

		/*
		 * (non-Javadoc)
		 * @see com.esotericsoftware.kryo.Serializer#write(com.esotericsoftware.kryo.Kryo,
		 * com.esotericsoftware.kryo.io.Output, java.lang.Object)
		 */
		@Override
		public void write(Kryo kryo, com.esotericsoftware.kryo.io.Output output, JsonStreamExpression object) {
			this.delegate.write(kryo, output, object);
		}

	}
}
