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

package eu.stratosphere.sopremo.serialization;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import eu.stratosphere.api.common.typeutils.TypeSerializer;
import eu.stratosphere.core.memory.DataInputView;
import eu.stratosphere.core.memory.DataOutputView;
import eu.stratosphere.sopremo.SopremoEnvironment;
import eu.stratosphere.sopremo.packages.ITypeRegistry;

/**
 * Implementation of the (de)serialization and copying logic for the {@link SopremoRecord}.
 */
public class SopremoRecordSerializer extends TypeSerializer<SopremoRecord> {
	private SopremoRecordLayout layout;

	private ITypeRegistry typeRegistry;

	private transient SopremoRecord writeRecord, readRecord;

	/**
	 * Creates a new instance of the SopremoRecordSerializers. Private to prevent instantiation.
	 */
	SopremoRecordSerializer(final SopremoRecordLayout layout, final ITypeRegistry typeRegistry) {
		if (layout == null)
			throw new NullPointerException();
		this.layout = layout;
		this.typeRegistry = typeRegistry;
	}

	private void readObject(ObjectInputStream ois) {
		Input input = new Input(ois);
		final Kryo kryo = SopremoEnvironment.getInstance().getEvaluationContext().getKryo();
		this.layout = kryo.readObject(input, SopremoRecordLayout.class);
		this.typeRegistry = (ITypeRegistry) kryo.readClassAndObject(input);
	}

	private void writeObject(ObjectOutputStream oos) {
		final Output output = new Output(oos);
		final Kryo kryo = SopremoEnvironment.getInstance().getEvaluationContext().getKryo();
		kryo.writeObject(output, this.layout);
		kryo.writeClassAndObject(output, this.typeRegistry);
		output.flush();
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

	// --------------------------------------------------------------------------------------------

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.serialization.TypeAccessorsV2#copy(eu.stratosphere.core.memory.
	 * DataInputViewV2, eu.stratosphere.core.memory.DataOutputViewV2)
	 */
	@Override
	public void copy(final DataInputView source, final DataOutputView target) throws IOException {
		final int numKeys = this.layout.getNumKeys();
		for (int index = 0; index < numKeys; index++)
			target.writeInt(source.readInt());
		final int size = source.readInt();
		target.writeInt(size);
		target.write(source, size);
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.api.common.typeutils.TypeSerializer#copy(java.lang.Object, java.lang.Object)
	 */
	@Override
	public SopremoRecord copy(SopremoRecord from, SopremoRecord reuse) {
		from.copyTo(reuse);
		return reuse;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.serialization.TypeAccessors#createInstance()
	 */
	@Override
	public SopremoRecord createInstance() {
		return new SopremoRecord(this.layout, this.typeRegistry);
	}

	// --------------------------------------------------------------------------------------------

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.serialization.TypeAccessorsV2#deserialize(java.lang.Object,
	 * eu.stratosphere.core.memory.DataInputViewV2)
	 */
	@Override
	public SopremoRecord deserialize(final SopremoRecord record, final DataInputView source) throws IOException {
		if (this.readRecord != record) {
			this.readRecord = record;
			this.readRecord.init(this.layout, this.typeRegistry);
		}
		record.read(source);
		record.parseNode();
		return record;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.serialization.TypeAccessorsV2#getLength()
	 */
	@Override
	public int getLength() {
		return -1;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.serialization.TypeAccessorsV2#serialize(java.lang.Object,
	 * eu.stratosphere.core.memory.DataOutputViewV2)
	 */
	@Override
	public void serialize(final SopremoRecord record, final DataOutputView target) throws IOException {
		if (this.writeRecord != record) {
			this.writeRecord = record;
			this.writeRecord.init(this.layout, this.typeRegistry);
		}
		record.write(target);
	}

	/* (non-Javadoc)
	 * @see eu.stratosphere.api.common.typeutils.TypeSerializer#isImmutableType()
	 */
	@Override
	public boolean isImmutableType() {
		return false;
	}

	/* (non-Javadoc)
	 * @see eu.stratosphere.api.common.typeutils.TypeSerializer#isStateful()
	 */
	@Override
	public boolean isStateful() {
		return true;
	}
}
